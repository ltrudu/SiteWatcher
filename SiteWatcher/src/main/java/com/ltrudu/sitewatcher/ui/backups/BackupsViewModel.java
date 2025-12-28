package com.ltrudu.sitewatcher.ui.backups;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ltrudu.sitewatcher.data.database.SiteWatcherDatabase;
import com.ltrudu.sitewatcher.data.dao.SiteHistoryDao;
import com.ltrudu.sitewatcher.data.dao.WatchedSiteDao;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the BackupsManagerFragment.
 * Handles loading and managing backup entries from all sites.
 */
public class BackupsViewModel extends AndroidViewModel {

    private static final String TAG = "BackupsViewModel";

    /**
     * Represents the current state of the backups manager.
     */
    public enum BackupsState {
        LOADING,
        CONTENT_READY,
        EMPTY,
        ERROR
    }

    /**
     * Data class representing a single backup item with site information.
     */
    public static class BackupItem {
        private final long historyId;
        private final long siteId;
        private final String siteUrl;
        private final String siteName;
        private final long checkTime;
        private final long contentSize;
        private final boolean isCompressed;
        private final String storagePath;

        public BackupItem(long historyId, long siteId, String siteUrl, String siteName,
                          long checkTime, long contentSize, boolean isCompressed, String storagePath) {
            this.historyId = historyId;
            this.siteId = siteId;
            this.siteUrl = siteUrl;
            this.siteName = siteName;
            this.checkTime = checkTime;
            this.contentSize = contentSize;
            this.isCompressed = isCompressed;
            this.storagePath = storagePath;
        }

        public long getHistoryId() {
            return historyId;
        }

        public long getSiteId() {
            return siteId;
        }

        public String getSiteUrl() {
            return siteUrl;
        }

        public String getSiteName() {
            return siteName;
        }

        public long getCheckTime() {
            return checkTime;
        }

        public long getContentSize() {
            return contentSize;
        }

        public boolean isCompressed() {
            return isCompressed;
        }

        public String getStoragePath() {
            return storagePath;
        }

        /**
         * Get display name for the site.
         * Uses site name if available, otherwise the URL.
         */
        @NonNull
        public String getDisplayName() {
            if (siteName != null && !siteName.isEmpty()) {
                return siteName;
            }
            return siteUrl;
        }
    }

    private final SiteHistoryDao siteHistoryDao;
    private final WatchedSiteDao watchedSiteDao;
    private final ExecutorService executorService;
    private final Context applicationContext;

    private final MutableLiveData<BackupsState> state = new MutableLiveData<>(BackupsState.LOADING);
    private final MutableLiveData<List<BackupItem>> backups = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleteSuccess = new MutableLiveData<>();

    public BackupsViewModel(@NonNull Application application) {
        super(application);
        this.applicationContext = application.getApplicationContext();
        SiteWatcherDatabase database = SiteWatcherDatabase.getInstance(application);
        this.siteHistoryDao = database.siteHistoryDao();
        this.watchedSiteDao = database.watchedSiteDao();
        this.executorService = Executors.newSingleThreadExecutor();

        // Load backups on initialization
        loadBackups();
    }

    /**
     * Get the current state of the backups manager.
     */
    @NonNull
    public LiveData<BackupsState> getState() {
        return state;
    }

    /**
     * Get the list of backup items.
     */
    @NonNull
    public LiveData<List<BackupItem>> getBackups() {
        return backups;
    }

    /**
     * Get the error message.
     */
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get delete operation success status.
     */
    @NonNull
    public LiveData<Boolean> getDeleteSuccess() {
        return deleteSuccess;
    }

    /**
     * Reload backup data.
     */
    public void refresh() {
        loadBackups();
    }

    /**
     * Load all backups from all sites.
     */
    private void loadBackups() {
        state.postValue(BackupsState.LOADING);

        executorService.execute(() -> {
            try {
                // Get all sites first to build a map of site info
                List<WatchedSite> allSites = watchedSiteDao.getEnabled();
                // Also get disabled sites
                List<WatchedSite> allSitesSync = getAllSitesSync();

                Map<Long, WatchedSite> siteMap = new HashMap<>();
                for (WatchedSite site : allSitesSync) {
                    siteMap.put(site.getId(), site);
                }

                // Get all history entries from all sites
                List<BackupItem> allBackups = new ArrayList<>();

                for (WatchedSite site : allSitesSync) {
                    List<SiteHistory> historyList = siteHistoryDao.getBySiteId(site.getId());

                    for (SiteHistory history : historyList) {
                        BackupItem item = new BackupItem(
                                history.getId(),
                                history.getSiteId(),
                                site.getUrl(),
                                site.getName(),
                                history.getCheckTime(),
                                history.getContentSize(),
                                history.isCompressed(),
                                history.getStoragePath()
                        );
                        allBackups.add(item);
                    }
                }

                // Sort by check time (newest first)
                allBackups.sort((a, b) -> Long.compare(b.getCheckTime(), a.getCheckTime()));

                // Post results
                backups.postValue(allBackups);

                if (allBackups.isEmpty()) {
                    state.postValue(BackupsState.EMPTY);
                } else {
                    state.postValue(BackupsState.CONTENT_READY);
                }

            } catch (Exception e) {
                Logger.e(TAG, "Error loading backups", e);
                errorMessage.postValue(e.getMessage());
                state.postValue(BackupsState.ERROR);
            }
        });
    }

    /**
     * Get all sites that have history entries synchronously.
     */
    @NonNull
    private List<WatchedSite> getAllSitesSync() {
        List<WatchedSite> result = new ArrayList<>();
        List<Long> siteIds = siteHistoryDao.getAllSiteIdsWithHistory();

        for (Long siteId : siteIds) {
            WatchedSite site = watchedSiteDao.getById(siteId);
            if (site != null) {
                result.add(site);
            }
        }

        return result;
    }

    /**
     * Delete a backup entry.
     *
     * @param backup The backup to delete
     */
    public void deleteBackup(@NonNull BackupItem backup) {
        executorService.execute(() -> {
            try {
                // Create a SiteHistory object for deletion
                SiteHistory history = new SiteHistory();
                history.setId(backup.getHistoryId());
                history.setSiteId(backup.getSiteId());
                history.setCheckTime(backup.getCheckTime());
                history.setContentSize(backup.getContentSize());
                history.setCompressed(backup.isCompressed());
                history.setStoragePath(backup.getStoragePath());

                // Delete the storage file if it exists
                String storagePath = backup.getStoragePath();
                if (storagePath != null && !storagePath.isEmpty()) {
                    File file = new File(storagePath);
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        Logger.d(TAG, "Storage file deleted: " + deleted + " for " + storagePath);
                    }
                }

                // Delete from database
                siteHistoryDao.delete(history);

                Logger.d(TAG, "Backup deleted: " + backup.getHistoryId());
                deleteSuccess.postValue(true);

                // Reload the list
                loadBackups();

            } catch (Exception e) {
                Logger.e(TAG, "Error deleting backup: " + backup.getHistoryId(), e);
                errorMessage.postValue(e.getMessage());
                deleteSuccess.postValue(false);
            }
        });
    }

    /**
     * Format file size for display.
     *
     * @param bytes Size in bytes
     * @return Formatted size string
     */
    @NonNull
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
