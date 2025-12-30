package com.ltrudu.sitewatcher.accessibility;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * Helper for checking and requesting accessibility service permissions.
 */
public class AccessibilityHelper {

    /**
     * Check if the TapAccessibilityService is enabled.
     */
    public static boolean isAccessibilityServiceEnabled(@NonNull Context context) {
        return TapAccessibilityService.isServiceEnabled();
    }

    /**
     * Open accessibility settings for user to enable the service.
     */
    public static void openAccessibilitySettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
