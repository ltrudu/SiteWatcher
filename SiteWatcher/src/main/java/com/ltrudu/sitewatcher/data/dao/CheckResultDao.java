package com.ltrudu.sitewatcher.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.ltrudu.sitewatcher.data.model.CheckResult;

import java.util.List;

/**
 * Data Access Object for CheckResult entities.
 * Manages check operation logs and history.
 */
@Dao
public interface CheckResultDao {

    /**
     * Insert a new check result.
     *
     * @param result The check result to insert
     * @return The row ID of the newly inserted result
     */
    @Insert
    long insert(CheckResult result);

    /**
     * Get all check results for a site, ordered by check time (newest first).
     *
     * @param siteId The site ID
     * @return List of check results for the site
     */
    @Query("SELECT * FROM check_results WHERE site_id = :siteId ORDER BY check_time DESC")
    List<CheckResult> getBySiteId(long siteId);

    /**
     * Get check results for a site as LiveData for UI observation.
     *
     * @param siteId The site ID
     * @return LiveData containing list of check results
     */
    @Query("SELECT * FROM check_results WHERE site_id = :siteId ORDER BY check_time DESC")
    LiveData<List<CheckResult>> getBySiteIdLive(long siteId);

    /**
     * Delete check results older than the specified timestamp.
     * Used for cleanup of old log entries.
     *
     * @param timestamp The cutoff timestamp (entries older than this are deleted)
     */
    @Query("DELETE FROM check_results WHERE check_time < :timestamp")
    void deleteOlderThan(long timestamp);

    /**
     * Delete all check results for a specific site.
     *
     * @param siteId The site ID
     */
    @Query("DELETE FROM check_results WHERE site_id = :siteId")
    void deleteBySiteId(long siteId);

    /**
     * Get the most recent check result for a site.
     *
     * @param siteId The site ID
     * @return The latest check result, or null if none exists
     */
    @Query("SELECT * FROM check_results WHERE site_id = :siteId ORDER BY check_time DESC LIMIT 1")
    CheckResult getLatestBySiteId(long siteId);

    /**
     * Get check results count for a site.
     *
     * @param siteId The site ID
     * @return Number of check results for the site
     */
    @Query("SELECT COUNT(*) FROM check_results WHERE site_id = :siteId")
    int getCountBySiteId(long siteId);

    /**
     * Get recent check results with a limit.
     *
     * @param siteId The site ID
     * @param limit  Maximum number of results to return
     * @return List of most recent check results
     */
    @Query("SELECT * FROM check_results WHERE site_id = :siteId ORDER BY check_time DESC LIMIT :limit")
    List<CheckResult> getRecentBySiteId(long siteId, int limit);

    /**
     * Get successful check results count for a site.
     *
     * @param siteId The site ID
     * @return Number of successful checks
     */
    @Query("SELECT COUNT(*) FROM check_results WHERE site_id = :siteId AND success = 1")
    int getSuccessCountBySiteId(long siteId);

    /**
     * Get failed check results count for a site.
     *
     * @param siteId The site ID
     * @return Number of failed checks
     */
    @Query("SELECT COUNT(*) FROM check_results WHERE site_id = :siteId AND success = 0")
    int getFailureCountBySiteId(long siteId);

    /**
     * Get total count of all check results.
     *
     * @return Total number of check results
     */
    @Query("SELECT COUNT(*) FROM check_results")
    int getTotalCount();
}
