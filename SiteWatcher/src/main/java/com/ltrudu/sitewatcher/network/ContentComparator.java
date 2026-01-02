package com.ltrudu.sitewatcher.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.DiffAlgorithmType;
import com.ltrudu.sitewatcher.network.diff.DiffAlgorithm;
import com.ltrudu.sitewatcher.network.diff.DiffAlgorithmFactory;
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
     * @param oldContent         The previous content
     * @param newContent         The current content
     * @param mode               The comparison mode to use
     * @param cssSelector        CSS selector for CSS_SELECTOR mode (can be null for other modes)
     * @param minTextLength      Minimum text block length for TEXT_ONLY mode (3-50, filters small elements)
     * @param minWordLength      Minimum word length for TEXT_ONLY mode (words shorter than this are filtered out)
     * @param diffAlgorithm      The diff algorithm to use for change detection
     * @return ComparisonResult containing change metrics
     */
    @NonNull
    public ComparisonResult compareContent(@Nullable String oldContent,
                                           @Nullable String newContent,
                                           @NonNull ComparisonMode mode,
                                           @Nullable String cssSelector,
                                           int minTextLength,
                                           int minWordLength,
                                           @NonNull DiffAlgorithmType diffAlgorithm) {
        return compareContent(oldContent, newContent, mode, cssSelector, null, null,
                minTextLength, minWordLength, diffAlgorithm);
    }

    /**
     * Compare two content strings based on the specified mode with include/exclude filtering.
     *
     * @param oldContent         The previous content
     * @param newContent         The current content
     * @param mode               The comparison mode to use
     * @param cssSelector        CSS selector for CSS_SELECTOR mode (legacy, can be null)
     * @param cssIncludeSelector CSS selectors for elements to include (comma-separated, null = all elements)
     * @param cssExcludeSelector CSS selectors for elements to exclude (comma-separated, null = no exclusion)
     * @param minTextLength      Minimum text block length for TEXT_ONLY mode (3-50, filters small elements)
     * @param minWordLength      Minimum word length for TEXT_ONLY mode (words shorter than this are filtered out)
     * @param diffAlgorithm      The diff algorithm to use for change detection
     * @return ComparisonResult containing change metrics
     */
    @NonNull
    public ComparisonResult compareContent(@Nullable String oldContent,
                                           @Nullable String newContent,
                                           @NonNull ComparisonMode mode,
                                           @Nullable String cssSelector,
                                           @Nullable String cssIncludeSelector,
                                           @Nullable String cssExcludeSelector,
                                           int minTextLength,
                                           int minWordLength,
                                           @NonNull DiffAlgorithmType diffAlgorithm) {
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
                return compareTextOnly(oldContent, newContent, minTextLength, minWordLength, diffAlgorithm);
            case CSS_SELECTOR:
                return compareCssSelector(oldContent, newContent, cssSelector, cssIncludeSelector,
                        cssExcludeSelector, diffAlgorithm);
            case FULL_HTML:
            default:
                return compareFullHtml(oldContent, newContent, diffAlgorithm);
        }
    }

    /**
     * Compare full HTML content using the selected diff algorithm.
     *
     * @param oldContent    Previous HTML
     * @param newContent    Current HTML
     * @param algorithmType The diff algorithm to use
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareFullHtml(@NonNull String oldContent,
                                             @NonNull String newContent,
                                             @NonNull DiffAlgorithmType algorithmType) {
        int oldSize = oldContent.length();
        int newSize = newContent.length();

        // Use the selected diff algorithm for change detection
        DiffAlgorithm algorithm = DiffAlgorithmFactory.create(algorithmType);
        DiffAlgorithm.DiffResult diffResult = algorithm.computeDiff(oldContent, newContent);
        float changePercent = diffResult.getChangePercent();

        if (changePercent < 0.01f) {
            return ComparisonResult.noChange(oldSize);
        }

        String description = diffResult.getDescription();
        return ComparisonResult.changed(changePercent, oldSize, newSize, description);
    }

    /**
     * Compare only the text content, stripping HTML tags.
     * Filters out text blocks shorter than minTextLength to reduce false positives
     * from dynamic content like timestamps, counters, etc.
     * Uses the selected diff algorithm for accurate change detection.
     *
     * @param oldContent    Previous HTML
     * @param newContent    Current HTML
     * @param minTextLength Minimum text block length (elements with shorter text are ignored)
     * @param minWordLength Minimum word length (words shorter than this are filtered out)
     * @param algorithmType The diff algorithm to use
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareTextOnly(@NonNull String oldContent,
                                             @NonNull String newContent,
                                             int minTextLength,
                                             int minWordLength,
                                             @NonNull DiffAlgorithmType algorithmType) {
        try {
            String oldText = extractTextFiltered(oldContent, minTextLength, minWordLength);
            String newText = extractTextFiltered(newContent, minTextLength, minWordLength);

            if (oldText.equals(newText)) {
                return ComparisonResult.noChange(oldText.length());
            }

            int oldSize = oldText.length();
            int newSize = newText.length();

            // Use the selected diff algorithm for change detection
            DiffAlgorithm algorithm = DiffAlgorithmFactory.create(algorithmType);
            DiffAlgorithm.DiffResult diffResult = algorithm.computeDiff(oldText, newText);
            float changePercent = diffResult.getChangePercent();

            String description = diffResult.getDescription();
            return ComparisonResult.changed(changePercent, oldSize, newSize, description);

        } catch (Exception e) {
            Logger.e(TAG, "Error comparing text content", e);
            // Fall back to full HTML comparison
            return compareFullHtml(oldContent, newContent, algorithmType);
        }
    }

    /**
     * Compare content matched by CSS selectors with include/exclude filtering.
     * Uses the selected diff algorithm for accurate change detection.
     *
     * @param oldContent         Previous HTML
     * @param newContent         Current HTML
     * @param cssSelector        Legacy CSS selector (for backward compatibility)
     * @param cssIncludeSelector CSS selectors for elements to include (null = all elements)
     * @param cssExcludeSelector CSS selectors for elements to exclude (null = no exclusion)
     * @param algorithmType      The diff algorithm to use
     * @return Comparison result
     */
    @NonNull
    private ComparisonResult compareCssSelector(@NonNull String oldContent,
                                                @NonNull String newContent,
                                                @Nullable String cssSelector,
                                                @Nullable String cssIncludeSelector,
                                                @Nullable String cssExcludeSelector,
                                                @NonNull DiffAlgorithmType algorithmType) {
        // Determine which selector to use for includes
        // Priority: cssIncludeSelector > cssSelector (legacy)
        String effectiveIncludeSelector = cssIncludeSelector;
        if ((effectiveIncludeSelector == null || effectiveIncludeSelector.trim().isEmpty())
                && cssSelector != null && !cssSelector.trim().isEmpty()) {
            effectiveIncludeSelector = cssSelector;
        }

        // Log selector configuration
        Logger.d(TAG, "CSS Selector comparison - include: " +
                (effectiveIncludeSelector != null ? effectiveIncludeSelector : "ALL") +
                ", exclude: " + (cssExcludeSelector != null ? cssExcludeSelector : "NONE"));

        try {
            String oldSelected = extractBySelector(oldContent, effectiveIncludeSelector, cssExcludeSelector);
            String newSelected = extractBySelector(newContent, effectiveIncludeSelector, cssExcludeSelector);

            if (oldSelected == null && newSelected == null) {
                return ComparisonResult.noChange(0);
            }

            String selectorDesc = buildSelectorDescription(effectiveIncludeSelector, cssExcludeSelector);

            if (oldSelected == null) {
                return ComparisonResult.changed(100f, 0,
                        newSelected != null ? newSelected.length() : 0,
                        "Element appeared: " + selectorDesc);
            }

            if (newSelected == null) {
                return ComparisonResult.changed(100f, oldSelected.length(), 0,
                        "Element disappeared: " + selectorDesc);
            }

            if (oldSelected.equals(newSelected)) {
                return ComparisonResult.noChange(oldSelected.length());
            }

            int oldSize = oldSelected.length();
            int newSize = newSelected.length();

            // Use the selected diff algorithm for change detection
            DiffAlgorithm algorithm = DiffAlgorithmFactory.create(algorithmType);
            DiffAlgorithm.DiffResult diffResult = algorithm.computeDiff(oldSelected, newSelected);
            float changePercent = diffResult.getChangePercent();

            String description = "Element changed (" + selectorDesc + "): " + diffResult.getDescription();
            return ComparisonResult.changed(changePercent, oldSize, newSize, description);

        } catch (Exception e) {
            Logger.e(TAG, "Error comparing CSS selector content", e);
            return ComparisonResult.changed(0f, 0, 0,
                    "Error comparing selector: " + e.getMessage());
        }
    }

    /**
     * Build a human-readable description of the selector configuration.
     */
    @NonNull
    private String buildSelectorDescription(@Nullable String includeSelector,
                                            @Nullable String excludeSelector) {
        StringBuilder desc = new StringBuilder();
        if (includeSelector != null && !includeSelector.trim().isEmpty()) {
            desc.append("include: ").append(includeSelector);
        } else {
            desc.append("all elements");
        }
        if (excludeSelector != null && !excludeSelector.trim().isEmpty()) {
            desc.append(", exclude: ").append(excludeSelector);
        }
        return desc.toString();
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
     * Legacy method for backward compatibility.
     *
     * @param html        The HTML content
     * @param cssSelector The CSS selector
     * @return Selected content, or null if not found
     */
    @Nullable
    private String extractBySelector(@NonNull String html, @NonNull String cssSelector) {
        return extractBySelector(html, cssSelector, null);
    }

    /**
     * Extract content with include/exclude CSS selector filtering.
     *
     * The filtering logic:
     * 1. If cssIncludeSelector is empty/null: start with ALL elements from the body
     * 2. If cssIncludeSelector has value: select only elements matching those selectors
     * 3. If cssExcludeSelector has value: remove elements matching those selectors from the result
     *
     * @param html               The HTML content
     * @param cssIncludeSelector CSS selectors for elements to include (null = all body content)
     * @param cssExcludeSelector CSS selectors for elements to exclude (null = no exclusion)
     * @return Selected content after filtering, or null if no content
     */
    @Nullable
    private String extractBySelector(@NonNull String html,
                                     @Nullable String cssIncludeSelector,
                                     @Nullable String cssExcludeSelector) {
        Document doc = Jsoup.parse(html);

        // Remove script, style, and noscript elements first (they're never useful for comparison)
        doc.select("script, style, noscript").remove();

        Elements elements;

        // Step 1: Get base elements (include filter)
        if (cssIncludeSelector == null || cssIncludeSelector.trim().isEmpty()) {
            // No include selector: select all content from body
            Logger.d(TAG, "No include selector, selecting all body content");
            elements = doc.select("body *");
            if (elements.isEmpty()) {
                // Fallback to all elements if no body
                elements = doc.getAllElements();
            }
        } else {
            // Include selector specified: select matching elements
            Logger.d(TAG, "Using include selector: " + cssIncludeSelector);
            elements = doc.select(cssIncludeSelector.trim());
            if (elements.isEmpty()) {
                Logger.w(TAG, "No elements found for include selector: " + cssIncludeSelector);
                return null;
            }
        }

        // Step 2: Apply exclude filter
        if (cssExcludeSelector != null && !cssExcludeSelector.trim().isEmpty()) {
            Logger.d(TAG, "Applying exclude selector: " + cssExcludeSelector);
            // Remove excluded elements from the selection
            Elements toRemove = new Elements();
            for (Element element : elements) {
                // Check if this element matches any exclude selector
                if (element.is(cssExcludeSelector.trim())) {
                    toRemove.add(element);
                }
                // Also remove child elements that match exclude selector
                Elements excludedChildren = element.select(cssExcludeSelector.trim());
                excludedChildren.remove();
            }
            elements.removeAll(toRemove);

            if (elements.isEmpty()) {
                Logger.w(TAG, "All elements were excluded, no content remaining");
                return null;
            }
        }

        // Build the output from remaining elements
        StringBuilder sb = new StringBuilder();
        for (Element element : elements) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(element.outerHtml());
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            return null;
        }

        Logger.d(TAG, "Extracted " + result.length() + " characters from " + elements.size() + " elements");
        return result;
    }
}
