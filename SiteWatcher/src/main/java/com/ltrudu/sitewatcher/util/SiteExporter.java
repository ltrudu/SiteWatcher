package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.ScheduleType;
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
    private static final String KEY_SCHEDULE_TYPE = "scheduleType";
    private static final String KEY_SCHEDULE_HOUR = "scheduleHour";
    private static final String KEY_SCHEDULE_MINUTE = "scheduleMinute";
    private static final String KEY_PERIODIC_INTERVAL_MINUTES = "periodicIntervalMinutes";
    private static final String KEY_ENABLED_DAYS = "enabledDays";
    private static final String KEY_COMPARISON_MODE = "comparisonMode";
    private static final String KEY_CSS_SELECTOR = "cssSelector";
    private static final String KEY_THRESHOLD_PERCENT = "thresholdPercent";
    private static final String KEY_IS_ENABLED = "isEnabled";

    // Current export format version
    private static final int CURRENT_VERSION = 1;

    /**
     * Generates a filename with the format: SiteWatcher_YY-MM-DD_HH-MM-SS.sw
     *
     * @return The generated filename
     */
    @NonNull
    public static String generateFilename() {
        SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.US);
        String timestamp = sdf.format(new Date());
        return "SiteWatcher_" + timestamp + ".sw";
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
        json.put(KEY_SCHEDULE_TYPE, site.getScheduleType().name());
        json.put(KEY_SCHEDULE_HOUR, site.getScheduleHour());
        json.put(KEY_SCHEDULE_MINUTE, site.getScheduleMinute());
        json.put(KEY_PERIODIC_INTERVAL_MINUTES, site.getPeriodicIntervalMinutes());
        json.put(KEY_ENABLED_DAYS, site.getEnabledDays());
        json.put(KEY_COMPARISON_MODE, site.getComparisonMode().name());
        if (site.getCssSelector() != null) {
            json.put(KEY_CSS_SELECTOR, site.getCssSelector());
        }
        json.put(KEY_THRESHOLD_PERCENT, site.getThresholdPercent());
        json.put(KEY_IS_ENABLED, site.isEnabled());

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

            // Parse schedule type
            String scheduleTypeStr = json.optString(KEY_SCHEDULE_TYPE, ScheduleType.PERIODIC.name());
            try {
                site.setScheduleType(ScheduleType.valueOf(scheduleTypeStr));
            } catch (IllegalArgumentException e) {
                site.setScheduleType(ScheduleType.PERIODIC);
            }

            site.setScheduleHour(json.optInt(KEY_SCHEDULE_HOUR, 9));
            site.setScheduleMinute(json.optInt(KEY_SCHEDULE_MINUTE, 0));
            site.setPeriodicIntervalMinutes(json.optInt(KEY_PERIODIC_INTERVAL_MINUTES, 60));
            site.setEnabledDays(json.optInt(KEY_ENABLED_DAYS, WatchedSite.ALL_DAYS));

            // Parse comparison mode
            String comparisonModeStr = json.optString(KEY_COMPARISON_MODE, ComparisonMode.TEXT_ONLY.name());
            try {
                site.setComparisonMode(ComparisonMode.valueOf(comparisonModeStr));
            } catch (IllegalArgumentException e) {
                site.setComparisonMode(ComparisonMode.TEXT_ONLY);
            }

            site.setCssSelector(json.optString(KEY_CSS_SELECTOR, null));
            site.setThresholdPercent(json.optInt(KEY_THRESHOLD_PERCENT, 5));
            site.setEnabled(json.optBoolean(KEY_IS_ENABLED, true));

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
}
