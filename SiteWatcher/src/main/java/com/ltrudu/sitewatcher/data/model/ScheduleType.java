package com.ltrudu.sitewatcher.data.model;

/**
 * Defines the scheduling type for site monitoring.
 */
public enum ScheduleType {
    /**
     * Check at a specific hour of the day.
     */
    SPECIFIC_HOUR,

    /**
     * Check periodically at a defined interval (minimum 15 minutes).
     */
    PERIODIC,

    /**
     * Live tracking mode with very short intervals (seconds to minutes).
     * Used for real-time monitoring when changes need to be detected immediately.
     * Interval is specified in minutes (0-15) + seconds (1-60).
     */
    LIVE_TRACKING
}
