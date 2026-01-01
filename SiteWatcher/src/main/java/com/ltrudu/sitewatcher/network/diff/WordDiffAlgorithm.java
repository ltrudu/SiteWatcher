package com.ltrudu.sitewatcher.network.diff;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-based diff algorithm using LCS on words instead of lines.
 *
 * <h2>Algorithm Overview</h2>
 *
 * <p>This algorithm tokenizes content into words and applies LCS at the word level.
 * It's particularly useful for prose, articles, and text content where changes
 * often occur within lines rather than as whole line additions/deletions.</p>
 *
 * <h3>When to Use:</h3>
 *
 * <ul>
 *   <li>News articles and blog posts</li>
 *   <li>Product descriptions</li>
 *   <li>Content where sentences are edited rather than replaced</li>
 *   <li>When you need fine-grained change detection</li>
 * </ul>
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * Old: "The product costs $99"
 * New: "The product costs $79"
 *
 * Line-based: 100% change (whole line different)
 * Word-based: 20% change (1 of 5 words changed)
 * </pre>
 *
 * <h3>Complexity:</h3>
 *
 * <ul>
 *   <li><b>Time:</b> O(n * m) where n and m are word counts</li>
 *   <li><b>Space:</b> O(n * m) for the LCS matrix</li>
 * </ul>
 *
 * @see DiffAlgorithm
 * @see LcsDiffAlgorithm
 */
public class WordDiffAlgorithm implements DiffAlgorithm {

    private static final int MAX_WORDS = 10000;

    @NonNull
    @Override
    public DiffResult computeDiff(@NonNull String oldContent, @NonNull String newContent) {
        // Tokenize into words
        String[] oldWords = tokenize(oldContent);
        String[] newWords = tokenize(newContent);

        // For very large content, sample
        if (oldWords.length > MAX_WORDS || newWords.length > MAX_WORDS) {
            return computeSampledDiff(oldWords, newWords);
        }

        return computeFullDiff(oldWords, newWords);
    }

    /**
     * Tokenize content into words.
     */
    private String[] tokenize(String content) {
        // Split on whitespace and filter empty strings
        String[] parts = content.split("\\s+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words.toArray(new String[0]);
    }

    private DiffResult computeFullDiff(String[] oldWords, String[] newWords) {
        int[][] lcs = computeLcsMatrix(oldWords, newWords);

        int i = oldWords.length;
        int j = newWords.length;
        int addedCount = 0;
        int removedCount = 0;
        int unchangedCount = 0;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldWords[i - 1].equals(newWords[j - 1])) {
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
                oldWords.length, newWords.length);
    }

    private DiffResult computeSampledDiff(String[] oldWords, String[] newWords) {
        int sampleSize = MAX_WORDS / 3;

        String[] oldSample = sampleArray(oldWords, sampleSize);
        String[] newSample = sampleArray(newWords, sampleSize);

        DiffResult sampleResult = computeFullDiff(oldSample, newSample);

        float scaleFactor = Math.max(oldWords.length, newWords.length) /
                (float) Math.max(oldSample.length, newSample.length);

        return new DiffResult(
                Math.round(sampleResult.getAddedLines() * scaleFactor),
                Math.round(sampleResult.getRemovedLines() * scaleFactor),
                Math.round(sampleResult.getUnchangedLines() * scaleFactor),
                oldWords.length,
                newWords.length
        );
    }

    private int[][] computeLcsMatrix(String[] oldWords, String[] newWords) {
        int m = oldWords.length;
        int n = newWords.length;
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldWords[i - 1].equals(newWords[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        return lcs;
    }

    private String[] sampleArray(String[] arr, int sampleSize) {
        if (arr.length <= sampleSize * 3) {
            return arr;
        }

        String[] sample = new String[sampleSize * 3];
        int middle = arr.length / 2;

        System.arraycopy(arr, 0, sample, 0, sampleSize);
        System.arraycopy(arr, middle - sampleSize / 2, sample, sampleSize, sampleSize);
        System.arraycopy(arr, arr.length - sampleSize, sample, sampleSize * 2, sampleSize);

        return sample;
    }

    @NonNull
    @Override
    public String getName() {
        return "Word";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Word-based diff using LCS on words. More sensitive to small text changes. " +
                "Best for articles, descriptions, and prose content.";
    }
}
