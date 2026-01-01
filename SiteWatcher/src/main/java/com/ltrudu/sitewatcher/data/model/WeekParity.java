package com.ltrudu.sitewatcher.data.model;

/**
 * Defines the week parity filter for EVERY_WEEKS schedule type.
 */
public enum WeekParity {
    /**
     * Execute on both even and odd weeks (every week).
     */
    BOTH,

    /**
     * Execute only on even weeks.
     * Week number is calculated as ISO week of year.
     */
    EVEN,

    /**
     * Execute only on odd weeks.
     * Week number is calculated as ISO week of year.
     */
    ODD
}
