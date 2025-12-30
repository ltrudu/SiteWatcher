package com.ltrudu.sitewatcher.network;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.ltrudu.sitewatcher.accessibility.TapAccessibilityService;
import com.ltrudu.sitewatcher.data.model.ActionType;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Executes auto-click actions on a WebView.
 * Supports CLICK actions (click elements), WAIT actions (pause execution),
 * and TAP_COORDINATES actions (tap at screen coordinates via accessibility service).
 *
 * For TAP_COORDINATES actions, the executor automatically enters fullscreen
 * immersive mode to ensure coordinates match what was selected in the picker.
 */
public class AutoClickExecutor {

    private static final String TAG = "AutoClickExecutor";

    /**
     * Default delay after a click action to allow DOM to update.
     */
    private static final int DEFAULT_CLICK_DELAY_MS = 500;

    /**
     * Delay after entering fullscreen to allow layout to stabilize.
     */
    private static final int FULLSCREEN_SETTLE_DELAY_MS = 300;

    /**
     * Callback interface for action execution completion.
     */
    public interface ExecutionCallback {
        /**
         * Called when all actions have been executed.
         *
         * @param successCount Number of successful click actions
         * @param failCount    Number of failed click actions
         */
        void onComplete(int successCount, int failCount);

        /**
         * Called after each individual action executes.
         *
         * @param actionId Action identifier
         * @param success  Whether the action succeeded
         */
        default void onActionResult(String actionId, boolean success) {
            // Optional callback
        }
    }

    private final Handler mainHandler;
    private boolean isInFullscreen = false;
    private Activity currentActivity = null;

    public AutoClickExecutor() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Execute a list of auto-click actions sequentially.
     * Only enabled actions are executed, sorted by order.
     *
     * @param webView  The WebView to execute actions on (must be on main thread)
     * @param actions  List of actions to execute
     * @param callback Callback for completion
     */
    public void executeActions(
            @NonNull WebView webView,
            @NonNull List<AutoClickAction> actions,
            @NonNull ExecutionCallback callback) {

        // Filter enabled actions and sort by order
        List<AutoClickAction> enabledActions = new ArrayList<>();
        for (AutoClickAction action : actions) {
            if (action.isEnabled()) {
                enabledActions.add(action);
            }
        }
        enabledActions.sort(Comparator.comparingInt(AutoClickAction::getOrder));

        if (enabledActions.isEmpty()) {
            Logger.d(TAG, "No enabled actions to execute");
            callback.onComplete(0, 0);
            return;
        }

        Logger.d(TAG, "Executing " + enabledActions.size() + " auto-click actions");

        // Check if any TAP_COORDINATES actions exist
        boolean hasTapActions = false;
        for (AutoClickAction action : enabledActions) {
            if (action.getType() == ActionType.TAP_COORDINATES) {
                hasTapActions = true;
                break;
            }
        }

        // If there are tap actions, enter fullscreen mode first
        if (hasTapActions) {
            Activity activity = getActivityFromView(webView);
            if (activity != null) {
                Logger.d(TAG, "TAP_COORDINATES actions found, entering fullscreen mode");
                enterFullscreenMode(activity);

                // Wait for layout to settle before executing actions
                mainHandler.postDelayed(() -> {
                    executeNextAction(webView, enabledActions, 0, 0, 0, callback);
                }, FULLSCREEN_SETTLE_DELAY_MS);
                return;
            } else {
                Logger.w(TAG, "Could not get Activity for fullscreen mode, tap coordinates may be inaccurate");
            }
        }

        // Execute actions immediately if no tap actions or no activity
        executeNextAction(webView, enabledActions, 0, 0, 0, callback);
    }

    /**
     * Get the Activity from a View's context.
     */
    @Nullable
    private Activity getActivityFromView(@NonNull View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * Enter fullscreen immersive mode.
     */
    private void enterFullscreenMode(@NonNull Activity activity) {
        if (isInFullscreen) return;

        Window window = activity.getWindow();
        if (window == null) return;

        currentActivity = activity;
        isInFullscreen = true;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());

        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
        Logger.d(TAG, "Entered fullscreen mode for tap actions");
    }

    /**
     * Exit fullscreen immersive mode.
     * Call this when done with actions that required fullscreen.
     */
    public void exitFullscreenMode() {
        if (!isInFullscreen || currentActivity == null) return;

        Window window = currentActivity.getWindow();
        if (window == null) {
            isInFullscreen = false;
            currentActivity = null;
            return;
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        WindowCompat.setDecorFitsSystemWindows(window, true);

        isInFullscreen = false;
        currentActivity = null;
        Logger.d(TAG, "Exited fullscreen mode");
    }

    /**
     * Check if currently in fullscreen mode.
     */
    public boolean isInFullscreenMode() {
        return isInFullscreen;
    }

    /**
     * Execute the next action in the list recursively.
     */
    private void executeNextAction(
            @NonNull WebView webView,
            @NonNull List<AutoClickAction> actions,
            int index,
            int successCount,
            int failCount,
            @NonNull ExecutionCallback callback) {

        if (index >= actions.size()) {
            // All actions complete
            Logger.d(TAG, "All actions complete: " + successCount + " success, " + failCount + " failed");
            callback.onComplete(successCount, failCount);
            return;
        }

        AutoClickAction action = actions.get(index);
        Logger.d(TAG, "Executing action " + (index + 1) + "/" + actions.size() + ": " + action.getLabel());

        if (action.getType() == ActionType.WAIT) {
            // WAIT action: just delay
            int delayMs = action.getWaitSeconds() * 1000;
            Logger.d(TAG, "WAIT action: waiting " + action.getWaitSeconds() + " seconds");

            callback.onActionResult(action.getId(), true);

            mainHandler.postDelayed(() -> {
                executeNextAction(webView, actions, index + 1, successCount, failCount, callback);
            }, delayMs);

        } else if (action.getType() == ActionType.TAP_COORDINATES) {
            // TAP_COORDINATES action: use accessibility service
            executeTapCoordinatesAction(webView, action, result -> {
                callback.onActionResult(action.getId(), result);

                mainHandler.postDelayed(() -> {
                    executeNextAction(
                            webView,
                            actions,
                            index + 1,
                            result ? successCount + 1 : successCount,
                            result ? failCount : failCount + 1,
                            callback
                    );
                }, DEFAULT_CLICK_DELAY_MS);
            });

        } else {
            // CLICK action: execute JavaScript
            String selector = action.getSelector();
            if (selector == null || selector.isEmpty()) {
                Logger.w(TAG, "CLICK action has no selector: " + action.getLabel());
                callback.onActionResult(action.getId(), false);
                mainHandler.postDelayed(() -> {
                    executeNextAction(webView, actions, index + 1, successCount, failCount + 1, callback);
                }, DEFAULT_CLICK_DELAY_MS);
                return;
            }

            String clickScript = generateClickScript(selector);

            webView.evaluateJavascript(clickScript, result -> {
                boolean success = "\"success\"".equals(result);
                if (success) {
                    Logger.d(TAG, "CLICK action succeeded: " + action.getLabel());
                } else {
                    Logger.d(TAG, "CLICK action result: " + result + " for " + action.getLabel());
                }

                callback.onActionResult(action.getId(), success);

                // Wait after click for DOM to update
                mainHandler.postDelayed(() -> {
                    executeNextAction(
                            webView,
                            actions,
                            index + 1,
                            success ? successCount + 1 : successCount,
                            success ? failCount : failCount + 1,
                            callback
                    );
                }, DEFAULT_CLICK_DELAY_MS);
            });
        }
    }

    /**
     * Callback for tap result.
     */
    public interface TapResultCallback {
        void onResult(boolean success);
    }

    /**
     * Execute a TAP_COORDINATES action using the accessibility service.
     * Assumes fullscreen mode has already been entered if needed.
     */
    private void executeTapCoordinatesAction(
            @NonNull WebView webView,
            @NonNull AutoClickAction action,
            @NonNull TapResultCallback callback) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Logger.e(TAG, "TAP_COORDINATES requires API 24+");
            callback.onResult(false);
            return;
        }

        TapAccessibilityService service = TapAccessibilityService.getInstance();
        if (service == null) {
            Logger.e(TAG, "Accessibility service not enabled");
            callback.onResult(false);
            return;
        }

        // Get WebView location on screen
        int[] location = new int[2];
        webView.getLocationOnScreen(location);

        // Calculate absolute screen coordinates from percentages
        float tapX = action.getTapX();
        float tapY = action.getTapY();

        float screenX = location[0] + (tapX * webView.getWidth());
        float screenY = location[1] + (tapY * webView.getHeight());

        Logger.d(TAG, "TAP_COORDINATES: WebView at (" + location[0] + ", " + location[1] +
                "), size " + webView.getWidth() + "x" + webView.getHeight() +
                ", tapping at (" + screenX + ", " + screenY + ")");

        boolean dispatched = service.performTap(screenX, screenY);

        if (dispatched) {
            Logger.d(TAG, "Tap gesture dispatched successfully");
            // Give time for the tap to be processed
            mainHandler.postDelayed(() -> callback.onResult(true), 200);
        } else {
            Logger.e(TAG, "Failed to dispatch tap gesture");
            callback.onResult(false);
        }
    }

    /**
     * Generate JavaScript to click an element by CSS selector.
     * The script tries to find and click the first matching element.
     *
     * @param selector CSS selector
     * @return JavaScript code that returns 'success', 'not_found', or 'error:message'
     */
    @NonNull
    public static String generateClickScript(@NonNull String selector) {
        // Escape single quotes in selector
        String escapedSelector = selector.replace("'", "\\'");

        return "(function() {\n" +
                "    try {\n" +
                "        var el = document.querySelector('" + escapedSelector + "');\n" +
                "        if (el) {\n" +
                "            el.click();\n" +
                "            return 'success';\n" +
                "        }\n" +
                "        return 'not_found';\n" +
                "    } catch (e) {\n" +
                "        return 'error:' + e.message;\n" +
                "    }\n" +
                "})();";
    }

    /**
     * Generate JavaScript to click all matching elements.
     * Useful for multi-selector patterns.
     *
     * @param selector CSS selector (can be comma-separated)
     * @return JavaScript code
     */
    @NonNull
    public static String generateClickAllScript(@NonNull String selector) {
        String escapedSelector = selector.replace("'", "\\'");

        return "(function() {\n" +
                "    try {\n" +
                "        var elements = document.querySelectorAll('" + escapedSelector + "');\n" +
                "        if (elements.length > 0) {\n" +
                "            for (var i = 0; i < elements.length; i++) {\n" +
                "                elements[i].click();\n" +
                "            }\n" +
                "            return 'success:' + elements.length;\n" +
                "        }\n" +
                "        return 'not_found';\n" +
                "    } catch (e) {\n" +
                "        return 'error:' + e.message;\n" +
                "    }\n" +
                "})();";
    }
}
