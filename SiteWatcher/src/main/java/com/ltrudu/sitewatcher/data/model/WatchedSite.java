package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.List;

/**
 * Represents a website being monitored for changes.
 */
@Entity(tableName = "watched_sites")
public class WatchedSite {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "url")
    private String url;

    @Nullable
    @ColumnInfo(name = "name")
    private String name;

    @NonNull
    @ColumnInfo(name = "comparison_mode")
    private ComparisonMode comparisonMode;

    @Nullable
    @ColumnInfo(name = "css_selector")
    private String cssSelector;

    /**
     * CSS selectors for elements to include in CSS_SELECTOR mode.
     * If empty, all elements are included (subject to exclude filter).
     */
    @Nullable
    @ColumnInfo(name = "css_include_selector")
    private String cssIncludeSelector;

    /**
     * CSS selectors for elements to exclude in CSS_SELECTOR mode.
     * Matched elements are filtered out from the comparison.
     */
    @Nullable
    @ColumnInfo(name = "css_exclude_selector")
    private String cssExcludeSelector;

    /**
     * Minimum text block length (3-150 characters) for TEXT_ONLY mode.
     * Text blocks shorter than this are ignored to reduce false positives
     * from dynamic content like timestamps, counters, etc.
     */
    @ColumnInfo(name = "min_text_length", defaultValue = "10")
    private int minTextLength;

    /**
     * Minimum word length (1-8 characters) for TEXT_ONLY mode.
     * Words shorter than this are filtered out to reduce noise from
     * short tokens like "a", "an", "the", numbers, etc.
     */
    @ColumnInfo(name = "min_word_length", defaultValue = "3")
    private int minWordLength;

    /**
     * Fetch mode: STATIC (fast, no JS) or JAVASCRIPT (slower, executes JS).
     * Use JAVASCRIPT for dynamic sites with calendars, AJAX content, etc.
     */
    @NonNull
    @ColumnInfo(name = "fetch_mode", defaultValue = "JAVASCRIPT")
    private FetchMode fetchMode;

    /**
     * JSON array of auto-click actions to execute when page loads (for JAVASCRIPT mode).
     * Each action can be CLICK (click element) or WAIT (pause for N seconds).
     * Used to dismiss cookie consent dialogs and similar overlays.
     */
    @Nullable
    @ColumnInfo(name = "auto_click_actions")
    private String autoClickActionsJson;

    /**
     * JSON array of schedules for multi-schedule support.
     * Each schedule defines when checks should occur (calendar type + interval).
     * Replaces legacy single schedule fields for more flexibility.
     */
    @Nullable
    @ColumnInfo(name = "schedules_json")
    private String schedulesJson;

    /**
     * Diff algorithm to use for content comparison.
     * LINE (default) = line-based LCS like Git
     * WORD = word-based for prose/articles
     * CHARACTER = character-based for maximum sensitivity
     */
    @NonNull
    @ColumnInfo(name = "diff_algorithm", defaultValue = "LINE")
    private DiffAlgorithmType diffAlgorithm;

    /**
     * Minimum change percentage (1-99) to trigger notification.
     */
    @ColumnInfo(name = "threshold_percent")
    private int thresholdPercent;

    @ColumnInfo(name = "is_enabled")
    private boolean isEnabled;

    @ColumnInfo(name = "last_check_time")
    private long lastCheckTime;

    @ColumnInfo(name = "last_change_percent")
    private float lastChangePercent;

    @Nullable
    @ColumnInfo(name = "last_error")
    private String lastError;

    @ColumnInfo(name = "consecutive_failures")
    private int consecutiveFailures;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /**
     * Default constructor for Room.
     */
    public WatchedSite() {
        this.url = "";
        this.comparisonMode = ComparisonMode.FULL_HTML;
        this.fetchMode = FetchMode.JAVASCRIPT;
        this.diffAlgorithm = DiffAlgorithmType.LINE;
        this.minTextLength = 10; // Minimum 10 characters per text block
        this.minWordLength = 3; // Minimum 3 characters per word
        this.thresholdPercent = 5;
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @NonNull
    public ComparisonMode getComparisonMode() {
        return comparisonMode;
    }

    public void setComparisonMode(@NonNull ComparisonMode comparisonMode) {
        this.comparisonMode = comparisonMode;
    }

    @Nullable
    public String getCssSelector() {
        return cssSelector;
    }

    public void setCssSelector(@Nullable String cssSelector) {
        this.cssSelector = cssSelector;
    }

    @Nullable
    public String getCssIncludeSelector() {
        return cssIncludeSelector;
    }

    public void setCssIncludeSelector(@Nullable String cssIncludeSelector) {
        this.cssIncludeSelector = cssIncludeSelector;
    }

    @Nullable
    public String getCssExcludeSelector() {
        return cssExcludeSelector;
    }

    public void setCssExcludeSelector(@Nullable String cssExcludeSelector) {
        this.cssExcludeSelector = cssExcludeSelector;
    }

    public int getMinTextLength() {
        return minTextLength;
    }

    public void setMinTextLength(int minTextLength) {
        this.minTextLength = minTextLength;
    }

    public int getMinWordLength() {
        return minWordLength;
    }

    public void setMinWordLength(int minWordLength) {
        this.minWordLength = minWordLength;
    }

    @NonNull
    public FetchMode getFetchMode() {
        return fetchMode;
    }

    public void setFetchMode(@NonNull FetchMode fetchMode) {
        this.fetchMode = fetchMode;
    }

    @NonNull
    public DiffAlgorithmType getDiffAlgorithm() {
        return diffAlgorithm != null ? diffAlgorithm : DiffAlgorithmType.LINE;
    }

    public void setDiffAlgorithm(@NonNull DiffAlgorithmType diffAlgorithm) {
        this.diffAlgorithm = diffAlgorithm;
    }

    @Nullable
    public String getAutoClickActionsJson() {
        return autoClickActionsJson;
    }

    public void setAutoClickActionsJson(@Nullable String autoClickActionsJson) {
        this.autoClickActionsJson = autoClickActionsJson;
    }

    /**
     * Get auto-click actions as a list.
     *
     * @return List of auto-click actions (empty if none configured)
     */
    @NonNull
    public java.util.List<AutoClickAction> getAutoClickActions() {
        return AutoClickAction.fromJsonString(autoClickActionsJson);
    }

    /**
     * Set auto-click actions from a list.
     *
     * @param actions List of auto-click actions (null or empty to clear)
     */
    public void setAutoClickActions(@Nullable java.util.List<AutoClickAction> actions) {
        this.autoClickActionsJson = AutoClickAction.toJsonString(actions);
    }

    @Nullable
    public String getSchedulesJson() {
        return schedulesJson;
    }

    public void setSchedulesJson(@Nullable String schedulesJson) {
        this.schedulesJson = schedulesJson;
    }

    /**
     * Get schedules as a list.
     * If no schedules are configured, creates a default ALL_THE_TIME schedule.
     *
     * @return List of schedules (never empty)
     */
    @NonNull
    public List<Schedule> getSchedules() {
        List<Schedule> schedules = Schedule.fromJsonString(schedulesJson);
        if (schedules.isEmpty()) {
            // Create default schedule
            schedules = Schedule.createDefaultList();
        }
        return schedules;
    }

    /**
     * Set schedules from a list.
     *
     * @param schedules List of schedules (null or empty to clear)
     */
    public void setSchedules(@Nullable List<Schedule> schedules) {
        this.schedulesJson = Schedule.toJsonString(schedules);
    }

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(int thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(long lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }

    public float getLastChangePercent() {
        return lastChangePercent;
    }

    public void setLastChangePercent(float lastChangePercent) {
        this.lastChangePercent = lastChangePercent;
    }

    @Nullable
    public String getLastError() {
        return lastError;
    }

    public void setLastError(@Nullable String lastError) {
        this.lastError = lastError;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Get the display name for this site.
     * Returns the custom name if set, otherwise extracts domain from URL.
     *
     * @return Display name for this site
     */
    @NonNull
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        // Extract domain from URL as fallback
        try {
            String domain = url.replaceFirst("^(https?://)?([^/]+).*$", "$2");
            return domain.isEmpty() ? url : domain;
        } catch (Exception e) {
            return url;
        }
    }
}
