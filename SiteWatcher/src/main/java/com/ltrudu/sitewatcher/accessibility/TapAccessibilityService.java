package com.ltrudu.sitewatcher.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.ltrudu.sitewatcher.util.Logger;

/**
 * Accessibility service that performs tap gestures at specific coordinates.
 * Used for clicking elements in iframes or other contexts where CSS selectors don't work.
 */
public class TapAccessibilityService extends AccessibilityService {

    private static final String TAG = "TapAccessibility";
    private static TapAccessibilityService instance;

    public static TapAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isServiceEnabled() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Logger.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used - we only need this service for performing gestures
    }

    @Override
    public void onInterrupt() {
        Logger.d(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Logger.d(TAG, "Accessibility service destroyed");
    }

    /**
     * Perform a tap gesture at the specified screen coordinates.
     *
     * @param x X coordinate in screen pixels
     * @param y Y coordinate in screen pixels
     * @param callback Callback for completion (null for fire-and-forget)
     * @return true if the gesture was dispatched, false if service unavailable
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean performTap(float x, float y, GestureResultCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Logger.e(TAG, "Gesture dispatch requires API 24+");
            return false;
        }

        Logger.d(TAG, "Performing tap at (" + x + ", " + y + ")");

        Path tapPath = new Path();
        tapPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                tapPath, 0, 100); // 100ms tap duration

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        return dispatchGesture(gesture, callback, null);
    }

    /**
     * Perform a tap gesture at the specified screen coordinates (fire-and-forget).
     *
     * @param x X coordinate in screen pixels
     * @param y Y coordinate in screen pixels
     * @return true if the gesture was dispatched
     */
    public boolean performTap(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return performTap(x, y, null);
        }
        return false;
    }
}
