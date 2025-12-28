package com.ltrudu.sitewatcher.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.List;

/**
 * Data Access Object for WatchedSite entities.
 * Provides methods for CRUD operations and queries on watched sites.
 */
@Dao
public interface WatchedSiteDao {

    /**
     * Insert a new watched site.
     *
     * @param site The site to insert
     * @return The row ID of the newly inserted site
     */
    @Insert
    long insert(WatchedSite site);

    /**
     * Update an existing watched site.
     *
     * @param site The site to update
     */
    @Update
    void update(WatchedSite site);

    /**
     * Delete a watched site.
     *
     * @param site The site to delete
     */
    @Delete
    void delete(WatchedSite site);

    /**
     * Get all watched sites as LiveData for UI observation.
     * Results are ordered by creation time (newest first).
     *
     * @return LiveData containing list of all watched sites
     */
    @Query("SELECT * FROM watched_sites ORDER BY created_at DESC")
    LiveData<List<WatchedSite>> getAll();

    /**
     * Get all watched sites synchronously.
     * For background operations like export.
     *
     * @return List of all watched sites
     */
    @Query("SELECT * FROM watched_sites ORDER BY created_at DESC")
    List<WatchedSite> getAllSync();

    /**
     * Get a specific watched site by its ID.
     *
     * @param id The site ID
     * @return The watched site, or null if not found
     */
    @Query("SELECT * FROM watched_sites WHERE id = :id")
    WatchedSite getById(long id);

    /**
     * Get a watched site by URL.
     *
     * @param url The URL to search for
     * @return The watched site with that URL, or null if not found
     */
    @Query("SELECT * FROM watched_sites WHERE url = :url LIMIT 1")
    WatchedSite getByUrl(String url);

    /**
     * Get all enabled watched sites for background checking.
     * Returns a regular List (not LiveData) for background work.
     *
     * @return List of enabled watched sites
     */
    @Query("SELECT * FROM watched_sites WHERE is_enabled = 1")
    List<WatchedSite> getEnabled();

    /**
     * Update the last check information for a site.
     *
     * @param id            The site ID
     * @param time          The check timestamp
     * @param changePercent The percentage of content change detected
     * @param error         Error message if check failed, null otherwise
     */
    @Query("UPDATE watched_sites SET last_check_time = :time, last_change_percent = :changePercent, last_error = :error, updated_at = :time WHERE id = :id")
    void updateLastCheck(long id, long time, float changePercent, String error);

    /**
     * Update consecutive failure count for a site.
     *
     * @param id       The site ID
     * @param failures The new failure count
     */
    @Query("UPDATE watched_sites SET consecutive_failures = :failures, updated_at = :updatedAt WHERE id = :id")
    void updateConsecutiveFailures(long id, int failures, long updatedAt);

    /**
     * Reset consecutive failures to zero (called after successful check).
     *
     * @param id The site ID
     */
    @Query("UPDATE watched_sites SET consecutive_failures = 0, updated_at = :updatedAt WHERE id = :id")
    void resetConsecutiveFailures(long id, long updatedAt);

    /**
     * Toggle the enabled state of a site.
     *
     * @param id      The site ID
     * @param enabled The new enabled state
     */
    @Query("UPDATE watched_sites SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    void setEnabled(long id, boolean enabled, long updatedAt);

    /**
     * Get count of all watched sites.
     *
     * @return Total number of watched sites
     */
    @Query("SELECT COUNT(*) FROM watched_sites")
    int getCount();

    /**
     * Get count of enabled watched sites.
     *
     * @return Number of enabled watched sites
     */
    @Query("SELECT COUNT(*) FROM watched_sites WHERE is_enabled = 1")
    int getEnabledCount();

    /**
     * Delete a watched site by ID.
     *
     * @param id The site ID to delete
     */
    @Query("DELETE FROM watched_sites WHERE id = :id")
    void deleteById(long id);
}
