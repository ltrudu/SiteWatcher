package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a feedback action that can be triggered when a site change is detected.
 * Actions can notify the user through various channels: notification, SMS, alarm, sound, flash, or vibration.
 */
public class FeedbackAction {

    // Default values
    private static final int DEFAULT_FLASH_SPEED_MS = 200;  // 200ms per flash ON cycle (fast blink)
    private static final int DEFAULT_DURATION_SECONDS = 10;
    private static final long[] DEFAULT_VIBRATION_PATTERN = new long[]{0, 250, 250, 250};

    private String id;
    private FeedbackActionType type;
    private String label;
    private String phoneNumber;       // For SEND_SMS type
    private String soundUri;          // For PLAY_SOUND and TRIGGER_ALARM types
    private long[] vibrationPattern;  // For VIBRATE type
    private int flashSpeedMs;         // For CAMERA_FLASH type - duration of each flash ON cycle (controls blink rate)
    private int durationSeconds;      // For TRIGGER_ALARM, PLAY_SOUND, CAMERA_FLASH, VIBRATE (0 = indefinite for alarm)
    private boolean enabled;
    private int order;

    /**
     * Default constructor.
     */
    public FeedbackAction() {
        this.id = UUID.randomUUID().toString();
        this.type = FeedbackActionType.NOTIFICATION;
        this.label = "";
        this.phoneNumber = null;
        this.soundUri = null;
        this.vibrationPattern = DEFAULT_VIBRATION_PATTERN.clone();
        this.flashSpeedMs = DEFAULT_FLASH_SPEED_MS;
        this.durationSeconds = DEFAULT_DURATION_SECONDS;
        this.enabled = true;
        this.order = 0;
    }

    // Factory methods

    /**
     * Create a NOTIFICATION action.
     *
     * @param label User-friendly label
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createNotification(@NonNull String label) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.NOTIFICATION;
        action.label = label;
        return action;
    }

    /**
     * Create a SEND_SMS action.
     *
     * @param label       User-friendly label
     * @param phoneNumber Phone number to send SMS to
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createSmsAction(@NonNull String label, @NonNull String phoneNumber) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.SEND_SMS;
        action.label = label;
        action.phoneNumber = phoneNumber;
        return action;
    }

    /**
     * Create a TRIGGER_ALARM action.
     *
     * @param label           User-friendly label
     * @param soundUri        URI of the alarm sound to play
     * @param durationSeconds Duration in seconds (0 = indefinite)
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createAlarmAction(@NonNull String label, @Nullable String soundUri, int durationSeconds) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.TRIGGER_ALARM;
        action.label = label;
        action.soundUri = soundUri;
        action.durationSeconds = Math.max(0, durationSeconds);
        return action;
    }

    /**
     * Create a PLAY_SOUND action.
     *
     * @param label    User-friendly label
     * @param soundUri URI of the sound to play
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createSoundAction(@NonNull String label, @Nullable String soundUri) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.PLAY_SOUND;
        action.label = label;
        action.soundUri = soundUri;
        return action;
    }

    /**
     * Create a CAMERA_FLASH action.
     *
     * @param label        User-friendly label
     * @param flashSpeedMs Duration of each flash ON cycle in ms (controls blink rate: lower = faster)
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createFlashAction(@NonNull String label, int flashSpeedMs) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.CAMERA_FLASH;
        action.label = label;
        action.flashSpeedMs = Math.max(50, Math.min(1000, flashSpeedMs));
        return action;
    }

    /**
     * Create a VIBRATE action.
     *
     * @param label   User-friendly label
     * @param pattern Vibration pattern (off/on durations in milliseconds)
     * @return FeedbackAction instance
     */
    @NonNull
    public static FeedbackAction createVibrateAction(@NonNull String label, @Nullable long[] pattern) {
        FeedbackAction action = new FeedbackAction();
        action.type = FeedbackActionType.VIBRATE;
        action.label = label;
        action.vibrationPattern = pattern != null ? pattern.clone() : DEFAULT_VIBRATION_PATTERN.clone();
        return action;
    }

    // Getters and setters

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public FeedbackActionType getType() {
        return type;
    }

    public void setType(@NonNull FeedbackActionType type) {
        this.type = type;
    }

    @NonNull
    public String getLabel() {
        return label != null ? label : "";
    }

    public void setLabel(@NonNull String label) {
        this.label = label;
    }

    @Nullable
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(@Nullable String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Nullable
    public String getSoundUri() {
        return soundUri;
    }

    public void setSoundUri(@Nullable String soundUri) {
        this.soundUri = soundUri;
    }

    @NonNull
    public long[] getVibrationPattern() {
        return vibrationPattern != null ? vibrationPattern : DEFAULT_VIBRATION_PATTERN.clone();
    }

    public void setVibrationPattern(@Nullable long[] vibrationPattern) {
        this.vibrationPattern = vibrationPattern != null ? vibrationPattern.clone() : DEFAULT_VIBRATION_PATTERN.clone();
    }

    /**
     * Get flash speed in milliseconds (duration of each flash ON cycle).
     * Lower values = faster blinking.
     */
    public int getFlashSpeedMs() {
        return flashSpeedMs;
    }

    /**
     * Set flash speed in milliseconds.
     * @param flashSpeedMs Duration of each flash ON cycle (50-1000ms)
     */
    public void setFlashSpeedMs(int flashSpeedMs) {
        this.flashSpeedMs = Math.max(50, Math.min(1000, flashSpeedMs));
    }

    /**
     * Get the duration in seconds for timed actions (alarm, sound, flash, vibrate).
     *
     * @return Duration in seconds (0 = indefinite for alarm, default 10 for others)
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Set the duration in seconds for timed actions.
     *
     * @param durationSeconds Duration in seconds (0 = indefinite for alarm)
     */
    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = Math.max(0, durationSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Get a display summary for the action.
     *
     * @return Summary string based on action type
     */
    @NonNull
    public String getSummary() {
        switch (type) {
            case NOTIFICATION:
                return "Notification";
            case SEND_SMS:
                return "SMS: " + (phoneNumber != null ? phoneNumber : "Not set");
            case TRIGGER_ALARM:
                String alarmDuration = durationSeconds == 0 ? "indefinite" : formatDuration(durationSeconds);
                return "Alarm: " + alarmDuration + (soundUri != null ? " (custom)" : "");
            case PLAY_SOUND:
                return "Sound: " + formatDuration(durationSeconds) + (soundUri != null ? " (custom)" : "");
            case CAMERA_FLASH:
                String speed = flashSpeedMs <= 100 ? "fast" : (flashSpeedMs <= 300 ? "medium" : "slow");
                return "Flash: " + speed + ", " + formatDuration(durationSeconds);
            case VIBRATE:
                return "Vibrate: " + formatDuration(durationSeconds);
            default:
                return "Unknown";
        }
    }

    /**
     * Format duration in seconds to a human-readable string.
     *
     * @param seconds Duration in seconds
     * @return Formatted string (e.g., "1m 30s", "10s", "2m")
     */
    @NonNull
    private String formatDuration(int seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (minutes > 0 && secs > 0) {
            return minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return secs + "s";
        }
    }

    /**
     * Get a human-readable description of the vibration pattern.
     *
     * @return Description string
     */
    @NonNull
    private String getVibrationPatternDescription() {
        if (vibrationPattern == null || vibrationPattern.length == 0) {
            return "Default";
        }
        long totalDuration = 0;
        for (long duration : vibrationPattern) {
            totalDuration += duration;
        }
        if (totalDuration < 500) {
            return "Short pulse";
        } else if (totalDuration < 2000) {
            return "Medium pulse";
        } else {
            return "Long pulse";
        }
    }

    // JSON serialization

    /**
     * Convert this action to a JSON object.
     *
     * @return JSON representation
     * @throws JSONException if serialization fails
     */
    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type.name());
        json.put("label", label);
        json.put("phoneNumber", phoneNumber);
        json.put("soundUri", soundUri);
        json.put("flashSpeedMs", flashSpeedMs);
        json.put("durationSeconds", durationSeconds);
        json.put("enabled", enabled);
        json.put("order", order);

        // Serialize vibration pattern as JSON array
        if (vibrationPattern != null) {
            JSONArray patternArray = new JSONArray();
            for (long duration : vibrationPattern) {
                patternArray.put(duration);
            }
            json.put("vibrationPattern", patternArray);
        }

        return json;
    }

    /**
     * Create an action from a JSON object.
     *
     * @param json JSON object
     * @return FeedbackAction instance
     * @throws JSONException if parsing fails
     */
    @NonNull
    public static FeedbackAction fromJson(@NonNull JSONObject json) throws JSONException {
        FeedbackAction action = new FeedbackAction();
        action.id = json.optString("id", UUID.randomUUID().toString());
        action.type = FeedbackActionType.valueOf(json.optString("type", "NOTIFICATION"));
        action.label = json.optString("label", "");
        action.phoneNumber = json.optString("phoneNumber", null);
        action.soundUri = json.optString("soundUri", null);
        // Support both new flashSpeedMs and legacy flashDurationMs
        action.flashSpeedMs = json.optInt("flashSpeedMs",
            json.optInt("flashDurationMs", DEFAULT_FLASH_SPEED_MS));
        action.durationSeconds = json.optInt("durationSeconds", DEFAULT_DURATION_SECONDS);
        action.enabled = json.optBoolean("enabled", true);
        action.order = json.optInt("order", 0);

        // Deserialize vibration pattern
        if (json.has("vibrationPattern") && !json.isNull("vibrationPattern")) {
            JSONArray patternArray = json.getJSONArray("vibrationPattern");
            action.vibrationPattern = new long[patternArray.length()];
            for (int i = 0; i < patternArray.length(); i++) {
                action.vibrationPattern[i] = patternArray.getLong(i);
            }
        } else {
            action.vibrationPattern = DEFAULT_VIBRATION_PATTERN.clone();
        }

        return action;
    }

    /**
     * Convert a list of actions to a JSON string.
     *
     * @param actions List of actions
     * @return JSON string, or null if empty or error
     */
    @Nullable
    public static String toJsonString(@Nullable List<FeedbackAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray();
            for (FeedbackAction action : actions) {
                array.put(action.toJson());
            }
            return array.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Parse a list of actions from a JSON string.
     *
     * @param jsonString JSON string
     * @return List of actions (empty if parsing fails)
     */
    @NonNull
    public static List<FeedbackAction> fromJsonString(@Nullable String jsonString) {
        List<FeedbackAction> actions = new ArrayList<>();
        if (jsonString == null || jsonString.isEmpty()) {
            return actions;
        }
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                actions.add(fromJson(json));
            }
        } catch (JSONException e) {
            // Return empty list on error
        }
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeedbackAction that = (FeedbackAction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Create a copy of this action with a new ID.
     *
     * @return Copy of the action
     */
    @NonNull
    public FeedbackAction copy() {
        FeedbackAction copy = new FeedbackAction();
        copy.id = UUID.randomUUID().toString();
        copy.type = this.type;
        copy.label = this.label;
        copy.phoneNumber = this.phoneNumber;
        copy.soundUri = this.soundUri;
        copy.vibrationPattern = this.vibrationPattern != null ? this.vibrationPattern.clone() : null;
        copy.flashSpeedMs = this.flashSpeedMs;
        copy.durationSeconds = this.durationSeconds;
        copy.enabled = this.enabled;
        copy.order = this.order;
        return copy;
    }
}
