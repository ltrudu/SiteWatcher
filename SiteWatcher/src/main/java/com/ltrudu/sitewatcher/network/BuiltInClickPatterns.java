package com.ltrudu.sitewatcher.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.ActionType;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages built-in click patterns for cookie consent dialogs and similar overlays.
 * Patterns are loaded from res/raw/builtin_click_patterns.json.
 */
public class BuiltInClickPatterns {

    private static final String TAG = "BuiltInClickPatterns";

    private static List<AutoClickAction> cachedPatterns;

    /**
     * Get all built-in click patterns.
     * Patterns are cached after first load.
     *
     * @param context Application context
     * @return List of built-in patterns
     */
    @NonNull
    public static synchronized List<AutoClickAction> getPatterns(@NonNull Context context) {
        if (cachedPatterns != null) {
            return new ArrayList<>(cachedPatterns);
        }

        cachedPatterns = loadPatterns(context);
        return new ArrayList<>(cachedPatterns);
    }

    /**
     * Get a specific pattern by ID.
     *
     * @param context Application context
     * @param id      Pattern ID
     * @return The pattern, or null if not found
     */
    @Nullable
    public static AutoClickAction getPatternById(@NonNull Context context, @NonNull String id) {
        List<AutoClickAction> patterns = getPatterns(context);
        for (AutoClickAction pattern : patterns) {
            if (id.equals(pattern.getId())) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * Get default patterns that should be enabled for new sites.
     * By default, generic reject patterns are enabled.
     *
     * @param context Application context
     * @return List of default patterns with enabled state set
     */
    @NonNull
    public static List<AutoClickAction> getDefaultPatterns(@NonNull Context context) {
        List<AutoClickAction> patterns = getPatterns(context);
        List<AutoClickAction> defaults = new ArrayList<>();

        // All patterns are disabled by default - user chooses which to enable
        int order = 0;
        for (AutoClickAction pattern : patterns) {
            AutoClickAction copy = pattern.copy();
            copy.setId(pattern.getId()); // Keep original ID for built-in patterns
            copy.setBuiltIn(true);
            copy.setEnabled(false);
            copy.setOrder(order++);
            defaults.add(copy);
        }

        return defaults;
    }

    /**
     * Load patterns from the JSON resource file.
     *
     * @param context Application context
     * @return List of loaded patterns
     */
    @NonNull
    private static List<AutoClickAction> loadPatterns(@NonNull Context context) {
        List<AutoClickAction> patterns = new ArrayList<>();

        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.builtin_click_patterns);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray patternsArray = root.getJSONArray("patterns");

            for (int i = 0; i < patternsArray.length(); i++) {
                JSONObject patternJson = patternsArray.getJSONObject(i);
                AutoClickAction action = AutoClickAction.createClickAction(
                        patternJson.getString("id"),
                        patternJson.getString("label"),
                        patternJson.getString("selector"),
                        false, // Disabled by default
                        true,  // Is built-in
                        i      // Order
                );
                patterns.add(action);
            }

            Logger.d(TAG, "Loaded " + patterns.size() + " built-in click patterns");

        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing built-in patterns JSON", e);
        } catch (Exception e) {
            Logger.e(TAG, "Error loading built-in patterns", e);
        }

        return patterns;
    }

    /**
     * Clear the cached patterns.
     * Useful for testing or when patterns file is updated.
     */
    public static synchronized void clearCache() {
        cachedPatterns = null;
    }

    /**
     * Check if a pattern ID is a built-in pattern.
     *
     * @param context Application context
     * @param id      Pattern ID to check
     * @return true if the ID matches a built-in pattern
     */
    public static boolean isBuiltInPattern(@NonNull Context context, @NonNull String id) {
        return getPatternById(context, id) != null;
    }

    /**
     * Get all built-in patterns grouped by category.
     * Patterns with IDs starting with "generic_" go to "Generic Patterns" category,
     * others go to "Cookie Consent Frameworks" category.
     *
     * @param context Application context
     * @return LinkedHashMap of category name to list of patterns (preserves insertion order)
     */
    @NonNull
    public static Map<String, List<AutoClickAction>> getPatternsByCategory(@NonNull Context context) {
        List<AutoClickAction> allPatterns = getPatterns(context);

        // Use LinkedHashMap to preserve order: Cookie Consent Frameworks first, then Generic Patterns
        Map<String, List<AutoClickAction>> patternsByCategory = new LinkedHashMap<>();

        List<AutoClickAction> cookieFrameworks = new ArrayList<>();
        List<AutoClickAction> genericPatterns = new ArrayList<>();

        for (AutoClickAction pattern : allPatterns) {
            String id = pattern.getId();
            if (id != null && id.startsWith("generic_")) {
                genericPatterns.add(pattern);
            } else {
                cookieFrameworks.add(pattern);
            }
        }

        // Add categories in order
        if (!cookieFrameworks.isEmpty()) {
            patternsByCategory.put("Cookie Consent Frameworks", cookieFrameworks);
        }
        if (!genericPatterns.isEmpty()) {
            patternsByCategory.put("Generic Patterns", genericPatterns);
        }

        return patternsByCategory;
    }
}
