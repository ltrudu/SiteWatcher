package com.ltrudu.sitewatcher.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FeedbackActionType;
import com.ltrudu.sitewatcher.data.model.FeedbackPlayMode;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.notification.NotificationHelper;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes feedback actions when a site change is detected.
 * Supports both sequential and parallel execution based on FeedbackPlayMode.
 * Supports various notification methods: notifications, email, SMS, alarm, sound, flash, and vibration.
 */
public class FeedbackActionExecutor {
    private static final String TAG = "FeedbackActionExecutor";

    /**
     * Notification ID for alarm notifications.
     */
    private static final int ALARM_NOTIFICATION_ID = 9999;

    /**
     * Notification channel ID for alarm notifications.
     */
    private static final String ALARM_CHANNEL_ID = "alarm_channel";

    /**
     * Intent action for stopping the alarm via notification.
     */
    private static final String ACTION_STOP_ALARM = "com.ltrudu.sitewatcher.STOP_ALARM";

    /**
     * Static reference to the current executor instance for the BroadcastReceiver.
     */
    private static FeedbackActionExecutor currentInstance;

    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private MediaPlayer alarmPlayer;
    private CameraManager cameraManager;
    private String cameraId;
    private volatile boolean isFlashStrobing = false;
    private Runnable alarmStopRunnable;
    private AlarmStopReceiver alarmStopReceiver;

    /**
     * Callback interface for action execution progress and completion.
     */
    public interface ExecutionCallback {
        /**
         * Called after each individual action is executed.
         *
         * @param action       The action that was executed
         * @param success      Whether the action succeeded
         * @param errorMessage Optional error message if failed
         */
        void onActionExecuted(FeedbackAction action, boolean success, @Nullable String errorMessage);

        /**
         * Called when all actions have been executed.
         *
         * @param successCount Number of successful actions
         * @param failCount    Number of failed actions
         */
        void onAllActionsComplete(int successCount, int failCount);
    }

    /**
     * Create a new FeedbackActionExecutor.
     *
     * @param context Application context
     */
    public FeedbackActionExecutor(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        initCamera();
        createAlarmNotificationChannel();
        registerAlarmStopReceiver();
        currentInstance = this;
    }

    /**
     * Initialize camera manager for flash actions.
     */
    private void initCamera() {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                String[] cameraIds = cameraManager.getCameraIdList();
                if (cameraIds.length > 0) {
                    cameraId = cameraIds[0];
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to initialize camera", e);
        }
    }

    /**
     * Execute feedback actions for a site that has changed.
     * Actions are executed based on the site's FeedbackPlayMode:
     * - SEQUENTIAL: Actions are executed one after another in order
     * - ALL_AT_ONCE: Actions are executed in parallel simultaneously
     *
     * @param site          The site that changed
     * @param changePercent The percentage of change detected
     * @param callback      Optional callback for progress updates
     */
    public void executeFeedbackActions(@NonNull WatchedSite site, float changePercent,
                                       @Nullable ExecutionCallback callback) {
        List<FeedbackAction> actions = site.getFeedbackActions();

        // Handle legacy sites with no feedback actions configured - show default notification
        if (actions.isEmpty()) {
            Logger.d(TAG, "No feedback actions configured for site: " + site.getDisplayName() + ", showing default notification");
            executeNotification(site, changePercent);
            if (callback != null) {
                mainHandler.post(() -> callback.onAllActionsComplete(1, 0));
            }
            return;
        }

        // Filter enabled actions and sort by order
        List<FeedbackAction> enabledActions = new ArrayList<>();
        for (FeedbackAction action : actions) {
            if (action.isEnabled()) {
                enabledActions.add(action);
            }
        }
        enabledActions.sort(Comparator.comparingInt(FeedbackAction::getOrder));

        if (enabledActions.isEmpty()) {
            Logger.d(TAG, "All feedback actions are disabled for site: " + site.getDisplayName());
            if (callback != null) {
                mainHandler.post(() -> callback.onAllActionsComplete(0, 0));
            }
            return;
        }

        FeedbackPlayMode playMode = site.getFeedbackPlayMode();
        Logger.d(TAG, "Executing " + enabledActions.size() + " feedback actions for site: " + site.getDisplayName() + " (mode: " + playMode + ")");

        if (playMode == FeedbackPlayMode.ALL_AT_ONCE) {
            // Execute all actions in parallel
            executorService.execute(() -> {
                List<Future<?>> futures = new ArrayList<>();
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failCount = new AtomicInteger(0);

                for (FeedbackAction action : enabledActions) {
                    futures.add(executorService.submit(() -> {
                        try {
                            boolean success = executeAction(action, site, changePercent);
                            if (success) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                            if (callback != null) {
                                mainHandler.post(() -> callback.onActionExecuted(action, success, null));
                            }
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            Logger.e(TAG, "Exception executing action: " + action.getLabel(), e);
                            if (callback != null) {
                                mainHandler.post(() -> callback.onActionExecuted(action, false, e.getMessage()));
                            }
                        }
                    }));
                }

                // Wait for all to complete
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error waiting for parallel action", e);
                    }
                }

                if (callback != null) {
                    mainHandler.post(() -> callback.onAllActionsComplete(successCount.get(), failCount.get()));
                }

                Logger.d(TAG, "Feedback actions complete (parallel): " + successCount.get() + " success, " + failCount.get() + " failed");
            });
        } else {
            // SEQUENTIAL: Execute actions one after another (default behavior)
            executorService.execute(() -> {
                int successCount = 0;
                int failCount = 0;

                for (FeedbackAction action : enabledActions) {
                    String errorMessage = null;
                    boolean success = false;

                    try {
                        success = executeAction(action, site, changePercent);
                    } catch (Exception e) {
                        errorMessage = e.getMessage();
                        Logger.e(TAG, "Exception executing action: " + action.getLabel(), e);
                    }

                    if (success) {
                        successCount++;
                    } else {
                        failCount++;
                    }

                    if (callback != null) {
                        final boolean finalSuccess = success;
                        final String finalErrorMessage = errorMessage;
                        mainHandler.post(() -> callback.onActionExecuted(action, finalSuccess, finalErrorMessage));
                    }
                }

                if (callback != null) {
                    final int finalSuccessCount = successCount;
                    final int finalFailCount = failCount;
                    mainHandler.post(() -> callback.onAllActionsComplete(finalSuccessCount, finalFailCount));
                }

                Logger.d(TAG, "Feedback actions complete (sequential): " + successCount + " success, " + failCount + " failed");
            });
        }
    }

    /**
     * Execute a single feedback action.
     *
     * @param action        The action to execute
     * @param site          The site that changed
     * @param changePercent The percentage of change detected
     * @return true if action executed successfully
     */
    private boolean executeAction(FeedbackAction action, WatchedSite site, float changePercent) {
        Logger.d(TAG, "Executing feedback action: " + action.getType() + " - " + action.getLabel());

        switch (action.getType()) {
            case NOTIFICATION:
                return executeNotification(site, changePercent);
            case SEND_SMS:
                return executeSendSms(action, site, changePercent);
            case TRIGGER_ALARM:
                return executeTriggerAlarm(action, site);
            case PLAY_SOUND:
                return executePlaySound(action);
            case CAMERA_FLASH:
                return executeCameraFlash(action);
            case VIBRATE:
                return executeVibrate(action);
            default:
                Logger.w(TAG, "Unknown feedback action type: " + action.getType());
                return false;
        }
    }

    /**
     * Execute NOTIFICATION action.
     * Shows a standard notification using NotificationHelper.
     */
    private boolean executeNotification(WatchedSite site, float changePercent) {
        try {
            Logger.d(TAG, "Showing notification for site: " + site.getDisplayName());
            NotificationHelper.showSiteChangedNotification(context, site, changePercent);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to show notification", e);
            return false;
        }
    }

    /**
     * Execute SEND_SMS action.
     * Sends an SMS message using SmsManager.
     * Requires SEND_SMS permission.
     */
    private boolean executeSendSms(FeedbackAction action, WatchedSite site, float changePercent) {
        try {
            String phoneNumber = action.getPhoneNumber();
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                Logger.w(TAG, "SMS action has no phone number configured");
                return false;
            }

            Logger.d(TAG, "Sending SMS to: " + phoneNumber);

            String message = "SiteWatcher: " + site.getDisplayName() + " changed by " +
                    String.format("%.1f", changePercent) + "%. URL: " + site.getUrl();

            // Truncate message if too long for single SMS
            if (message.length() > 160) {
                message = message.substring(0, 157) + "...";
            }

            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager != null) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Logger.d(TAG, "SMS sent successfully");
                return true;
            } else {
                Logger.e(TAG, "SmsManager not available");
                return false;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to send SMS", e);
            return false;
        }
    }

    /**
     * Execute TRIGGER_ALARM action.
     * Plays an alarm sound in a loop until stopped or duration expires.
     *
     * @param action The feedback action containing alarm configuration
     * @param site   The site that triggered the alarm (used for notification)
     */
    private boolean executeTriggerAlarm(FeedbackAction action, WatchedSite site) {
        try {
            // Stop any existing alarm first
            stopAlarm();

            Logger.d(TAG, "Triggering alarm");

            Uri alarmUri;
            String soundUri = action.getSoundUri();

            if (soundUri != null && !soundUri.isEmpty()) {
                alarmUri = Uri.parse(soundUri);
            } else {
                // Use default alarm sound
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    // Fallback to notification sound
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
            }

            if (alarmUri == null) {
                Logger.w(TAG, "No alarm sound available");
                return false;
            }

            alarmPlayer = new MediaPlayer();
            alarmPlayer.setDataSource(context, alarmUri);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alarmPlayer.setAudioAttributes(attributes);

            alarmPlayer.setLooping(true);
            alarmPlayer.prepare();
            alarmPlayer.start();

            // Show the alarm notification
            showAlarmNotification(site);

            // Schedule auto-stop if duration is configured
            int durationSeconds = action.getDurationSeconds();
            if (durationSeconds > 0) {
                Logger.d(TAG, "Scheduling alarm stop in " + durationSeconds + " seconds");
                alarmStopRunnable = this::stopAlarm;
                mainHandler.postDelayed(alarmStopRunnable, durationSeconds * 1000L);
            } else {
                Logger.d(TAG, "Alarm will play indefinitely until notification is dismissed");
            }

            Logger.d(TAG, "Alarm started successfully");
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to trigger alarm", e);
            return false;
        }
    }

    /**
     * Execute PLAY_SOUND action.
     * Plays a sound once (not looping).
     */
    private boolean executePlaySound(FeedbackAction action) {
        try {
            Logger.d(TAG, "Playing sound");

            Uri soundUri;
            String soundUriString = action.getSoundUri();

            if (soundUriString != null && !soundUriString.isEmpty()) {
                soundUri = Uri.parse(soundUriString);
            } else {
                // Use default notification sound
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            if (soundUri == null) {
                Logger.w(TAG, "No sound available");
                return false;
            }

            Ringtone ringtone = RingtoneManager.getRingtone(context, soundUri);
            if (ringtone != null) {
                ringtone.play();
                Logger.d(TAG, "Sound played successfully");
                return true;
            } else {
                Logger.w(TAG, "Could not get ringtone for URI: " + soundUri);
                return false;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to play sound", e);
            return false;
        }
    }

    /**
     * Execute CAMERA_FLASH action.
     * Strobes the camera flash for a specified duration at a specified speed.
     */
    private boolean executeCameraFlash(FeedbackAction action) {
        if (cameraManager == null || cameraId == null) {
            Logger.w(TAG, "Camera not available for flash");
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Logger.w(TAG, "Camera flash requires API 23+");
            return false;
        }

        try {
            int flashSpeedMs = action.getFlashSpeedMs();
            int durationSeconds = action.getDurationSeconds();
            int flashCount = (durationSeconds * 1000) / (flashSpeedMs * 2);

            Logger.d(TAG, "Starting camera flash strobe: " + flashCount + " flashes over " +
                    durationSeconds + " seconds (speed: " + flashSpeedMs + "ms)");

            isFlashStrobing = true;

            for (int i = 0; i < flashCount && isFlashStrobing; i++) {
                // Turn flash on
                cameraManager.setTorchMode(cameraId, true);
                Thread.sleep(flashSpeedMs);

                // Turn flash off
                cameraManager.setTorchMode(cameraId, false);

                // Wait between flashes (symmetrical blink - same duration as ON)
                if (i < flashCount - 1) {
                    Thread.sleep(flashSpeedMs);
                }
            }

            isFlashStrobing = false;
            Logger.d(TAG, "Camera flash strobe completed");
            return true;
        } catch (CameraAccessException e) {
            Logger.e(TAG, "Camera access exception during flash", e);
            isFlashStrobing = false;
            return false;
        } catch (InterruptedException e) {
            Logger.w(TAG, "Flash strobe interrupted");
            Thread.currentThread().interrupt();
            isFlashStrobing = false;
            // Try to turn off flash
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException ex) {
                Logger.e(TAG, "Failed to turn off flash after interrupt", ex);
            }
            return false;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to execute camera flash", e);
            isFlashStrobing = false;
            return false;
        }
    }

    /**
     * Execute VIBRATE action.
     * Vibrates the device with the specified pattern.
     */
    private boolean executeVibrate(FeedbackAction action) {
        try {
            Logger.d(TAG, "Executing vibration");

            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                } else {
                    Logger.w(TAG, "VibratorManager not available");
                    return false;
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                Logger.w(TAG, "Device does not have vibrator");
                return false;
            }

            long[] pattern = action.getVibrationPattern();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create vibration effect from pattern
                // Pattern: [delay, vibrate, delay, vibrate, ...]
                // -1 means don't repeat
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                vibrator.vibrate(effect);
            } else {
                // Legacy vibration API
                vibrator.vibrate(pattern, -1);
            }

            Logger.d(TAG, "Vibration executed successfully");
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Failed to vibrate", e);
            return false;
        }
    }

    /**
     * Build a message describing the site change.
     *
     * @param site          The site that changed
     * @param changePercent The percentage of change
     * @return Formatted message string
     */
    private String buildChangeMessage(WatchedSite site, float changePercent) {
        return "SiteWatcher has detected a change on the monitored site.\n\n" +
                "Site: " + site.getDisplayName() + "\n" +
                "URL: " + site.getUrl() + "\n" +
                "Change: " + String.format("%.1f", changePercent) + "%\n\n" +
                "Open SiteWatcher app to view the changes.";
    }

    /**
     * Stop the currently playing alarm.
     * Call this to dismiss a triggered alarm.
     */
    public void stopAlarm() {
        Logger.d(TAG, "stopAlarm() called");

        // Cancel any scheduled auto-stop
        if (alarmStopRunnable != null) {
            mainHandler.removeCallbacks(alarmStopRunnable);
            alarmStopRunnable = null;
        }

        // Stop the media player
        if (alarmPlayer != null) {
            try {
                if (alarmPlayer.isPlaying()) {
                    alarmPlayer.stop();
                }
                alarmPlayer.release();
                Logger.d(TAG, "Alarm stopped");
            } catch (Exception e) {
                Logger.e(TAG, "Error stopping alarm", e);
            } finally {
                alarmPlayer = null;
            }
        }

        // Cancel the alarm notification
        cancelAlarmNotification();
    }

    /**
     * Stop flash strobe if in progress.
     */
    public void stopFlash() {
        isFlashStrobing = false;
        if (cameraManager != null && cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException e) {
                Logger.e(TAG, "Failed to turn off flash", e);
            }
        }
    }

    /**
     * Clean up resources.
     * Call this when the executor is no longer needed.
     */
    public void cleanup() {
        Logger.d(TAG, "Cleaning up FeedbackActionExecutor");

        // Stop any ongoing alarm
        stopAlarm();

        // Stop flash strobe
        stopFlash();

        // Unregister the broadcast receiver
        unregisterAlarmStopReceiver();

        // Clear static instance reference
        if (currentInstance == this) {
            currentInstance = null;
        }

        // Shutdown executor service
        executorService.shutdown();
    }

    /**
     * Create notification channel for alarm notifications.
     * Required for Android 8.0+ (API 26+).
     */
    private void createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                // Check if channel already exists
                if (notificationManager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                    String name = context.getString(R.string.alarm_notification_channel_name);
                    String description = context.getString(R.string.alarm_notification_channel_desc);

                    NotificationChannel channel = new NotificationChannel(
                            ALARM_CHANNEL_ID,
                            name,
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    channel.setDescription(description);
                    channel.enableLights(true);
                    channel.enableVibration(true);

                    notificationManager.createNotificationChannel(channel);
                    Logger.d(TAG, "Alarm notification channel created");
                }
            }
        }
    }

    /**
     * Show a persistent notification for the alarm.
     * The notification allows the user to stop the alarm by tapping or dismissing it.
     *
     * @param site The site that triggered the alarm
     */
    private void showAlarmNotification(WatchedSite site) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            Logger.w(TAG, "NotificationManager not available");
            return;
        }

        // Create intent to stop alarm when notification is tapped
        Intent stopIntent = new Intent(ACTION_STOP_ALARM);
        PendingIntent contentIntent = PendingIntent.getBroadcast(
                context,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create intent to stop alarm when notification is dismissed
        Intent deleteIntent = new Intent(ACTION_STOP_ALARM);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = context.getString(R.string.alarm_notification_title);
        String text = context.getString(R.string.alarm_notification_text);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(contentIntent)
                .setDeleteIntent(deletePendingIntent)
                .setOngoing(true)
                .setAutoCancel(true);

        notificationManager.notify(ALARM_NOTIFICATION_ID, builder.build());
        Logger.d(TAG, "Alarm notification shown");
    }

    /**
     * Cancel the alarm notification.
     */
    private void cancelAlarmNotification() {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(ALARM_NOTIFICATION_ID);
            Logger.d(TAG, "Alarm notification cancelled");
        }
    }

    /**
     * Register the broadcast receiver to handle alarm stop actions.
     */
    private void registerAlarmStopReceiver() {
        if (alarmStopReceiver == null) {
            alarmStopReceiver = new AlarmStopReceiver();
            IntentFilter filter = new IntentFilter(ACTION_STOP_ALARM);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(alarmStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(alarmStopReceiver, filter);
            }
            Logger.d(TAG, "AlarmStopReceiver registered");
        }
    }

    /**
     * Unregister the broadcast receiver.
     */
    private void unregisterAlarmStopReceiver() {
        if (alarmStopReceiver != null) {
            try {
                context.unregisterReceiver(alarmStopReceiver);
                Logger.d(TAG, "AlarmStopReceiver unregistered");
            } catch (Exception e) {
                Logger.e(TAG, "Error unregistering AlarmStopReceiver", e);
            }
            alarmStopReceiver = null;
        }
    }

    /**
     * BroadcastReceiver to handle alarm stop actions from the notification.
     */
    private static class AlarmStopReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.d(TAG, "AlarmStopReceiver received action: " + intent.getAction());
            if (ACTION_STOP_ALARM.equals(intent.getAction())) {
                if (currentInstance != null) {
                    currentInstance.stopAlarm();
                    Logger.d(TAG, "Alarm stopped via notification");
                } else {
                    Logger.w(TAG, "No executor instance available to stop alarm");
                }
            }
        }
    }
}
