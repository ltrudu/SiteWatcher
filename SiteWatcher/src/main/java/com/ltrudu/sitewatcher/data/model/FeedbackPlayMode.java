package com.ltrudu.sitewatcher.data.model;

/**
 * Defines how feedback actions are executed when a site change is detected.
 */
public enum FeedbackPlayMode {
    /**
     * Execute feedback actions one after another in order.
     * This is the default mode.
     */
    SEQUENTIAL,

    /**
     * Execute all feedback actions simultaneously using separate threads.
     * Useful when you want immediate feedback from all channels at once.
     */
    ALL_AT_ONCE
}
