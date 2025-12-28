package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents a website being monitored for changes.
 */
@Entity(tableName = "watched_sites")
public class WatchedSite {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "url")
    private String url;

    @Nullable
    @ColumnInfo(name = "name")
    private String name;

    @NonNull
    @ColumnInfo(name = "schedule_type")
    private ScheduleType scheduleType;

    @ColumnInfo(name = "schedule_hour")
    private int scheduleHour;

    @ColumnInfo(name = "schedule_minute")
    private int scheduleMinute;

    @ColumnInfo(name = "periodic_interval_minutes")
    private int periodicIntervalMinutes;

    /**
     * Bitmask for enabled days of the week.
     * Sunday = 1, Monday = 2, Tuesday = 4, Wednesday = 8,
     * Thursday = 16, Friday = 32, Saturday = 64
     */
    @ColumnInfo(name = "enabled_days")
    private int enabledDays;

    @NonNull
    @ColumnInfo(name = "comparison_mode")
    private ComparisonMode comparisonMode;

    @Nullable
    @ColumnInfo(name = "css_selector")
    private String cssSelector;

    /**
     * Minimum change percentage (1-99) to trigger notification.
     */
    @ColumnInfo(name = "threshold_percent")
    private int thresholdPercent;

    @ColumnInfo(name = "is_enabled")
    private boolean isEnabled;

    @ColumnInfo(name = "last_check_time")
    private long lastCheckTime;

    @ColumnInfo(name = "last_change_percent")
    private float lastChangePercent;

    @Nullable
    @ColumnInfo(name = "last_error")
    private String lastError;

    @ColumnInfo(name = "consecutive_failures")
    private int consecutiveFailures;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /**
     * Default constructor for Room.
     */
    public WatchedSite() {
        this.url = "";
        this.scheduleType = ScheduleType.PERIODIC;
        this.comparisonMode = ComparisonMode.TEXT_ONLY;
        this.periodicIntervalMinutes = 60;
        this.enabledDays = 127; // All days enabled by default
        this.thresholdPercent = 5;
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @NonNull
    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(@NonNull ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public int getScheduleHour() {
        return scheduleHour;
    }

    public void setScheduleHour(int scheduleHour) {
        this.scheduleHour = scheduleHour;
    }

    public int getScheduleMinute() {
        return scheduleMinute;
    }

    public void setScheduleMinute(int scheduleMinute) {
        this.scheduleMinute = scheduleMinute;
    }

    public int getPeriodicIntervalMinutes() {
        return periodicIntervalMinutes;
    }

    public void setPeriodicIntervalMinutes(int periodicIntervalMinutes) {
        this.periodicIntervalMinutes = periodicIntervalMinutes;
    }

    public int getEnabledDays() {
        return enabledDays;
    }

    public void setEnabledDays(int enabledDays) {
        this.enabledDays = enabledDays;
    }

    @NonNull
    public ComparisonMode getComparisonMode() {
        return comparisonMode;
    }

    public void setComparisonMode(@NonNull ComparisonMode comparisonMode) {
        this.comparisonMode = comparisonMode;
    }

    @Nullable
    public String getCssSelector() {
        return cssSelector;
    }

    public void setCssSelector(@Nullable String cssSelector) {
        this.cssSelector = cssSelector;
    }

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(int thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(long lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }

    public float getLastChangePercent() {
        return lastChangePercent;
    }

    public void setLastChangePercent(float lastChangePercent) {
        this.lastChangePercent = lastChangePercent;
    }

    @Nullable
    public String getLastError() {
        return lastError;
    }

    public void setLastError(@Nullable String lastError) {
        this.lastError = lastError;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Day of week helper constants
    public static final int SUNDAY = 1;
    public static final int MONDAY = 2;
    public static final int TUESDAY = 4;
    public static final int WEDNESDAY = 8;
    public static final int THURSDAY = 16;
    public static final int FRIDAY = 32;
    public static final int SATURDAY = 64;
    public static final int ALL_DAYS = 127;
    public static final int WEEKDAYS = MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY;
    public static final int WEEKENDS = SATURDAY | SUNDAY;

    /**
     * Check if a specific day is enabled.
     *
     * @param dayBitmask The day bitmask (use SUNDAY, MONDAY, etc. constants)
     * @return true if the day is enabled
     */
    public boolean isDayEnabled(int dayBitmask) {
        return (enabledDays & dayBitmask) != 0;
    }

    /**
     * Enable or disable a specific day.
     *
     * @param dayBitmask The day bitmask (use SUNDAY, MONDAY, etc. constants)
     * @param enabled    Whether to enable or disable the day
     */
    public void setDayEnabled(int dayBitmask, boolean enabled) {
        if (enabled) {
            enabledDays |= dayBitmask;
        } else {
            enabledDays &= ~dayBitmask;
        }
    }

    /**
     * Get the display name for this site.
     * Returns the custom name if set, otherwise extracts domain from URL.
     *
     * @return Display name for this site
     */
    @NonNull
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        // Extract domain from URL as fallback
        try {
            String domain = url.replaceFirst("^(https?://)?([^/]+).*$", "$2");
            return domain.isEmpty() ? url : domain;
        } catch (Exception e) {
            return url;
        }
    }
}
