package com.ltrudu.sitewatcher.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ltrudu.sitewatcher.data.model.NetworkMode;
import com.ltrudu.sitewatcher.data.model.NotificationAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.util.Logger;

/**
 * ViewModel for the Settings screen.
 * Manages all application settings and synchronizes with PreferencesManager.
 */
public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";

    private final PreferencesManager preferencesManager;

    // LiveData for all settings
    private final MutableLiveData<NotificationAction> notificationAction = new MutableLiveData<>();
    private final MutableLiveData<NetworkMode> networkMode = new MutableLiveData<>();
    private final MutableLiveData<Integer> retryCount = new MutableLiveData<>();
    private final MutableLiveData<Integer> maxThreads = new MutableLiveData<>();
    private final MutableLiveData<Integer> historyCount = new MutableLiveData<>();
    private final MutableLiveData<Integer> searchEngineIndex = new MutableLiveData<>();
    private final MutableLiveData<Boolean> debugMode = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        preferencesManager = new PreferencesManager(application);
        loadSettings();
    }

    /**
     * Loads all settings from PreferencesManager into LiveData.
     */
    private void loadSettings() {
        notificationAction.setValue(preferencesManager.getNotificationAction());
        networkMode.setValue(preferencesManager.getNetworkMode());
        retryCount.setValue(preferencesManager.getRetryCount());
        maxThreads.setValue(preferencesManager.getMaxThreads());
        historyCount.setValue(preferencesManager.getHistoryCount());
        searchEngineIndex.setValue(preferencesManager.getSearchEngineIndex());
        debugMode.setValue(preferencesManager.isDebugMode());

        Logger.d(TAG, "Settings loaded from preferences");
    }

    // Getters for LiveData

    public LiveData<NotificationAction> getNotificationAction() {
        return notificationAction;
    }

    public LiveData<NetworkMode> getNetworkMode() {
        return networkMode;
    }

    public LiveData<Integer> getRetryCount() {
        return retryCount;
    }

    public LiveData<Integer> getMaxThreads() {
        return maxThreads;
    }

    public LiveData<Integer> getHistoryCount() {
        return historyCount;
    }

    public LiveData<Integer> getSearchEngineIndex() {
        return searchEngineIndex;
    }

    public LiveData<Boolean> getDebugMode() {
        return debugMode;
    }

    // Setters that update both LiveData and PreferencesManager

    public void setNotificationAction(NotificationAction action) {
        if (action == null) return;
        preferencesManager.setNotificationAction(action);
        notificationAction.setValue(action);
        Logger.d(TAG, "Notification action updated to: " + action.name());
    }

    public void setNotificationActionByIndex(int index) {
        if (index >= 0 && index < NotificationAction.values().length) {
            setNotificationAction(NotificationAction.values()[index]);
        }
    }

    public void setNetworkMode(NetworkMode mode) {
        if (mode == null) return;
        preferencesManager.setNetworkMode(mode);
        networkMode.setValue(mode);
        Logger.d(TAG, "Network mode updated to: " + mode.name());
    }

    public void setNetworkModeByIndex(int index) {
        if (index >= 0 && index < NetworkMode.values().length) {
            setNetworkMode(NetworkMode.values()[index]);
        }
    }

    public void setRetryCount(int count) {
        int validCount = Math.max(1, Math.min(10, count));
        preferencesManager.setRetryCount(validCount);
        retryCount.setValue(validCount);
        Logger.d(TAG, "Retry count updated to: " + validCount);
    }

    public void setMaxThreads(int threads) {
        int validThreads = Math.max(1, Math.min(10, threads));
        preferencesManager.setMaxThreads(validThreads);
        maxThreads.setValue(validThreads);
        Logger.d(TAG, "Max threads updated to: " + validThreads);
    }

    public void setHistoryCount(int count) {
        int validCount = Math.max(1, Math.min(50, count));
        preferencesManager.setHistoryCount(validCount);
        historyCount.setValue(validCount);
        Logger.d(TAG, "History count updated to: " + validCount);
    }

    public void setSearchEngineIndex(int index) {
        preferencesManager.setSearchEngineIndex(index);
        searchEngineIndex.setValue(preferencesManager.getSearchEngineIndex());
        Logger.d(TAG, "Search engine updated to index: " + index);
    }

    public void setDebugMode(boolean enabled) {
        preferencesManager.setDebugMode(enabled);
        debugMode.setValue(enabled);
        Logger.setEnabled(enabled);
        Logger.d(TAG, "Debug mode updated to: " + enabled);
    }

    /**
     * Gets the PreferencesManager instance.
     * Useful for accessing additional preference methods not exposed through LiveData.
     *
     * @return The PreferencesManager instance
     */
    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }
}
