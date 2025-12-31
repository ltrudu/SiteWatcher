package com.ltrudu.sitewatcher.ui.browser;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;

/**
 * Fragment for browsing websites to discover URLs for monitoring.
 * Displays a WebView starting at the user's preferred search engine.
 * User can navigate to any URL and tap "Watch" to select it for monitoring.
 */
public class BrowserDiscoveryFragment extends Fragment {

    private static final String TAG = "BrowserDiscovery";
    private static final String FRAGMENT_RESULT_KEY = "browserResult";
    private static final String URL_KEY = "url";

    private WebView webView;
    private TextInputEditText editUrl;
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnWatch;

    private String currentUrl = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browser_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupWebView();
        setupListeners();
        setupBackPressHandler();
        loadInitialPage();
    }

    private void initializeViews(@NonNull View view) {
        webView = view.findViewById(R.id.webView);
        editUrl = view.findViewById(R.id.editUrl);
        progressBar = view.findViewById(R.id.progressBar);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnWatch = view.findViewById(R.id.btnWatch);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();

        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);

        // Enable DOM storage for modern websites
        webSettings.setDomStorageEnabled(true);

        // Enable zoom controls
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        // Enable wide viewport for responsive sites
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        // Enable mixed content (some sites use mixed http/https resources)
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Fix for black screen / flickering issues
        webView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sw_background));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Set WebViewClient to handle page navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                    @NonNull WebResourceRequest request) {
                String url = request.getUrl().toString();
                Logger.d(TAG, "Loading URL: " + url);
                return false; // Allow WebView to handle the navigation
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                currentUrl = url;
                updateUrlField(url);
                Logger.d(TAG, "Page started: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                currentUrl = url;
                updateUrlField(url);
                Logger.d(TAG, "Page finished: " + url);
            }
        });

        // Set WebChromeClient to handle progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                updateProgress(newProgress);
            }
        });
    }

    private void setupListeners() {
        // Cancel button - pop back without result
        btnCancel.setOnClickListener(v -> {
            Logger.d(TAG, "Cancel clicked");
            navigateBack();
        });

        // Watch button - set result and pop back
        btnWatch.setOnClickListener(v -> {
            Logger.d(TAG, "Watch clicked with URL: " + currentUrl);
            setResultAndNavigateBack();
        });

        // URL field - navigate when user presses Enter/Go
        editUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateToUrl();
                return true;
            }
            return false;
        });
    }

    private void setupBackPressHandler() {
        // Handle back press to go back in WebView history first
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            // Disable this callback and let the system handle back
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    private void loadInitialPage() {
        PreferencesManager preferencesManager = new PreferencesManager(requireContext());
        String searchEngineUrl = preferencesManager.getSearchEngineUrl();
        Logger.d(TAG, "Loading initial search engine: " + searchEngineUrl);

        currentUrl = searchEngineUrl;
        updateUrlField(searchEngineUrl);
        webView.loadUrl(searchEngineUrl);
    }

    private void updateUrlField(@NonNull String url) {
        if (editUrl != null && !editUrl.hasFocus()) {
            editUrl.setText(url);
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

    private void navigateToUrl() {
        if (editUrl == null) return;

        String inputUrl = editUrl.getText() != null ? editUrl.getText().toString().trim() : "";
        if (inputUrl.isEmpty()) return;

        // Add http:// if no protocol specified
        String url = normalizeUrl(inputUrl);
        Logger.d(TAG, "Navigating to: " + url);

        currentUrl = url;
        webView.loadUrl(url);
    }

    @NonNull
    private String normalizeUrl(@NonNull String url) {
        String trimmedUrl = url.trim();

        // If no protocol, add https://
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "https://" + trimmedUrl;
        }

        return trimmedUrl;
    }

    private void setResultAndNavigateBack() {
        // Set fragment result with current URL
        Bundle result = new Bundle();
        result.putString(URL_KEY, currentUrl);
        getParentFragmentManager().setFragmentResult(FRAGMENT_RESULT_KEY, result);

        Logger.d(TAG, "Setting result with URL: " + currentUrl);
        navigateBack();
    }

    private void navigateBack() {
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
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}
