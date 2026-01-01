package com.ltrudu.sitewatcher.network.diff;

import androidx.annotation.NonNull;

/**
 * Character-based diff algorithm using LCS on characters.
 *
 * <h2>Algorithm Overview</h2>
 *
 * <p>This algorithm applies LCS at the character level, providing the most
 * granular change detection. It's useful for detecting very small changes
 * like typo fixes or single character modifications.</p>
 *
 * <h3>When to Use:</h3>
 *
 * <ul>
 *   <li>Detecting typo fixes</li>
 *   <li>Monitoring content that changes by single characters (prices, numbers)</li>
 *   <li>When you need maximum sensitivity to changes</li>
 * </ul>
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * Old: "Price: $99.99"
 * New: "Price: $89.99"
 *
 * Line-based: 100% change
 * Word-based: ~20% change
 * Character-based: ~7% change (1 of 14 chars)
 * </pre>
 *
 * <h3>Limitations:</h3>
 *
 * <ul>
 *   <li>Very memory intensive for large content</li>
 *   <li>May report low percentages for significant content changes</li>
 *   <li>Samples content over 10000 characters</li>
 * </ul>
 *
 * @see DiffAlgorithm
 * @see LcsDiffAlgorithm
 */
public class CharacterDiffAlgorithm implements DiffAlgorithm {

    private static final int MAX_CHARS = 10000;

    @NonNull
    @Override
    public DiffResult computeDiff(@NonNull String oldContent, @NonNull String newContent) {
        // For very large content, sample
        String oldSample = oldContent.length() > MAX_CHARS ?
                sampleString(oldContent) : oldContent;
        String newSample = newContent.length() > MAX_CHARS ?
                sampleString(newContent) : newContent;

        char[] oldChars = oldSample.toCharArray();
        char[] newChars = newSample.toCharArray();

        // Use optimized space LCS (only need previous row)
        int[] prev = new int[newChars.length + 1];
        int[] curr = new int[newChars.length + 1];

        for (int i = 1; i <= oldChars.length; i++) {
            for (int j = 1; j <= newChars.length; j++) {
                if (oldChars[i - 1] == newChars[j - 1]) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            // Swap rows
            int[] temp = prev;
            prev = curr;
            curr = temp;
            java.util.Arrays.fill(curr, 0);
        }

        int lcsLength = prev[newChars.length];
        int unchangedCount = lcsLength;
        int removedCount = oldChars.length - lcsLength;
        int addedCount = newChars.length - lcsLength;

        // Scale back if we sampled
        if (oldContent.length() > MAX_CHARS || newContent.length() > MAX_CHARS) {
            float scaleFactor = Math.max(oldContent.length(), newContent.length()) /
                    (float) Math.max(oldSample.length(), newSample.length());
            addedCount = Math.round(addedCount * scaleFactor);
            removedCount = Math.round(removedCount * scaleFactor);
            unchangedCount = Math.round(unchangedCount * scaleFactor);
        }

        return new DiffResult(addedCount, removedCount, unchangedCount,
                oldContent.length(), newContent.length());
    }

    /**
     * Sample string from beginning, middle, and end.
     */
    private String sampleString(String input) {
        int sampleSize = MAX_CHARS / 3;
        int middle = input.length() / 2;

        return input.substring(0, sampleSize) +
                input.substring(middle - sampleSize / 2, middle + sampleSize / 2) +
                input.substring(input.length() - sampleSize);
    }

    @NonNull
    @Override
    public String getName() {
        return "Character";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Character-based diff using LCS on individual characters. " +
                "Most sensitive to tiny changes. Best for prices, numbers, or typos.";
    }
}
