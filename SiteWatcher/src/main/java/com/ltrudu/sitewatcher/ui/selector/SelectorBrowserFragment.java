package com.ltrudu.sitewatcher.ui.selector;

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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.network.AutoClickExecutor;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for visually selecting elements on a webpage to generate CSS selectors.
 * User can tap elements to select/deselect them, and the fragment returns
 * a combined CSS selector for all selected elements.
 *
 * If auto-click actions are provided, they will be executed before element selection
 * to ensure the page is in the same state as during actual content fetching.
 */
public class SelectorBrowserFragment extends Fragment {

    private static final String TAG = "SelectorBrowser";
    private static final String ARG_URL = "url";
    private static final String ARG_ACTIONS_JSON = "actions_json";
    private static final String ARG_RESULT_KEY = "result_key";
    private static final String FRAGMENT_RESULT_KEY = "selectorResult";
    private static final String SELECTOR_KEY = "selector";

    /**
     * Fragment states.
     */
    private enum State {
        LOADING,    // Page is loading
        EXECUTING,  // Running auto-click actions
        SELECTING   // Element selection mode
    }

    // UI components
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout loadingOverlay;
    private MaterialCardView statusCard;
    private TextView textProgress;
    private TextView textCurrentAction;
    private TextView textInstructions;
    private TextView textSelectedCount;
    private Button btnClear;
    private Button btnCancel;
    private Button btnConfirm;

    // State
    private State currentState = State.LOADING;
    private String targetUrl;
    private String actionsJson;
    private String resultKey;
    private List<AutoClickAction> actions;
    private final List<String> selectedSelectors = new ArrayList<>();
    private boolean pageLoaded = false;

    // Utilities
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AutoClickExecutor autoClickExecutor;
    private PreferencesManager preferencesManager;
    private CountDownTimer countDownTimer;

    public SelectorBrowserFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetUrl = getArguments().getString(ARG_URL, "");
            actionsJson = getArguments().getString(ARG_ACTIONS_JSON, null);
            resultKey = getArguments().getString(ARG_RESULT_KEY, FRAGMENT_RESULT_KEY);
        } else {
            resultKey = FRAGMENT_RESULT_KEY;
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
        return inflater.inflate(R.layout.fragment_selector_browser, container, false);
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
        webView = view.findViewById(R.id.webView);
        progressBar = view.findViewById(R.id.progressBar);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        statusCard = view.findViewById(R.id.statusCard);
        textProgress = view.findViewById(R.id.textProgress);
        textCurrentAction = view.findViewById(R.id.textCurrentAction);
        textInstructions = view.findViewById(R.id.textInstructions);
        textSelectedCount = view.findViewById(R.id.textSelectedCount);
        btnClear = view.findViewById(R.id.btnClear);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnConfirm = view.findViewById(R.id.btnConfirm);

        // Set instructions based on mode (include vs exclude)
        if (resultKey != null && resultKey.toLowerCase().contains("exclude")) {
            textInstructions.setText(R.string.selector_instructions_exclude);
        } else {
            textInstructions.setText(R.string.selector_instructions);
        }

        // Initial state: loading
        updateStateUI(State.LOADING);
        updateSelectedCount();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();

        // Enable JavaScript (required for element selection)
        webSettings.setJavaScriptEnabled(true);

        // Enable DOM storage for modern websites
        webSettings.setDomStorageEnabled(true);

        // Enable zoom controls
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Enable wide viewport for responsive sites
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // Enable mixed content
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Fix for black screen / flickering issues
        webView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sw_background));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Add JavaScript interface for communication
        webView.addJavascriptInterface(new SelectorJsInterface(), "SelectorBridge");

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
                updateProgress(newProgress);
            }
        });
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> navigateBack());

        btnConfirm.setOnClickListener(v -> confirmSelection());

        btnClear.setOnClickListener(v -> clearSelection());
    }

    private void loadTargetPage() {
        if (targetUrl == null || targetUrl.isEmpty()) {
            Logger.e(TAG, "No URL provided");
            navigateBack();
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

    /**
     * Called when the initial page load is complete.
     */
    private void onPageLoadComplete() {
        int pageLoadDelay = getPageLoadDelayMs();
        // Wait for JS-loaded content (cookie dialogs) to appear before proceeding
        Logger.d(TAG, "Waiting " + pageLoadDelay + "ms for JS content to load...");
        statusCard.setVisibility(View.VISIBLE);

        // Start countdown timer for visual feedback
        startCountdownTimer(pageLoadDelay, () -> {
            if (!isAdded() || webView == null) return;

            if (hasActionsToExecute()) {
                // Start executing actions
                startActionExecution();
            } else {
                // No actions, go directly to selection mode
                enableSelectionMode();
            }
        });
    }

    /**
     * Starts a countdown timer that updates the UI every 100ms with remaining time.
     *
     * @param durationMs  Total duration in milliseconds
     * @param onComplete  Callback to run when countdown finishes
     */
    private void startCountdownTimer(int durationMs, Runnable onComplete) {
        // Cancel any existing timer
        cancelCountdownTimer();

        countDownTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded() || textProgress == null) return;
                int seconds = (int) (millisUntilFinished / 1000);
                int deciseconds = (int) ((millisUntilFinished % 1000) / 100);
                textProgress.setText(getString(R.string.waiting_countdown, seconds, deciseconds));
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;
                countDownTimer = null;
                onComplete.run();
            }
        };
        countDownTimer.start();
    }

    /**
     * Cancels the countdown timer if running.
     */
    private void cancelCountdownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
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

                    // Wait for page to settle before enabling selection with countdown
                    int postActionDelay = getPostActionDelayMs();
                    startCountdownTimer(postActionDelay, () -> enableSelectionMode());
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

        ProgressBar statusProgressBar = statusCard.findViewById(R.id.statusProgressBar);
        if (statusProgressBar != null) {
            int progressPercent = total > 0 ? (current * 100) / total : 0;
            statusProgressBar.setProgress(progressPercent);
        }
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
                btnConfirm.setEnabled(false);
                break;

            case EXECUTING:
                loadingOverlay.setVisibility(View.GONE);
                statusCard.setVisibility(View.VISIBLE);
                textCurrentAction.setVisibility(View.VISIBLE);
                btnConfirm.setEnabled(false);
                break;

            case SELECTING:
                loadingOverlay.setVisibility(View.GONE);
                statusCard.setVisibility(View.GONE);
                break;
        }
    }

    private void updateProgress(int progress) {
        if (progressBar == null) return;

        if (progress < 100) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(progress);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Inject JavaScript to handle element selection.
     */
    private void injectSelectorScript() {
        String script = getSelectionScript();
        webView.evaluateJavascript(script, null);
        Logger.d(TAG, "Selector script injected");
    }

    /**
     * Returns the JavaScript code for element selection.
     */
    @NonNull
    private String getSelectionScript() {
        return "(function() {" +
                // Style for highlighting selected elements
                "var style = document.createElement('style');" +
                "style.id = 'sitewatcher-selector-styles';" +
                "style.textContent = '" +
                ".sitewatcher-selected { " +
                "  outline: 3px solid #4CAF50 !important; " +
                "  outline-offset: 2px !important; " +
                "  background-color: rgba(76, 175, 80, 0.2) !important; " +
                "} " +
                ".sitewatcher-hover { " +
                "  outline: 2px dashed #2196F3 !important; " +
                "  outline-offset: 1px !important; " +
                "  cursor: pointer !important; " +
                "}';" +
                "document.head.appendChild(style);" +

                // Track selected elements
                "window.siteWatcherSelected = window.siteWatcherSelected || [];" +

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

                // Find the best interactive element at a point
                "function findInteractiveElement(x, y) {" +
                "  var elements = document.elementsFromPoint(x, y);" +
                "  for (var i = 0; i < elements.length; i++) {" +
                "    var el = elements[i];" +
                "    if (el.tagName === 'BODY' || el.tagName === 'HTML') continue;" +
                "    var style = window.getComputedStyle(el);" +
                // Skip elements with pointer-events: none or hidden
                "    if (style.pointerEvents === 'none') continue;" +
                "    if (style.visibility === 'hidden') continue;" +
                "    if (style.display === 'none') continue;" +
                // Skip fully transparent elements (likely overlays)
                "    if (parseFloat(style.opacity) === 0) continue;" +
                // Prefer interactive elements (buttons, links, inputs)
                "    var tag = el.tagName.toUpperCase();" +
                "    if (tag === 'BUTTON' || tag === 'A' || tag === 'INPUT' || " +
                "        tag === 'SELECT' || tag === 'TEXTAREA' || " +
                "        el.getAttribute('role') === 'button' || " +
                "        el.onclick || el.hasAttribute('onclick')) {" +
                "      return el;" +
                "    }" +
                "  }" +
                // If no interactive element found, return first visible non-body element
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

                // Handle click events
                "function handleClick(e) {" +
                "  e.preventDefault();" +
                "  e.stopPropagation();" +

                // Find the best interactive element at click position
                "  var el = findInteractiveElement(e.clientX, e.clientY);" +
                "  if (!el) el = e.target;" +

                // Skip if it's the body or html
                "  if (el.tagName === 'BODY' || el.tagName === 'HTML') return;" +

                "  var selector = getSelector(el);" +
                "  var index = window.siteWatcherSelected.indexOf(selector);" +

                "  if (index > -1) {" +
                // Deselect
                "    window.siteWatcherSelected.splice(index, 1);" +
                "    el.classList.remove('sitewatcher-selected');" +
                "  } else {" +
                // Select
                "    window.siteWatcherSelected.push(selector);" +
                "    el.classList.add('sitewatcher-selected');" +
                "  }" +

                // Notify native code
                "  SelectorBridge.onSelectionChanged(JSON.stringify(window.siteWatcherSelected));" +
                "}" +

                // Handle hover for visual feedback
                "var lastHovered = null;" +
                "function handleMouseOver(e) {" +
                "  if (lastHovered && lastHovered !== e.target) {" +
                "    lastHovered.classList.remove('sitewatcher-hover');" +
                "  }" +
                "  if (e.target.tagName !== 'BODY' && e.target.tagName !== 'HTML') {" +
                "    e.target.classList.add('sitewatcher-hover');" +
                "    lastHovered = e.target;" +
                "  }" +
                "}" +

                "function handleMouseOut(e) {" +
                "  e.target.classList.remove('sitewatcher-hover');" +
                "}" +

                // Add event listeners
                "document.addEventListener('click', handleClick, true);" +
                "document.addEventListener('mouseover', handleMouseOver, true);" +
                "document.addEventListener('mouseout', handleMouseOut, true);" +

                // Prevent text selection and context menu
                "document.addEventListener('selectstart', function(e) { e.preventDefault(); }, true);" +
                "document.addEventListener('contextmenu', function(e) { e.preventDefault(); }, true);" +

                "console.log('SiteWatcher selector script loaded');" +

                // Try to inject into accessible iframes
                "function injectIntoIframes() {" +
                "  var iframes = document.querySelectorAll('iframe');" +
                "  iframes.forEach(function(iframe) {" +
                "    try {" +
                "      var iframeDoc = iframe.contentDocument || iframe.contentWindow.document;" +
                "      if (iframeDoc) {" +
                // Add styles to iframe
                "        var style = iframeDoc.createElement('style');" +
                "        style.textContent = '.sitewatcher-selected { outline: 3px solid #4CAF50 !important; outline-offset: 2px !important; background-color: rgba(76, 175, 80, 0.2) !important; } .sitewatcher-hover { outline: 2px dashed #2196F3 !important; outline-offset: 1px !important; cursor: pointer !important; }';" +
                "        iframeDoc.head.appendChild(style);" +
                // Add click handler to iframe
                "        iframeDoc.addEventListener('click', function(e) {" +
                "          e.preventDefault();" +
                "          e.stopPropagation();" +
                "          var el = e.target;" +
                "          if (el.tagName === 'BODY' || el.tagName === 'HTML') return;" +
                // Generate selector with iframe prefix
                "          var selector = '';" +
                "          if (el.id) { selector = '#' + el.id; }" +
                "          else if (el.className && typeof el.className === 'string') {" +
                "            var classes = el.className.trim().split(/\\s+/).filter(function(c) { return c && !c.startsWith('sitewatcher-'); });" +
                "            if (classes.length > 0) { selector = el.tagName.toLowerCase() + '.' + classes.join('.'); }" +
                "            else { selector = el.tagName.toLowerCase(); }" +
                "          } else { selector = el.tagName.toLowerCase(); }" +
                // Add iframe context to selector
                "          var iframeSelector = '';" +
                "          if (iframe.id) { iframeSelector = 'iframe#' + iframe.id + ' '; }" +
                "          else if (iframe.className) { iframeSelector = 'iframe.' + iframe.className.split(' ')[0] + ' '; }" +
                "          else { iframeSelector = 'iframe '; }" +
                "          var fullSelector = iframeSelector + selector;" +
                // Toggle selection
                "          var index = window.parent.siteWatcherSelected.indexOf(fullSelector);" +
                "          if (index > -1) {" +
                "            window.parent.siteWatcherSelected.splice(index, 1);" +
                "            el.classList.remove('sitewatcher-selected');" +
                "          } else {" +
                "            window.parent.siteWatcherSelected.push(fullSelector);" +
                "            el.classList.add('sitewatcher-selected');" +
                "          }" +
                "          window.parent.SelectorBridge.onSelectionChanged(JSON.stringify(window.parent.siteWatcherSelected));" +
                "        }, true);" +
                // Add hover handlers
                "        iframeDoc.addEventListener('mouseover', function(e) {" +
                "          if (e.target.tagName !== 'BODY' && e.target.tagName !== 'HTML') {" +
                "            e.target.classList.add('sitewatcher-hover');" +
                "          }" +
                "        }, true);" +
                "        iframeDoc.addEventListener('mouseout', function(e) {" +
                "          e.target.classList.remove('sitewatcher-hover');" +
                "        }, true);" +
                "        console.log('SiteWatcher: Injected into iframe', iframe);" +
                "      }" +
                "    } catch (e) {" +
                "      console.log('SiteWatcher: Cannot access iframe (cross-origin):', e.message);" +
                "    }" +
                "  });" +
                "}" +
                "injectIntoIframes();" +
                // Re-inject when new iframes are added
                "var observer = new MutationObserver(function(mutations) {" +
                "  mutations.forEach(function(mutation) {" +
                "    mutation.addedNodes.forEach(function(node) {" +
                "      if (node.tagName === 'IFRAME') {" +
                "        setTimeout(injectIntoIframes, 500);" +
                "      }" +
                "    });" +
                "  });" +
                "});" +
                "observer.observe(document.body, { childList: true, subtree: true });" +

                "})();";
    }

    /**
     * JavaScript interface for communication between WebView and native code.
     */
    private class SelectorJsInterface {
        @JavascriptInterface
        public void onSelectionChanged(String selectorsJson) {
            requireActivity().runOnUiThread(() -> {
                try {
                    selectedSelectors.clear();
                    // Parse JSON array
                    String json = selectorsJson.trim();
                    if (json.startsWith("[") && json.endsWith("]")) {
                        json = json.substring(1, json.length() - 1);
                        if (!json.isEmpty()) {
                            String[] parts = json.split("\",\"");
                            for (String part : parts) {
                                String selector = part.replace("\"", "").trim();
                                if (!selector.isEmpty()) {
                                    selectedSelectors.add(selector);
                                }
                            }
                        }
                    }
                    updateSelectedCount();
                    Logger.d(TAG, "Selection changed: " + selectedSelectors.size() + " elements");
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing selectors", e);
                }
            });
        }
    }

    private void updateSelectedCount() {
        int count = selectedSelectors.size();

        if (count == 0) {
            textSelectedCount.setText(R.string.no_elements_selected);
            btnClear.setVisibility(View.GONE);
            btnConfirm.setEnabled(false);
        } else {
            // Build display text with count and selector names
            String countText = getResources().getQuantityString(
                    R.plurals.elements_selected, count, count);
            String selectorsText = String.join(", ", selectedSelectors);
            textSelectedCount.setText(countText + ": " + selectorsText);
            btnClear.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
        }
    }

    private void clearSelection() {
        selectedSelectors.clear();
        updateSelectedCount();

        // Clear visual selection in WebView
        String script = "(function() {" +
                "var selected = document.querySelectorAll('.sitewatcher-selected');" +
                "selected.forEach(function(el) { el.classList.remove('sitewatcher-selected'); });" +
                "window.siteWatcherSelected = [];" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    private void confirmSelection() {
        if (selectedSelectors.isEmpty()) {
            return;
        }

        // Combine selectors with comma
        String combinedSelector = String.join(", ", selectedSelectors);
        Logger.d(TAG, "Confirming selector: " + combinedSelector + " with result key: " + resultKey);

        // Set fragment result using the configured result key
        Bundle result = new Bundle();
        result.putString(SELECTOR_KEY, combinedSelector);
        getParentFragmentManager().setFragmentResult(resultKey, result);

        navigateBack();
    }

    private void navigateBack() {
        // Exit fullscreen mode if still active
        if (autoClickExecutor != null) {
            autoClickExecutor.exitFullscreenMode();
        }

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
        // Cancel any running countdown timer
        cancelCountdownTimer();

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
