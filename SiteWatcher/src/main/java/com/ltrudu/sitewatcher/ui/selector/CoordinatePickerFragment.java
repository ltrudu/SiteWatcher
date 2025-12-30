package com.ltrudu.sitewatcher.ui.selector;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.network.AutoClickExecutor;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;

/**
 * Fragment for selecting tap coordinates on a WebView.
 * User taps on the page to mark where the tap action should occur.
 * Coordinates are returned as percentages (0.0-1.0) of the WebView dimensions.
 *
 * IMPORTANT: The WebView is rendered in fullscreen immersive mode to match
 * exactly how WebViewContentFetcher renders pages during actual fetching.
 * This ensures tap coordinates are accurate.
 *
 * If previous actions are provided, they will be executed first (with visual feedback),
 * and a delay is applied after the last action to allow the page to settle
 * before enabling coordinate selection.
 */
public class CoordinatePickerFragment extends Fragment {

    private static final String TAG = "CoordinatePicker";
    private static final String ARG_URL = "url";
    private static final String ARG_ACTIONS_JSON = "actions_json";
    private static final String FRAGMENT_RESULT_KEY = "coordinatePickerResult";

    /**
     * Fragment states.
     */
    private enum State {
        LOADING,    // Page is loading
        EXECUTING,  // Running previous actions
        SELECTING   // Coordinate selection mode (fullscreen)
    }

    private WebView webView;
    private FrameLayout loadingOverlay;
    private ProgressBar progressBar;
    private TextView textStatus;
    private TextView textCurrentAction;
    private MaterialCardView instructionBanner;
    private MaterialCardView coordinatesCard;
    private TextView textCoordinates;
    private View crosshairVertical;
    private View crosshairHorizontal;
    private LinearLayout floatingButtonBar;
    private MaterialButton buttonCancel;
    private MaterialButton buttonConfirm;

    private String targetUrl;
    private String actionsJson;
    private List<AutoClickAction> actions;
    private State currentState = State.LOADING;
    private boolean pageLoaded = false;
    private float selectedX = -1f;
    private float selectedY = -1f;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AutoClickExecutor autoClickExecutor;
    private PreferencesManager preferencesManager;

    // Store original system UI visibility to restore later
    private boolean wasImmersive = false;

    public static CoordinatePickerFragment newInstance(@NonNull String url) {
        CoordinatePickerFragment fragment = new CoordinatePickerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    public static CoordinatePickerFragment newInstance(@NonNull String url, @Nullable String actionsJson) {
        CoordinatePickerFragment fragment = new CoordinatePickerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_ACTIONS_JSON, actionsJson);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetUrl = getArguments().getString(ARG_URL, "");
            actionsJson = getArguments().getString(ARG_ACTIONS_JSON, null);
        }

        // Parse actions from JSON
        actions = AutoClickAction.fromJsonString(actionsJson);
        autoClickExecutor = new AutoClickExecutor();
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_coordinate_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupWebView();
        setupListeners();
        loadPage();
    }

    private void initializeViews(@NonNull View view) {
        webView = view.findViewById(R.id.webView);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        progressBar = view.findViewById(R.id.progressBar);
        textStatus = view.findViewById(R.id.textStatus);
        textCurrentAction = view.findViewById(R.id.textCurrentAction);
        instructionBanner = view.findViewById(R.id.instructionBanner);
        coordinatesCard = view.findViewById(R.id.coordinatesCard);
        textCoordinates = view.findViewById(R.id.textCoordinates);
        crosshairVertical = view.findViewById(R.id.crosshairVertical);
        crosshairHorizontal = view.findViewById(R.id.crosshairHorizontal);
        floatingButtonBar = view.findViewById(R.id.floatingButtonBar);
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonConfirm = view.findViewById(R.id.buttonConfirm);

        // Initial state
        updateStateUI(State.LOADING);
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Match WebViewContentFetcher configuration exactly
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Match user agent from WebViewContentFetcher
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36 SiteWatcher/1.0");

        // Fix rendering issues
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
                // Allow navigation during action execution (clicks may navigate)
                if (currentState == State.EXECUTING) {
                    return false;
                }
                // Prevent navigation in selection mode
                return currentState == State.SELECTING;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoaded = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                Logger.d(TAG, "Page loaded: " + url);

                // Handle page load completion based on state
                if (currentState == State.LOADING) {
                    onPageLoadComplete();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (currentState == State.LOADING && progressBar != null) {
                    progressBar.setProgress(newProgress);
                }
            }
        });

        // Capture taps on WebView - only in SELECTING state
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && pageLoaded && currentState == State.SELECTING) {
                handleTap(event.getX(), event.getY());
            }
            // Allow WebView to handle the event during EXECUTING state
            return currentState != State.EXECUTING && currentState != State.LOADING;
        });
    }

    private void setupListeners() {
        buttonCancel.setOnClickListener(v -> navigateBack());

        buttonConfirm.setOnClickListener(v -> {
            if (selectedX >= 0 && selectedY >= 0) {
                returnResult();
            }
        });
    }

    private void loadPage() {
        if (targetUrl == null || targetUrl.isEmpty()) {
            Logger.e(TAG, "No URL provided");
            navigateBack();
            return;
        }

        Logger.d(TAG, "Loading URL: " + targetUrl);
        updateStateUI(State.LOADING);

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

    /**
     * Called when the initial page load is complete.
     */
    private void onPageLoadComplete() {
        int pageLoadDelay = getPageLoadDelayMs();
        // Wait for JS-loaded content (cookie dialogs) to appear before proceeding
        Logger.d(TAG, "Waiting " + pageLoadDelay + "ms for JS content to load...");
        textStatus.setText(R.string.waiting_for_content);

        mainHandler.postDelayed(() -> {
            if (!isAdded() || webView == null) return;

            if (hasActionsToExecute()) {
                // Start executing actions
                startActionExecution();
            } else {
                // No actions, go directly to selection mode
                enableSelectionMode();
            }
        }, pageLoadDelay);
    }

    private boolean hasActionsToExecute() {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        // Check if there are any enabled actions
        for (AutoClickAction action : actions) {
            if (action.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Start executing the auto-click actions.
     */
    private void startActionExecution() {
        updateStateUI(State.EXECUTING);

        // Count enabled actions for progress display
        int enabledCount = 0;
        for (AutoClickAction action : actions) {
            if (action.isEnabled()) {
                enabledCount++;
            }
        }

        final int totalActions = enabledCount;
        final int[] currentAction = {0};

        Logger.d(TAG, "Starting execution of " + totalActions + " actions");

        autoClickExecutor.executeActions(webView, actions, new AutoClickExecutor.ExecutionCallback() {
            @Override
            public void onActionResult(String actionId, boolean success) {
                mainHandler.post(() -> {
                    currentAction[0]++;
                    updateExecutionProgress(currentAction[0], totalActions, getActionLabel(actionId));
                });
            }

            @Override
            public void onComplete(int successCount, int failCount) {
                mainHandler.post(() -> {
                    Logger.d(TAG, "Action execution complete: " + successCount + " success, " + failCount + " failed");

                    // Wait for page to settle before enabling selection
                    textStatus.setText(R.string.waiting_for_page_settle);
                    textCurrentAction.setVisibility(View.GONE);

                    mainHandler.postDelayed(() -> enableSelectionMode(), getPostActionDelayMs());
                });
            }
        });

        // Show initial progress
        if (totalActions > 0) {
            AutoClickAction firstAction = getFirstEnabledAction();
            if (firstAction != null) {
                updateExecutionProgress(0, totalActions, firstAction.getLabel());
            }
        }
    }

    @Nullable
    private AutoClickAction getFirstEnabledAction() {
        if (actions == null) return null;
        for (AutoClickAction action : actions) {
            if (action.isEnabled()) {
                return action;
            }
        }
        return null;
    }

    @NonNull
    private String getActionLabel(String actionId) {
        if (actions == null) return "";
        for (AutoClickAction action : actions) {
            if (action.getId().equals(actionId)) {
                return action.getLabel();
            }
        }
        return "";
    }

    /**
     * Update the execution progress UI.
     */
    private void updateExecutionProgress(int current, int total, String actionLabel) {
        String progressText = getString(R.string.interactive_picker_progress, current + 1, total);
        textStatus.setText(progressText);
        textCurrentAction.setText(actionLabel);
        textCurrentAction.setVisibility(View.VISIBLE);

        int progressPercent = total > 0 ? (current * 100) / total : 0;
        progressBar.setProgress(progressPercent);
    }

    /**
     * Enable coordinate selection mode with fullscreen immersive UI.
     */
    private void enableSelectionMode() {
        // Enter fullscreen immersive mode
        enterImmersiveMode();

        updateStateUI(State.SELECTING);
        Logger.d(TAG, "Selection mode enabled - tap to select coordinates (fullscreen)");
    }

    /**
     * Enter fullscreen immersive mode to hide system bars.
     * This makes the WebView render at the same size as during fetching.
     */
    private void enterImmersiveMode() {
        if (getActivity() == null || getActivity().getWindow() == null) return;

        wasImmersive = true;

        // Use WindowInsetsControllerCompat for better compatibility
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getActivity().getWindow(),
                getActivity().getWindow().getDecorView()
        );

        if (controller != null) {
            // Hide both status bar and navigation bar
            controller.hide(WindowInsetsCompat.Type.systemBars());
            // Allow showing bars with swipe gesture
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

        // Make the layout extend under system bars
        WindowCompat.setDecorFitsSystemWindows(getActivity().getWindow(), false);

        Logger.d(TAG, "Entered immersive mode");
    }

    /**
     * Exit fullscreen immersive mode to restore system bars.
     */
    private void exitImmersiveMode() {
        if (getActivity() == null || getActivity().getWindow() == null || !wasImmersive) return;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getActivity().getWindow(),
                getActivity().getWindow().getDecorView()
        );

        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        WindowCompat.setDecorFitsSystemWindows(getActivity().getWindow(), true);
        wasImmersive = false;

        Logger.d(TAG, "Exited immersive mode");
    }

    /**
     * Update UI based on current state.
     */
    private void updateStateUI(State state) {
        currentState = state;

        switch (state) {
            case LOADING:
                loadingOverlay.setVisibility(View.VISIBLE);
                textStatus.setText(R.string.loading_page);
                textCurrentAction.setVisibility(View.GONE);
                instructionBanner.setVisibility(View.GONE);
                coordinatesCard.setVisibility(View.GONE);
                floatingButtonBar.setVisibility(View.GONE);
                progressBar.setProgress(0);
                buttonConfirm.setEnabled(false);
                crosshairVertical.setVisibility(View.GONE);
                crosshairHorizontal.setVisibility(View.GONE);
                break;

            case EXECUTING:
                loadingOverlay.setVisibility(View.VISIBLE);
                textCurrentAction.setVisibility(View.VISIBLE);
                instructionBanner.setVisibility(View.GONE);
                coordinatesCard.setVisibility(View.GONE);
                floatingButtonBar.setVisibility(View.GONE);
                buttonConfirm.setEnabled(false);
                crosshairVertical.setVisibility(View.GONE);
                crosshairHorizontal.setVisibility(View.GONE);
                break;

            case SELECTING:
                // Hide loading overlay, show WebView fullscreen
                loadingOverlay.setVisibility(View.GONE);
                // Show floating instruction banner
                instructionBanner.setVisibility(View.VISIBLE);
                coordinatesCard.setVisibility(View.GONE);
                // Show floating buttons
                floatingButtonBar.setVisibility(View.VISIBLE);
                buttonConfirm.setEnabled(selectedX >= 0 && selectedY >= 0);
                break;
        }
    }

    private void handleTap(float x, float y) {
        int webViewWidth = webView.getWidth();
        int webViewHeight = webView.getHeight();

        if (webViewWidth <= 0 || webViewHeight <= 0) {
            return;
        }

        // Calculate percentage coordinates
        selectedX = x / webViewWidth;
        selectedY = y / webViewHeight;

        // Clamp to valid range
        selectedX = Math.max(0f, Math.min(1f, selectedX));
        selectedY = Math.max(0f, Math.min(1f, selectedY));

        Logger.d(TAG, "Selected coordinates: " + selectedX + ", " + selectedY +
                " (WebView size: " + webViewWidth + "x" + webViewHeight + ")");

        // Update UI
        updateCrosshair(x, y);
        updateCoordinatesText();
        buttonConfirm.setEnabled(true);
    }

    private void updateCrosshair(float x, float y) {
        crosshairVertical.setVisibility(View.VISIBLE);
        crosshairHorizontal.setVisibility(View.VISIBLE);

        // Position vertical line at tap X
        crosshairVertical.setX(x - 1);

        // Position horizontal line at tap Y
        crosshairHorizontal.setY(y - 1);
    }

    private void updateCoordinatesText() {
        int xPercent = Math.round(selectedX * 100);
        int yPercent = Math.round(selectedY * 100);
        String coordText = getString(R.string.coordinates_selected, xPercent, yPercent);
        textCoordinates.setText(coordText);

        // Hide instruction, show coordinates
        instructionBanner.setVisibility(View.GONE);
        coordinatesCard.setVisibility(View.VISIBLE);
    }

    private void returnResult() {
        Bundle result = new Bundle();
        result.putFloat("tapX", selectedX);
        result.putFloat("tapY", selectedY);
        getParentFragmentManager().setFragmentResult(FRAGMENT_RESULT_KEY, result);
        Logger.d(TAG, "Returning coordinates: " + selectedX + ", " + selectedY);
        navigateBack();
    }

    private void navigateBack() {
        // Exit immersive mode before navigating back
        exitImmersiveMode();

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
        // Always exit immersive mode when view is destroyed
        exitImmersiveMode();

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
