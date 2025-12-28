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
     * @param oldContent    The previous content
     * @param newContent    The current content
     * @param mode          The comparison mode to use
     * @param cssSelector   CSS selector for CSS_SELECTOR mode (can be null for other modes)
     * @param minTextLength Minimum text block length for TEXT_ONLY mode (3-50, filters small elements)
     * @param minWordLength Minimum word length for TEXT_ONLY mode (words shorter than this are filtered out)
     * @return ComparisonResult containing change metrics
     */
    @NonNull
    public ComparisonResult compareContent(@Nullable String oldContent,
                                           @Nullable String newContent,
                                           @NonNull ComparisonMode mode,
                                           @Nullable String cssSelector,
                                           int minTextLength,
                                           int minWordLength) {
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
                return compareTextOnly(oldContent, newContent, minTextLength, minWordLength);
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
     * Filters out text blocks shorter than minTextLength to reduce false positives
     * from dynamic content like timestamps, counters, etc.
     *
     * @param oldContent    Previous HTML
     * @param newContent    Current HTML
     * @param minTextLength Minimum text block length (elements with shorter text are ignored)
     * @param minWordLength Minimum word length (words shorter than this are filtered out)
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareTextOnly(@NonNull String oldContent,
                                             @NonNull String newContent,
                                             int minTextLength,
                                             int minWordLength) {
        try {
            String oldText = extractTextFiltered(oldContent, minTextLength, minWordLength);
            String newText = extractTextFiltered(newContent, minTextLength, minWordLength);

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
            return compareTextOnly(oldContent, newContent, 3, 1); // Use minimal filtering for fallback
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
     * Extract text content from HTML, filtering out text blocks shorter than minLength.
     * This helps reduce false positives from dynamic content like timestamps, counters, etc.
     *
     * @param html          The HTML content
     * @param minLength     Minimum text length per element (text shorter than this is ignored)
     * @param minWordLength Minimum word length (words shorter than this are filtered out)
     * @return Extracted and filtered text
     */
    @NonNull
    private String extractTextFiltered(@NonNull String html, int minLength, int minWordLength) {
        Document doc = Jsoup.parse(html);
        // Remove script and style elements
        doc.select("script, style, noscript").remove();

        // If minimal filtering, just return all text
        if (minLength <= 3 && minWordLength <= 1) {
            return doc.text();
        }

        StringBuilder result = new StringBuilder();

        // Get all text nodes by traversing leaf elements
        // We look at elements that directly contain text (not just child elements)
        for (Element element : doc.getAllElements()) {
            // Skip elements that are just containers
            if (element.children().isEmpty() || element.ownText().length() > 0) {
                String ownText = element.ownText().trim();
                // Only include text blocks that meet the minimum length
                if (ownText.length() >= minLength) {
                    // Filter words by minimum word length
                    String filteredText = filterShortWords(ownText, minWordLength);
                    if (!filteredText.isEmpty()) {
                        if (result.length() > 0) {
                            result.append(" ");
                        }
                        result.append(filteredText);
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Filter out words shorter than the minimum length.
     *
     * @param text The text to filter
     * @param minWordLength Minimum word length (words shorter than this are removed)
     * @return Text with short words removed
     */
    @NonNull
    private String filterShortWords(@NonNull String text, int minWordLength) {
        if (minWordLength <= 1) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        String[] words = text.split("\\s+");

        for (String word : words) {
            // Remove punctuation for length check but keep original word
            String cleanWord = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (cleanWord.length() >= minWordLength) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(word);
            }
        }

        return result.toString();
    }

    /**
     * Extract text content from HTML (no filtering).
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
