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
     * Check periodically at a defined interval.
     */
    PERIODIC
}
