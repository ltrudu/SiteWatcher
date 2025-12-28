package com.ltrudu.sitewatcher.network;

import androidx.annotation.NonNull;

/**
 * Represents the result of comparing two content strings.
 * Contains metrics about the differences between old and new content.
 */
public class ComparisonResult {

    /**
     * The percentage of change between old and new content.
     * Range: 0.0 (identical) to 100.0 (completely different).
     */
    private final float changePercent;

    /**
     * The size of the old content in characters.
     */
    private final int oldSize;

    /**
     * The size of the new content in characters.
     */
    private final int newSize;

    /**
     * Whether the content has changed based on the comparison.
     */
    private final boolean hasChanged;

    /**
     * A brief description of the change.
     */
    @NonNull
    private final String changeDescription;

    /**
     * Create a ComparisonResult.
     *
     * @param changePercent     Percentage of change (0-100)
     * @param oldSize           Size of old content
     * @param newSize           Size of new content
     * @param hasChanged        Whether content has changed
     * @param changeDescription Description of the change
     */
    public ComparisonResult(float changePercent, int oldSize, int newSize,
                            boolean hasChanged, @NonNull String changeDescription) {
        this.changePercent = Math.max(0, Math.min(100, changePercent)); // Clamp to 0-100
        this.oldSize = oldSize;
        this.newSize = newSize;
        this.hasChanged = hasChanged;
        this.changeDescription = changeDescription;
    }

    /**
     * Create a result indicating no change.
     *
     * @param size The size of both contents
     * @return ComparisonResult indicating no change
     */
    @NonNull
    public static ComparisonResult noChange(int size) {
        return new ComparisonResult(0f, size, size, false, "No changes detected");
    }

    /**
     * Create a result indicating the content has changed.
     *
     * @param changePercent     Percentage of change
     * @param oldSize           Old content size
     * @param newSize           New content size
     * @param changeDescription Description of the change
     * @return ComparisonResult indicating change
     */
    @NonNull
    public static ComparisonResult changed(float changePercent, int oldSize, int newSize,
                                           @NonNull String changeDescription) {
        return new ComparisonResult(changePercent, oldSize, newSize, true, changeDescription);
    }

    /**
     * Get the change percentage.
     *
     * @return Change percentage (0-100)
     */
    public float getChangePercent() {
        return changePercent;
    }

    /**
     * Get the old content size.
     *
     * @return Old size in characters
     */
    public int getOldSize() {
        return oldSize;
    }

    /**
     * Get the new content size.
     *
     * @return New size in characters
     */
    public int getNewSize() {
        return newSize;
    }

    /**
     * Check if content has changed.
     *
     * @return true if content changed
     */
    public boolean hasChanged() {
        return hasChanged;
    }

    /**
     * Get a description of the change.
     *
     * @return Human-readable change description
     */
    @NonNull
    public String getChangeDescription() {
        return changeDescription;
    }

    /**
     * Get the size difference between old and new content.
     *
     * @return Size difference (positive if new is larger, negative if smaller)
     */
    public int getSizeDifference() {
        return newSize - oldSize;
    }

    /**
     * Get the absolute size difference.
     *
     * @return Absolute difference in size
     */
    public int getAbsoluteSizeDifference() {
        return Math.abs(newSize - oldSize);
    }

    /**
     * Check if this is a significant change (above threshold).
     *
     * @param thresholdPercent Minimum percentage to consider significant
     * @return true if change exceeds threshold
     */
    public boolean isSignificantChange(float thresholdPercent) {
        return hasChanged && changePercent >= thresholdPercent;
    }

    @NonNull
    @Override
    public String toString() {
        return "ComparisonResult{" +
                "hasChanged=" + hasChanged +
                ", changePercent=" + String.format("%.2f", changePercent) + "%" +
                ", oldSize=" + oldSize +
                ", newSize=" + newSize +
                ", description='" + changeDescription + "'" +
                "}";
    }
}
