package com.ltrudu.sitewatcher.network;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.notification.NotificationHelper;
import com.ltrudu.sitewatcher.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Performs site check operations including fetching content,
 * comparing with previous version, updating database, and sending notifications.
 */
public class SiteChecker {

    private static final String TAG = "SiteChecker";
    private static final String HISTORY_DIR = "site_history";

    @SuppressLint("StaticFieldLeak")
    private static volatile SiteChecker instance;

    private final Context context;
    private final SiteRepository repository;
    private final SiteContentFetcher fetcher;
    private final ContentComparator comparator;
    private final ExecutorService executorService;

    /**
     * Callback interface for site check results.
     */
    public interface CheckCallback {
        /**
         * Called when site check completes successfully.
         * @param siteId The ID of the checked site
         * @param changePercent The percentage of change detected
         * @param hasChanged Whether significant change was detected
         */
        void onCheckComplete(long siteId, float changePercent, boolean hasChanged);

        /**
         * Called when site check fails.
         * @param siteId The ID of the checked site
         * @param error The error message
         */
        void onCheckError(long siteId, @NonNull String error);
    }

    /**
     * Private constructor for singleton pattern.
     * @param context Application context
     */
    private SiteChecker(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.repository = SiteRepository.getInstance(
                (android.app.Application) context.getApplicationContext());
        this.fetcher = new SiteContentFetcher();
        this.comparator = new ContentComparator();
        this.executorService = Executors.newFixedThreadPool(3);

        // Ensure history directory exists
        File historyDir = new File(context.getFilesDir(), HISTORY_DIR);
        if (!historyDir.exists()) {
            historyDir.mkdirs();
        }
    }

    /**
     * Get the singleton instance of SiteChecker.
     * @param context Application context
     * @return The SiteChecker instance
     */
    @NonNull
    public static SiteChecker getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SiteChecker.class) {
                if (instance == null) {
                    instance = new SiteChecker(context);
                }
            }
        }
        return instance;
    }

    /**
     * Check a site for changes.
     * Runs asynchronously and calls the callback when complete.
     * @param site The site to check
     * @param callback The callback for check results
     */
    public void checkSite(@NonNull WatchedSite site, @NonNull CheckCallback callback) {
        executorService.execute(() -> performCheck(site, callback));
    }

    /**
     * Perform the actual site check on a background thread.
     * @param site The site to check
     * @param callback The callback for check results
     */
    private void performCheck(@NonNull WatchedSite site, @NonNull CheckCallback callback) {
        long checkTime = System.currentTimeMillis();
        Logger.d(TAG, "Starting check for site: " + site.getId() + " - " + site.getUrl());

        try {
            // Fetch current content
            FetchResult fetchResult = fetcher.fetchContent(site.getUrl());

            if (!fetchResult.isSuccess()) {
                String error = fetchResult.getError();
                Logger.e(TAG, "Fetch failed for site " + site.getId() + ": " + error);

                // Update site with error
                repository.updateLastCheck(site.getId(), checkTime, 0, error);

                callback.onCheckError(site.getId(), error != null ? error : "Unknown error");
                return;
            }

            String newContent = fetchResult.getContent();
            String newContentHash = calculateHash(newContent);

            // Get previous history entry
            SiteHistory latestHistory = repository.getLatestHistoryForSite(site.getId());

            // Fast path: if this is the first check, save and return
            if (latestHistory == null) {
                Logger.d(TAG, "First check for site " + site.getId() + ", saving initial content");
                repository.updateLastCheck(site.getId(), checkTime, 0, null);
                saveHistory(site.getId(), newContent, checkTime);
                callback.onCheckComplete(site.getId(), 0, false);
                return;
            }

            // Fast path: compare hashes first (most reliable)
            String oldContentHash = latestHistory.getContentHash();
            if (oldContentHash != null && oldContentHash.equals(newContentHash)) {
                Logger.d(TAG, "Hash match for site " + site.getId() + " - no change");
                repository.updateLastCheck(site.getId(), checkTime, 0, null);
                callback.onCheckComplete(site.getId(), 0, false);
                return;
            }

            // Hashes differ - load old content and do detailed comparison
            String oldContent = loadContentFromHistory(latestHistory);

            // If we couldn't load old content, treat as first check
            if (oldContent == null) {
                Logger.w(TAG, "Could not load old content for site " + site.getId() + ", saving new content");
                repository.updateLastCheck(site.getId(), checkTime, 0, null);
                saveHistory(site.getId(), newContent, checkTime);
                callback.onCheckComplete(site.getId(), 0, false);
                return;
            }

            // Compare content using the configured comparison mode
            ComparisonResult comparisonResult = comparator.compareContent(
                    oldContent,
                    newContent,
                    site.getComparisonMode(),
                    site.getCssSelector()
            );

            float changePercent = comparisonResult.getChangePercent();
            boolean hasChanged = comparisonResult.hasChanged();
            boolean isSignificant = comparisonResult.isSignificantChange(site.getThresholdPercent());

            Logger.d(TAG, "Check complete for site " + site.getId() +
                    ": " + changePercent + "% change, hasChanged=" + hasChanged +
                    ", significant=" + isSignificant);

            // Update site with check result
            repository.updateLastCheck(site.getId(), checkTime, changePercent, null);

            // Only save new history if change is significant (above threshold)
            // This prevents minor dynamic content from polluting history
            if (isSignificant) {
                saveHistory(site.getId(), newContent, checkTime);
                NotificationHelper.showSiteChangedNotification(context, site, changePercent);
            }

            callback.onCheckComplete(site.getId(), changePercent, hasChanged);

        } catch (Exception e) {
            Logger.e(TAG, "Error checking site " + site.getId(), e);
            String error = e.getMessage() != null ? e.getMessage() : "Unknown error";
            repository.updateLastCheck(site.getId(), checkTime, 0, error);
            callback.onCheckError(site.getId(), error);
        }
    }

    /**
     * Load content from a history entry's storage path.
     * @param history The history entry
     * @return The content, or null if not found
     */
    @Nullable
    private String loadContentFromHistory(@Nullable SiteHistory history) {
        if (history == null) {
            return null;
        }

        String storagePath = history.getStoragePath();
        if (storagePath == null || storagePath.isEmpty()) {
            return null;
        }

        File file = new File(storagePath);
        if (!file.exists()) {
            Logger.w(TAG, "History file not found: " + storagePath);
            return null;
        }

        try {
            return loadPlainContent(file);
        } catch (IOException e) {
            Logger.e(TAG, "Error loading content from history: " + storagePath, e);
            return null;
        }
    }

    /**
     * Load plain text content from a file.
     * Reads the entire file as-is to preserve exact content.
     * @param file The file to read
     * @return The content
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
     * Save content to a history entry as plain text.
     * @param siteId The site ID
     * @param content The content to save
     * @param checkTime The check timestamp
     */
    private void saveHistory(long siteId, @NonNull String content, long checkTime) {
        try {
            // Generate file path (plain text)
            String fileName = siteId + "_" + checkTime + ".html";
            File historyDir = new File(context.getFilesDir(), HISTORY_DIR);
            File file = new File(historyDir, fileName);

            // Save plain text content
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file),
                    StandardCharsets.UTF_8)) {
                writer.write(content);
            }

            // Calculate content hash
            String contentHash = calculateHash(content);

            // Create history entry
            SiteHistory history = new SiteHistory();
            history.setSiteId(siteId);
            history.setStoragePath(file.getAbsolutePath());
            history.setContentHash(contentHash);
            history.setContentSize(content.length());
            history.setCheckTime(checkTime);
            history.setCompressed(false);

            repository.insertHistory(history);

            // Prune old history entries
            repository.pruneOldHistory(siteId, 10);

            Logger.d(TAG, "Saved history for site " + siteId + " to " + file.getAbsolutePath());

        } catch (IOException e) {
            Logger.e(TAG, "Error saving history for site " + siteId, e);
        }
    }

    /**
     * Calculate MD5 hash of content.
     * @param content The content to hash
     * @return The hex-encoded hash
     */
    @NonNull
    private String calculateHash(@NonNull String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        Logger.d(TAG, "SiteChecker executor shutdown");
    }
}
