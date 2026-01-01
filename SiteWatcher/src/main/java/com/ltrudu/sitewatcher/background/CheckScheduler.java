package com.ltrudu.sitewatcher.background;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.CalendarScheduleType;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.model.WeekParity;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;

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
     * Evaluates all enabled schedules and returns the earliest next alarm time.
     * This method is public so the UI can display the correct "Next Check In" time.
     * @param site The WatchedSite
     * @return The next alarm time in milliseconds, or -1 if unable to calculate
     */
    public long calculateNextAlarmTime(@NonNull WatchedSite site) {
        Calendar now = Calendar.getInstance();
        List<Schedule> schedules = site.getSchedules();
        long earliestAlarm = Long.MAX_VALUE;

        for (Schedule schedule : schedules) {
            if (!schedule.isEnabled()) {
                continue;
            }

            long nextAlarmForSchedule = calculateNextAlarmTimeForSchedule(schedule, site, now);
            if (nextAlarmForSchedule > 0 && nextAlarmForSchedule < earliestAlarm) {
                earliestAlarm = nextAlarmForSchedule;
                Logger.d(TAG, "Schedule " + schedule.getId() + " next alarm: " +
                        new java.util.Date(nextAlarmForSchedule));
            }
        }

        if (earliestAlarm == Long.MAX_VALUE) {
            Logger.d(TAG, "No valid schedule found for site " + site.getId());
            return -1;
        }

        return earliestAlarm;
    }

    /**
     * Calculate the next alarm time for a specific schedule.
     * @param schedule The schedule to calculate for
     * @param site The WatchedSite (for last check time)
     * @param now Current time
     * @return Next alarm time in milliseconds, or -1 if schedule is not active
     */
    private long calculateNextAlarmTimeForSchedule(@NonNull Schedule schedule,
            @NonNull WatchedSite site, @NonNull Calendar now) {

        // First, find the next date when the calendar type allows checks
        Calendar nextValidDate = findNextValidDate(schedule, now);
        if (nextValidDate == null) {
            return -1; // Schedule is not active (e.g., past date range)
        }

        // Then apply the interval timing
        if (schedule.getIntervalType() == ScheduleType.PERIODIC) {
            return calculatePeriodicAlarmTimeForSchedule(schedule, site, now, nextValidDate);
        } else {
            return calculateSpecificHourAlarmTimeForSchedule(schedule, nextValidDate);
        }
    }

    /**
     * Find the next date when the schedule's calendar type allows checks.
     * @param schedule The schedule
     * @param now Current time
     * @return Calendar set to the next valid date, or null if no valid date
     */
    @Nullable
    private Calendar findNextValidDate(@NonNull Schedule schedule, @NonNull Calendar now) {
        Calendar candidate = (Calendar) now.clone();

        switch (schedule.getCalendarType()) {
            case ALL_THE_TIME:
                // Always valid
                return candidate;

            case SELECTED_DAY:
                // Only valid on the selected date
                Calendar selectedCal = Calendar.getInstance();
                selectedCal.setTimeInMillis(schedule.getSelectedDate());

                // If selected date is today or in the future, use it
                if (isSameDay(now.getTimeInMillis(), schedule.getSelectedDate()) ||
                    startOfDay(schedule.getSelectedDate()) > now.getTimeInMillis()) {
                    candidate.setTimeInMillis(schedule.getSelectedDate());
                    return candidate;
                }
                // Selected date is in the past
                return null;

            case DATE_RANGE:
                // Check if we're within the date range
                long nowMillis = now.getTimeInMillis();
                long rangeStart = startOfDay(schedule.getFromDate());
                long rangeEnd = endOfDay(schedule.getToDate());

                if (nowMillis > rangeEnd) {
                    // Past the date range
                    return null;
                }
                if (nowMillis < rangeStart) {
                    // Before the date range, schedule for range start
                    candidate.setTimeInMillis(rangeStart);
                }
                // Otherwise, current date is valid
                return candidate;

            case EVERY_WEEKS:
                // Find the next enabled day with correct week parity
                return findNextEnabledDayWithParity(schedule, now);

            default:
                return candidate;
        }
    }

    /**
     * Find the next enabled day that matches the week parity requirement.
     * @param schedule The schedule with day and parity settings
     * @param now Current time
     * @return Calendar set to the next valid day, or null if none found
     */
    @Nullable
    private Calendar findNextEnabledDayWithParity(@NonNull Schedule schedule, @NonNull Calendar now) {
        Calendar candidate = (Calendar) now.clone();
        int attempts = 0;

        // Look up to 14 days ahead (covers 2 weeks for parity)
        while (attempts < 14) {
            int dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK);
            int dayBitmask = getScheduleDayBitmask(dayOfWeek);

            if (schedule.isDayEnabled(dayBitmask) && checkWeekParity(candidate, schedule.getWeekParity())) {
                return candidate;
            }

            candidate.add(Calendar.DAY_OF_YEAR, 1);
            attempts++;
        }

        // No valid day found in the next 2 weeks
        return null;
    }

    /**
     * Calculate next periodic alarm time for a Schedule object.
     * @param schedule The schedule
     * @param site The WatchedSite (for last check time)
     * @param now Current time
     * @param nextValidDate The next date when the schedule is active
     * @return Next alarm time in milliseconds
     */
    private long calculatePeriodicAlarmTimeForSchedule(@NonNull Schedule schedule,
            @NonNull WatchedSite site, @NonNull Calendar now, @NonNull Calendar nextValidDate) {

        int intervalMinutes = schedule.getIntervalMinutes();
        if (intervalMinutes <= 0) {
            intervalMinutes = 60; // Default to 1 hour
        }

        Calendar nextAlarm = (Calendar) nextValidDate.clone();

        // If the valid date is today, calculate based on last check time
        if (isSameDay(now.getTimeInMillis(), nextValidDate.getTimeInMillis())) {
            if (site.getLastCheckTime() > 0) {
                Calendar fromLastCheck = Calendar.getInstance();
                fromLastCheck.setTimeInMillis(site.getLastCheckTime());
                fromLastCheck.add(Calendar.MINUTE, intervalMinutes);

                // If the calculated time is in the future and on a valid date, use it
                if (fromLastCheck.after(now)) {
                    // Verify it's still on a valid date
                    if (isDateValidForSchedule(schedule, fromLastCheck)) {
                        return fromLastCheck.getTimeInMillis();
                    }
                }
            }
            // No previous check or time already passed, schedule for interval from now
            nextAlarm = (Calendar) now.clone();
            nextAlarm.add(Calendar.MINUTE, intervalMinutes);
        } else {
            // Future date: schedule for start of that day plus interval
            nextAlarm.set(Calendar.HOUR_OF_DAY, 0);
            nextAlarm.set(Calendar.MINUTE, 0);
            nextAlarm.set(Calendar.SECOND, 0);
            nextAlarm.set(Calendar.MILLISECOND, 0);
            nextAlarm.add(Calendar.MINUTE, intervalMinutes);
        }

        // Validate the result is still on a valid date for the schedule
        if (!isDateValidForSchedule(schedule, nextAlarm)) {
            // Move to next valid date
            Calendar next = findNextValidDate(schedule, nextAlarm);
            if (next == null) {
                return -1;
            }
            next.set(Calendar.HOUR_OF_DAY, 0);
            next.set(Calendar.MINUTE, 0);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            next.add(Calendar.MINUTE, intervalMinutes);
            return next.getTimeInMillis();
        }

        return nextAlarm.getTimeInMillis();
    }

    /**
     * Calculate next specific hour alarm time for a Schedule object.
     * @param schedule The schedule
     * @param nextValidDate The next date when the schedule is active
     * @return Next alarm time in milliseconds
     */
    private long calculateSpecificHourAlarmTimeForSchedule(@NonNull Schedule schedule,
            @NonNull Calendar nextValidDate) {

        Calendar nextAlarm = (Calendar) nextValidDate.clone();
        nextAlarm.set(Calendar.HOUR_OF_DAY, schedule.getScheduleHour());
        nextAlarm.set(Calendar.MINUTE, schedule.getScheduleMinute());
        nextAlarm.set(Calendar.SECOND, 0);
        nextAlarm.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();

        // If the time has already passed for today's valid date, find next valid date
        if (nextAlarm.before(now) || !isDateValidForSchedule(schedule, nextAlarm)) {
            // Move to next valid date
            Calendar searchFrom = (Calendar) nextValidDate.clone();
            searchFrom.add(Calendar.DAY_OF_YEAR, 1);
            Calendar next = findNextValidDate(schedule, searchFrom);
            if (next == null) {
                return -1;
            }
            nextAlarm = next;
            nextAlarm.set(Calendar.HOUR_OF_DAY, schedule.getScheduleHour());
            nextAlarm.set(Calendar.MINUTE, schedule.getScheduleMinute());
            nextAlarm.set(Calendar.SECOND, 0);
            nextAlarm.set(Calendar.MILLISECOND, 0);
        }

        return nextAlarm.getTimeInMillis();
    }

    /**
     * Check if a date is valid for a given schedule.
     * @param schedule The schedule
     * @param date The date to check
     * @return true if the date is valid for the schedule
     */
    private boolean isDateValidForSchedule(@NonNull Schedule schedule, @NonNull Calendar date) {
        switch (schedule.getCalendarType()) {
            case ALL_THE_TIME:
                return true;

            case SELECTED_DAY:
                return isSameDay(date.getTimeInMillis(), schedule.getSelectedDate());

            case DATE_RANGE:
                long dateMillis = date.getTimeInMillis();
                return dateMillis >= startOfDay(schedule.getFromDate()) &&
                       dateMillis <= endOfDay(schedule.getToDate());

            case EVERY_WEEKS:
                int dayOfWeek = date.get(Calendar.DAY_OF_WEEK);
                int dayBitmask = getScheduleDayBitmask(dayOfWeek);
                return schedule.isDayEnabled(dayBitmask) && checkWeekParity(date, schedule.getWeekParity());

            default:
                return true;
        }
    }

    /**
     * Convert Calendar day of week to Schedule day bitmask.
     * @param calendarDay Calendar.DAY_OF_WEEK value
     * @return Corresponding bitmask value
     */
    private int getDayBitmask(int calendarDay) {
        switch (calendarDay) {
            case Calendar.SUNDAY:
                return Schedule.SUNDAY;
            case Calendar.MONDAY:
                return Schedule.MONDAY;
            case Calendar.TUESDAY:
                return Schedule.TUESDAY;
            case Calendar.WEDNESDAY:
                return Schedule.WEDNESDAY;
            case Calendar.THURSDAY:
                return Schedule.THURSDAY;
            case Calendar.FRIDAY:
                return Schedule.FRIDAY;
            case Calendar.SATURDAY:
                return Schedule.SATURDAY;
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

    // ============================================================================
    // Multi-Schedule Helper Methods
    // ============================================================================

    /**
     * Check if two timestamps represent the same day.
     * @param time1 First timestamp in milliseconds
     * @param time2 Second timestamp in milliseconds
     * @return true if both timestamps are on the same day
     */
    private boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Get the start of day (00:00:00.000) for a given timestamp.
     * @param time Timestamp in milliseconds
     * @return Start of day timestamp
     */
    private long startOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Get the end of day (23:59:59.999) for a given timestamp.
     * @param time Timestamp in milliseconds
     * @return End of day timestamp
     */
    private long endOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    /**
     * Convert Calendar day of week to Schedule day bitmask.
     * @param calendarDay Calendar.DAY_OF_WEEK value
     * @return Corresponding Schedule bitmask value
     */
    private int getScheduleDayBitmask(int calendarDay) {
        switch (calendarDay) {
            case Calendar.SUNDAY:
                return Schedule.SUNDAY;
            case Calendar.MONDAY:
                return Schedule.MONDAY;
            case Calendar.TUESDAY:
                return Schedule.TUESDAY;
            case Calendar.WEDNESDAY:
                return Schedule.WEDNESDAY;
            case Calendar.THURSDAY:
                return Schedule.THURSDAY;
            case Calendar.FRIDAY:
                return Schedule.FRIDAY;
            case Calendar.SATURDAY:
                return Schedule.SATURDAY;
            default:
                return 0;
        }
    }

    /**
     * Check if the current week matches the week parity requirement.
     * Uses ISO week of year for calculation.
     * @param now Current time
     * @param parity Week parity requirement
     * @return true if current week matches the parity
     */
    private boolean checkWeekParity(@NonNull Calendar now, @NonNull WeekParity parity) {
        if (parity == WeekParity.BOTH) {
            return true;
        }
        int weekOfYear = now.get(Calendar.WEEK_OF_YEAR);
        boolean isEvenWeek = (weekOfYear % 2 == 0);
        return (parity == WeekParity.EVEN) == isEvenWeek;
    }

    /**
     * Check if any enabled schedule matches the current time (OR logic).
     * @param site The WatchedSite to check
     * @return true if at least one enabled schedule matches current time
     */
    public boolean shouldCheckNow(@NonNull WatchedSite site) {
        if (!site.isEnabled()) {
            Logger.d(TAG, "Site " + site.getId() + " is disabled");
            return false;
        }

        Calendar now = Calendar.getInstance();
        List<Schedule> schedules = site.getSchedules();

        for (Schedule schedule : schedules) {
            if (scheduleMatches(schedule, now, site.getLastCheckTime())) {
                Logger.d(TAG, "Schedule " + schedule.getId() + " matches for site " + site.getId());
                return true;
            }
        }

        Logger.d(TAG, "No schedule matches for site " + site.getId());
        return false;
    }

    /**
     * Check if a single schedule matches the current time.
     * @param schedule The schedule to check
     * @param now Current time
     * @param lastCheckTime Time of last check (for interval calculation)
     * @return true if the schedule matches
     */
    private boolean scheduleMatches(@NonNull Schedule schedule, @NonNull Calendar now, long lastCheckTime) {
        if (!schedule.isEnabled()) {
            return false;
        }

        // First check calendar type constraints
        switch (schedule.getCalendarType()) {
            case ALL_THE_TIME:
                // Always eligible, just check interval below
                break;

            case SELECTED_DAY:
                // Check if today matches the selected date
                if (!isSameDay(now.getTimeInMillis(), schedule.getSelectedDate())) {
                    return false;
                }
                break;

            case DATE_RANGE:
                // Check if today is within the date range
                long today = now.getTimeInMillis();
                if (today < startOfDay(schedule.getFromDate()) ||
                    today > endOfDay(schedule.getToDate())) {
                    return false;
                }
                break;

            case EVERY_WEEKS:
                // Check if today's day of week is enabled
                int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                int dayBitmask = getScheduleDayBitmask(dayOfWeek);
                if (!schedule.isDayEnabled(dayBitmask)) {
                    return false;
                }
                // Check week parity
                if (!checkWeekParity(now, schedule.getWeekParity())) {
                    return false;
                }
                break;
        }

        // Then check interval type
        return checkIntervalMatches(schedule, now, lastCheckTime);
    }

    /**
     * Check if the interval timing matches for a schedule.
     * @param schedule The schedule to check
     * @param now Current time
     * @param lastCheckTime Time of last check
     * @return true if it's time for a check based on the interval
     */
    private boolean checkIntervalMatches(@NonNull Schedule schedule, @NonNull Calendar now, long lastCheckTime) {
        if (schedule.getIntervalType() == ScheduleType.PERIODIC) {
            // For periodic: check if enough time has passed since last check
            if (lastCheckTime <= 0) {
                return true; // Never checked, should check now
            }
            long intervalMillis = schedule.getIntervalMinutes() * 60L * 1000L;
            long elapsed = now.getTimeInMillis() - lastCheckTime;
            return elapsed >= intervalMillis;
        } else {
            // For specific hour: check if current time matches scheduled time
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);

            // Allow a 5-minute window for the scheduled time
            int scheduledMinuteOfDay = schedule.getScheduleHour() * 60 + schedule.getScheduleMinute();
            int currentMinuteOfDay = currentHour * 60 + currentMinute;
            int diff = Math.abs(currentMinuteOfDay - scheduledMinuteOfDay);

            if (diff <= 5) {
                // Within the window, check if we haven't already checked today
                if (lastCheckTime <= 0) {
                    return true;
                }
                // Don't trigger if we already checked within the last hour
                long elapsed = now.getTimeInMillis() - lastCheckTime;
                return elapsed >= 60L * 60L * 1000L;
            }
            return false;
        }
    }
}
