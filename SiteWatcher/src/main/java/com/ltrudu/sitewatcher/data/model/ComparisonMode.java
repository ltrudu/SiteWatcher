package com.ltrudu.sitewatcher.data.model;

/**
 * Defines how site content should be compared for changes.
 */
public enum ComparisonMode {
    /**
     * Compare the full HTML content of the page.
     */
    FULL_HTML,

    /**
     * Compare only the text content, ignoring HTML tags.
     */
    TEXT_ONLY,

    /**
     * Compare content matched by a specific CSS selector.
     */
    CSS_SELECTOR
}
