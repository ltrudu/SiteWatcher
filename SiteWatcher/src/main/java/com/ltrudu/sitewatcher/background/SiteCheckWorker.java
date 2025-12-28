package com.ltrudu.sitewatcher.background;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.CheckResult;
import com.ltrudu.sitewatcher.data.model.NetworkMode;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Constants;
import com.ltrudu.sitewatcher.util.Logger;

/**
 * Runnable that performs the actual site check operation.
 * Handles network validation, content fetching, comparison, and result processing.
 */
public class SiteCheckWorker implements Runnable {

    private static final String TAG = "SiteCheckWorker";

    /**
     * Callback interface for check completion events.
     */
    public interface OnCheckCompleteListener {
        /**
         * Called when a check completes successfully.
         * @param siteId The site ID
         * @param changePercent The percentage of content that changed
         * @param thresholdExceeded Whether the change exceeded the notification threshold
         */
        void onSuccess(long siteId, float changePercent, boolean thresholdExceeded);

        /**
         * Called when a check fails with an error.
         * @param siteId The site ID
         * @param error The error that occurred
         */
        void onError(long siteId, Throwable error);
    }

    /**
     * Provider interface for fetching site content.
     * Must be implemented to provide actual HTTP fetching capability.
     */
    public interface SiteContentFetcher {
        /**
         * Fetch the content from a URL.
         * @param url The URL to fetch
         * @param cssSelector Optional CSS selector to extract specific content
         * @return The fetched content as a string
         * @throws Exception If fetching fails
         */
        String fetchContent(String url, @Nullable String cssSelector) throws Exception;
    }

    /**
     * Provider interface for content comparison.
     * Must be implemented to provide comparison logic.
     */
    public interface ContentComparator {
        /**
         * Compare two content strings and return the change percentage.
         * @param oldContent The previous content
         * @param newContent The new content
         * @return Change percentage (0-100)
         */
        float compareContent(@Nullable String oldContent, @NonNull String newContent);
    }

    /**
     * Provider interface for history data access.
     * Must be implemented to provide database access.
     */
    public interface HistoryProvider {
        /**
         * Get the most recent history entry for a site.
         * @param siteId The site ID
         * @return The most recent SiteHistory or null if none exists
         */
        @Nullable
        SiteHistory getLatestHistory(long siteId);

        /**
         * Get the content stored at a history entry's storage path.
         * @param history The history entry
         * @return The stored content or null if not available
         */
        @Nullable
        String getStoredContent(SiteHistory history);

        /**
         * Save a new history entry with content.
         * @param siteId The site ID
         * @param content The content to store
         * @param contentHash Hash of the content
         * @return The created SiteHistory
         */
        SiteHistory saveHistory(long siteId, String content, String contentHash);
    }

    /**
     * Provider interface for updating site data.
     * Must be implemented to provide database access.
     */
    public interface SiteUpdater {
        /**
         * Update a site after a successful check.
         * @param siteId The site ID
         * @param checkTime The check timestamp
         * @param changePercent The change percentage
         * @param error Error message or null if successful
         * @param consecutiveFailures Number of consecutive failures
         */
        void updateSiteAfterCheck(long siteId, long checkTime, float changePercent,
                @Nullable String error, int consecutiveFailures);

        /**
         * Save a check result.
         * @param result The CheckResult to save
         */
        void saveCheckResult(CheckResult result);
    }

    /**
     * Provider interface for triggering notifications.
     * Must be implemented to provide notification capability.
     */
    public interface NotificationTrigger {
        /**
         * Trigger a change notification for a site.
         * @param context Application context
         * @param site The site that changed
         * @param changePercent The change percentage
         */
        void notifyChange(Context context, WatchedSite site, float changePercent);
    }

    /**
     * Provider interface for getting the current network mode setting.
     */
    public interface NetworkModeProvider {
        /**
         * Get the current network mode setting.
         * @return The NetworkMode setting
         */
        NetworkMode getNetworkMode();
    }

    // Static providers that should be set during application initialization
    private static SiteContentFetcher contentFetcher;
    private static ContentComparator contentComparator;
    private static HistoryProvider historyProvider;
    private static SiteUpdater siteUpdater;
    private static NotificationTrigger notificationTrigger;
    private static NetworkModeProvider networkModeProvider;

    private final Context context;
    private final WatchedSite site;
    private final OnCheckCompleteListener listener;

    /**
     * Create a new SiteCheckWorker.
     * @param context Application context
     * @param site The site to check
     * @param listener Callback for completion events
     */
    public SiteCheckWorker(@NonNull Context context, @NonNull WatchedSite site,
            @NonNull OnCheckCompleteListener listener) {
        this.context = context.getApplicationContext();
        this.site = site;
        this.listener = listener;
    }

    /**
     * Set the content fetcher provider.
     * @param fetcher The SiteContentFetcher implementation
     */
    public static void setContentFetcher(@NonNull SiteContentFetcher fetcher) {
        contentFetcher = fetcher;
    }

    /**
     * Set the content comparator provider.
     * @param comparator The ContentComparator implementation
     */
    public static void setContentComparator(@NonNull ContentComparator comparator) {
        contentComparator = comparator;
    }

    /**
     * Set the history provider.
     * @param provider The HistoryProvider implementation
     */
    public static void setHistoryProvider(@NonNull HistoryProvider provider) {
        historyProvider = provider;
    }

    /**
     * Set the site updater provider.
     * @param updater The SiteUpdater implementation
     */
    public static void setSiteUpdater(@NonNull SiteUpdater updater) {
        siteUpdater = updater;
    }

    /**
     * Set the notification trigger provider.
     * @param trigger The NotificationTrigger implementation
     */
    public static void setNotificationTrigger(@NonNull NotificationTrigger trigger) {
        notificationTrigger = trigger;
    }

    /**
     * Set the network mode provider.
     * @param provider The NetworkModeProvider implementation
     */
    public static void setNetworkModeProvider(@NonNull NetworkModeProvider provider) {
        networkModeProvider = provider;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        String errorMessage = null;
        float changePercent = 0f;
        boolean success = false;
        int retryCount = 0;

        Logger.d(TAG, "Starting check for site " + site.getId() + ": " + site.getUrl());

        try {
            // Step 1: Check network availability based on NetworkMode setting
            if (!isNetworkAvailable()) {
                throw new NetworkUnavailableException("Network not available for current mode");
            }

            // Retry loop
            Exception lastError = null;
            while (retryCount < Constants.DEFAULT_RETRY_COUNT) {
                try {
                    // Step 2: Fetch site content
                    String newContent = fetchSiteContent();

                    // Step 3: Get previous content from history
                    String previousContent = getPreviousContent();

                    // Step 4: Compare content
                    changePercent = compareContent(previousContent, newContent);

                    // Step 5: Save new history entry
                    saveNewHistory(newContent);

                    // Step 6: Update WatchedSite with results
                    updateSite(changePercent, null);

                    // Step 7: Check threshold and trigger notification if needed
                    boolean thresholdExceeded = changePercent >= site.getThresholdPercent();
                    if (thresholdExceeded) {
                        triggerNotification(changePercent);
                    }

                    success = true;
                    Logger.i(TAG, "Check completed for site " + site.getId() +
                            ", change: " + changePercent + "%");

                    // Success - exit retry loop
                    break;

                } catch (Exception e) {
                    lastError = e;
                    retryCount++;
                    Logger.w(TAG, "Check attempt " + retryCount + " failed for site " +
                            site.getId() + ": " + e.getMessage());

                    if (retryCount < Constants.DEFAULT_RETRY_COUNT) {
                        // Wait before retry with exponential backoff
                        try {
                            Thread.sleep(1000L * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Check interrupted", ie);
                        }
                    }
                }
            }

            if (!success && lastError != null) {
                throw lastError;
            }

        } catch (NetworkUnavailableException e) {
            errorMessage = e.getMessage();
            Logger.w(TAG, "Network unavailable for site " + site.getId() + ": " + errorMessage);
            // Don't count network issues as failures
            updateSite(0, errorMessage);

        } catch (Exception e) {
            errorMessage = e.getMessage();
            Logger.e(TAG, "Check failed for site " + site.getId(), e);

            // Update site with error
            int failures = site.getConsecutiveFailures() + 1;
            updateSiteWithFailure(errorMessage, failures);
        }

        // Save check result
        long responseTime = System.currentTimeMillis() - startTime;
        saveCheckResult(success, changePercent, errorMessage, responseTime);

        // Notify listener
        if (success) {
            listener.onSuccess(site.getId(), changePercent,
                    changePercent >= site.getThresholdPercent());
        } else {
            listener.onError(site.getId(),
                    errorMessage != null ? new Exception(errorMessage) : new Exception("Unknown error"));
        }
    }

    /**
     * Check if network is available based on the current NetworkMode setting.
     * @return true if network is available for the current mode
     */
    private boolean isNetworkAvailable() {
        NetworkMode mode = NetworkMode.WIFI_AND_DATA; // Default
        if (networkModeProvider != null) {
            mode = networkModeProvider.getNetworkMode();
        }

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }

            boolean hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

            if (!hasInternet) {
                return false;
            }

            switch (mode) {
                case WIFI_ONLY:
                    return hasWifi;
                case DATA_ONLY:
                    return hasCellular;
                case WIFI_AND_DATA:
                default:
                    return hasWifi || hasCellular;
            }
        } else {
            // Legacy network check
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                return false;
            }

            int type = activeNetwork.getType();
            switch (mode) {
                case WIFI_ONLY:
                    return type == ConnectivityManager.TYPE_WIFI;
                case DATA_ONLY:
                    return type == ConnectivityManager.TYPE_MOBILE;
                case WIFI_AND_DATA:
                default:
                    return true;
            }
        }
    }

    /**
     * Fetch the site content using the content fetcher.
     * @return The fetched content
     * @throws Exception If fetching fails
     */
    @NonNull
    private String fetchSiteContent() throws Exception {
        if (contentFetcher == null) {
            throw new IllegalStateException("SiteContentFetcher not set");
        }

        String content = contentFetcher.fetchContent(site.getUrl(), site.getCssSelector());
        if (content == null) {
            throw new Exception("Fetched content is null");
        }

        Logger.d(TAG, "Fetched " + content.length() + " bytes for site " + site.getId());
        return content;
    }

    /**
     * Get the previous content from history.
     * @return Previous content or null if no history exists
     */
    @Nullable
    private String getPreviousContent() {
        if (historyProvider == null) {
            Logger.w(TAG, "HistoryProvider not set, no previous content available");
            return null;
        }

        SiteHistory latestHistory = historyProvider.getLatestHistory(site.getId());
        if (latestHistory == null) {
            Logger.d(TAG, "No previous history for site " + site.getId());
            return null;
        }

        return historyProvider.getStoredContent(latestHistory);
    }

    /**
     * Compare old and new content.
     * @param oldContent Previous content (may be null)
     * @param newContent New content
     * @return Change percentage
     */
    private float compareContent(@Nullable String oldContent, @NonNull String newContent) {
        if (contentComparator == null) {
            Logger.w(TAG, "ContentComparator not set, returning 0%");
            return 0f;
        }

        if (oldContent == null) {
            // First check - no change
            return 0f;
        }

        return contentComparator.compareContent(oldContent, newContent);
    }

    /**
     * Save new content to history.
     * @param content The content to save
     */
    private void saveNewHistory(@NonNull String content) {
        if (historyProvider == null) {
            Logger.w(TAG, "HistoryProvider not set, cannot save history");
            return;
        }

        String contentHash = computeContentHash(content);
        historyProvider.saveHistory(site.getId(), content, contentHash);
        Logger.d(TAG, "Saved history for site " + site.getId());
    }

    /**
     * Compute a hash of the content.
     * @param content The content to hash
     * @return The hash string
     */
    @NonNull
    private String computeContentHash(@NonNull String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * Update the site with check results.
     * @param changePercent The change percentage
     * @param error Error message or null
     */
    private void updateSite(float changePercent, @Nullable String error) {
        if (siteUpdater == null) {
            Logger.w(TAG, "SiteUpdater not set, cannot update site");
            return;
        }

        // Reset consecutive failures on success
        int failures = (error == null) ? 0 : site.getConsecutiveFailures();
        siteUpdater.updateSiteAfterCheck(site.getId(), System.currentTimeMillis(),
                changePercent, error, failures);
    }

    /**
     * Update the site with failure information.
     * @param error Error message
     * @param consecutiveFailures Number of consecutive failures
     */
    private void updateSiteWithFailure(@Nullable String error, int consecutiveFailures) {
        if (siteUpdater == null) {
            Logger.w(TAG, "SiteUpdater not set, cannot update site");
            return;
        }

        siteUpdater.updateSiteAfterCheck(site.getId(), System.currentTimeMillis(),
                site.getLastChangePercent(), error, consecutiveFailures);
    }

    /**
     * Trigger a change notification.
     * @param changePercent The change percentage
     */
    private void triggerNotification(float changePercent) {
        if (notificationTrigger == null) {
            Logger.w(TAG, "NotificationTrigger not set, cannot trigger notification");
            return;
        }

        Logger.i(TAG, "Triggering notification for site " + site.getId() +
                ", change: " + changePercent + "%");
        notificationTrigger.notifyChange(context, site, changePercent);
    }

    /**
     * Save the check result to the database.
     * @param success Whether the check was successful
     * @param changePercent The change percentage
     * @param errorMessage Error message or null
     * @param responseTimeMs Response time in milliseconds
     */
    private void saveCheckResult(boolean success, float changePercent,
            @Nullable String errorMessage, long responseTimeMs) {
        if (siteUpdater == null) {
            Logger.w(TAG, "SiteUpdater not set, cannot save check result");
            return;
        }

        CheckResult result = new CheckResult();
        result.setSiteId(site.getId());
        result.setCheckTime(System.currentTimeMillis());
        result.setSuccess(success);
        result.setChangePercent(changePercent);
        result.setErrorMessage(errorMessage);
        result.setResponseTimeMs(responseTimeMs);

        siteUpdater.saveCheckResult(result);
    }

    /**
     * Custom exception for network unavailability.
     */
    public static class NetworkUnavailableException extends Exception {
        public NetworkUnavailableException(String message) {
            super(message);
        }
    }
}
