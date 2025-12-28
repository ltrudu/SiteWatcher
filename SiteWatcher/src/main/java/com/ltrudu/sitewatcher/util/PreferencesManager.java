package com.ltrudu.sitewatcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.NetworkMode;
import com.ltrudu.sitewatcher.data.model.NotificationAction;

/**
 * Singleton class for managing application preferences.
 * Thread-safe implementation using SharedPreferences.
 */
public final class PreferencesManager {

    private static final String PREFS_NAME = "site_watcher_prefs";

    // Preference keys
    public static final String KEY_NOTIFICATION_ACTION = "notification_action";
    public static final String KEY_NETWORK_MODE = "network_mode";
    public static final String KEY_RETRY_COUNT = "retry_count";
    public static final String KEY_MAX_THREADS = "max_threads";
    public static final String KEY_HISTORY_COUNT = "history_count";
    public static final String KEY_SEARCH_ENGINE_INDEX = "search_engine_index";
    public static final String KEY_DEBUG_ENABLED = "debug_enabled";

    private static volatile PreferencesManager instance;
    private final SharedPreferences preferences;

    /**
     * Private constructor for singleton pattern.
     * @param context Application context
     */
    private PreferencesManager(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get the singleton instance of PreferencesManager.
     * @param context Application context
     * @return PreferencesManager instance
     */
    @NonNull
    public static PreferencesManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (PreferencesManager.class) {
                if (instance == null) {
                    instance = new PreferencesManager(context);
                }
            }
        }
        return instance;
    }

    // Notification Action

    /**
     * Get the notification tap action.
     * @return NotificationAction enum value
     */
    @NonNull
    public NotificationAction getNotificationAction() {
        String value = preferences.getString(KEY_NOTIFICATION_ACTION,
                NotificationAction.OPEN_APP.name());
        try {
            return NotificationAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return NotificationAction.OPEN_APP;
        }
    }

    /**
     * Set the notification tap action.
     * @param action NotificationAction enum value
     */
    public void setNotificationAction(@NonNull NotificationAction action) {
        preferences.edit()
                .putString(KEY_NOTIFICATION_ACTION, action.name())
                .apply();
    }

    // Network Mode

    /**
     * Get the network mode for site checks.
     * @return NetworkMode enum value
     */
    @NonNull
    public NetworkMode getNetworkMode() {
        String value = preferences.getString(KEY_NETWORK_MODE,
                NetworkMode.WIFI_AND_DATA.name());
        try {
            return NetworkMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return NetworkMode.WIFI_AND_DATA;
        }
    }

    /**
     * Set the network mode for site checks.
     * @param mode NetworkMode enum value
     */
    public void setNetworkMode(@NonNull NetworkMode mode) {
        preferences.edit()
                .putString(KEY_NETWORK_MODE, mode.name())
                .apply();
    }

    // Retry Count

    /**
     * Get the retry count for failed checks.
     * @return Number of retries (default: 3)
     */
    public int getRetryCount() {
        return preferences.getInt(KEY_RETRY_COUNT, Constants.DEFAULT_RETRY_COUNT);
    }

    /**
     * Set the retry count for failed checks.
     * @param count Number of retries
     */
    public void setRetryCount(int count) {
        preferences.edit()
                .putInt(KEY_RETRY_COUNT, count)
                .apply();
    }

    // Max Threads

    /**
     * Get the maximum number of concurrent threads for site checks.
     * @return Maximum threads (default: 3)
     */
    public int getMaxThreads() {
        return preferences.getInt(KEY_MAX_THREADS, Constants.DEFAULT_MAX_THREADS);
    }

    /**
     * Set the maximum number of concurrent threads for site checks.
     * @param threads Maximum threads
     */
    public void setMaxThreads(int threads) {
        preferences.edit()
                .putInt(KEY_MAX_THREADS, threads)
                .apply();
    }

    // History Count

    /**
     * Get the number of history versions to keep.
     * @return History count (default: 10)
     */
    public int getHistoryCount() {
        return preferences.getInt(KEY_HISTORY_COUNT, Constants.DEFAULT_HISTORY_COUNT);
    }

    /**
     * Set the number of history versions to keep.
     * @param count History count
     */
    public void setHistoryCount(int count) {
        preferences.edit()
                .putInt(KEY_HISTORY_COUNT, count)
                .apply();
    }

    // Search Engine

    /**
     * Get the selected search engine index.
     * @return Search engine index (default: 0 = DuckDuckGo)
     */
    public int getSearchEngineIndex() {
        int index = preferences.getInt(KEY_SEARCH_ENGINE_INDEX,
                Constants.DEFAULT_SEARCH_ENGINE_INDEX);
        // Validate index is within bounds
        if (index < 0 || index >= Constants.SEARCH_ENGINES.length) {
            return Constants.DEFAULT_SEARCH_ENGINE_INDEX;
        }
        return index;
    }

    /**
     * Set the selected search engine index.
     * @param index Search engine index
     */
    public void setSearchEngineIndex(int index) {
        if (index >= 0 && index < Constants.SEARCH_ENGINES.length) {
            preferences.edit()
                    .putInt(KEY_SEARCH_ENGINE_INDEX, index)
                    .apply();
        }
    }

    /**
     * Get the URL of the selected search engine.
     * @return Search engine URL
     */
    @NonNull
    public String getSearchEngineUrl() {
        return Constants.SEARCH_ENGINES[getSearchEngineIndex()];
    }

    /**
     * Get the name of the selected search engine.
     * @return Search engine name
     */
    @NonNull
    public String getSearchEngineName() {
        return Constants.SEARCH_ENGINE_NAMES[getSearchEngineIndex()];
    }

    // Debug Enabled

    /**
     * Check if debug logging is enabled.
     * @return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return preferences.getBoolean(KEY_DEBUG_ENABLED, false);
    }

    /**
     * Set debug logging enabled state.
     * Also updates the Logger enabled state.
     * @param enabled true to enable debug logging
     */
    public void setDebugEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_DEBUG_ENABLED, enabled)
                .apply();
        Logger.setEnabled(enabled);
    }

    /**
     * Register a listener for preference changes.
     * @param listener The listener to register
     */
    public void registerOnSharedPreferenceChangeListener(
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Unregister a listener for preference changes.
     * @param listener The listener to unregister
     */
    public void unregisterOnSharedPreferenceChangeListener(
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Clear all preferences (for testing or reset).
     */
    public void clearAll() {
        preferences.edit().clear().apply();
    }
}
