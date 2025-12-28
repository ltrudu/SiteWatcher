package com.ltrudu.sitewatcher.util;

/**
 * Application-wide constants for SiteWatcher.
 * Contains configuration values, default settings, and static data.
 */
public final class Constants {

    // Private constructor to prevent instantiation
    private Constants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Notification
    public static final String NOTIFICATION_CHANNEL_ID = "site_watcher_channel";

    // Schedule defaults
    public static final int DEFAULT_PERIODIC_INTERVAL = 60; // minutes
    public static final int MIN_PERIODIC_INTERVAL = 15;
    public static final int MAX_PERIODIC_INTERVAL = 600;
    public static final int PERIODIC_STEP = 15;

    // Threshold
    public static final int DEFAULT_THRESHOLD = 25; // percent

    // Days bitmask (Sun=1, Mon=2, Tue=4, Wed=8, Thu=16, Fri=32, Sat=64)
    public static final int DEFAULT_ENABLED_DAYS = 127; // all days

    // Network and execution
    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final int DEFAULT_MAX_THREADS = 3;

    // History
    public static final int DEFAULT_HISTORY_COUNT = 10;

    // Storage directories
    public static final String BACKUP_DIR_NAME = "backups";
    public static final String TEMP_DIR_NAME = "temp";

    // Search engines
    public static final String[] SEARCH_ENGINES = {
            "https://duckduckgo.com",
            "https://www.google.com",
            "https://www.bing.com",
            "https://www.qwant.com"
    };

    public static final String[] SEARCH_ENGINE_NAMES = {
            "DuckDuckGo",
            "Google",
            "Bing",
            "Qwant"
    };

    public static final int DEFAULT_SEARCH_ENGINE_INDEX = 0; // DuckDuckGo

    // Logging
    public static final String LOG_FILE_NAME = "sitewatcher_debug.log";
}
