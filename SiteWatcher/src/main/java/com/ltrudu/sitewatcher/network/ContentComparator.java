package com.ltrudu.sitewatcher.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.util.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Compares website content to detect changes.
 * Supports multiple comparison modes: full HTML, text only, and CSS selector.
 */
public class ContentComparator {

    private static final String TAG = "ContentComparator";

    /**
     * Compare two content strings based on the specified mode.
     *
     * @param oldContent  The previous content
     * @param newContent  The current content
     * @param mode        The comparison mode to use
     * @param cssSelector CSS selector for CSS_SELECTOR mode (can be null for other modes)
     * @return ComparisonResult containing change metrics
     */
    @NonNull
    public ComparisonResult compareContent(@Nullable String oldContent,
                                           @Nullable String newContent,
                                           @NonNull ComparisonMode mode,
                                           @Nullable String cssSelector) {
        // Handle null cases
        if (oldContent == null && newContent == null) {
            return ComparisonResult.noChange(0);
        }

        if (oldContent == null) {
            return ComparisonResult.changed(100f, 0, newContent.length(),
                    "Initial content captured");
        }

        if (newContent == null) {
            return ComparisonResult.changed(100f, oldContent.length(), 0,
                    "Content no longer available");
        }

        // Fast path: if contents are identical, no need to compare
        if (oldContent.equals(newContent)) {
            return ComparisonResult.noChange(oldContent.length());
        }

        switch (mode) {
            case TEXT_ONLY:
                return compareTextOnly(oldContent, newContent);
            case CSS_SELECTOR:
                return compareCssSelector(oldContent, newContent, cssSelector);
            case FULL_HTML:
            default:
                return compareFullHtml(oldContent, newContent);
        }
    }

    /**
     * Compare full HTML content.
     *
     * @param oldContent Previous HTML
     * @param newContent Current HTML
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareFullHtml(@NonNull String oldContent,
                                             @NonNull String newContent) {
        int oldSize = oldContent.length();
        int newSize = newContent.length();

        float changePercent = calculateSizeBasedChangePercent(oldSize, newSize);

        // If sizes are similar, do a more sophisticated character-level comparison
        if (changePercent < 20) {
            float charDiffPercent = calculateCharacterDifference(oldContent, newContent);
            // Use the higher of the two metrics
            changePercent = Math.max(changePercent, charDiffPercent);
        }

        if (changePercent < 0.01f) {
            return ComparisonResult.noChange(oldSize);
        }

        String description = buildChangeDescription(oldSize, newSize, changePercent);
        return ComparisonResult.changed(changePercent, oldSize, newSize, description);
    }

    /**
     * Compare only the text content, stripping HTML tags.
     *
     * @param oldContent Previous HTML
     * @param newContent Current HTML
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareTextOnly(@NonNull String oldContent,
                                             @NonNull String newContent) {
        try {
            String oldText = extractText(oldContent);
            String newText = extractText(newContent);

            if (oldText.equals(newText)) {
                return ComparisonResult.noChange(oldText.length());
            }

            int oldSize = oldText.length();
            int newSize = newText.length();

            float changePercent = calculateSizeBasedChangePercent(oldSize, newSize);

            // For similar-sized text, do character comparison
            if (changePercent < 20) {
                float charDiffPercent = calculateCharacterDifference(oldText, newText);
                changePercent = Math.max(changePercent, charDiffPercent);
            }

            String description = buildChangeDescription(oldSize, newSize, changePercent);
            return ComparisonResult.changed(changePercent, oldSize, newSize, description);

        } catch (Exception e) {
            Logger.e(TAG, "Error comparing text content", e);
            // Fall back to full HTML comparison
            return compareFullHtml(oldContent, newContent);
        }
    }

    /**
     * Compare content matched by a CSS selector.
     *
     * @param oldContent  Previous HTML
     * @param newContent  Current HTML
     * @param cssSelector The CSS selector to use
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareCssSelector(@NonNull String oldContent,
                                                @NonNull String newContent,
                                                @Nullable String cssSelector) {
        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            Logger.w(TAG, "CSS selector is empty, falling back to text comparison");
            return compareTextOnly(oldContent, newContent);
        }

        try {
            String oldSelected = extractBySelector(oldContent, cssSelector);
            String newSelected = extractBySelector(newContent, cssSelector);

            if (oldSelected == null && newSelected == null) {
                return ComparisonResult.noChange(0);
            }

            if (oldSelected == null) {
                return ComparisonResult.changed(100f, 0,
                        newSelected != null ? newSelected.length() : 0,
                        "Element appeared: " + cssSelector);
            }

            if (newSelected == null) {
                return ComparisonResult.changed(100f, oldSelected.length(), 0,
                        "Element disappeared: " + cssSelector);
            }

            if (oldSelected.equals(newSelected)) {
                return ComparisonResult.noChange(oldSelected.length());
            }

            int oldSize = oldSelected.length();
            int newSize = newSelected.length();

            float changePercent = calculateSizeBasedChangePercent(oldSize, newSize);

            // For similar sizes, compare characters
            if (changePercent < 20) {
                float charDiffPercent = calculateCharacterDifference(oldSelected, newSelected);
                changePercent = Math.max(changePercent, charDiffPercent);
            }

            String description = "Element changed (" + cssSelector + "): " +
                    buildChangeDescription(oldSize, newSize, changePercent);
            return ComparisonResult.changed(changePercent, oldSize, newSize, description);

        } catch (Exception e) {
            Logger.e(TAG, "Error comparing CSS selector content: " + cssSelector, e);
            return ComparisonResult.changed(0f, 0, 0,
                    "Error comparing selector: " + e.getMessage());
        }
    }

    /**
     * Extract text content from HTML.
     *
     * @param html The HTML content
     * @return Extracted text
     */
    @NonNull
    private String extractText(@NonNull String html) {
        Document doc = Jsoup.parse(html);
        // Remove script and style elements
        doc.select("script, style, noscript").remove();
        return doc.text();
    }

    /**
     * Extract content matching a CSS selector.
     *
     * @param html        The HTML content
     * @param cssSelector The CSS selector
     * @return Selected content, or null if not found
     */
    @Nullable
    private String extractBySelector(@NonNull String html, @NonNull String cssSelector) {
        Document doc = Jsoup.parse(html);
        Elements elements = doc.select(cssSelector);

        if (elements.isEmpty()) {
            return null;
        }

        // If multiple elements match, combine their content
        StringBuilder sb = new StringBuilder();
        for (Element element : elements) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(element.outerHtml());
        }

        return sb.toString();
    }

    /**
     * Calculate change percentage based on size difference.
     * Formula: |newSize - oldSize| / max(oldSize, newSize) * 100
     *
     * @param oldSize Old content size
     * @param newSize New content size
     * @return Change percentage (0-100)
     */
    private float calculateSizeBasedChangePercent(int oldSize, int newSize) {
        if (oldSize == 0 && newSize == 0) {
            return 0f;
        }

        int maxSize = Math.max(oldSize, newSize);
        int sizeDiff = Math.abs(newSize - oldSize);

        return (sizeDiff / (float) maxSize) * 100f;
    }

    /**
     * Calculate a more sophisticated character-level difference percentage.
     * Uses Levenshtein-like approach but optimized for large strings.
     *
     * @param oldContent Old content
     * @param newContent New content
     * @return Difference percentage (0-100)
     */
    private float calculateCharacterDifference(@NonNull String oldContent,
                                               @NonNull String newContent) {
        // For very large strings, sample instead of full comparison
        int maxSampleSize = 10000;

        String oldSample = oldContent.length() > maxSampleSize ?
                sampleString(oldContent, maxSampleSize) : oldContent;
        String newSample = newContent.length() > maxSampleSize ?
                sampleString(newContent, maxSampleSize) : newContent;

        // Count differing characters using a simple approach
        int maxLen = Math.max(oldSample.length(), newSample.length());
        int minLen = Math.min(oldSample.length(), newSample.length());

        if (maxLen == 0) {
            return 0f;
        }

        int diffCount = 0;

        // Compare overlapping portion
        for (int i = 0; i < minLen; i++) {
            if (oldSample.charAt(i) != newSample.charAt(i)) {
                diffCount++;
            }
        }

        // Add non-overlapping portion as differences
        diffCount += (maxLen - minLen);

        return (diffCount / (float) maxLen) * 100f;
    }

    /**
     * Sample a string by taking characters from beginning, middle, and end.
     *
     * @param input      The string to sample
     * @param sampleSize Target sample size
     * @return Sampled string
     */
    @NonNull
    private String sampleString(@NonNull String input, int sampleSize) {
        if (input.length() <= sampleSize) {
            return input;
        }

        int thirdSize = sampleSize / 3;
        int start = thirdSize;
        int middle = input.length() / 2;
        int end = input.length() - thirdSize;

        return input.substring(0, start) +
                input.substring(middle - thirdSize / 2, middle + thirdSize / 2) +
                input.substring(end);
    }

    /**
     * Build a human-readable change description.
     *
     * @param oldSize       Old content size
     * @param newSize       New content size
     * @param changePercent Change percentage
     * @return Description string
     */
    @NonNull
    private String buildChangeDescription(int oldSize, int newSize, float changePercent) {
        int diff = newSize - oldSize;

        if (diff > 0) {
            return String.format("Content increased by %d chars (%.1f%% change)",
                    diff, changePercent);
        } else if (diff < 0) {
            return String.format("Content decreased by %d chars (%.1f%% change)",
                    Math.abs(diff), changePercent);
        } else {
            return String.format("Content modified (%.1f%% change)", changePercent);
        }
    }
}
