package com.ltrudu.sitewatcher.data.model;

/**
 * Types of auto-click actions that can be performed.
 */
public enum ActionType {
    /**
     * Click an element by CSS selector.
     */
    CLICK,

    /**
     * Wait for a specified duration before the next action.
     */
    WAIT,

    /**
     * Tap at specific screen coordinates using Accessibility Service.
     * Used for elements that cannot be accessed via CSS selectors (e.g., iframe content).
     */
    TAP_COORDINATES
}
