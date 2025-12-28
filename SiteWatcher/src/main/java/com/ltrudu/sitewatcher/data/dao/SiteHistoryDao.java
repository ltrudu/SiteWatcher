package com.ltrudu.sitewatcher.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.ltrudu.sitewatcher.data.model.SiteHistory;

import java.util.List;

/**
 * Data Access Object for SiteHistory entities.
 * Manages historical content snapshots for change comparison.
 */
@Dao
public interface SiteHistoryDao {

    /**
     * Insert a new history entry.
     *
     * @param history The history entry to insert
     * @return The row ID of the newly inserted entry
     */
    @Insert
    long insert(SiteHistory history);

    /**
     * Delete a history entry.
     *
     * @param history The history entry to delete
     */
    @Delete
    void delete(SiteHistory history);

    /**
     * Get all history entries for a site, ordered by check time (newest first).
     *
     * @param siteId The site ID
     * @return List of history entries for the site
     */
    @Query("SELECT * FROM site_history WHERE site_id = :siteId ORDER BY check_time DESC")
    List<SiteHistory> getBySiteId(long siteId);

    /**
     * Get the most recent history entry for a site.
     *
     * @param siteId The site ID
     * @return The latest history entry, or null if none exists
     */
    @Query("SELECT * FROM site_history WHERE site_id = :siteId ORDER BY check_time DESC LIMIT 1")
    SiteHistory getLatestBySiteId(long siteId);

    /**
     * Delete older history entries, keeping only the most recent ones.
     * This helps manage storage by removing old snapshots.
     *
     * @param siteId    The site ID
     * @param keepCount Number of most recent entries to keep
     */
    @Query("DELETE FROM site_history WHERE site_id = :siteId AND id NOT IN (SELECT id FROM site_history WHERE site_id = :siteId ORDER BY check_time DESC LIMIT :keepCount)")
    void deleteOlderThan(long siteId, int keepCount);

    /**
     * Get history count for a site.
     *
     * @param siteId The site ID
     * @return Number of history entries for the site
     */
    @Query("SELECT COUNT(*) FROM site_history WHERE site_id = :siteId")
    int getCountBySiteId(long siteId);

    /**
     * Delete all history entries for a site.
     *
     * @param siteId The site ID
     */
    @Query("DELETE FROM site_history WHERE site_id = :siteId")
    void deleteBySiteId(long siteId);

    /**
     * Get two most recent history entries for comparison.
     *
     * @param siteId The site ID
     * @return List containing up to 2 most recent history entries
     */
    @Query("SELECT * FROM site_history WHERE site_id = :siteId ORDER BY check_time DESC LIMIT 2")
    List<SiteHistory> getLatestTwoBySiteId(long siteId);

    /**
     * Get all unique site IDs that have history entries.
     *
     * @return List of site IDs
     */
    @Query("SELECT DISTINCT site_id FROM site_history")
    List<Long> getAllSiteIdsWithHistory();

    /**
     * Get all history entries across all sites, ordered by check time (newest first).
     *
     * @return List of all history entries
     */
    @Query("SELECT * FROM site_history ORDER BY check_time DESC")
    List<SiteHistory> getAll();
}
