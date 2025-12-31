package com.ltrudu.sitewatcher.data.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.ltrudu.sitewatcher.data.database.SiteWatcherDatabase;
import com.ltrudu.sitewatcher.data.dao.CheckResultDao;
import com.ltrudu.sitewatcher.data.dao.SiteHistoryDao;
import com.ltrudu.sitewatcher.data.dao.WatchedSiteDao;
import com.ltrudu.sitewatcher.data.model.CheckResult;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing site data and database operations.
 * Singleton that provides a clean API for data access.
 * All database operations are executed on a background thread.
 */
public class SiteRepository {

    private static final String TAG = "SiteRepository";
    private static volatile SiteRepository instance;

    private final WatchedSiteDao watchedSiteDao;
    private final SiteHistoryDao siteHistoryDao;
    private final CheckResultDao checkResultDao;
    private final ExecutorService executorService;

    /**
     * Callback interface for async database operations.
     */
    public interface OnOperationCompleteListener {
        /**
         * Called when the operation completes successfully.
         */
        void onSuccess();

        /**
         * Called when the operation fails.
         * @param exception The exception that caused the failure
         */
        void onError(@NonNull Exception exception);
    }

    /**
     * Callback interface for async database operations that return a value.
     * @param <T> The type of value returned
     */
    public interface OnResultListener<T> {
        /**
         * Called when the operation completes successfully.
         * @param result The result of the operation
         */
        void onSuccess(@Nullable T result);

        /**
         * Called when the operation fails.
         * @param exception The exception that caused the failure
         */
        void onError(@NonNull Exception exception);
    }

    /**
     * Callback interface for insert operations that return the inserted row ID.
     */
    public interface OnInsertCompleteListener {
        /**
         * Called when the insert completes successfully.
         * @param insertedId The ID of the inserted row
         */
        void onSuccess(long insertedId);

        /**
         * Called when the operation fails.
         * @param exception The exception that caused the failure
         */
        void onError(@NonNull Exception exception);
    }

    /**
     * Private constructor to enforce singleton pattern.
     * @param application The application context
     */
    private SiteRepository(@NonNull Application application) {
        SiteWatcherDatabase database = SiteWatcherDatabase.getInstance(application);
        this.watchedSiteDao = database.watchedSiteDao();
        this.siteHistoryDao = database.siteHistoryDao();
        this.checkResultDao = database.checkResultDao();
        this.executorService = Executors.newSingleThreadExecutor();
        Logger.d(TAG, "SiteRepository initialized");
    }

    /**
     * Get the singleton instance of SiteRepository.
     * @param application The application context
     * @return The SiteRepository instance
     */
    public static SiteRepository getInstance(@NonNull Application application) {
        if (instance == null) {
            synchronized (SiteRepository.class) {
                if (instance == null) {
                    instance = new SiteRepository(application);
                }
            }
        }
        return instance;
    }

    // ========================================
    // WatchedSite Operations
    // ========================================

    /**
     * Get all watched sites as LiveData.
     * @return LiveData list of all watched sites
     */
    @NonNull
    public LiveData<List<WatchedSite>> getAllSites() {
        return watchedSiteDao.getAll();
    }

    /**
     * Get a site by its ID asynchronously.
     * @param id The site ID
     * @param listener Callback for the result
     */
    public void getSiteById(long id, @NonNull OnResultListener<WatchedSite> listener) {
        executorService.execute(() -> {
            try {
                WatchedSite site = watchedSiteDao.getById(id);
                listener.onSuccess(site);
            } catch (Exception e) {
                Logger.e(TAG, "Error getting site by ID: " + id, e);
                listener.onError(e);
            }
        });
    }

    /**
     * Get a site by its ID synchronously.
     * Intended for use in background workers like AlarmReceiver.
     * @param id The site ID
     * @return The WatchedSite or null if not found
     */
    @Nullable
    public WatchedSite getSiteByIdSync(long id) {
        return watchedSiteDao.getById(id);
    }

    /**
     * Insert a new site.
     * @param site The site to insert
     * @param listener Callback for operation completion
     */
    public void insertSite(@NonNull WatchedSite site, @Nullable OnInsertCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long insertedId = watchedSiteDao.insert(site);
                Logger.d(TAG, "Site inserted with ID: " + insertedId);
                if (listener != null) {
                    listener.onSuccess(insertedId);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error inserting site", e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Update an existing site.
     * @param site The site to update
     * @param listener Callback for operation completion
     */
    public void updateSite(@NonNull WatchedSite site, @Nullable OnOperationCompleteListener listener) {
        executorService.execute(() -> {
            try {
                site.setUpdatedAt(System.currentTimeMillis());
                watchedSiteDao.update(site);
                Logger.d(TAG, "Site updated: " + site.getId());
                if (listener != null) {
                    listener.onSuccess();
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error updating site: " + site.getId(), e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Delete a site and its associated history.
     * @param site The site to delete
     * @param listener Callback for operation completion
     */
    public void deleteSite(@NonNull WatchedSite site, @Nullable OnOperationCompleteListener listener) {
        executorService.execute(() -> {
            try {
                watchedSiteDao.delete(site);
                Logger.d(TAG, "Site deleted: " + site.getId());
                if (listener != null) {
                    listener.onSuccess();
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error deleting site: " + site.getId(), e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Duplicate a site with a new ID.
     * @param site The site to duplicate
     * @param listener Callback for operation completion with new ID
     */
    public void duplicateSite(@NonNull WatchedSite site, @Nullable OnInsertCompleteListener listener) {
        executorService.execute(() -> {
            try {
                WatchedSite duplicate = new WatchedSite();
                duplicate.setUrl(site.getUrl());
                duplicate.setName(site.getName() != null ? site.getName() + " (Copy)" : null);
                duplicate.setScheduleType(site.getScheduleType());
                duplicate.setScheduleHour(site.getScheduleHour());
                duplicate.setScheduleMinute(site.getScheduleMinute());
                duplicate.setPeriodicIntervalMinutes(site.getPeriodicIntervalMinutes());
                duplicate.setEnabledDays(site.getEnabledDays());
                duplicate.setComparisonMode(site.getComparisonMode());
                duplicate.setCssSelector(site.getCssSelector());
                duplicate.setThresholdPercent(site.getThresholdPercent());
                duplicate.setEnabled(site.isEnabled());
                // v2 fields
                duplicate.setMinTextLength(site.getMinTextLength());
                duplicate.setMinWordLength(site.getMinWordLength());
                duplicate.setFetchMode(site.getFetchMode());
                duplicate.setAutoClickActionsJson(site.getAutoClickActionsJson());
                duplicate.setCreatedAt(System.currentTimeMillis());
                duplicate.setUpdatedAt(System.currentTimeMillis());

                long insertedId = watchedSiteDao.insert(duplicate);
                Logger.d(TAG, "Site duplicated. Original: " + site.getId() + ", New: " + insertedId);
                if (listener != null) {
                    listener.onSuccess(insertedId);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error duplicating site: " + site.getId(), e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Get all sites synchronously.
     * Intended for use in background operations like export.
     * @return List of all sites
     */
    @NonNull
    public List<WatchedSite> getAllSitesSync() {
        return watchedSiteDao.getAllSync();
    }

    /**
     * Get all sites asynchronously with callback.
     * @param listener Callback for the result
     */
    public void getAllSitesAsync(@NonNull OnResultListener<List<WatchedSite>> listener) {
        executorService.execute(() -> {
            try {
                List<WatchedSite> sites = watchedSiteDao.getAllSync();
                listener.onSuccess(sites);
            } catch (Exception e) {
                Logger.e(TAG, "Error getting all sites", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Check if a site with the given URL exists.
     * @param url The URL to check
     * @return true if a site with that URL exists
     */
    public boolean siteExistsWithUrl(@NonNull String url) {
        WatchedSite site = watchedSiteDao.getByUrl(url);
        return site != null;
    }

    /**
     * Get all enabled sites for background checking.
     * This is a synchronous method intended for use in background workers.
     * @return List of enabled sites
     */
    @NonNull
    public List<WatchedSite> getEnabledSites() {
        return watchedSiteDao.getEnabled();
    }

    /**
     * Get enabled sites asynchronously.
     * @param listener Callback for the result
     */
    public void getEnabledSitesAsync(@NonNull OnResultListener<List<WatchedSite>> listener) {
        executorService.execute(() -> {
            try {
                List<WatchedSite> sites = watchedSiteDao.getEnabled();
                listener.onSuccess(sites);
            } catch (Exception e) {
                Logger.e(TAG, "Error getting enabled sites", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Update the last check information for a site.
     * @param id The site ID
     * @param checkTime The time of the last check
     * @param changePercent The percentage of content change detected
     * @param error Error message if check failed, null otherwise
     */
    public void updateLastCheck(long id, long checkTime, float changePercent, @Nullable String error) {
        executorService.execute(() -> {
            try {
                // Update the last check information
                watchedSiteDao.updateLastCheck(id, checkTime, changePercent, error);

                // Update consecutive failures
                if (error != null) {
                    // Increment failure count
                    WatchedSite site = watchedSiteDao.getById(id);
                    if (site != null) {
                        int newFailures = site.getConsecutiveFailures() + 1;
                        watchedSiteDao.updateConsecutiveFailures(id, newFailures, checkTime);
                    }
                } else {
                    // Reset failures on success
                    watchedSiteDao.resetConsecutiveFailures(id, checkTime);
                }
                Logger.d(TAG, "Updated last check for site: " + id);
            } catch (Exception e) {
                Logger.e(TAG, "Error updating last check for site: " + id, e);
            }
        });
    }

    // ========================================
    // SiteHistory Operations
    // ========================================

    /**
     * Get history for a site asynchronously.
     * @param siteId The site ID
     * @param listener Callback for the result
     */
    public void getSiteHistory(long siteId, @NonNull OnResultListener<List<SiteHistory>> listener) {
        executorService.execute(() -> {
            try {
                List<SiteHistory> history = siteHistoryDao.getBySiteId(siteId);
                listener.onSuccess(history);
            } catch (Exception e) {
                Logger.e(TAG, "Error getting history for site: " + siteId, e);
                listener.onError(e);
            }
        });
    }

    /**
     * Get history for a site synchronously.
     * Intended for use in background workers.
     * @param siteId The site ID
     * @return List of site history entries
     */
    @NonNull
    public List<SiteHistory> getSiteHistorySync(long siteId) {
        return siteHistoryDao.getBySiteId(siteId);
    }

    /**
     * Get the latest history entry for a site synchronously.
     * Intended for use in background workers.
     * @param siteId The site ID
     * @return The latest history entry, or null if none exists
     */
    @Nullable
    public SiteHistory getLatestHistoryForSite(long siteId) {
        return siteHistoryDao.getLatestBySiteId(siteId);
    }

    /**
     * Insert a history entry.
     * @param history The history to insert
     */
    public void insertHistory(@NonNull SiteHistory history) {
        executorService.execute(() -> {
            try {
                siteHistoryDao.insert(history);
                Logger.d(TAG, "History inserted for site: " + history.getSiteId());
            } catch (Exception e) {
                Logger.e(TAG, "Error inserting history for site: " + history.getSiteId(), e);
            }
        });
    }

    /**
     * Insert a history entry with callback.
     * @param history The history to insert
     * @param listener Callback for operation completion
     */
    public void insertHistory(@NonNull SiteHistory history, @Nullable OnInsertCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long insertedId = siteHistoryDao.insert(history);
                Logger.d(TAG, "History inserted with ID: " + insertedId);
                if (listener != null) {
                    listener.onSuccess(insertedId);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error inserting history", e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Prune old history entries, keeping only the most recent ones.
     * @param siteId The site ID
     * @param keepCount Number of recent entries to keep
     */
    public void pruneOldHistory(long siteId, int keepCount) {
        executorService.execute(() -> {
            try {
                siteHistoryDao.deleteOlderThan(siteId, keepCount);
                Logger.d(TAG, "Pruned old history for site: " + siteId + ", keeping: " + keepCount);
            } catch (Exception e) {
                Logger.e(TAG, "Error pruning history for site: " + siteId, e);
            }
        });
    }

    /**
     * Prune old history entries with callback.
     * @param siteId The site ID
     * @param keepCount Number of recent entries to keep
     * @param listener Callback for operation completion
     */
    public void pruneOldHistory(long siteId, int keepCount, @Nullable OnOperationCompleteListener listener) {
        executorService.execute(() -> {
            try {
                siteHistoryDao.deleteOlderThan(siteId, keepCount);
                Logger.d(TAG, "Pruned old history for site: " + siteId);
                if (listener != null) {
                    listener.onSuccess();
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error pruning history for site: " + siteId, e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    // ========================================
    // CheckResult Operations
    // ========================================

    /**
     * Insert a check result.
     * @param result The check result to insert
     */
    public void insertCheckResult(@NonNull CheckResult result) {
        executorService.execute(() -> {
            try {
                checkResultDao.insert(result);
                Logger.d(TAG, "Check result inserted for site: " + result.getSiteId());
            } catch (Exception e) {
                Logger.e(TAG, "Error inserting check result for site: " + result.getSiteId(), e);
            }
        });
    }

    /**
     * Insert a check result with callback.
     * @param result The check result to insert
     * @param listener Callback for operation completion
     */
    public void insertCheckResult(@NonNull CheckResult result, @Nullable OnInsertCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long insertedId = checkResultDao.insert(result);
                Logger.d(TAG, "Check result inserted with ID: " + insertedId);
                if (listener != null) {
                    listener.onSuccess(insertedId);
                }
            } catch (Exception e) {
                Logger.e(TAG, "Error inserting check result", e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Get check results for a site.
     * @param siteId The site ID
     * @param listener Callback for the result
     */
    public void getCheckResultsForSite(long siteId, @NonNull OnResultListener<List<CheckResult>> listener) {
        executorService.execute(() -> {
            try {
                List<CheckResult> results = checkResultDao.getBySiteId(siteId);
                listener.onSuccess(results);
            } catch (Exception e) {
                Logger.e(TAG, "Error getting check results for site: " + siteId, e);
                listener.onError(e);
            }
        });
    }

    /**
     * Get recent check results for a site as LiveData.
     * @param siteId The site ID
     * @param limit Maximum number of results to return
     * @return LiveData list of recent check results
     */
    @NonNull
    public LiveData<List<CheckResult>> getRecentCheckResults(long siteId, int limit) {
        return checkResultDao.getBySiteIdLive(siteId);
    }

    /**
     * Delete old check results, keeping only the most recent ones.
     * @param siteId The site ID
     * @param keepCount Number of recent results to keep
     */
    public void pruneOldCheckResults(long siteId, int keepCount) {
        executorService.execute(() -> {
            try {
                // Get recent results to find the cutoff timestamp
                List<CheckResult> recent = checkResultDao.getRecentBySiteId(siteId, keepCount);
                if (recent != null && recent.size() >= keepCount) {
                    // Get the timestamp of the oldest result we want to keep
                    CheckResult oldest = recent.get(recent.size() - 1);
                    checkResultDao.deleteOlderThan(oldest.getCheckTime());
                }
                Logger.d(TAG, "Pruned old check results for site: " + siteId);
            } catch (Exception e) {
                Logger.e(TAG, "Error pruning check results for site: " + siteId, e);
            }
        });
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Shutdown the executor service.
     * Call this when the repository is no longer needed.
     */
    public void shutdown() {
        executorService.shutdown();
        Logger.d(TAG, "SiteRepository executor shutdown");
    }
}
