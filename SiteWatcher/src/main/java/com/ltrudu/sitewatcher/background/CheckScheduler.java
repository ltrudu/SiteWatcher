package com.ltrudu.sitewatcher.background;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.Calendar;

/**
 * Singleton scheduler for managing site check alarms.
 * Handles scheduling and canceling of exact alarms for site monitoring.
 *
 * Uses AlarmManager with setExactAndAllowWhileIdle() for reliable alarm
 * delivery even when the device is in Doze mode.
 */
public final class CheckScheduler {

    private static final String TAG = "CheckScheduler";
    private static final String EXTRA_SITE_ID = "extra_site_id";

    @SuppressLint("StaticFieldLeak")
    private static volatile CheckScheduler instance;
    private final Context context;
    private final AlarmManager alarmManager;

    /**
     * Private constructor for singleton pattern.
     * @param context Application context
     */
    private CheckScheduler(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Get the singleton instance of CheckScheduler.
     * @param context Application context
     * @return The CheckScheduler instance
     */
    @NonNull
    public static CheckScheduler getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (CheckScheduler.class) {
                if (instance == null) {
                    instance = new CheckScheduler(context);
                }
            }
        }
        return instance;
    }

    /**
     * Check if the app can schedule exact alarms.
     * On Android 12+ (API 31+), this requires user permission.
     * @return true if exact alarms can be scheduled
     */
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms();
        }
        return true; // Always allowed on older versions
    }

    /**
     * Schedule a check for a specific site.
     * Calculates the next alarm time based on the site's schedule configuration.
     * Uses exact alarms if permitted, otherwise falls back to inexact alarms.
     * @param site The WatchedSite to schedule
     */
    public void scheduleCheck(@NonNull WatchedSite site) {
        if (!site.isEnabled()) {
            Logger.d(TAG, "Site " + site.getId() + " is disabled, skipping schedule");
            return;
        }

        long nextAlarmTime = calculateNextAlarmTime(site);
        if (nextAlarmTime <= 0) {
            Logger.w(TAG, "Could not calculate next alarm time for site " + site.getId());
            return;
        }

        PendingIntent pendingIntent = createPendingIntent(site.getId());

        try {
            scheduleAlarm(nextAlarmTime, pendingIntent);
            Logger.d(TAG, "Scheduled check for site " + site.getId() +
                    " at " + new java.util.Date(nextAlarmTime));
        } catch (SecurityException e) {
            Logger.e(TAG, "Failed to schedule alarm for site " + site.getId(), e);
            // Try with inexact alarm as fallback
            scheduleInexactAlarm(nextAlarmTime, pendingIntent);
        }
    }

    /**
     * Schedule an exact alarm if permitted, otherwise use inexact.
     */
    @SuppressLint("ScheduleExactAlarm")
    private void scheduleAlarm(long triggerTime, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires checking permission first
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
                Logger.d(TAG, "Scheduled exact alarm");
            } else {
                // Fall back to inexact alarm
                Logger.w(TAG, "Exact alarms not permitted, using inexact alarm");
                scheduleInexactAlarm(triggerTime, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ with Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
            Logger.d(TAG, "Scheduled exact alarm (pre-S)");
        } else {
            // Older Android versions
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
            Logger.d(TAG, "Scheduled exact alarm (legacy)");
        }
    }

    /**
     * Schedule an inexact alarm as fallback when exact alarms aren't permitted.
     */
    private void scheduleInexactAlarm(long triggerTime, PendingIntent pendingIntent) {
        // Use setAndAllowWhileIdle for better reliability in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }
        Logger.d(TAG, "Scheduled inexact alarm at " + new java.util.Date(triggerTime));
    }

    /**
     * Cancel a scheduled check for a specific site.
     * @param siteId The ID of the site to cancel
     */
    public void cancelCheck(long siteId) {
        PendingIntent pendingIntent = createPendingIntent(siteId);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Logger.d(TAG, "Cancelled check for site " + siteId);
    }

    /**
     * Calculate the next alarm time for a site based on its schedule configuration.
     * @param site The WatchedSite
     * @return The next alarm time in milliseconds, or -1 if unable to calculate
     */
    private long calculateNextAlarmTime(@NonNull WatchedSite site) {
        Calendar now = Calendar.getInstance();

        if (site.getScheduleType() == ScheduleType.PERIODIC) {
            return calculatePeriodicAlarmTime(site, now);
        } else {
            return calculateSpecificHourAlarmTime(site, now);
        }
    }

    /**
     * Calculate next alarm time for periodic schedule.
     * @param site The WatchedSite
     * @param now Current time
     * @return Next alarm time in milliseconds
     */
    private long calculatePeriodicAlarmTime(@NonNull WatchedSite site, @NonNull Calendar now) {
        int intervalMinutes = site.getPeriodicIntervalMinutes();
        if (intervalMinutes <= 0) {
            intervalMinutes = 60; // Default to 1 hour
        }

        Calendar nextAlarm = (Calendar) now.clone();

        // If we have a last check time, calculate from there
        if (site.getLastCheckTime() > 0) {
            nextAlarm.setTimeInMillis(site.getLastCheckTime());
            nextAlarm.add(Calendar.MINUTE, intervalMinutes);

            // If the calculated time is in the past, calculate from now
            if (nextAlarm.before(now)) {
                nextAlarm = (Calendar) now.clone();
                nextAlarm.add(Calendar.MINUTE, intervalMinutes);
            }
        } else {
            // No previous check, schedule for interval from now
            nextAlarm.add(Calendar.MINUTE, intervalMinutes);
        }

        // Check if the day is enabled
        return adjustForEnabledDays(site, nextAlarm, intervalMinutes);
    }

    /**
     * Calculate next alarm time for specific hour schedule.
     * @param site The WatchedSite
     * @param now Current time
     * @return Next alarm time in milliseconds
     */
    private long calculateSpecificHourAlarmTime(@NonNull WatchedSite site, @NonNull Calendar now) {
        Calendar nextAlarm = (Calendar) now.clone();
        nextAlarm.set(Calendar.HOUR_OF_DAY, site.getScheduleHour());
        nextAlarm.set(Calendar.MINUTE, site.getScheduleMinute());
        nextAlarm.set(Calendar.SECOND, 0);
        nextAlarm.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, move to tomorrow
        if (nextAlarm.before(now)) {
            nextAlarm.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Find the next enabled day
        return findNextEnabledDay(site, nextAlarm);
    }

    /**
     * Adjust alarm time for enabled days (periodic schedule).
     * @param site The WatchedSite
     * @param alarm The proposed alarm time
     * @param intervalMinutes The interval in minutes
     * @return Adjusted alarm time in milliseconds
     */
    private long adjustForEnabledDays(@NonNull WatchedSite site, @NonNull Calendar alarm,
            int intervalMinutes) {
        int enabledDays = site.getEnabledDays();
        if (enabledDays == WatchedSite.ALL_DAYS) {
            return alarm.getTimeInMillis();
        }

        int attempts = 0;
        while (attempts < 7) {
            int dayOfWeek = alarm.get(Calendar.DAY_OF_WEEK);
            int dayBitmask = getDayBitmask(dayOfWeek);

            if ((enabledDays & dayBitmask) != 0) {
                return alarm.getTimeInMillis();
            }

            // Move to start of next day
            alarm.add(Calendar.DAY_OF_YEAR, 1);
            alarm.set(Calendar.HOUR_OF_DAY, 0);
            alarm.set(Calendar.MINUTE, 0);
            alarm.set(Calendar.SECOND, 0);
            alarm.add(Calendar.MINUTE, intervalMinutes);
            attempts++;
        }

        // Fallback: no enabled days, return original time
        return alarm.getTimeInMillis();
    }

    /**
     * Find the next enabled day (specific hour schedule).
     * @param site The WatchedSite
     * @param alarm The proposed alarm time
     * @return Adjusted alarm time in milliseconds
     */
    private long findNextEnabledDay(@NonNull WatchedSite site, @NonNull Calendar alarm) {
        int enabledDays = site.getEnabledDays();
        if (enabledDays == WatchedSite.ALL_DAYS) {
            return alarm.getTimeInMillis();
        }

        int attempts = 0;
        while (attempts < 7) {
            int dayOfWeek = alarm.get(Calendar.DAY_OF_WEEK);
            int dayBitmask = getDayBitmask(dayOfWeek);

            if ((enabledDays & dayBitmask) != 0) {
                return alarm.getTimeInMillis();
            }

            alarm.add(Calendar.DAY_OF_YEAR, 1);
            attempts++;
        }

        // Fallback: no enabled days, return original time
        return alarm.getTimeInMillis();
    }

    /**
     * Convert Calendar day of week to WatchedSite day bitmask.
     * @param calendarDay Calendar.DAY_OF_WEEK value
     * @return Corresponding bitmask value
     */
    private int getDayBitmask(int calendarDay) {
        switch (calendarDay) {
            case Calendar.SUNDAY:
                return WatchedSite.SUNDAY;
            case Calendar.MONDAY:
                return WatchedSite.MONDAY;
            case Calendar.TUESDAY:
                return WatchedSite.TUESDAY;
            case Calendar.WEDNESDAY:
                return WatchedSite.WEDNESDAY;
            case Calendar.THURSDAY:
                return WatchedSite.THURSDAY;
            case Calendar.FRIDAY:
                return WatchedSite.FRIDAY;
            case Calendar.SATURDAY:
                return WatchedSite.SATURDAY;
            default:
                return 0;
        }
    }

    /**
     * Create a PendingIntent for the alarm.
     * @param siteId The site ID to include in the intent
     * @return The PendingIntent
     */
    @NonNull
    private PendingIntent createPendingIntent(long siteId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_SITE_ID, siteId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(
                context,
                (int) siteId, // Use site ID as request code for unique PendingIntents
                intent,
                flags
        );
    }

    /**
     * Get the extra key for site ID.
     * @return The extra key string
     */
    @NonNull
    public static String getExtraSiteId() {
        return EXTRA_SITE_ID;
    }
}
