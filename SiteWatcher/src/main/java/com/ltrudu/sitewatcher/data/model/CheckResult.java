package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents the result of a single site check operation.
 * Used for logging and displaying check history.
 */
@Entity(
    tableName = "check_results",
    foreignKeys = @ForeignKey(
        entity = WatchedSite.class,
        parentColumns = "id",
        childColumns = "site_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "site_id")
)
public class CheckResult {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "site_id")
    private long siteId;

    /**
     * Timestamp when the check was performed.
     */
    @ColumnInfo(name = "check_time")
    private long checkTime;

    /**
     * Whether the check completed successfully.
     */
    @ColumnInfo(name = "success")
    private boolean success;

    /**
     * Percentage of content that changed compared to previous check.
     */
    @ColumnInfo(name = "change_percent")
    private float changePercent;

    /**
     * Error message if the check failed.
     */
    @Nullable
    @ColumnInfo(name = "error_message")
    private String errorMessage;

    /**
     * Time taken to fetch and process the response in milliseconds.
     */
    @ColumnInfo(name = "response_time_ms")
    private long responseTimeMs;

    /**
     * Default constructor for Room.
     */
    public CheckResult() {
        this.checkTime = System.currentTimeMillis();
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSiteId() {
        return siteId;
    }

    public void setSiteId(long siteId) {
        this.siteId = siteId;
    }

    public long getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(long checkTime) {
        this.checkTime = checkTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public float getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(float changePercent) {
        this.changePercent = changePercent;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }
}
