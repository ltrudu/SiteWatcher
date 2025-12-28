package com.ltrudu.sitewatcher.ui.sitelist;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.ltrudu.sitewatcher.background.CheckScheduler;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.network.SiteChecker;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;

/**
 * ViewModel for the SiteListFragment.
 * Manages site data and provides methods for site operations.
 */
public class SiteListViewModel extends AndroidViewModel {

    private static final String TAG = "SiteListViewModel";

    private final SiteRepository repository;
    private final LiveData<List<WatchedSite>> allSites;
    private final CheckScheduler checkScheduler;

    /**
     * Constructor for SiteListViewModel.
     * @param application The application context
     */
    public SiteListViewModel(@NonNull Application application) {
        super(application);
        repository = SiteRepository.getInstance(application);
        allSites = repository.getAllSites();
        checkScheduler = CheckScheduler.getInstance(application);
        Logger.d(TAG, "SiteListViewModel initialized");
    }

    /**
     * Get all watched sites as LiveData for UI observation.
     * @return LiveData containing list of all watched sites
     */
    @NonNull
    public LiveData<List<WatchedSite>> getAllSites() {
        return allSites;
    }

    /**
     * Delete a site from the database.
     * Also cancels any scheduled checks for this site.
     * @param site The site to delete
     */
    public void deleteSite(@NonNull WatchedSite site) {
        Logger.d(TAG, "Deleting site: " + site.getId());

        // Cancel scheduled checks
        checkScheduler.cancelCheck(site.getId());

        // Delete from database
        repository.deleteSite(site, new SiteRepository.OnOperationCompleteListener() {
            @Override
            public void onSuccess() {
                Logger.d(TAG, "Site deleted successfully: " + site.getId());
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Logger.e(TAG, "Failed to delete site: " + site.getId(), exception);
            }
        });
    }

    /**
     * Duplicate a site with a new ID.
     * The duplicate will have the same settings but reset check status.
     * @param site The site to duplicate
     */
    public void duplicateSite(@NonNull WatchedSite site) {
        Logger.d(TAG, "Duplicating site: " + site.getId());

        repository.duplicateSite(site, new SiteRepository.OnInsertCompleteListener() {
            @Override
            public void onSuccess(long insertedId) {
                Logger.d(TAG, "Site duplicated successfully. New ID: " + insertedId);

                // Schedule check for the new site
                repository.getSiteById(insertedId, new SiteRepository.OnResultListener<WatchedSite>() {
                    @Override
                    public void onSuccess(WatchedSite newSite) {
                        if (newSite != null && newSite.isEnabled()) {
                            checkScheduler.scheduleCheck(newSite);
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Logger.e(TAG, "Failed to get duplicated site for scheduling", exception);
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Logger.e(TAG, "Failed to duplicate site: " + site.getId(), exception);
            }
        });
    }

    /**
     * Trigger an immediate check for a site.
     * @param site The site to check
     */
    public void checkSiteNow(@NonNull WatchedSite site) {
        Logger.d(TAG, "Checking site now: " + site.getId());

        SiteChecker siteChecker = SiteChecker.getInstance(getApplication());
        siteChecker.checkSite(site, new SiteChecker.CheckCallback() {
            @Override
            public void onCheckComplete(long siteId, float changePercent, boolean hasChanged) {
                Logger.d(TAG, "Check complete for site " + siteId +
                        ": " + changePercent + "% change, hasChanged=" + hasChanged);
            }

            @Override
            public void onCheckError(long siteId, @NonNull String error) {
                Logger.e(TAG, "Check error for site " + siteId + ": " + error);
            }
        });
    }
}
