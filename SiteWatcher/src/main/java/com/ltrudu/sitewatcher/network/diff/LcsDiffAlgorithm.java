package com.ltrudu.sitewatcher.network.diff;

import androidx.annotation.NonNull;

/**
 * LCS (Longest Common Subsequence) based diff algorithm.
 *
 * <h2>Algorithm Overview</h2>
 *
 * <p>The LCS algorithm finds the longest subsequence common to both input sequences.
 * A subsequence is a sequence that appears in the same relative order, but not
 * necessarily contiguous. Once the LCS is found, the diff is computed by identifying
 * elements not in the LCS as additions or deletions.</p>
 *
 * <h3>How It Works:</h3>
 *
 * <ol>
 *   <li><b>Build LCS Matrix</b> - Create a 2D matrix where lcs[i][j] represents the
 *       length of the LCS of the first i elements of old and first j elements of new.</li>
 *   <li><b>Fill Matrix</b> - For each pair (i,j):
 *       <ul>
 *         <li>If old[i] == new[j]: lcs[i][j] = lcs[i-1][j-1] + 1</li>
 *         <li>Else: lcs[i][j] = max(lcs[i-1][j], lcs[i][j-1])</li>
 *       </ul>
 *   </li>
 *   <li><b>Backtrack</b> - Starting from lcs[m][n], trace back to find the actual LCS
 *       and identify additions (in new but not LCS) and deletions (in old but not LCS).</li>
 * </ol>
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * Old: "The quick brown fox"
 * New: "The slow brown dog"
 *
 * LCS: "The  brown "
 * Removed: "quick", "fox"
 * Added: "slow", "dog"
 * </pre>
 *
 * <h3>Complexity:</h3>
 *
 * <ul>
 *   <li><b>Time:</b> O(n * m) where n and m are the number of lines</li>
 *   <li><b>Space:</b> O(n * m) for the LCS matrix</li>
 * </ul>
 *
 * <h3>Advantages:</h3>
 *
 * <ul>
 *   <li>Handles insertions and deletions correctly (unlike character-at-position)</li>
 *   <li>Produces minimal diff (shortest edit script)</li>
 *   <li>Industry standard - used by Git, diff, and most comparison tools</li>
 * </ul>
 *
 * <h3>Limitations:</h3>
 *
 * <ul>
 *   <li>O(n*m) memory can be problematic for very large files</li>
 *   <li>Doesn't detect moved blocks (treats as delete + add)</li>
 * </ul>
 *
 * @see DiffAlgorithm
 */
public class LcsDiffAlgorithm implements DiffAlgorithm {

    private static final int MAX_LINES = 5000;
    private static final int MAX_SAMPLED_LINES = MAX_LINES / 3 * 3;
    private static final long MAX_MATRIX_CELLS = 1_000_000L;

    @NonNull
    @Override
    public DiffResult computeDiff(@NonNull String oldContent, @NonNull String newContent) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // For very large files, use sampling to avoid memory issues
        if (oldLines.length > MAX_LINES || newLines.length > MAX_LINES ||
                !isWithinMatrixBudget(oldLines.length, newLines.length)) {
            return computeSampledDiff(oldLines, newLines);
        }

        return computeFullDiff(oldLines, newLines);
    }

    /**
     * Compute full LCS-based diff for normal-sized content.
     */
    private DiffResult computeFullDiff(String[] oldLines, String[] newLines) {
        // Compute LCS matrix
        int[][] lcs = computeLcsMatrix(oldLines, newLines);

        // Backtrack to count added/removed/unchanged lines
        int i = oldLines.length;
        int j = newLines.length;
        int addedCount = 0;
        int removedCount = 0;
        int unchangedCount = 0;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                unchangedCount++;
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                addedCount++;
                j--;
            } else if (i > 0) {
                removedCount++;
                i--;
            }
        }

        return new DiffResult(addedCount, removedCount, unchangedCount,
                oldLines.length, newLines.length);
    }

    /**
     * Compute sampled diff for very large files.
     */
    private DiffResult computeSampledDiff(String[] oldLines, String[] newLines) {
        int[] sampleCounts = getSampleCounts(oldLines.length, newLines.length);
        String[] oldSample = sampleLines(oldLines, sampleCounts[0]);
        String[] newSample = sampleLines(newLines, sampleCounts[1]);

        DiffResult sampleResult = computeFullDiff(oldSample, newSample);

        // Scale results to full file size
        float scaleFactor = Math.max(oldLines.length, newLines.length) /
                (float) Math.max(oldSample.length, newSample.length);

        return new DiffResult(
                Math.round(sampleResult.getAddedLines() * scaleFactor),
                Math.round(sampleResult.getRemovedLines() * scaleFactor),
                Math.round(sampleResult.getUnchangedLines() * scaleFactor),
                oldLines.length,
                newLines.length
        );
    }

    /**
     * Compute the LCS (Longest Common Subsequence) matrix.
     *
     * <p>The matrix lcs[i][j] contains the length of the LCS of
     * oldLines[0..i-1] and newLines[0..j-1].</p>
     */
    private int[][] computeLcsMatrix(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;
        if (!isWithinMatrixBudget(m, n)) {
            throw new IllegalArgumentException("LCS matrix exceeds the cell budget");
        }
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        return lcs;
    }

    /**
     * Sample lines from beginning, middle, and end.
     */
    private int[] getSampleCounts(int oldCount, int newCount) {
        int oldSampleLimit = Math.min(oldCount, MAX_SAMPLED_LINES);
        int newSampleLimit = Math.min(newCount, MAX_SAMPLED_LINES);
        if (isWithinMatrixBudget(oldSampleLimit, newSampleLimit)) {
            return new int[] { oldSampleLimit, newSampleLimit };
        }

        double scaleFactor = Math.sqrt(MAX_MATRIX_CELLS /
                (oldSampleLimit + 1.0) / (newSampleLimit + 1.0));
        int oldSampleCount = scaleSampleCount(oldSampleLimit, scaleFactor);
        int newSampleCount = scaleSampleCount(newSampleLimit, scaleFactor);
        while (!isWithinMatrixBudget(oldSampleCount, newSampleCount)) {
            if (oldSampleCount >= newSampleCount && oldSampleCount > 0) {
                oldSampleCount = Math.max(0, oldSampleCount - (oldSampleCount > 3 ? 3 : 1));
            } else if (newSampleCount > 0) {
                newSampleCount = Math.max(0, newSampleCount - (newSampleCount > 3 ? 3 : 1));
            }
        }
        return new int[] { oldSampleCount, newSampleCount };
    }

    private int scaleSampleCount(int sampleLimit, double scaleFactor) {
        if (sampleLimit <= 3) {
            return sampleLimit;
        }
        return Math.max(3, (int) (Math.floor(sampleLimit * scaleFactor / 3.0) * 3));
    }

    private boolean isWithinMatrixBudget(int oldCount, int newCount) {
        long rows = (long) oldCount + 1;
        long columns = (long) newCount + 1;
        return rows <= MAX_MATRIX_CELLS / columns;
    }

    private String[] sampleLines(String[] lines, int maxSampleCount) {
        if (lines.length <= maxSampleCount) {
            return lines;
        }

        if (maxSampleCount < 3) {
            String[] sample = new String[maxSampleCount];
            if (maxSampleCount == 1) {
                sample[0] = lines[lines.length / 2];
            } else if (maxSampleCount == 2) {
                sample[0] = lines[0];
                sample[1] = lines[lines.length - 1];
            }
            return sample;
        }

        int sampleSize = maxSampleCount / 3;
        String[] sample = new String[sampleSize * 3];
        int middle = lines.length / 2;

        System.arraycopy(lines, 0, sample, 0, sampleSize);
        System.arraycopy(lines, middle - sampleSize / 2, sample, sampleSize, sampleSize);
        System.arraycopy(lines, lines.length - sampleSize, sample, sampleSize * 2, sampleSize);

        return sample;
    }

    @NonNull
    @Override
    public String getName() {
        return "LCS";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Longest Common Subsequence - Standard diff algorithm used by Git. " +
                "Accurately detects additions and deletions. Best for general use.";
    }
}
