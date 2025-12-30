package com.ltrudu.sitewatcher.ui.selector;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.network.AutoClickExecutor;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;

/**
 * Fragment for interactively picking elements on a webpage.
 * Loads a URL, executes previous auto-click actions with visual feedback,
 * then allows the user to select an element to generate a CSS selector.
 *
 * The WebView is fullscreen to match the rendering used during actual
 * content fetching, ensuring tap coordinates are accurate.
 */
public class InteractivePickerFragment extends Fragment {

    private static final String TAG = "InteractivePicker";
    private static final String ARG_URL = "url";
    private static final String ARG_ACTIONS_JSON = "actions_json";
    private static final String FRAGMENT_RESULT_KEY = "interactivePickerResult";
    private static final String SELECTOR_KEY = "selector";

    /**
     * Fragment states.
     */
    private enum State {
        LOADING,    // Page is loading
        EXECUTING,  // Running previous actions
        SELECTING   // Element selection mode
    }

    // UI components
    private LinearLayout loadingOverlay;
    private MaterialCardView statusCard;
    private TextView textProgress;
    private TextView textCurrentAction;
    private ProgressBar progressBar;
    private MaterialCardView instructionBanner;
    private LinearLayout floatingButtonBar;
    private WebView webView;
    private MaterialButton buttonCancel;
    private MaterialButton buttonConfirm;

    // State
    private State currentState = State.LOADING;
    private String targetUrl;
    private String actionsJson;
    private List<AutoClickAction> actions;
    private String selectedSelector;
    private boolean pageLoaded = false;

    // Utilities
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AutoClickExecutor autoClickExecutor;
    private PreferencesManager preferencesManager;

    public InteractivePickerFragment() {
        // Required empty public constructor
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
        return inflater.inflate(R.layout.fragment_interactive_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupWebView();
        setupListeners();
        loadTargetPage();
    }

    private void initializeViews(@NonNull View view) {
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        statusCard = view.findViewById(R.id.statusCard);
        textProgress = view.findViewById(R.id.textProgress);
        textCurrentAction = view.findViewById(R.id.textCurrentAction);
        progressBar = view.findViewById(R.id.progressBar);
        instructionBanner = view.findViewById(R.id.instructionBanner);
        floatingButtonBar = view.findViewById(R.id.floatingButtonBar);
        webView = view.findViewById(R.id.webView);
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonConfirm = view.findViewById(R.id.buttonConfirm);

        // Initial state: loading
        updateStateUI(State.LOADING);
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
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Add JavaScript interface for communication
        webView.addJavascriptInterface(new PickerJsInterface(), "PickerBridge");

        // Set WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                                    @NonNull WebResourceRequest request) {
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
                Logger.d(TAG, "Page started: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                Logger.d(TAG, "Page finished: " + url);

                // Hide loading overlay
                loadingOverlay.setVisibility(View.GONE);

                // Handle page load completion based on state
                if (currentState == State.LOADING) {
                    onPageLoadComplete();
                }
            }
        });

        // Set WebChromeClient for progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (currentState == State.LOADING && progressBar != null) {
                    progressBar.setProgress(newProgress);
                }
            }
        });
    }

    private void setupListeners() {
        buttonCancel.setOnClickListener(v -> navigateBack());
        buttonConfirm.setOnClickListener(v -> confirmSelection());
    }

    private void loadTargetPage() {
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
        textProgress.setText(R.string.waiting_for_content);
        statusCard.setVisibility(View.VISIBLE);

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
        return actions != null && !actions.isEmpty();
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

        // AutoClickExecutor will automatically enter fullscreen for TAP_COORDINATES actions
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

                    // Exit fullscreen mode if executor entered it
                    autoClickExecutor.exitFullscreenMode();

                    // Wait for page to settle before enabling selection
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
        textProgress.setText(progressText);
        textCurrentAction.setText(actionLabel);
        textCurrentAction.setVisibility(View.VISIBLE);

        int progressPercent = total > 0 ? (current * 100) / total : 0;
        progressBar.setProgress(progressPercent);
    }

    /**
     * Enable element selection mode.
     */
    private void enableSelectionMode() {
        updateStateUI(State.SELECTING);
        injectSelectorScript();
        Logger.d(TAG, "Selection mode enabled");
    }

    /**
     * Update UI based on current state.
     */
    private void updateStateUI(State state) {
        currentState = state;

        switch (state) {
            case LOADING:
                loadingOverlay.setVisibility(View.VISIBLE);
                statusCard.setVisibility(View.GONE);
                instructionBanner.setVisibility(View.GONE);
                floatingButtonBar.setVisibility(View.GONE);
                textProgress.setText(R.string.loading_page);
                textCurrentAction.setVisibility(View.GONE);
                progressBar.setProgress(0);
                buttonConfirm.setEnabled(false);
                break;

            case EXECUTING:
                loadingOverlay.setVisibility(View.GONE);
                statusCard.setVisibility(View.VISIBLE);
                instructionBanner.setVisibility(View.GONE);
                floatingButtonBar.setVisibility(View.GONE);
                textCurrentAction.setVisibility(View.VISIBLE);
                buttonConfirm.setEnabled(false);
                break;

            case SELECTING:
                loadingOverlay.setVisibility(View.GONE);
                statusCard.setVisibility(View.GONE);
                instructionBanner.setVisibility(View.VISIBLE);
                floatingButtonBar.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                buttonConfirm.setEnabled(selectedSelector != null && !selectedSelector.isEmpty());
                break;
        }
    }

    /**
     * Inject JavaScript to handle single element selection.
     */
    private void injectSelectorScript() {
        String script = getSingleSelectionScript();
        webView.evaluateJavascript(script, null);
        Logger.d(TAG, "Selector script injected");
    }

    /**
     * Returns the JavaScript code for single element selection.
     */
    @NonNull
    private String getSingleSelectionScript() {
        return "(function() {" +
                // Style for highlighting selected and hovered elements
                "var style = document.createElement('style');" +
                "style.id = 'sitewatcher-picker-styles';" +
                "style.textContent = '" +
                ".sitewatcher-picker-selected { " +
                "  outline: 3px solid #4CAF50 !important; " +
                "  outline-offset: 2px !important; " +
                "  background-color: rgba(76, 175, 80, 0.2) !important; " +
                "} " +
                ".sitewatcher-picker-hover { " +
                "  outline: 2px dashed #2196F3 !important; " +
                "  outline-offset: 1px !important; " +
                "  cursor: pointer !important; " +
                "}';" +
                "document.head.appendChild(style);" +

                // Track selected element
                "window.siteWatcherPickerSelected = null;" +
                "window.siteWatcherPickerSelector = null;" +

                // Generate unique CSS selector for an element
                "function getSelector(el) {" +
                "  if (el.id) return '#' + CSS.escape(el.id);" +
                "  if (el.tagName === 'BODY') return 'body';" +
                "  if (el.tagName === 'HTML') return 'html';" +
                "  var selector = el.tagName.toLowerCase();" +

                // Use class names if available
                "  if (el.className && typeof el.className === 'string') {" +
                "    var classes = el.className.trim().split(/\\s+/).filter(function(c) {" +
                "      return c && !c.startsWith('sitewatcher-');" +
                "    });" +
                "    if (classes.length > 0) {" +
                "      selector += '.' + classes.map(function(c) { return CSS.escape(c); }).join('.');" +
                "    }" +
                "  }" +

                // Check if selector is unique
                "  var matches = document.querySelectorAll(selector);" +
                "  if (matches.length === 1) return selector;" +

                // Add nth-child for uniqueness
                "  var parent = el.parentElement;" +
                "  if (parent) {" +
                "    var siblings = Array.from(parent.children);" +
                "    var index = siblings.indexOf(el) + 1;" +
                "    selector += ':nth-child(' + index + ')';" +
                "  }" +

                // Build full path if still not unique
                "  matches = document.querySelectorAll(selector);" +
                "  if (matches.length > 1 && parent && parent.tagName !== 'HTML') {" +
                "    return getSelector(parent) + ' > ' + selector;" +
                "  }" +

                "  return selector;" +
                "}" +

                // Clear previous selection
                "function clearSelection() {" +
                "  if (window.siteWatcherPickerSelected) {" +
                "    window.siteWatcherPickerSelected.classList.remove('sitewatcher-picker-selected');" +
                "    window.siteWatcherPickerSelected = null;" +
                "    window.siteWatcherPickerSelector = null;" +
                "  }" +
                "}" +

                // Find the best interactive element at a point
                "function findInteractiveElement(x, y) {" +
                "  var elements = document.elementsFromPoint(x, y);" +
                "  for (var i = 0; i < elements.length; i++) {" +
                "    var el = elements[i];" +
                "    if (el.tagName === 'BODY' || el.tagName === 'HTML') continue;" +
                "    var style = window.getComputedStyle(el);" +
                "    if (style.pointerEvents === 'none') continue;" +
                "    if (style.visibility === 'hidden') continue;" +
                "    if (style.display === 'none') continue;" +
                "    if (parseFloat(style.opacity) === 0) continue;" +
                "    var tag = el.tagName.toUpperCase();" +
                "    if (tag === 'BUTTON' || tag === 'A' || tag === 'INPUT' || " +
                "        tag === 'SELECT' || tag === 'TEXTAREA' || " +
                "        el.getAttribute('role') === 'button' || " +
                "        el.onclick || el.hasAttribute('onclick')) {" +
                "      return el;" +
                "    }" +
                "  }" +
                "  for (var i = 0; i < elements.length; i++) {" +
                "    var el = elements[i];" +
                "    if (el.tagName === 'BODY' || el.tagName === 'HTML') continue;" +
                "    var style = window.getComputedStyle(el);" +
                "    if (style.pointerEvents === 'none') continue;" +
                "    if (style.visibility === 'hidden') continue;" +
                "    if (style.display === 'none') continue;" +
                "    if (parseFloat(style.opacity) === 0) continue;" +
                "    return el;" +
                "  }" +
                "  return null;" +
                "}" +

                // Handle click events (single selection)
                "function handleClick(e) {" +
                "  e.preventDefault();" +
                "  e.stopPropagation();" +

                // Find the best interactive element at click position
                "  var el = findInteractiveElement(e.clientX, e.clientY);" +
                "  if (!el) el = e.target;" +

                // Skip if it's the body or html
                "  if (el.tagName === 'BODY' || el.tagName === 'HTML') return;" +

                "  var selector = getSelector(el);" +

                // If clicking on already selected element, deselect it
                "  if (window.siteWatcherPickerSelected === el) {" +
                "    clearSelection();" +
                "    PickerBridge.onSelectionChanged('');" +
                "    return;" +
                "  }" +

                // Clear previous selection and select new element
                "  clearSelection();" +
                "  window.siteWatcherPickerSelected = el;" +
                "  window.siteWatcherPickerSelector = selector;" +
                "  el.classList.add('sitewatcher-picker-selected');" +

                // Notify native code
                "  PickerBridge.onSelectionChanged(selector);" +
                "}" +

                // Handle hover for visual feedback
                "var lastHovered = null;" +
                "function handleMouseOver(e) {" +
                "  if (lastHovered && lastHovered !== e.target) {" +
                "    lastHovered.classList.remove('sitewatcher-picker-hover');" +
                "  }" +
                "  if (e.target.tagName !== 'BODY' && e.target.tagName !== 'HTML') {" +
                "    e.target.classList.add('sitewatcher-picker-hover');" +
                "    lastHovered = e.target;" +
                "  }" +
                "}" +

                "function handleMouseOut(e) {" +
                "  e.target.classList.remove('sitewatcher-picker-hover');" +
                "}" +

                // Add event listeners
                "document.addEventListener('click', handleClick, true);" +
                "document.addEventListener('mouseover', handleMouseOver, true);" +
                "document.addEventListener('mouseout', handleMouseOut, true);" +

                // Prevent text selection and context menu
                "document.addEventListener('selectstart', function(e) { e.preventDefault(); }, true);" +
                "document.addEventListener('contextmenu', function(e) { e.preventDefault(); }, true);" +

                "console.log('SiteWatcher picker script loaded');" +
                "})();";
    }

    /**
     * JavaScript interface for communication between WebView and native code.
     */
    private class PickerJsInterface {
        @JavascriptInterface
        public void onSelectionChanged(String selector) {
            mainHandler.post(() -> {
                selectedSelector = selector;
                boolean hasSelection = selector != null && !selector.isEmpty();
                buttonConfirm.setEnabled(hasSelection);

                if (hasSelection) {
                    Logger.d(TAG, "Element selected: " + selector);
                } else {
                    Logger.d(TAG, "Selection cleared");
                }
            });
        }
    }

    private void confirmSelection() {
        if (selectedSelector == null || selectedSelector.isEmpty()) {
            return;
        }

        Logger.d(TAG, "Confirming selector: " + selectedSelector);

        // Set fragment result
        Bundle result = new Bundle();
        result.putString(SELECTOR_KEY, selectedSelector);
        getParentFragmentManager().setFragmentResult(FRAGMENT_RESULT_KEY, result);

        navigateBack();
    }

    private void navigateBack() {
        // Exit fullscreen mode if still active
        autoClickExecutor.exitFullscreenMode();

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
        // Exit fullscreen mode if still active
        if (autoClickExecutor != null) {
            autoClickExecutor.exitFullscreenMode();
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
