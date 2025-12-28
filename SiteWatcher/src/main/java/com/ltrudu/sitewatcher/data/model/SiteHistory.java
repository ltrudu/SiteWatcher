package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Represents a historical snapshot of a watched site's content.
 * Used to compare against new fetches to detect changes.
 */
@Entity(
    tableName = "site_history",
    foreignKeys = @ForeignKey(
        entity = WatchedSite.class,
        parentColumns = "id",
        childColumns = "site_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "site_id")
)
public class SiteHistory {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "site_id")
    private long siteId;

    /**
     * Hash of the content for quick comparison.
     */
    @Nullable
    @ColumnInfo(name = "content_hash")
    private String contentHash;

    /**
     * Size of the content in bytes.
     */
    @ColumnInfo(name = "content_size")
    private long contentSize;

    /**
     * Timestamp when this content was fetched.
     */
    @ColumnInfo(name = "check_time")
    private long checkTime;

    /**
     * Whether the stored content is compressed.
     */
    @ColumnInfo(name = "is_compressed")
    private boolean isCompressed;

    /**
     * Path to the stored content file.
     */
    @Nullable
    @ColumnInfo(name = "storage_path")
    private String storagePath;

    /**
     * Default constructor for Room.
     */
    public SiteHistory() {
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

    @Nullable
    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(@Nullable String contentHash) {
        this.contentHash = contentHash;
    }

    public long getContentSize() {
        return contentSize;
    }

    public void setContentSize(long contentSize) {
        this.contentSize = contentSize;
    }

    public long getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(long checkTime) {
        this.checkTime = checkTime;
    }

    public boolean isCompressed() {
        return isCompressed;
    }

    public void setCompressed(boolean compressed) {
        isCompressed = compressed;
    }

    @Nullable
    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(@Nullable String storagePath) {
        this.storagePath = storagePath;
    }
}
