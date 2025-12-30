package com.ltrudu.sitewatcher.data.model;

/**
 * Defines how site content should be fetched.
 */
public enum FetchMode {
    /**
     * Static fetch using OkHttp.
     * Fast and efficient, but only captures initial HTML response.
     * Does NOT execute JavaScript.
     */
    STATIC,

    /**
     * JavaScript-enabled fetch using WebView.
     * Slower but captures content after JavaScript execution.
     * Required for dynamic sites (SPAs, AJAX-loaded content, calendars, etc.)
     */
    JAVASCRIPT
}
