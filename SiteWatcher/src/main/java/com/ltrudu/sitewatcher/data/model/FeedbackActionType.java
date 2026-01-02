package com.ltrudu.sitewatcher.data.model;

/**
 * Types of feedback actions that can be triggered when a site change is detected.
 */
public enum FeedbackActionType {
    /**
     * Show a notification (default behavior).
     */
    NOTIFICATION,

    /**
     * Auto-send an SMS in the background.
     */
    SEND_SMS,

    /**
     * Play an alarm sound until dismissed.
     */
    TRIGGER_ALARM,

    /**
     * Play a notification/ringtone sound.
     */
    PLAY_SOUND,

    /**
     * Strobe camera flash.
     */
    CAMERA_FLASH,

    /**
     * Vibrate device with pattern.
     */
    VIBRATE
}
