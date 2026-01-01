package com.ltrudu.sitewatcher.data.model;

/**
 * Defines the type of calendar schedule for site monitoring.
 */
public enum CalendarScheduleType {
    /**
     * Execute checks continuously at the defined interval.
     * Checks occur every day at the specified interval.
     */
    ALL_THE_TIME,

    /**
     * Execute checks only on a specific date.
     * Useful for one-time monitoring of time-sensitive content.
     */
    SELECTED_DAY,

    /**
     * Execute checks between two dates (inclusive).
     * Useful for monitoring during a specific time period.
     */
    DATE_RANGE,

    /**
     * Execute checks on selected days of the week.
     * Supports odd/even week filtering.
     */
    EVERY_WEEKS
}
