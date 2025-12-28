package com.ltrudu.sitewatcher.ui.dataviewer;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ltrudu.sitewatcher.data.database.SiteWatcherDatabase;
import com.ltrudu.sitewatcher.data.dao.SiteHistoryDao;
import com.ltrudu.sitewatcher.data.dao.WatchedSiteDao;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the DataViewerFragment.
 * Handles loading and displaying site content based on the comparison mode.
 */
public class DataViewerViewModel extends AndroidViewModel {

    private static final String TAG = "DataViewerViewModel";

    /**
     * Represents the current state of the data viewer.
     */
    public enum ViewerState {
        LOADING,
        CONTENT_READY,
        NO_DATA,
        ERROR
    }

    /**
     * Data class containing the processed content for display.
     */
    public static class DataContent {
        private final String content;
        private final String modeDescription;
        private final long capturedAt;
        private final ComparisonMode mode;

        public DataContent(@NonNull String content, @NonNull String modeDescription,
                           long capturedAt, @NonNull ComparisonMode mode) {
            this.content = content;
            this.modeDescription = modeDescription;
            this.capturedAt = capturedAt;
            this.mode = mode;
        }

        @NonNull
        public String getContent() {
            return content;
        }

        @NonNull
        public String getModeDescription() {
            return modeDescription;
        }

        public long getCapturedAt() {
            return capturedAt;
        }

        @NonNull
        public ComparisonMode getMode() {
            return mode;
        }
    }

    private final SiteHistoryDao siteHistoryDao;
    private final WatchedSiteDao watchedSiteDao;
    private final ExecutorService executorService;

    private final MutableLiveData<ViewerState> state = new MutableLiveData<>(ViewerState.LOADING);
    private final MutableLiveData<DataContent> dataContent = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private long siteId = -1;

    public DataViewerViewModel(@NonNull Application application) {
        super(application);
        SiteWatcherDatabase database = SiteWatcherDatabase.getInstance(application);
        this.siteHistoryDao = database.siteHistoryDao();
        this.watchedSiteDao = database.watchedSiteDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Get the current state of the data viewer.
     */
    @NonNull
    public LiveData<ViewerState> getState() {
        return state;
    }

    /**
     * Get the data content.
     */
    @NonNull
    public LiveData<DataContent> getDataContent() {
        return dataContent;
    }

    /**
     * Get the error message.
     */
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Set the site ID and load the data.
     *
     * @param siteId The ID of the site to display
     */
    public void setSiteId(long siteId) {
        if (this.siteId != siteId) {
            this.siteId = siteId;
            loadData();
        }
    }

    /**
     * Reload the data.
     */
    public void retry() {
        loadData();
    }

    /**
     * Load site data based on the comparison mode.
     */
    private void loadData() {
        if (siteId < 0) {
            state.setValue(ViewerState.ERROR);
            errorMessage.setValue("Invalid site ID");
            return;
        }

        state.setValue(ViewerState.LOADING);

        executorService.execute(() -> {
            try {
                // Get the site info
                WatchedSite site = watchedSiteDao.getById(siteId);
                if (site == null) {
                    postError("Site not found");
                    return;
                }

                // Get the latest history entry for this site
                SiteHistory latestHistory = siteHistoryDao.getLatestBySiteId(siteId);
                if (latestHistory == null) {
                    state.postValue(ViewerState.NO_DATA);
                    return;
                }

                // Load raw content from the storage path
                String rawContent = loadContentFromHistory(latestHistory);
                if (rawContent == null) {
                    postError("Failed to load content from storage");
                    return;
                }

                // Process content based on comparison mode
                ComparisonMode mode = site.getComparisonMode();
                String processedContent = processContent(rawContent, mode, site.getCssSelector());
                String modeDescription = getModeDescription(mode, site.getCssSelector());

                DataContent result = new DataContent(
                        processedContent,
                        modeDescription,
                        latestHistory.getCheckTime(),
                        mode
                );

                dataContent.postValue(result);
                state.postValue(ViewerState.CONTENT_READY);

            } catch (Exception e) {
                Logger.e(TAG, "Error loading data", e);
                postError(e.getMessage());
            }
        });
    }

    /**
     * Load content from a history entry.
     */
    @Nullable
    private String loadContentFromHistory(@NonNull SiteHistory history) {
        String storagePath = history.getStoragePath();
        if (storagePath == null || storagePath.isEmpty()) {
            Logger.w(TAG, "Storage path is null or empty");
            return null;
        }

        File contentFile = new File(storagePath);
        if (!contentFile.exists()) {
            Logger.w(TAG, "Content file not found: " + storagePath);
            return null;
        }

        try {
            return loadPlainContent(contentFile);
        } catch (IOException e) {
            Logger.e(TAG, "Error loading content from: " + storagePath, e);
            return null;
        }
    }

    /**
     * Load plain text content from a file.
     */
    @NonNull
    private String loadPlainContent(@NonNull File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                content.append(buffer, 0, charsRead);
            }
        }
        return content.toString();
    }

    /**
     * Process content based on the comparison mode.
     *
     * @param rawContent  The raw HTML content
     * @param mode        The comparison mode
     * @param cssSelector The CSS selector (used for CSS_SELECTOR mode)
     * @return Processed content according to the mode
     */
    @NonNull
    private String processContent(@NonNull String rawContent, @NonNull ComparisonMode mode,
                                  @Nullable String cssSelector) {
        switch (mode) {
            case FULL_HTML:
                // Return raw HTML content as-is
                return rawContent;

            case TEXT_ONLY:
                // Extract text content using Jsoup, removing script and style tags
                return extractTextContent(rawContent);

            case CSS_SELECTOR:
                // Extract elements matching the CSS selector
                return extractCssSelectorContent(rawContent, cssSelector);

            default:
                return rawContent;
        }
    }

    /**
     * Extract text content from HTML, removing scripts and styles.
     *
     * @param html The raw HTML content
     * @return Text-only content
     */
    @NonNull
    private String extractTextContent(@NonNull String html) {
        try {
            Document doc = Jsoup.parse(html);
            // Remove script and style elements
            doc.select("script").remove();
            doc.select("style").remove();
            // Return the text content
            return doc.text();
        } catch (Exception e) {
            Logger.e(TAG, "Error extracting text content", e);
            return html; // Fallback to raw content
        }
    }

    /**
     * Extract content matching a CSS selector.
     *
     * @param html        The raw HTML content
     * @param cssSelector The CSS selector to match
     * @return Content from matching elements
     */
    @NonNull
    private String extractCssSelectorContent(@NonNull String html, @Nullable String cssSelector) {
        if (cssSelector == null || cssSelector.isEmpty()) {
            Logger.w(TAG, "CSS selector is null or empty, returning raw content");
            return html;
        }

        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(cssSelector);
            if (elements.isEmpty()) {
                Logger.w(TAG, "No elements found for selector: " + cssSelector);
                return "";
            }
            // Return the outer HTML of all matching elements
            return elements.outerHtml();
        } catch (Exception e) {
            Logger.e(TAG, "Error extracting CSS selector content: " + cssSelector, e);
            return html; // Fallback to raw content
        }
    }

    /**
     * Get a human-readable description of the comparison mode.
     *
     * @param mode        The comparison mode
     * @param cssSelector The CSS selector (used for CSS_SELECTOR mode)
     * @return Description string
     */
    @NonNull
    private String getModeDescription(@NonNull ComparisonMode mode, @Nullable String cssSelector) {
        switch (mode) {
            case FULL_HTML:
                return "Full HTML";
            case TEXT_ONLY:
                return "Text Only";
            case CSS_SELECTOR:
                if (cssSelector != null && !cssSelector.isEmpty()) {
                    return "CSS Selector: " + cssSelector;
                }
                return "CSS Selector";
            default:
                return mode.name();
        }
    }

    /**
     * Post an error state with a message.
     */
    private void postError(@Nullable String message) {
        errorMessage.postValue(message != null ? message : "Unknown error");
        state.postValue(ViewerState.ERROR);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
