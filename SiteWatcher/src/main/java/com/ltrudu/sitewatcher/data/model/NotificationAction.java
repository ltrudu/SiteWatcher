package com.ltrudu.sitewatcher.data.model;

/**
 * Defines the action to take when a notification is tapped.
 */
public enum NotificationAction {
    /**
     * Open the SiteWatcher app when notification is tapped.
     */
    OPEN_APP,

    /**
     * Open the changed site in the default browser.
     */
    OPEN_BROWSER
}
