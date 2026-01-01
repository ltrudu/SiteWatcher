package com.ltrudu.sitewatcher.ui.addedit;

import android.app.Application;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ltrudu.sitewatcher.background.CheckScheduler;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.model.CalendarScheduleType;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.FetchMode;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.network.BuiltInClickPatterns;
import com.ltrudu.sitewatcher.network.SiteChecker;
import com.ltrudu.sitewatcher.util.Logger;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the Add/Edit Site screen.
 * Manages form state and site persistence.
 */
public class AddEditViewModel extends AndroidViewModel {

    private static final String TAG = "AddEditViewModel";

    // Repository for database operations
    private final SiteRepository repository;

    // Scheduler for alarms
    private final CheckScheduler checkScheduler;

    // Site checker for initial check
    private final SiteChecker siteChecker;

    // Edit mode flag
    private boolean isEditMode = false;
    private long siteId = -1L;

    // Current site being edited (null for add mode)
    private final MutableLiveData<WatchedSite> currentSite = new MutableLiveData<>();

    // Form state
    private final MutableLiveData<String> url = new MutableLiveData<>("");
    private final MutableLiveData<Integer> enabledDays = new MutableLiveData<>(WatchedSite.ALL_DAYS);
    private final MutableLiveData<ScheduleType> scheduleType = new MutableLiveData<>(ScheduleType.PERIODIC);
    private final MutableLiveData<Integer> intervalMinutes = new MutableLiveData<>(60);
    private final MutableLiveData<Integer> scheduleHour = new MutableLiveData<>(9);
    private final MutableLiveData<Integer> scheduleMinute = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> thresholdPercent = new MutableLiveData<>(25);
    private final MutableLiveData<FetchMode> fetchMode = new MutableLiveData<>(FetchMode.STATIC);
    private final MutableLiveData<ComparisonMode> comparisonMode = new MutableLiveData<>(ComparisonMode.TEXT_ONLY);
    private final MutableLiveData<String> cssSelector = new MutableLiveData<>("");
    private final MutableLiveData<Integer> minTextLength = new MutableLiveData<>(10);
    private final MutableLiveData<Integer> minWordLength = new MutableLiveData<>(3);
    private final MutableLiveData<List<AutoClickAction>> autoClickActions = new MutableLiveData<>(new ArrayList<>());

    // New schedules system (JSON format)
    private String schedulesJson;

    // Validation state
    private final MutableLiveData<Boolean> isUrlValid = new MutableLiveData<>(false);
    private final MutableLiveData<String> urlError = new MutableLiveData<>(null);

    // Save result event
    private final MutableLiveData<SaveResult> saveResult = new MutableLiveData<>();

    public AddEditViewModel(@NonNull Application application) {
        super(application);
        repository = SiteRepository.getInstance(application);
        checkScheduler = CheckScheduler.getInstance(application);
        siteChecker = SiteChecker.getInstance(application);
    }

    /**
     * Initialize the ViewModel with a site ID.
     * If siteId is -1, we're in add mode with defaults.
     * Otherwise, load the site for editing.
     *
     * @param siteId The site ID to edit, or -1 for add mode
     */
    public void initialize(long siteId) {
        this.siteId = siteId;
        this.isEditMode = siteId != -1L;

        if (isEditMode) {
            loadSite(siteId);
        } else {
            // Set defaults for add mode
            setDefaults();
        }
    }

    /**
     * Set default values for add mode.
     */
    private void setDefaults() {
        url.setValue("");
        enabledDays.setValue(WatchedSite.ALL_DAYS);
        scheduleType.setValue(ScheduleType.PERIODIC);
        intervalMinutes.setValue(60);
        scheduleHour.setValue(9);
        scheduleMinute.setValue(0);
        thresholdPercent.setValue(25);
        fetchMode.setValue(FetchMode.STATIC);
        comparisonMode.setValue(ComparisonMode.TEXT_ONLY);
        cssSelector.setValue("");
        minTextLength.setValue(10);
        minWordLength.setValue(3);
        autoClickActions.setValue(new ArrayList<>());
        schedulesJson = Schedule.toJsonString(Schedule.createDefaultList());
        isUrlValid.setValue(false);
    }

    /**
     * Load a site by ID for editing.
     *
     * @param siteId The site ID to load
     */
    private void loadSite(long siteId) {
        repository.getSiteById(siteId, new SiteRepository.OnResultListener<WatchedSite>() {
            @Override
            public void onSuccess(WatchedSite site) {
                if (site != null) {
                    populateFromSite(site);
                    Logger.d(TAG, "Site loaded for editing: " + siteId);
                } else {
                    Logger.e(TAG, "Site not found: " + siteId);
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Logger.e(TAG, "Error loading site: " + siteId, exception);
            }
        });
    }

    /**
     * Populate form fields from an existing site.
     * Used when loading a site for editing.
     * Uses postValue() because this may be called from a background thread.
     *
     * @param site The site to populate from
     */
    public void populateFromSite(@NonNull WatchedSite site) {
        this.siteId = site.getId();
        this.isEditMode = true;
        currentSite.postValue(site);

        url.postValue(site.getUrl());
        enabledDays.postValue(site.getEnabledDays());
        scheduleType.postValue(site.getScheduleType());
        intervalMinutes.postValue(site.getPeriodicIntervalMinutes());
        scheduleHour.postValue(site.getScheduleHour());
        scheduleMinute.postValue(site.getScheduleMinute());
        thresholdPercent.postValue(site.getThresholdPercent());
        fetchMode.postValue(site.getFetchMode());
        comparisonMode.postValue(site.getComparisonMode());
        cssSelector.postValue(site.getCssSelector() != null ? site.getCssSelector() : "");
        minTextLength.postValue(site.getMinTextLength());
        minWordLength.postValue(site.getMinWordLength());

        // Load auto-click actions
        List<AutoClickAction> actions = site.getAutoClickActions();
        autoClickActions.postValue(actions);

        // Load schedules JSON
        schedulesJson = site.getSchedulesJson();

        // Validate URL - use postValue version
        isUrlValid.postValue(true);
        urlError.postValue(null);
    }

    /**
     * Validate a URL and update validation state.
     *
     * @param urlText The URL to validate
     * @return true if valid, false otherwise
     */
    public boolean validateUrl(String urlText) {
        if (urlText == null || urlText.trim().isEmpty()) {
            isUrlValid.setValue(false);
            urlError.setValue(null);
            return false;
        }

        String trimmedUrl = urlText.trim();

        // Add https:// if no protocol specified
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            trimmedUrl = "https://" + trimmedUrl;
        }

        boolean valid = Patterns.WEB_URL.matcher(trimmedUrl).matches();
        isUrlValid.setValue(valid);
        urlError.setValue(valid ? null : "Invalid URL format");

        return valid;
    }

    /**
     * Build a WatchedSite from current form state.
     *
     * @return A new or updated WatchedSite
     */
    public WatchedSite buildSite() {
        WatchedSite site;

        if (isEditMode && currentSite.getValue() != null) {
            site = currentSite.getValue();
            site.setUpdatedAt(System.currentTimeMillis());
        } else {
            site = new WatchedSite();
        }

        String urlValue = url.getValue();
        if (urlValue != null) {
            String trimmedUrl = urlValue.trim();
            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                trimmedUrl = "https://" + trimmedUrl;
            }
            site.setUrl(trimmedUrl);
        }

        Integer days = enabledDays.getValue();
        site.setEnabledDays(days != null ? days : WatchedSite.ALL_DAYS);

        ScheduleType type = scheduleType.getValue();
        site.setScheduleType(type != null ? type : ScheduleType.PERIODIC);

        Integer interval = intervalMinutes.getValue();
        site.setPeriodicIntervalMinutes(interval != null ? interval : 60);

        Integer hour = scheduleHour.getValue();
        site.setScheduleHour(hour != null ? hour : 9);

        Integer minute = scheduleMinute.getValue();
        site.setScheduleMinute(minute != null ? minute : 0);

        Integer threshold = thresholdPercent.getValue();
        site.setThresholdPercent(threshold != null ? threshold : 25);

        FetchMode fetch = fetchMode.getValue();
        site.setFetchMode(fetch != null ? fetch : FetchMode.STATIC);

        ComparisonMode mode = comparisonMode.getValue();
        site.setComparisonMode(mode != null ? mode : ComparisonMode.TEXT_ONLY);

        if (mode == ComparisonMode.CSS_SELECTOR) {
            String selector = cssSelector.getValue();
            site.setCssSelector(selector != null && !selector.trim().isEmpty() ? selector.trim() : null);
        } else {
            site.setCssSelector(null);
        }

        Integer minLen = minTextLength.getValue();
        site.setMinTextLength(minLen != null ? minLen : 10);

        Integer minWord = minWordLength.getValue();
        site.setMinWordLength(minWord != null ? minWord : 3);

        List<AutoClickAction> actions = autoClickActions.getValue();
        site.setAutoClickActions(actions);

        // Set schedules JSON
        site.setSchedulesJson(schedulesJson);

        return site;
    }

    /**
     * Save the current site.
     * Creates a new site or updates an existing one.
     */
    public void save() {
        Boolean valid = isUrlValid.getValue();
        if (valid == null || !valid) {
            saveResult.setValue(new SaveResult(false, "Please enter a valid URL"));
            return;
        }

        WatchedSite site = buildSite();

        if (isEditMode) {
            // Update existing site
            repository.updateSite(site, new SiteRepository.OnOperationCompleteListener() {
                @Override
                public void onSuccess() {
                    Logger.d(TAG, "Site updated successfully: " + site.getId());
                    // Schedule or reschedule the alarm for this site
                    if (site.isEnabled()) {
                        checkScheduler.scheduleCheck(site);
                        Logger.d(TAG, "Scheduled alarm for updated site: " + site.getId());
                    } else {
                        checkScheduler.cancelCheck(site.getId());
                        Logger.d(TAG, "Cancelled alarm for disabled site: " + site.getId());
                    }
                    saveResult.postValue(new SaveResult(true, null, site));
                }

                @Override
                public void onError(@NonNull Exception exception) {
                    Logger.e(TAG, "Error updating site", exception);
                    saveResult.postValue(new SaveResult(false, "Failed to save site: " + exception.getMessage()));
                }
            });
        } else {
            // Insert new site
            repository.insertSite(site, new SiteRepository.OnInsertCompleteListener() {
                @Override
                public void onSuccess(long insertedId) {
                    Logger.d(TAG, "Site inserted with ID: " + insertedId);
                    site.setId(insertedId);
                    // Schedule the alarm for this new site
                    if (site.isEnabled()) {
                        checkScheduler.scheduleCheck(site);
                        Logger.d(TAG, "Scheduled alarm for new site: " + insertedId);
                    }
                    // Perform initial check to create backup data
                    performInitialCheck(site);
                    saveResult.postValue(new SaveResult(true, null, site));
                }

                @Override
                public void onError(@NonNull Exception exception) {
                    Logger.e(TAG, "Error inserting site", exception);
                    saveResult.postValue(new SaveResult(false, "Failed to save site: " + exception.getMessage()));
                }
            });
        }
    }

    // Getters for LiveData

    public boolean isEditMode() {
        return isEditMode;
    }

    public long getSiteId() {
        return siteId;
    }

    public LiveData<WatchedSite> getCurrentSite() {
        return currentSite;
    }

    public MutableLiveData<String> getUrl() {
        return url;
    }

    public MutableLiveData<Integer> getEnabledDays() {
        return enabledDays;
    }

    public MutableLiveData<ScheduleType> getScheduleType() {
        return scheduleType;
    }

    public MutableLiveData<Integer> getIntervalMinutes() {
        return intervalMinutes;
    }

    public MutableLiveData<Integer> getScheduleHour() {
        return scheduleHour;
    }

    public MutableLiveData<Integer> getScheduleMinute() {
        return scheduleMinute;
    }

    public MutableLiveData<Integer> getThresholdPercent() {
        return thresholdPercent;
    }

    public MutableLiveData<FetchMode> getFetchMode() {
        return fetchMode;
    }

    public MutableLiveData<ComparisonMode> getComparisonMode() {
        return comparisonMode;
    }

    public MutableLiveData<String> getCssSelector() {
        return cssSelector;
    }

    public MutableLiveData<Integer> getMinTextLength() {
        return minTextLength;
    }

    public MutableLiveData<Integer> getMinWordLength() {
        return minWordLength;
    }

    public MutableLiveData<List<AutoClickAction>> getAutoClickActions() {
        return autoClickActions;
    }

    public void setAutoClickActions(List<AutoClickAction> actions) {
        this.autoClickActions.setValue(actions);
    }

    @Nullable
    public String getSchedulesJson() {
        return schedulesJson;
    }

    public void setSchedulesJson(@Nullable String schedulesJson) {
        this.schedulesJson = schedulesJson;
    }

    public LiveData<Boolean> getIsUrlValid() {
        return isUrlValid;
    }

    public LiveData<String> getUrlError() {
        return urlError;
    }

    public LiveData<SaveResult> getSaveResult() {
        return saveResult;
    }

    // Setters for form state

    public void setUrl(String url) {
        this.url.setValue(url);
    }

    public void setEnabledDays(int days) {
        this.enabledDays.setValue(days);
    }

    public void setDayEnabled(int dayBitmask, boolean enabled) {
        Integer currentDays = enabledDays.getValue();
        if (currentDays == null) {
            currentDays = WatchedSite.ALL_DAYS;
        }

        if (enabled) {
            enabledDays.setValue(currentDays | dayBitmask);
        } else {
            enabledDays.setValue(currentDays & ~dayBitmask);
        }
    }

    public void setScheduleType(ScheduleType type) {
        this.scheduleType.setValue(type);
    }

    public void setIntervalMinutes(int minutes) {
        this.intervalMinutes.setValue(minutes);
    }

    public void setScheduleTime(int hour, int minute) {
        this.scheduleHour.setValue(hour);
        this.scheduleMinute.setValue(minute);
    }

    public void setThresholdPercent(int percent) {
        this.thresholdPercent.setValue(percent);
    }

    public void setFetchMode(FetchMode mode) {
        this.fetchMode.setValue(mode);
    }

    public void setComparisonMode(ComparisonMode mode) {
        this.comparisonMode.setValue(mode);
    }

    public void setCssSelector(String selector) {
        this.cssSelector.setValue(selector);
    }

    public void setMinTextLength(int length) {
        this.minTextLength.setValue(length);
    }

    public void setMinWordLength(int length) {
        this.minWordLength.setValue(length);
    }

    public void addAutoClickAction(AutoClickAction action) {
        List<AutoClickAction> current = new ArrayList<>(autoClickActions.getValue());
        action.setOrder(current.size());
        current.add(action);
        autoClickActions.setValue(current);
    }

    public void updateAutoClickAction(AutoClickAction action) {
        List<AutoClickAction> current = new ArrayList<>(autoClickActions.getValue());
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId().equals(action.getId())) {
                current.set(i, action);
                break;
            }
        }
        autoClickActions.setValue(current);
    }

    public void removeAutoClickAction(String actionId) {
        List<AutoClickAction> current = new ArrayList<>(autoClickActions.getValue());
        current.removeIf(a -> a.getId().equals(actionId));
        // Reorder remaining actions
        for (int i = 0; i < current.size(); i++) {
            current.get(i).setOrder(i);
        }
        autoClickActions.setValue(current);
    }

    public void setAutoClickActionEnabled(String actionId, boolean enabled) {
        List<AutoClickAction> current = new ArrayList<>(autoClickActions.getValue());
        for (AutoClickAction action : current) {
            if (action.getId().equals(actionId)) {
                action.setEnabled(enabled);
                break;
            }
        }
        autoClickActions.setValue(current);
    }

    public void reorderAutoClickActions(List<AutoClickAction> newOrder) {
        for (int i = 0; i < newOrder.size(); i++) {
            newOrder.get(i).setOrder(i);
        }
        autoClickActions.setValue(new ArrayList<>(newOrder));
    }

    /**
     * Performs an initial check on a newly added site to create backup data.
     * This runs asynchronously and doesn't block the UI.
     *
     * @param site The site to check
     */
    private void performInitialCheck(@NonNull WatchedSite site) {
        Logger.d(TAG, "Performing initial check for site: " + site.getId());
        siteChecker.checkSite(site, new SiteChecker.CheckCallback() {
            @Override
            public void onCheckComplete(long siteId, float changePercent, boolean hasChanged) {
                Logger.d(TAG, "Initial check complete for site " + siteId +
                        ", change: " + changePercent + "%");
            }

            @Override
            public void onCheckError(long siteId, @NonNull String error) {
                Logger.w(TAG, "Initial check failed for site " + siteId + ": " + error);
                // Don't report error to user, just log it
                // The site is still saved, initial check failure is not critical
            }
        });
    }

    /**
     * Result of a save operation.
     */
    public static class SaveResult {
        private final boolean success;
        private final String error;
        private final WatchedSite site;

        public SaveResult(boolean success, String error) {
            this(success, error, null);
        }

        public SaveResult(boolean success, String error, WatchedSite site) {
            this.success = success;
            this.error = error;
            this.site = site;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public WatchedSite getSite() {
            return site;
        }
    }
}
