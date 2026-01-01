package com.ltrudu.sitewatcher.network.diff;

import androidx.annotation.NonNull;

/**
 * Interface for diff algorithms that compare two strings and calculate change metrics.
 *
 * <h2>Diff Algorithms Overview</h2>
 *
 * <p>Diff algorithms are used to compare two versions of content and determine what changed.
 * Different algorithms have different trade-offs between accuracy, speed, and memory usage.</p>
 *
 * <h3>Common Diff Algorithms:</h3>
 *
 * <ul>
 *   <li><b>LCS (Longest Common Subsequence)</b> - The standard algorithm used by Git and most
 *       diff tools. Finds the longest sequence of elements common to both inputs, then
 *       identifies additions and deletions. O(n*m) time and space complexity.</li>
 *
 *   <li><b>Myers' Diff Algorithm</b> - An optimization of LCS that finds the shortest edit
 *       script. Used by Git internally. O((n+m)*d) where d is the number of differences.</li>
 *
 *   <li><b>Patience Diff</b> - A variant that first matches unique lines, then recursively
 *       diffs the gaps. Better for code with moved blocks. Used by Git with --patience flag.</li>
 *
 *   <li><b>Histogram Diff</b> - Similar to Patience but handles repeated lines better.
 *       Git's default algorithm since version 2.0.</li>
 *
 *   <li><b>Character-based</b> - Compares character by character. Fast but doesn't handle
 *       insertions/deletions well (everything shifts).</li>
 *
 *   <li><b>Word-based</b> - Tokenizes into words and compares. Good for prose/text content.</li>
 * </ul>
 *
 * <h3>Choosing an Algorithm:</h3>
 *
 * <table border="1">
 *   <tr><th>Algorithm</th><th>Best For</th><th>Trade-offs</th></tr>
 *   <tr><td>LCS</td><td>General purpose, code</td><td>Balanced accuracy/speed</td></tr>
 *   <tr><td>Myers</td><td>Minimal diffs</td><td>Faster for similar content</td></tr>
 *   <tr><td>Patience</td><td>Code with refactoring</td><td>Better semantic diffs</td></tr>
 *   <tr><td>Character</td><td>Very short strings</td><td>Fails on insertions</td></tr>
 * </table>
 *
 * @see LcsDiffAlgorithm
 * @see DiffAlgorithmFactory
 */
public interface DiffAlgorithm {

    /**
     * Result of a diff computation containing change statistics.
     */
    class DiffResult {
        private final int addedLines;
        private final int removedLines;
        private final int unchangedLines;
        private final int totalOldLines;
        private final int totalNewLines;
        private final float changePercent;
        private final String description;

        public DiffResult(int addedLines, int removedLines, int unchangedLines,
                          int totalOldLines, int totalNewLines) {
            this.addedLines = addedLines;
            this.removedLines = removedLines;
            this.unchangedLines = unchangedLines;
            this.totalOldLines = totalOldLines;
            this.totalNewLines = totalNewLines;

            // Calculate change percentage based on changed lines vs total lines
            int changedLines = addedLines + removedLines;
            int maxLines = Math.max(totalOldLines, totalNewLines);
            this.changePercent = maxLines > 0 ? (changedLines / (float) maxLines) * 100f : 0f;
            this.description = buildDescription();
        }

        private String buildDescription() {
            StringBuilder sb = new StringBuilder();

            if (addedLines > 0 && removedLines > 0) {
                sb.append(String.format("%d lines added, %d lines removed", addedLines, removedLines));
            } else if (addedLines > 0) {
                sb.append(String.format("%d lines added", addedLines));
            } else if (removedLines > 0) {
                sb.append(String.format("%d lines removed", removedLines));
            } else {
                sb.append("Content modified");
            }

            sb.append(String.format(" (%.1f%% change)", changePercent));
            return sb.toString();
        }

        public int getAddedLines() {
            return addedLines;
        }

        public int getRemovedLines() {
            return removedLines;
        }

        public int getUnchangedLines() {
            return unchangedLines;
        }

        public int getTotalOldLines() {
            return totalOldLines;
        }

        public int getTotalNewLines() {
            return totalNewLines;
        }

        public float getChangePercent() {
            return changePercent;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Compute the diff between old and new content.
     *
     * @param oldContent The previous content
     * @param newContent The current content
     * @return DiffResult containing change statistics
     */
    @NonNull
    DiffResult computeDiff(@NonNull String oldContent, @NonNull String newContent);

    /**
     * Get the name of this algorithm for logging/debugging.
     *
     * @return Algorithm name
     */
    @NonNull
    String getName();

    /**
     * Get a description of this algorithm's characteristics.
     *
     * @return Algorithm description
     */
    @NonNull
    String getDescription();
}
