package com.ltrudu.sitewatcher.ui.selector;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.ui.view.TapCrosshairView;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.network.AutoClickExecutor;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fragment for testing/previewing auto-click action sequences.
 * Loads the URL, executes all enabled actions with visual feedback,
 * then shows completion status.
 *
 * The WebView is fullscreen to match the rendering used during actual
 * content fetching, ensuring tap coordinates are accurate.
 */
public class ActionTesterFragment extends Fragment {

    private static final String TAG = "ActionTester";
    private static final String ARG_URL = "url";
    private static final String ARG_ACTIONS_JSON = "actions_json";
    private static final String ARG_EXECUTE_ACTIONS = "execute_actions";
    private static final int STATUS_CARD_FADE_DELAY_MS = 5000;
    private static final int STATUS_CARD_FADE_DURATION_MS = 500;

    private WebView webView;
    private TapCrosshairView tapCrosshair;
    private LinearLayout loadingOverlay;
    private MaterialCardView statusCard;
    private ProgressBar progressBar;
    private TextView textStepProgress;
    private TextView textActionLabel;
    private TextView textCountdown;
    private LinearLayout floatingButtonBar;
    private MaterialButton buttonClose;
    private MaterialButton buttonRestart;

    private String targetUrl;
    private String actionsJson;
    private boolean executeActions = true;
    private List<AutoClickAction> enabledActions;
    private AutoClickExecutor executor;
    private Handler mainHandler;
    private PreferencesManager preferencesManager;

    private int currentActionIndex = 0;
    private boolean pageLoaded = false;
    private boolean hasExecutedActions = false;
    private CountDownTimer waitCountdownTimer;
    private CountDownTimer actionWaitCountdownTimer;
    private CountDownTimer postActionCountdownTimer;
    private Runnable fadeOutRunnable;
    private ObjectAnimator fadeOutAnimator;

    public ActionTesterFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetUrl = getArguments().getString(ARG_URL, "");
            actionsJson = getArguments().getString(ARG_ACTIONS_JSON, "");
            executeActions = getArguments().getBoolean(ARG_EXECUTE_ACTIONS, true);
        }
        executor = new AutoClickExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        preferencesManager = new PreferencesManager(requireContext());
    }

    /**
     * Get the page load delay in milliseconds from settings.
     */
    private int getPageLoadDelayMs() {
        return preferencesManager.getPageLoadDelay() * 1000;
    }

    /**
     * Get the post-action delay in milliseconds from settings.
     */
    private int getPostActionDelayMs() {
        return preferencesManager.getPostActionDelay() * 1000;
    }

    /**
     * Start a countdown timer that updates the UI every 100ms with format "Waiting: X.X s".
     * After the countdown completes, action execution begins.
     *
     * @param durationMs The total duration in milliseconds
     */
    private void startWaitCountdown(int durationMs) {
        // Cancel any existing timer
        cancelWaitCountdown();

        // Update UI every 100ms for smooth decisecond display
        waitCountdownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded() || textStepProgress == null) return;

                // Calculate seconds and deciseconds (tenths of seconds)
                int seconds = (int) (millisUntilFinished / 1000);
                int deciseconds = (int) ((millisUntilFinished % 1000) / 100);

                // Update UI with countdown format: "Waiting: X.X s"
                String countdownText = getString(R.string.waiting_countdown, seconds, deciseconds);
                textStepProgress.setText(countdownText);
            }

            @Override
            public void onFinish() {
                if (!isAdded() || webView == null) return;

                Logger.d(TAG, "Countdown complete, starting action execution");
                startActionExecution();
            }
        };

        waitCountdownTimer.start();
    }

    /**
     * Cancel the wait countdown timer if running.
     */
    private void cancelWaitCountdown() {
        if (waitCountdownTimer != null) {
            waitCountdownTimer.cancel();
            waitCountdownTimer = null;
        }
    }

    /**
     * Schedule the status card to fade out after 5 seconds.
     */
    private void scheduleStatusCardFadeOut() {
        cancelStatusCardFadeOut();

        fadeOutRunnable = () -> {
            if (!isAdded() || statusCard == null) return;

            fadeOutAnimator = ObjectAnimator.ofFloat(statusCard, "alpha", 1f, 0f);
            fadeOutAnimator.setDuration(STATUS_CARD_FADE_DURATION_MS);
            fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (statusCard != null) {
                        statusCard.setVisibility(View.GONE);
                        statusCard.setAlpha(1f); // Reset alpha for next use
                    }
                }
            });
            fadeOutAnimator.start();
        };

        mainHandler.postDelayed(fadeOutRunnable, STATUS_CARD_FADE_DELAY_MS);
    }

    /**
     * Cancel any pending status card fade out.
     */
    private void cancelStatusCardFadeOut() {
        if (fadeOutRunnable != null) {
            mainHandler.removeCallbacks(fadeOutRunnable);
            fadeOutRunnable = null;
        }
        if (fadeOutAnimator != null) {
            fadeOutAnimator.cancel();
            fadeOutAnimator = null;
        }
        // Reset alpha in case animation was interrupted
        if (statusCard != null) {
            statusCard.setAlpha(1f);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_action_tester, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        parseActions();
        setupWebView();
        setupListeners();
        loadTargetPage();

        // Set title based on mode (Test Actions vs WebView Rendering)
        if (getActivity() instanceof androidx.appcompat.app.AppCompatActivity) {
            androidx.appcompat.app.ActionBar actionBar =
                    ((androidx.appcompat.app.AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                if (executeActions) {
                    actionBar.setTitle(R.string.test_actions);
                } else {
                    actionBar.setTitle(R.string.webview_rendering);
                }
            }
        }
    }

    private void initializeViews(@NonNull View view) {
        webView = view.findViewById(R.id.webView);
        tapCrosshair = view.findViewById(R.id.tapCrosshair);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        statusCard = view.findViewById(R.id.statusCard);
        progressBar = view.findViewById(R.id.progressBar);
        textStepProgress = view.findViewById(R.id.textStepProgress);
        textActionLabel = view.findViewById(R.id.textActionLabel);
        textCountdown = view.findViewById(R.id.textCountdown);
        floatingButtonBar = view.findViewById(R.id.floatingButtonBar);
        buttonClose = view.findViewById(R.id.buttonClose);
        buttonRestart = view.findViewById(R.id.buttonRestart);

        // Initial state
        textStepProgress.setText(R.string.loading);
        textActionLabel.setText("");
        progressBar.setProgress(0);
        buttonRestart.setVisibility(View.GONE);
        statusCard.setVisibility(View.GONE);
        floatingButtonBar.setVisibility(View.GONE);
    }

    private void parseActions() {
        List<AutoClickAction> allActions = AutoClickAction.fromJsonString(actionsJson);

        // Filter to enabled actions only and sort by order
        enabledActions = new ArrayList<>();
        for (AutoClickAction action : allActions) {
            if (action.isEnabled()) {
                enabledActions.add(action);
            }
        }
        enabledActions.sort(Comparator.comparingInt(AutoClickAction::getOrder));

        Logger.d(TAG, "Parsed " + enabledActions.size() + " enabled actions from JSON");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();

        // Match WebViewContentFetcher configuration exactly
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Match user agent from WebViewContentFetcher
        webSettings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36 SiteWatcher/1.0");

        // Fix for black screen / flickering issues
        webView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sw_background));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Set WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                                    @NonNull WebResourceRequest request) {
                // Allow navigation during action execution
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false;
                Logger.d(TAG, "Page started: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                Logger.d(TAG, "Page finished: " + url + ", executeActions=" + executeActions + ", hasExecutedActions=" + hasExecutedActions);

                // Hide loading overlay - page should now be visible
                loadingOverlay.setVisibility(View.GONE);
                Logger.d(TAG, "Loading overlay hidden, page should be visible now");

                // Start executing actions after page loads (only if executeActions is true)
                // Use hasExecutedActions flag to prevent multiple executions from multiple onPageFinished calls
                if (executeActions && !hasExecutedActions) {
                    hasExecutedActions = true;
                    int pageLoadDelay = getPageLoadDelayMs();
                    // Delay action execution to allow JS-loaded content (cookie dialogs) to appear
                    Logger.d(TAG, "Waiting " + pageLoadDelay + "ms for JS content to load...");
                    statusCard.setVisibility(View.VISIBLE);
                    startWaitCountdown(pageLoadDelay);
                } else if (!executeActions) {
                    showViewOnlyState();
                }
            }
        });

        // Set WebChromeClient for progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                // Don't update progress bar during page load - we use it for action progress
            }
        });
    }

    private void setupListeners() {
        buttonClose.setOnClickListener(v -> navigateBack());
        buttonRestart.setOnClickListener(v -> restartTest());
    }

    private void loadTargetPage() {
        if (targetUrl == null || targetUrl.isEmpty()) {
            Logger.e(TAG, "No URL provided");
            showNoActionsState();
            return;
        }

        Logger.d(TAG, "Loading URL: " + targetUrl);
        loadingOverlay.setVisibility(View.VISIBLE);

        // Clear all WebView data for fresh start (ensures cookie consent dialogs appear)
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        if (webView.getContext() != null) {
            WebStorage.getInstance().deleteAllData();
        }

        webView.loadUrl(targetUrl);
    }

    private void startActionExecution() {
        if (enabledActions == null || enabledActions.isEmpty()) {
            showNoActionsState();
            return;
        }

        currentActionIndex = 0;
        updateProgressUI(0, enabledActions.size());
        statusCard.setVisibility(View.VISIBLE);

        // Execute actions using AutoClickExecutor with custom callback
        // AutoClickExecutor will automatically enter fullscreen for TAP_COORDINATES actions
        executor.executeActions(webView, enabledActions, new AutoClickExecutor.ExecutionCallback() {
            @Override
            public void onComplete(int successCount, int failCount) {
                mainHandler.post(() -> {
                    // Exit fullscreen mode if executor entered it
                    executor.exitFullscreenMode();

                    // Check if post-action delay is configured
                    int postActionDelayMs = getPostActionDelayMs();
                    if (postActionDelayMs > 0) {
                        // Start post-action countdown before showing completion
                        startPostActionCountdown(postActionDelayMs, successCount, failCount);
                    } else {
                        showCompletionState(successCount, failCount);
                    }
                });
            }

            @Override
            public void onActionResult(String actionId, boolean success) {
                mainHandler.post(() -> {
                    // Hide countdown when action completes
                    cancelActionWaitCountdown();

                    currentActionIndex++;
                    if (currentActionIndex < enabledActions.size()) {
                        updateProgressUI(currentActionIndex, enabledActions.size());
                    }
                });
            }

            @Override
            public void onBeforeTap(float screenX, float screenY) {
                mainHandler.post(() -> showTapCrosshair(screenX, screenY));
            }

            @Override
            public void onWaitStart(String actionId, int durationSeconds) {
                mainHandler.post(() -> startActionWaitCountdown(durationSeconds));
            }
        });
    }

    /**
     * Show the crosshair at the tap location.
     * The coordinates are absolute screen coordinates, so we need to convert
     * them to coordinates relative to our view.
     */
    private void showTapCrosshair(float screenX, float screenY) {
        if (tapCrosshair == null || !isAdded()) return;

        // Convert screen coordinates to coordinates relative to our crosshair view
        int[] viewLocation = new int[2];
        tapCrosshair.getLocationOnScreen(viewLocation);

        float localX = screenX - viewLocation[0];
        float localY = screenY - viewLocation[1];

        Logger.d(TAG, "Showing crosshair at screen(" + screenX + ", " + screenY +
                ") -> local(" + localX + ", " + localY + ")");

        // Show crosshair for 1.2 seconds (the tap happens after 400ms delay)
        tapCrosshair.showAt(localX, localY);
    }

    private void updateProgressUI(int currentIndex, int totalActions) {
        if (!isAdded() || textStepProgress == null) return;

        // Update step progress text: "Step X of Y"
        String stepText = getString(R.string.step_progress, currentIndex + 1, totalActions);
        textStepProgress.setText(stepText);

        // Update action label
        if (currentIndex < enabledActions.size()) {
            AutoClickAction currentAction = enabledActions.get(currentIndex);
            String label = currentAction.getLabel();
            if (label == null || label.isEmpty()) {
                label = currentAction.getSummary();
            }
            textActionLabel.setText(label);
            textActionLabel.setVisibility(View.VISIBLE);
        }

        // Update progress bar
        int progress = (int) ((currentIndex / (float) totalActions) * 100);
        progressBar.setProgress(progress);
    }

    private void showCompletionState(int successCount, int failCount) {
        if (!isAdded() || textStepProgress == null) return;

        // Hide countdown if showing
        cancelActionWaitCountdown();

        textStepProgress.setText(R.string.test_complete);
        textActionLabel.setVisibility(View.GONE);
        progressBar.setProgress(100);

        // Show buttons after completion
        floatingButtonBar.setVisibility(View.VISIBLE);
        buttonRestart.setVisibility(View.VISIBLE);

        // Schedule status card to fade out after 5 seconds
        scheduleStatusCardFadeOut();

        Logger.d(TAG, "Action execution complete: " + successCount + " success, " + failCount + " failed");
    }

    /**
     * Start a countdown timer for a WAIT action.
     * Shows the countdown in the UI updating every 100ms with deciseconds (matching page load format).
     *
     * @param durationSeconds Duration of the wait in seconds
     */
    private void startActionWaitCountdown(int durationSeconds) {
        if (!isAdded() || textCountdown == null) return;

        // Cancel any existing countdown
        cancelActionWaitCountdown();

        // Show countdown view with initial value
        textCountdown.setVisibility(View.VISIBLE);
        textCountdown.setText(getString(R.string.sleep_countdown, durationSeconds, 0));

        Logger.d(TAG, "Starting action wait countdown: " + durationSeconds + " seconds");

        // Create countdown timer that updates every 100ms for smooth decisecond display
        actionWaitCountdownTimer = new CountDownTimer(durationSeconds * 1000L, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded() || textCountdown == null) return;

                // Calculate seconds and deciseconds (tenths of seconds)
                int seconds = (int) (millisUntilFinished / 1000);
                int deciseconds = (int) ((millisUntilFinished % 1000) / 100);

                textCountdown.setText(getString(R.string.sleep_countdown, seconds, deciseconds));
            }

            @Override
            public void onFinish() {
                // Countdown complete - hide the view
                if (isAdded() && textCountdown != null) {
                    textCountdown.setVisibility(View.GONE);
                }
            }
        };

        actionWaitCountdownTimer.start();
    }

    /**
     * Cancel the action wait countdown timer if running.
     */
    private void cancelActionWaitCountdown() {
        if (actionWaitCountdownTimer != null) {
            actionWaitCountdownTimer.cancel();
            actionWaitCountdownTimer = null;
        }
        if (textCountdown != null) {
            textCountdown.setVisibility(View.GONE);
        }
    }

    /**
     * Start a countdown timer for the post-action delay (wait after last action).
     * Updates the UI every 100ms with format similar to page load countdown.
     *
     * @param durationMs   The total duration in milliseconds
     * @param successCount Number of successful actions (for completion state)
     * @param failCount    Number of failed actions (for completion state)
     */
    private void startPostActionCountdown(int durationMs, int successCount, int failCount) {
        if (!isAdded() || textStepProgress == null) return;

        // Cancel any existing timer
        cancelPostActionCountdown();

        Logger.d(TAG, "Starting post-action countdown: " + (durationMs / 1000) + " seconds");

        // Update UI every 100ms for smooth decisecond display
        postActionCountdownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded() || textStepProgress == null) return;

                // Calculate seconds and deciseconds (tenths of seconds)
                int seconds = (int) (millisUntilFinished / 1000);
                int deciseconds = (int) ((millisUntilFinished % 1000) / 100);

                // Update UI with countdown format
                String countdownText = getString(R.string.post_action_countdown, seconds, deciseconds);
                textStepProgress.setText(countdownText);
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;

                Logger.d(TAG, "Post-action countdown complete");
                showCompletionState(successCount, failCount);
            }
        };

        postActionCountdownTimer.start();
    }

    /**
     * Cancel the post-action countdown timer if running.
     */
    private void cancelPostActionCountdown() {
        if (postActionCountdownTimer != null) {
            postActionCountdownTimer.cancel();
            postActionCountdownTimer = null;
        }
    }

    private void showNoActionsState() {
        if (!isAdded() || textStepProgress == null) return;

        textStepProgress.setText(R.string.no_actions_to_test);
        textActionLabel.setVisibility(View.GONE);
        progressBar.setProgress(0);
        loadingOverlay.setVisibility(View.GONE);
        statusCard.setVisibility(View.VISIBLE);
        floatingButtonBar.setVisibility(View.VISIBLE);

        Logger.d(TAG, "No actions to test");
    }

    private void showViewOnlyState() {
        if (!isAdded() || textStepProgress == null) return;

        // Hide the progress UI when just viewing
        textStepProgress.setText(R.string.page_loaded);
        textActionLabel.setVisibility(View.GONE);
        progressBar.setProgress(100);
        statusCard.setVisibility(View.VISIBLE);
        floatingButtonBar.setVisibility(View.VISIBLE);

        // Schedule status card to fade out after 5 seconds
        scheduleStatusCardFadeOut();

        Logger.d(TAG, "View only mode - page loaded");
    }

    private void restartTest() {
        Logger.d(TAG, "Restarting test - clearing WebView data");

        // Cancel any running countdown and fade out
        cancelWaitCountdown();
        cancelStatusCardFadeOut();

        // Reset execution state
        hasExecutedActions = false;
        currentActionIndex = 0;

        // Hide UI elements
        floatingButtonBar.setVisibility(View.GONE);
        buttonRestart.setVisibility(View.GONE);
        statusCard.setVisibility(View.GONE);

        // Reset progress UI
        textStepProgress.setText(R.string.loading);
        textActionLabel.setText("");
        progressBar.setProgress(0);

        // Clear all WebView data for fresh start
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        if (webView.getContext() != null) {
            WebStorage.getInstance().deleteAllData();
        }

        // Show loading overlay and reload page
        loadingOverlay.setVisibility(View.VISIBLE);
        webView.loadUrl(targetUrl);
    }

    private void navigateBack() {
        // Exit fullscreen mode if still active
        executor.exitFullscreenMode();

        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onDestroyView() {
        // Cancel countdown timers and fade out if still running
        cancelWaitCountdown();
        cancelActionWaitCountdown();
        cancelPostActionCountdown();
        cancelStatusCardFadeOut();

        // Exit fullscreen mode if still active
        if (executor != null) {
            executor.exitFullscreenMode();
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
