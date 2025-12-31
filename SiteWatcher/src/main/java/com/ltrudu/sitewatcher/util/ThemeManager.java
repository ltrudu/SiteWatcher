package com.ltrudu.sitewatcher.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.ltrudu.sitewatcher.R;

/**
 * Manages application themes.
 * Provides methods to get, set, and apply themes.
 */
public class ThemeManager {

    private static final String PREFS_NAME = "sitewatcher_prefs";
    private static final String KEY_THEME = "app_theme";

    /**
     * Available app themes.
     */
    public enum Theme {
        ORANGE_FIRE(0, R.style.Theme_SiteWatcher_OrangeFire, "Orange Fire"),
        BLUE_LIGHT(1, R.style.Theme_SiteWatcher_BlueLight, "Blue Light"),
        FOREST_GREEN(2, R.style.Theme_SiteWatcher_ForestGreen, "Forest Green"),
        OCEAN_BLUE(3, R.style.Theme_SiteWatcher_OceanBlue, "Ocean Blue"),
        SYNTHWAVE(4, R.style.Theme_SiteWatcher_Synthwave, "Synthwave"),
        COASTAL_SUNSET(5, R.style.Theme_SiteWatcher_CoastalSunset, "Coastal Sunset"),
        NORDIC_RED(6, R.style.Theme_SiteWatcher_NordicRed, "Nordic Red"),
        NEON_CARNIVAL(7, R.style.Theme_SiteWatcher_NeonCarnival, "Neon Carnival");

        private final int id;
        private final int styleResId;
        private final String displayName;

        Theme(int id, @StyleRes int styleResId, String displayName) {
            this.id = id;
            this.styleResId = styleResId;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        @StyleRes
        public int getStyleResId() {
            return styleResId;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get theme by ID.
         */
        @NonNull
        public static Theme fromId(int id) {
            for (Theme theme : values()) {
                if (theme.id == id) {
                    return theme;
                }
            }
            return ORANGE_FIRE; // Default
        }
    }

    private final SharedPreferences preferences;

    public ThemeManager(@NonNull Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get the currently selected theme.
     */
    @NonNull
    public Theme getCurrentTheme() {
        int themeId = preferences.getInt(KEY_THEME, Theme.ORANGE_FIRE.getId());
        return Theme.fromId(themeId);
    }

    /**
     * Set the current theme preference.
     * Note: This only saves the preference. Call applyTheme() to apply it.
     */
    public void setCurrentTheme(@NonNull Theme theme) {
        preferences.edit().putInt(KEY_THEME, theme.getId()).apply();
    }

    /**
     * Apply the saved theme to an activity.
     * Must be called BEFORE super.onCreate() and setContentView().
     */
    public void applyTheme(@NonNull Activity activity) {
        Theme theme = getCurrentTheme();
        activity.setTheme(theme.getStyleResId());
    }

    /**
     * Get all available themes.
     */
    @NonNull
    public static Theme[] getAvailableThemes() {
        return Theme.values();
    }

    /**
     * Get theme display names for UI dropdown.
     */
    @NonNull
    public static String[] getThemeDisplayNames() {
        Theme[] themes = Theme.values();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].getDisplayName();
        }
        return names;
    }

    /**
     * Get the index of the current theme in the themes array.
     */
    public int getCurrentThemeIndex() {
        Theme current = getCurrentTheme();
        Theme[] themes = Theme.values();
        for (int i = 0; i < themes.length; i++) {
            if (themes[i] == current) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Set theme by index (for spinner/dropdown).
     */
    public void setThemeByIndex(int index) {
        Theme[] themes = Theme.values();
        if (index >= 0 && index < themes.length) {
            setCurrentTheme(themes[index]);
        }
    }
}
