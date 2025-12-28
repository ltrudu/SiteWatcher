package com.ltrudu.sitewatcher.data.model;

/**
 * Defines which network connections are allowed for site checking.
 */
public enum NetworkMode {
    /**
     * Only check sites when connected to WiFi.
     */
    WIFI_ONLY,

    /**
     * Check sites on both WiFi and mobile data.
     */
    WIFI_AND_DATA,

    /**
     * Only check sites when connected to mobile data.
     */
    DATA_ONLY
}
