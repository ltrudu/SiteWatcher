package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.DiffAlgorithmType;
import com.ltrudu.sitewatcher.data.model.FeedbackPlayMode;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FetchMode;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.data.model.WatchedSite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for exporting and importing WatchedSite data to/from JSON format.
 */
public class SiteExporter {

    private static final String TAG = "SiteExporter";

    // JSON keys
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORT_DATE = "exportDate";
    private static final String KEY_SITES = "sites";

    // Site field keys
    private static final String KEY_URL = "url";
    private static final String KEY_NAME = "name";
    private static final String KEY_COMPARISON_MODE = "comparisonMode";
    private static final String KEY_CSS_SELECTOR = "cssSelector";
    private static final String KEY_CSS_INCLUDE_SELECTOR = "cssIncludeSelector";
    private static final String KEY_CSS_EXCLUDE_SELECTOR = "cssExcludeSelector";
    private static final String KEY_THRESHOLD_PERCENT = "thresholdPercent";
    private static final String KEY_IS_ENABLED = "isEnabled";
    private static final String KEY_MIN_TEXT_LENGTH = "minTextLength";
    private static final String KEY_MIN_WORD_LENGTH = "minWordLength";
    private static final String KEY_FETCH_MODE = "fetchMode";
    private static final String KEY_AUTO_CLICK_ACTIONS = "autoClickActions";
    private static final String KEY_FEEDBACK_ACTIONS = "feedbackActions";
    private static final String KEY_FEEDBACK_PLAY_MODE = "feedbackPlayMode";
    private static final String KEY_SCHEDULES = "schedules";
    private static final String KEY_DIFF_ALGORITHM = "diffAlgorithm";

    // Current export format version
    private static final int CURRENT_VERSION = 5;

    /**
     * Generates a filename with the format: SiteWatcher_YY-MM-DD_HH-MM-SS.json
     *
     * @return The generated filename
     */
    @NonNull
    public static String generateFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.US);
        String timestamp = sdf.format(new Date());
        return "SiteWatcher_" + timestamp + ".json";
    }

    /**
     * Generates a filename for a single site export with the format:
     * [DomainName]-YYYYMMDD-HH_MM_SS.json
     * <p>
     * Domain name is extracted from URL without www prefix and without TLD extension.
     * For example:
     * - https://www.example.com -> example-20261203-14_30_45.json
     * - https://my-site.org/path -> my-site-20261203-14_30_45.json
     *
     * @param url The URL to extract domain name from
     * @return The generated filename
     */
    @NonNull
    public static String generateFilenameForSite(@NonNull String url) {
        String domainName = extractDomainName(url);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH_mm_ss", Locale.US);
        String timestamp = sdf.format(new Date());
        return domainName + "-" + timestamp + ".json";
    }

    /**
     * Extracts the domain name from a URL without www prefix and without TLD extension.
     * <p>
     * Examples:
     * - https://www.example.com -> example
     * - https://my-site.org/path -> my-site
     * - https://subdomain.example.co.uk -> subdomain.example
     *
     * @param url The URL to extract domain from
     * @return The domain name without www and TLD
     */
    @NonNull
    private static String extractDomainName(@NonNull String url) {
        try {
            // Remove protocol
            String domain = url.replaceFirst("^https?://", "");

            // Remove path and query string
            int pathIndex = domain.indexOf('/');
            if (pathIndex > 0) {
                domain = domain.substring(0, pathIndex);
            }
            int queryIndex = domain.indexOf('?');
            if (queryIndex > 0) {
                domain = domain.substring(0, queryIndex);
            }

            // Remove port if present
            int portIndex = domain.indexOf(':');
            if (portIndex > 0) {
                domain = domain.substring(0, portIndex);
            }

            // Remove www prefix
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }

            // Remove TLD extension (last part after the final dot)
            // Handle multi-part TLDs like .co.uk, .com.br
            String[] parts = domain.split("\\.");
            if (parts.length >= 2) {
                // Check for common two-part TLDs
                String lastPart = parts[parts.length - 1].toLowerCase(Locale.US);
                String secondLastPart = parts.length >= 2 ? parts[parts.length - 2].toLowerCase(Locale.US) : "";

                // Common two-part TLDs
                boolean isTwoPartTld = (secondLastPart.equals("co") || secondLastPart.equals("com") ||
                        secondLastPart.equals("org") || secondLastPart.equals("net") ||
                        secondLastPart.equals("gov") || secondLastPart.equals("edu") ||
                        secondLastPart.equals("ac")) &&
                        (lastPart.length() == 2 || lastPart.equals("uk")); // Country codes

                if (isTwoPartTld && parts.length >= 3) {
                    // Remove last two parts (e.g., .co.uk)
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length - 2; i++) {
                        if (sb.length() > 0) sb.append(".");
                        sb.append(parts[i]);
                    }
                    domain = sb.toString();
                } else {
                    // Remove only the last part (e.g., .com)
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (sb.length() > 0) sb.append(".");
                        sb.append(parts[i]);
                    }
                    domain = sb.toString();
                }
            }

            // Sanitize for filename: replace invalid characters
            domain = domain.replaceAll("[^a-zA-Z0-9._-]", "_");

            // Ensure we have something valid
            if (domain.isEmpty()) {
                domain = "site";
            }

            return domain;
        } catch (Exception e) {
            Logger.e(TAG, "Error extracting domain name from: " + url, e);
            return "site";
        }
    }

    /**
     * Exports a list of WatchedSite objects to JSON format.
     *
     * @param sites The list of sites to export
     * @return JSON string representation of the sites
     * @throws JSONException if JSON serialization fails
     */
    @NonNull
    public static String exportToJson(@NonNull List<WatchedSite> sites) throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_VERSION, CURRENT_VERSION);
        root.put(KEY_EXPORT_DATE, System.currentTimeMillis());

        JSONArray sitesArray = new JSONArray();
        for (WatchedSite site : sites) {
            sitesArray.put(siteToJson(site));
        }
        root.put(KEY_SITES, sitesArray);

        return root.toString(2); // Pretty print with 2 space indent
    }

    /**
     * Imports WatchedSite objects from JSON format.
     *
     * @param json The JSON string to parse
     * @return List of WatchedSite objects
     * @throws JSONException if JSON parsing fails
     */
    @NonNull
    public static List<WatchedSite> importFromJson(@NonNull String json) throws JSONException {
        List<WatchedSite> sites = new ArrayList<>();

        JSONObject root = new JSONObject(json);
        int version = root.optInt(KEY_VERSION, 1);

        Logger.d(TAG, "Importing sites from version " + version + " format");

        JSONArray sitesArray = root.getJSONArray(KEY_SITES);
        for (int i = 0; i < sitesArray.length(); i++) {
            JSONObject siteJson = sitesArray.getJSONObject(i);
            WatchedSite site = jsonToSite(siteJson, version);
            if (site != null) {
                sites.add(site);
            }
        }

        Logger.d(TAG, "Imported " + sites.size() + " sites");
        return sites;
    }

    /**
     * Converts a WatchedSite to a JSONObject.
     */
    @NonNull
    private static JSONObject siteToJson(@NonNull WatchedSite site) throws JSONException {
        JSONObject json = new JSONObject();

        json.put(KEY_URL, site.getUrl());
        if (site.getName() != null) {
            json.put(KEY_NAME, site.getName());
        }
        json.put(KEY_COMPARISON_MODE, site.getComparisonMode().name());
        if (site.getCssSelector() != null) {
            json.put(KEY_CSS_SELECTOR, site.getCssSelector());
        }
        if (site.getCssIncludeSelector() != null) {
            json.put(KEY_CSS_INCLUDE_SELECTOR, site.getCssIncludeSelector());
        }
        if (site.getCssExcludeSelector() != null) {
            json.put(KEY_CSS_EXCLUDE_SELECTOR, site.getCssExcludeSelector());
        }
        json.put(KEY_THRESHOLD_PERCENT, site.getThresholdPercent());
        json.put(KEY_IS_ENABLED, site.isEnabled());
        json.put(KEY_MIN_TEXT_LENGTH, site.getMinTextLength());
        json.put(KEY_MIN_WORD_LENGTH, site.getMinWordLength());
        json.put(KEY_FETCH_MODE, site.getFetchMode().name());
        json.put(KEY_DIFF_ALGORITHM, site.getDiffAlgorithm().name());

        // Export auto-click actions as JSON array
        String actionsJson = site.getAutoClickActionsJson();
        if (actionsJson != null && !actionsJson.isEmpty()) {
            json.put(KEY_AUTO_CLICK_ACTIONS, new JSONArray(actionsJson));
        }

        // Export feedback actions as JSON array
        String feedbackActionsJson = site.getFeedbackActionsJson();
        if (feedbackActionsJson != null && !feedbackActionsJson.isEmpty()) {
            json.put(KEY_FEEDBACK_ACTIONS, new JSONArray(feedbackActionsJson));
        }
        json.put(KEY_FEEDBACK_PLAY_MODE, site.getFeedbackPlayMode().name());

        // Export schedules as JSON array
        String schedulesJson = site.getSchedulesJson();
        Logger.d(TAG, "Exporting site: " + site.getUrl() +
                ", fetchMode=" + site.getFetchMode().name() +
                ", schedulesJson=" + (schedulesJson != null ? schedulesJson.length() + " chars" : "null"));
        if (schedulesJson != null && !schedulesJson.isEmpty()) {
            json.put(KEY_SCHEDULES, new JSONArray(schedulesJson));
        }

        return json;
    }

    /**
     * Converts a JSONObject to a WatchedSite.
     *
     * @param json    The JSON object to parse
     * @param version The export format version (for future compatibility)
     * @return A new WatchedSite object, or null if the URL is missing
     */
    @Nullable
    private static WatchedSite jsonToSite(@NonNull JSONObject json, int version) {
        try {
            String url = json.optString(KEY_URL, null);
            if (url == null || url.isEmpty()) {
                Logger.w(TAG, "Skipping site with missing URL");
                return null;
            }

            WatchedSite site = new WatchedSite();
            site.setUrl(url);
            site.setName(json.optString(KEY_NAME, null));

            // Parse comparison mode
            String comparisonModeStr = json.optString(KEY_COMPARISON_MODE, ComparisonMode.TEXT_ONLY.name());
            try {
                site.setComparisonMode(ComparisonMode.valueOf(comparisonModeStr));
            } catch (IllegalArgumentException e) {
                site.setComparisonMode(ComparisonMode.TEXT_ONLY);
            }

            site.setCssSelector(json.optString(KEY_CSS_SELECTOR, null));
            site.setCssIncludeSelector(json.optString(KEY_CSS_INCLUDE_SELECTOR, null));
            site.setCssExcludeSelector(json.optString(KEY_CSS_EXCLUDE_SELECTOR, null));
            site.setThresholdPercent(json.optInt(KEY_THRESHOLD_PERCENT, 5));
            site.setEnabled(json.optBoolean(KEY_IS_ENABLED, true));
            site.setMinTextLength(json.optInt(KEY_MIN_TEXT_LENGTH, 10));
            site.setMinWordLength(json.optInt(KEY_MIN_WORD_LENGTH, 3));

            // Parse fetch mode
            String fetchModeStr = json.optString(KEY_FETCH_MODE, FetchMode.STATIC.name());
            try {
                site.setFetchMode(FetchMode.valueOf(fetchModeStr));
            } catch (IllegalArgumentException e) {
                site.setFetchMode(FetchMode.STATIC);
            }

            // Parse diff algorithm
            String diffAlgorithmStr = json.optString(KEY_DIFF_ALGORITHM, DiffAlgorithmType.LINE.name());
            try {
                site.setDiffAlgorithm(DiffAlgorithmType.valueOf(diffAlgorithmStr));
            } catch (IllegalArgumentException e) {
                site.setDiffAlgorithm(DiffAlgorithmType.LINE);
            }

            // Parse auto-click actions
            if (json.has(KEY_AUTO_CLICK_ACTIONS)) {
                JSONArray actionsArray = json.optJSONArray(KEY_AUTO_CLICK_ACTIONS);
                if (actionsArray != null) {
                    site.setAutoClickActionsJson(actionsArray.toString());
                }
            }

            // Parse feedback actions - add default if empty
            if (json.has(KEY_FEEDBACK_ACTIONS)) {
                JSONArray feedbackActionsArray = json.optJSONArray(KEY_FEEDBACK_ACTIONS);
                if (feedbackActionsArray != null && feedbackActionsArray.length() > 0) {
                    site.setFeedbackActionsJson(feedbackActionsArray.toString());
                } else {
                    // Add default Notification feedback for sites with empty feedback list
                    addDefaultFeedbackAction(site);
                }
            } else {
                // Add default Notification feedback for sites without feedback actions
                addDefaultFeedbackAction(site);
            }

            // Parse feedback play mode
            if (json.has(KEY_FEEDBACK_PLAY_MODE)) {
                try {
                    site.setFeedbackPlayMode(FeedbackPlayMode.valueOf(json.getString(KEY_FEEDBACK_PLAY_MODE)));
                } catch (IllegalArgumentException e) {
                    site.setFeedbackPlayMode(FeedbackPlayMode.SEQUENTIAL);
                }
            }

            // Parse schedules (v3+) or create default schedule for older versions
            if (json.has(KEY_SCHEDULES)) {
                JSONArray schedulesArray = json.optJSONArray(KEY_SCHEDULES);
                if (schedulesArray != null) {
                    site.setSchedulesJson(schedulesArray.toString());
                }
            } else {
                // Create default schedule for older export versions
                site.setSchedulesJson(Schedule.toJsonString(Schedule.createDefaultList()));
            }

            Logger.d(TAG, "Imported site: " + url +
                    ", fetchMode=" + site.getFetchMode().name() +
                    ", schedulesJson=" + (site.getSchedulesJson() != null ?
                            site.getSchedulesJson().length() + " chars" : "null"));

            // Reset runtime state for imported sites
            site.setId(0); // Will be auto-generated
            site.setLastCheckTime(0);
            site.setLastChangePercent(0);
            site.setLastError(null);
            site.setConsecutiveFailures(0);
            site.setCreatedAt(System.currentTimeMillis());
            site.setUpdatedAt(System.currentTimeMillis());

            return site;

        } catch (Exception e) {
            Logger.e(TAG, "Error parsing site JSON", e);
            return null;
        }
    }

    /**
     * Adds a default Notification feedback action to a site.
     * Used when importing sites with empty feedback actions.
     *
     * @param site The site to add the default feedback action to
     */
    private static void addDefaultFeedbackAction(@NonNull WatchedSite site) {
        List<FeedbackAction> defaultFeedbackActions = new ArrayList<>();
        defaultFeedbackActions.add(FeedbackAction.createNotification("Notification"));
        site.setFeedbackActionsJson(FeedbackAction.toJsonString(defaultFeedbackActions));
        Logger.d(TAG, "Added default Notification feedback for imported site: " + site.getUrl());
    }
}
