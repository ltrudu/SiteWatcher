package com.ltrudu.sitewatcher.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.ltrudu.sitewatcher.data.model.NetworkMode;
import com.ltrudu.sitewatcher.data.model.NotificationAction;
import com.ltrudu.sitewatcher.ui.sitelist.SiteListAdapter;
import com.ltrudu.sitewatcher.util.Constants;

/**
 * Manages application preferences using SharedPreferences.
 * Provides type-safe access to all user settings.
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "sitewatcher_prefs";

    // Keys for preferences
    private static final String KEY_NOTIFICATION_ACTION = "notification_action";
    private static final String KEY_NETWORK_MODE = "network_mode";
    private static final String KEY_RETRY_COUNT = "retry_count";
    private static final String KEY_MAX_THREADS = "max_threads";
    private static final String KEY_HISTORY_COUNT = "history_count";
    private static final String KEY_SEARCH_ENGINE_INDEX = "search_engine_index";
    private static final String KEY_DEBUG_MODE = "debug_mode";
    private static final String KEY_PAGE_LOAD_DELAY = "page_load_delay";
    private static final String KEY_POST_ACTION_DELAY = "post_action_delay";
    private static final String KEY_SITE_LIST_SORT_ORDER = "site_list_sort_order";

    private final SharedPreferences preferences;

    public PreferencesManager(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Notification Action
    public NotificationAction getNotificationAction() {
        int ordinal = preferences.getInt(KEY_NOTIFICATION_ACTION, NotificationAction.OPEN_APP.ordinal());
        return NotificationAction.values()[ordinal];
    }

    public void setNotificationAction(NotificationAction action) {
        preferences.edit().putInt(KEY_NOTIFICATION_ACTION, action.ordinal()).apply();
    }

    // Network Mode
    public NetworkMode getNetworkMode() {
        int ordinal = preferences.getInt(KEY_NETWORK_MODE, NetworkMode.WIFI_AND_DATA.ordinal());
        return NetworkMode.values()[ordinal];
    }

    public void setNetworkMode(NetworkMode mode) {
        preferences.edit().putInt(KEY_NETWORK_MODE, mode.ordinal()).apply();
    }

    // Retry Count
    public int getRetryCount() {
        return preferences.getInt(KEY_RETRY_COUNT, Constants.DEFAULT_RETRY_COUNT);
    }

    public void setRetryCount(int count) {
        preferences.edit().putInt(KEY_RETRY_COUNT, Math.max(1, Math.min(10, count))).apply();
    }

    // Max Threads
    public int getMaxThreads() {
        return preferences.getInt(KEY_MAX_THREADS, Constants.DEFAULT_MAX_THREADS);
    }

    public void setMaxThreads(int threads) {
        preferences.edit().putInt(KEY_MAX_THREADS, Math.max(1, Math.min(10, threads))).apply();
    }

    // History Count
    public int getHistoryCount() {
        return preferences.getInt(KEY_HISTORY_COUNT, Constants.DEFAULT_HISTORY_COUNT);
    }

    public void setHistoryCount(int count) {
        preferences.edit().putInt(KEY_HISTORY_COUNT, Math.max(1, Math.min(50, count))).apply();
    }

    // Search Engine
    public int getSearchEngineIndex() {
        return preferences.getInt(KEY_SEARCH_ENGINE_INDEX, Constants.DEFAULT_SEARCH_ENGINE_INDEX);
    }

    public void setSearchEngineIndex(int index) {
        int validIndex = Math.max(0, Math.min(Constants.SEARCH_ENGINES.length - 1, index));
        preferences.edit().putInt(KEY_SEARCH_ENGINE_INDEX, validIndex).apply();
    }

    public String getSearchEngineUrl() {
        return Constants.SEARCH_ENGINES[getSearchEngineIndex()];
    }

    public String getSearchEngineName() {
        return Constants.SEARCH_ENGINE_NAMES[getSearchEngineIndex()];
    }

    // Debug Mode
    public boolean isDebugMode() {
        return preferences.getBoolean(KEY_DEBUG_MODE, false);
    }

    public void setDebugMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply();
    }

    // Page Load Delay (seconds to wait after page load before executing actions)
    public int getPageLoadDelay() {
        return preferences.getInt(KEY_PAGE_LOAD_DELAY, Constants.DEFAULT_PAGE_LOAD_DELAY);
    }

    public void setPageLoadDelay(int seconds) {
        int validSeconds = Math.max(Constants.MIN_DELAY_SECONDS,
                Math.min(Constants.MAX_DELAY_SECONDS, seconds));
        preferences.edit().putInt(KEY_PAGE_LOAD_DELAY, validSeconds).apply();
    }

    // Post-Action Delay (seconds to wait after last action before capturing content)
    public int getPostActionDelay() {
        return preferences.getInt(KEY_POST_ACTION_DELAY, Constants.DEFAULT_POST_ACTION_DELAY);
    }

    public void setPostActionDelay(int seconds) {
        int validSeconds = Math.max(Constants.MIN_DELAY_SECONDS,
                Math.min(Constants.MAX_DELAY_SECONDS, seconds));
        preferences.edit().putInt(KEY_POST_ACTION_DELAY, validSeconds).apply();
    }

    // Site List Sort Order
    public SiteListAdapter.SortOrder getSiteListSortOrder() {
        int ordinal = preferences.getInt(KEY_SITE_LIST_SORT_ORDER, SiteListAdapter.SortOrder.A_TO_Z.ordinal());
        try {
            return SiteListAdapter.SortOrder.values()[ordinal];
        } catch (ArrayIndexOutOfBoundsException e) {
            return SiteListAdapter.SortOrder.A_TO_Z;
        }
    }

    public void setSiteListSortOrder(SiteListAdapter.SortOrder sortOrder) {
        preferences.edit().putInt(KEY_SITE_LIST_SORT_ORDER, sortOrder.ordinal()).apply();
    }

    /**
     * Registers a listener to be notified when any preference changes.
     *
     * @param listener The listener to register
     */
    public void registerOnSharedPreferenceChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener The listener to unregister
     */
    public void unregisterOnSharedPreferenceChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
