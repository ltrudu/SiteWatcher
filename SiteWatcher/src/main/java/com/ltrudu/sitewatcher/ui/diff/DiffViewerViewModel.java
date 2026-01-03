package com.ltrudu.sitewatcher.ui.diff;

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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the DiffViewerFragment.
 * Handles loading and comparing site content versions.
 */
public class DiffViewerViewModel extends AndroidViewModel {

    private static final String TAG = "DiffViewerViewModel";

    /**
     * Represents the current state of the diff viewer.
     */
    public enum DiffState {
        LOADING,
        CONTENT_READY,
        NO_PREVIOUS_VERSION,
        ERROR
    }

    /**
     * Data class containing diff information.
     */
    public static class DiffResult {
        private final String siteUrl;
        private final String oldContent;
        private final String newContent;
        private final String oldContentFiltered;
        private final String newContentFiltered;
        private final long oldTimestamp;
        private final long newTimestamp;
        private final int addedLines;
        private final int removedLines;
        private final String diffText;
        private final ComparisonMode comparisonMode;
        private final boolean cssIncludeSelectorEmpty;

        public DiffResult(String siteUrl, String oldContent, String newContent,
                          String oldContentFiltered, String newContentFiltered,
                          long oldTimestamp, long newTimestamp, int addedLines,
                          int removedLines, String diffText, ComparisonMode comparisonMode,
                          boolean cssIncludeSelectorEmpty) {
            this.siteUrl = siteUrl;
            this.oldContent = oldContent;
            this.newContent = newContent;
            this.oldContentFiltered = oldContentFiltered;
            this.newContentFiltered = newContentFiltered;
            this.oldTimestamp = oldTimestamp;
            this.newTimestamp = newTimestamp;
            this.addedLines = addedLines;
            this.removedLines = removedLines;
            this.diffText = diffText;
            this.comparisonMode = comparisonMode;
            this.cssIncludeSelectorEmpty = cssIncludeSelectorEmpty;
        }

        public String getSiteUrl() {
            return siteUrl;
        }

        public String getOldContent() {
            return oldContent;
        }

        public String getNewContent() {
            return newContent;
        }

        /**
         * Get the filtered old content for WebView display.
         * Returns content with excluded elements removed.
         */
        public String getOldContentFiltered() {
            return oldContentFiltered;
        }

        /**
         * Get the filtered new content for WebView display.
         * Returns content with excluded elements removed.
         */
        public String getNewContentFiltered() {
            return newContentFiltered;
        }

        public long getOldTimestamp() {
            return oldTimestamp;
        }

        public long getNewTimestamp() {
            return newTimestamp;
        }

        public int getAddedLines() {
            return addedLines;
        }

        public int getRemovedLines() {
            return removedLines;
        }

        public String getDiffText() {
            return diffText;
        }

        public ComparisonMode getComparisonMode() {
            return comparisonMode;
        }

        /**
         * Returns true if the CSS include selector is empty.
         * When CSS_SELECTOR mode has empty include, full HTML is stored (minus excluded elements),
         * allowing Changes Only view mode.
         */
        public boolean isCssIncludeSelectorEmpty() {
            return cssIncludeSelectorEmpty;
        }
    }

    private final SiteHistoryDao siteHistoryDao;
    private final WatchedSiteDao watchedSiteDao;
    private final ExecutorService executorService;

    private final MutableLiveData<DiffState> state = new MutableLiveData<>(DiffState.LOADING);
    private final MutableLiveData<DiffResult> diffResult = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private long siteId = -1;

    public DiffViewerViewModel(@NonNull Application application) {
        super(application);
        SiteWatcherDatabase database = SiteWatcherDatabase.getInstance(application);
        this.siteHistoryDao = database.siteHistoryDao();
        this.watchedSiteDao = database.watchedSiteDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Get the current state of the diff viewer.
     */
    @NonNull
    public LiveData<DiffState> getState() {
        return state;
    }

    /**
     * Get the diff result.
     */
    @NonNull
    public LiveData<DiffResult> getDiffResult() {
        return diffResult;
    }

    /**
     * Get the error message.
     */
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Set the site ID and load the diff.
     *
     * @param siteId The ID of the site to compare
     */
    public void setSiteId(long siteId) {
        if (this.siteId != siteId) {
            this.siteId = siteId;
            loadDiff();
        }
    }

    /**
     * Reload the diff data.
     */
    public void retry() {
        loadDiff();
    }

    /**
     * Load and compare the two most recent history entries.
     */
    private void loadDiff() {
        if (siteId < 0) {
            state.setValue(DiffState.ERROR);
            errorMessage.setValue("Invalid site ID");
            return;
        }

        state.setValue(DiffState.LOADING);

        executorService.execute(() -> {
            try {
                // Get the site info
                WatchedSite site = watchedSiteDao.getById(siteId);
                if (site == null) {
                    postError("Site not found");
                    return;
                }

                // Get the two most recent history entries
                List<SiteHistory> historyList = siteHistoryDao.getLatestTwoBySiteId(siteId);

                if (historyList.isEmpty()) {
                    state.postValue(DiffState.NO_PREVIOUS_VERSION);
                    return;
                }

                if (historyList.size() < 2) {
                    state.postValue(DiffState.NO_PREVIOUS_VERSION);
                    return;
                }

                // historyList[0] is the newest, historyList[1] is the older one
                SiteHistory newHistory = historyList.get(0);
                SiteHistory oldHistory = historyList.get(1);

                // Load content from both versions
                String newContentRaw = loadContentFromHistory(newHistory);
                String oldContentRaw = loadContentFromHistory(oldHistory);

                if (newContentRaw == null || oldContentRaw == null) {
                    postError("Failed to load content");
                    return;
                }

                // Check if CSS include selector is empty
                String cssInclude = site.getCssIncludeSelector();
                String cssExclude = site.getCssExcludeSelector();
                String legacyCssSelector = site.getCssSelector();
                boolean cssIncludeEmpty = (cssInclude == null || cssInclude.trim().isEmpty())
                        && (legacyCssSelector == null || legacyCssSelector.trim().isEmpty());

                // Apply CSS filtering for CSS_SELECTOR mode
                String oldContent = oldContentRaw;
                String newContent = newContentRaw;
                String oldContentFiltered = oldContentRaw;
                String newContentFiltered = newContentRaw;

                if (site.getComparisonMode() == ComparisonMode.CSS_SELECTOR) {
                    // Apply CSS filtering for the diff comparison
                    oldContent = applyCssFilter(oldContentRaw, cssInclude, legacyCssSelector, cssExclude);
                    newContent = applyCssFilter(newContentRaw, cssInclude, legacyCssSelector, cssExclude);

                    // Generate filtered HTML for WebView display (exclude filter only)
                    if (cssExclude != null && !cssExclude.trim().isEmpty()) {
                        oldContentFiltered = generateFilteredHtml(oldContentRaw, cssExclude);
                        newContentFiltered = generateFilteredHtml(newContentRaw, cssExclude);
                    }
                }

                // Compute the diff
                DiffResult result = computeDiff(
                        site.getUrl(),
                        oldContent,
                        newContent,
                        oldContentFiltered,
                        newContentFiltered,
                        oldHistory.getCheckTime(),
                        newHistory.getCheckTime(),
                        site.getComparisonMode(),
                        cssIncludeEmpty
                );

                diffResult.postValue(result);
                state.postValue(DiffState.CONTENT_READY);

            } catch (Exception e) {
                Logger.e(TAG, "Error loading diff", e);
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
     * Reads the entire file as-is to preserve exact content.
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
     * Compute the diff between old and new content.
     */
    @NonNull
    private DiffResult computeDiff(String siteUrl, String oldContent, String newContent,
                                    String oldContentFiltered, String newContentFiltered,
                                    long oldTimestamp, long newTimestamp, ComparisonMode comparisonMode,
                                    boolean cssIncludeSelectorEmpty) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // Simple line-by-line diff using LCS (Longest Common Subsequence) approach
        List<DiffLine> diffLines = computeLineDiff(oldLines, newLines);

        int addedCount = 0;
        int removedCount = 0;
        StringBuilder diffText = new StringBuilder();

        for (DiffLine line : diffLines) {
            switch (line.type) {
                case ADDED:
                    addedCount++;
                    diffText.append("+ ").append(line.text).append("\n");
                    break;
                case REMOVED:
                    removedCount++;
                    diffText.append("- ").append(line.text).append("\n");
                    break;
                case UNCHANGED:
                    diffText.append("  ").append(line.text).append("\n");
                    break;
            }
        }

        return new DiffResult(
                siteUrl,
                oldContent,
                newContent,
                oldContentFiltered,
                newContentFiltered,
                oldTimestamp,
                newTimestamp,
                addedCount,
                removedCount,
                diffText.toString(),
                comparisonMode,
                cssIncludeSelectorEmpty
        );
    }

    /**
     * Enum for diff line types.
     */
    private enum DiffLineType {
        ADDED,
        REMOVED,
        UNCHANGED
    }

    /**
     * Represents a line in the diff output.
     */
    private static class DiffLine {
        final DiffLineType type;
        final String text;

        DiffLine(DiffLineType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    /**
     * Compute line-by-line diff using a simple algorithm.
     */
    @NonNull
    private List<DiffLine> computeLineDiff(String[] oldLines, String[] newLines) {
        List<DiffLine> result = new ArrayList<>();

        // Use the Hunt-McIlroy diff algorithm (simplified version)
        int[][] lcs = computeLCS(oldLines, newLines);

        int i = oldLines.length;
        int j = newLines.length;
        List<DiffLine> reversed = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                reversed.add(new DiffLine(DiffLineType.UNCHANGED, oldLines[i - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                reversed.add(new DiffLine(DiffLineType.ADDED, newLines[j - 1]));
                j--;
            } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                reversed.add(new DiffLine(DiffLineType.REMOVED, oldLines[i - 1]));
                i--;
            }
        }

        // Reverse the result
        for (int k = reversed.size() - 1; k >= 0; k--) {
            result.add(reversed.get(k));
        }

        return result;
    }

    /**
     * Compute the LCS (Longest Common Subsequence) matrix.
     */
    private int[][] computeLCS(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        return lcs;
    }

    /**
     * Apply CSS filtering to content.
     * Extracts text from elements matching include selectors and excludes elements matching exclude selectors.
     *
     * @param html The raw HTML content
     * @param cssInclude CSS selector for elements to include (null or empty = include all)
     * @param legacyCssSelector Legacy CSS selector (for backward compatibility)
     * @param cssExclude CSS selector for elements to exclude
     * @return Filtered text content
     */
    @NonNull
    private String applyCssFilter(@NonNull String html, @Nullable String cssInclude,
                                   @Nullable String legacyCssSelector, @Nullable String cssExclude) {
        try {
            Document doc = Jsoup.parse(html);

            // Remove script, style, and noscript elements first
            doc.select("script, style, noscript").remove();

            // Determine which include selector to use
            String includeSelector = null;
            if (cssInclude != null && !cssInclude.trim().isEmpty()) {
                includeSelector = cssInclude.trim();
            } else if (legacyCssSelector != null && !legacyCssSelector.trim().isEmpty()) {
                includeSelector = legacyCssSelector.trim();
            }

            // Apply include filter if specified
            Element targetElement;
            if (includeSelector != null) {
                Elements selectedElements = doc.select(includeSelector);
                if (selectedElements.isEmpty()) {
                    return ""; // No matching elements
                }
                // Create a container for all matched elements
                StringBuilder combinedText = new StringBuilder();
                for (Element el : selectedElements) {
                    // Apply exclude filter to each included element
                    if (cssExclude != null && !cssExclude.trim().isEmpty()) {
                        el.select(cssExclude.trim()).remove();
                    }
                    combinedText.append(el.text()).append("\n");
                }
                return combinedText.toString().trim();
            } else {
                // No include selector - use whole body
                targetElement = doc.body();
                if (targetElement == null) {
                    return doc.text();
                }

                // Apply exclude filter if specified
                if (cssExclude != null && !cssExclude.trim().isEmpty()) {
                    targetElement.select(cssExclude.trim()).remove();
                }

                return targetElement.text();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error applying CSS filter", e);
            return html; // Return original on error
        }
    }

    /**
     * Generate filtered HTML for WebView display.
     * Removes excluded elements from HTML while preserving structure for rendering.
     *
     * @param html The raw HTML content
     * @param cssExclude CSS selector for elements to exclude
     * @return HTML with excluded elements removed
     */
    @NonNull
    private String generateFilteredHtml(@NonNull String html, @NonNull String cssExclude) {
        try {
            Document doc = Jsoup.parse(html);

            // Remove script and style elements that might cause issues
            doc.select("script, style, noscript").remove();

            // Apply exclude filter
            Elements excludedElements = doc.select(cssExclude.trim());
            excludedElements.remove();

            return doc.html();
        } catch (Exception e) {
            Logger.e(TAG, "Error generating filtered HTML", e);
            return html; // Return original on error
        }
    }

    /**
     * Post an error state with a message.
     */
    private void postError(@Nullable String message) {
        errorMessage.postValue(message != null ? message : "Unknown error");
        state.postValue(DiffState.ERROR);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
