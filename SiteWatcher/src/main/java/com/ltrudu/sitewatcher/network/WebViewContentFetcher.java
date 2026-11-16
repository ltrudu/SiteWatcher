package com.ltrudu.sitewatcher.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.preferences.PreferencesManager;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches content from websites using WebView with JavaScript execution.
 * This fetcher captures the DOM after JavaScript has run, making it suitable
 * for dynamic sites (SPAs, AJAX content, calendars, etc.).
 *
 * Note: WebView operations must run on the main thread.
 */
public class WebViewContentFetcher {

    private static final String TAG = "WebViewContentFetcher";

    /**
     * Default timeout in seconds for page load.
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * JavaScript interface name for extracting HTML content.
     */
    private static final String JS_INTERFACE_NAME = "HtmlExtractor";
    private static final int MAX_EXTRACTED_HTML_CHARS = 5 * 1024 * 1024;
    private static final String PAGE_TOO_LARGE_MARKER = "__SITEWATCHER_PAGE_TOO_LARGE__";

    private final Context context;
    private final int timeoutSeconds;
    private final Handler mainHandler;
    private final AutoClickExecutor autoClickExecutor;
    private final PreferencesManager preferencesManager;

    /**
     * Create a WebViewContentFetcher with default timeout.
     *
     * @param context Application context
     */
    public WebViewContentFetcher(@NonNull Context context) {
        this(context, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Create a WebViewContentFetcher with custom timeout.
     *
     * @param context        Application context
     * @param timeoutSeconds Timeout in seconds for page load
     */
    public WebViewContentFetcher(@NonNull Context context, int timeoutSeconds) {
        this.context = context.getApplicationContext();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.autoClickExecutor = new AutoClickExecutor();
        this.preferencesManager = new PreferencesManager(context);
    }

    /**
     * Get the page load delay in milliseconds (time to wait after page load before actions).
     */
    private int getPageLoadDelayMs() {
        return preferencesManager.getPageLoadDelay() * 1000;
    }

    /**
     * Get the post-action delay in milliseconds (time to wait after last action before capture).
     */
    private int getPostActionDelayMs() {
        return preferencesManager.getPostActionDelay() * 1000;
    }

    /**
     * Fetch content from the specified URL using WebView.
     * This method blocks until the content is fetched or timeout occurs.
     * Must NOT be called from the main thread.
     *
     * @param url The URL to fetch
     * @return FetchResult containing the content or error information
     */
    @NonNull
    public FetchResult fetchContent(@NonNull String url) {
        return fetchContent(url, null);
    }

    /**
     * Fetch content from the specified URL using WebView with auto-click actions.
     * Auto-click actions are executed after page load before content extraction.
     * This method blocks until the content is fetched or timeout occurs.
     * Must NOT be called from the main thread.
     *
     * @param url              The URL to fetch
     * @param autoClickActions Optional list of auto-click actions to execute
     * @return FetchResult containing the content or error information
     */
    @NonNull
    public FetchResult fetchContent(@NonNull String url, @Nullable List<AutoClickAction> autoClickActions) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return FetchResult.failure("WebViewContentFetcher cannot be called from main thread", 0);
        }

        long startTime = System.currentTimeMillis();

        // Validate URL
        if (url.isEmpty()) {
            return FetchResult.failure("URL is empty", 0);
        }

        // Ensure URL has a scheme
        String normalizedUrl = normalizeUrl(url);

        Logger.d(TAG, "Fetching content with WebView from: " + normalizedUrl);
        if (autoClickActions != null && !autoClickActions.isEmpty()) {
            int enabledCount = 0;
            for (AutoClickAction action : autoClickActions) {
                if (action.isEnabled()) enabledCount++;
            }
            Logger.d(TAG, "Auto-click actions: " + enabledCount + " enabled");
        }

        // Use CountDownLatch to wait for WebView operation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FetchResult> resultRef = new AtomicReference<>();
        FetchSession session = new FetchSession(startTime, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        // Run WebView operations on main thread
        mainHandler.post(() -> {
            if (!session.isActive()) {
                return;
            }
            try {
                fetchWithWebView(normalizedUrl, autoClickActions, session);
            } catch (Exception e) {
                Logger.e(TAG, "Error creating WebView", e);
                session.complete(FetchResult.failure("WebView error: " + e.getMessage(),
                        System.currentTimeMillis() - startTime));
            }
        });

        // Wait for result with timeout
        try {
            boolean completed = latch.await(timeoutSeconds + 10, TimeUnit.SECONDS);
            if (!completed) {
                Logger.e(TAG, "WebView fetch timed out for: " + normalizedUrl);
                FetchResult timeoutResult = FetchResult.failure("Page load timed out",
                        System.currentTimeMillis() - startTime);
                session.cancel();
                mainHandler.post(() -> session.complete(timeoutResult));
                return timeoutResult;
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "WebView fetch interrupted", e);
            FetchResult interruptedResult = FetchResult.failure("Fetch interrupted",
                    System.currentTimeMillis() - startTime);
            session.cancel();
            mainHandler.post(() -> session.complete(interruptedResult));
            Thread.currentThread().interrupt();
            return interruptedResult;
        }

        FetchResult result = resultRef.get();
        return result != null ? result : FetchResult.failure("Unknown error",
                System.currentTimeMillis() - startTime);
    }

    /**
     * Callback interface for fetch result.
     */
    private interface FetchCallback {
        void onResult(@NonNull FetchResult result);
    }

    private final class FetchSession {
        private final long startTime;
        private final FetchCallback callback;
        private final AutoClickExecutor.CancellationToken cancellationToken =
                new AutoClickExecutor.CancellationToken();
        private final java.util.concurrent.atomic.AtomicBoolean completed =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        @Nullable
        private WebView webView;
        @Nullable
        private Runnable timeoutRunnable;

        FetchSession(long startTime, @NonNull FetchCallback callback) {
            this.startTime = startTime;
            this.callback = callback;
        }

        boolean isActive() {
            return !completed.get() && !cancellationToken.isCancelled();
        }

        void setWebView(@NonNull WebView webView) {
            this.webView = webView;
        }

        AutoClickExecutor.CancellationToken getCancellationToken() {
            return cancellationToken;
        }

        void cancel() {
            cancellationToken.cancel();
        }

        void startTimeout(@NonNull String url) {
            timeoutRunnable = () -> {
                if (!isActive()) {
                    return;
                }
                Logger.e(TAG, "WebView timeout for: " + url);
                complete(FetchResult.failure("Page load timed out",
                        System.currentTimeMillis() - startTime));
            };
            mainHandler.postDelayed(timeoutRunnable, timeoutSeconds * 1000L);
        }

        void postDelayed(@NonNull Runnable runnable, long delayMs) {
            if (!isActive()) {
                return;
            }
            mainHandler.postDelayed(() -> {
                if (isActive()) {
                    runnable.run();
                }
            }, delayMs);
        }

        void complete(@NonNull FetchResult result) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            cancellationToken.cancel();
            if (timeoutRunnable != null) {
                mainHandler.removeCallbacks(timeoutRunnable);
                timeoutRunnable = null;
            }
            cleanupWebView(webView);
            webView = null;
            callback.onResult(result);
        }
    }

    /**
     * Perform the actual WebView fetch on the main thread.
     */
    private void fetchWithWebView(@NonNull String url,
                                   @Nullable List<AutoClickAction> autoClickActions,
                                   @NonNull FetchSession session) {
        if (!session.isActive()) {
            return;
        }

        WebView webView = new WebView(context);
        session.setWebView(webView);

        try {
            if (!session.isActive()) {
                return;
            }
            configureWebView(webView);

            // Add JavaScript interface for extracting HTML
            HtmlExtractorInterface extractorInterface = new HtmlExtractorInterface();
            webView.addJavascriptInterface(extractorInterface, JS_INTERFACE_NAME);

            // Set up WebViewClient
            webView.setWebViewClient(new WebViewClient() {
                private boolean hasError = false;
                private boolean isFinished = false;

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!session.isActive() || isFinished || hasError) {
                        return;
                    }
                    super.onPageFinished(view, url);
                    isFinished = true;

                    Logger.d(TAG, "Page finished loading: " + url);

                    int pageLoadDelay = getPageLoadDelayMs();
                    int postActionDelay = getPostActionDelayMs();
                    Logger.d(TAG, "Using delays - pageLoad: " + pageLoadDelay + "ms, postAction: " + postActionDelay + "ms");

                    // Wait for page load delay (allows JS content to load, e.g., cookie dialogs)
                    session.postDelayed(() -> {
                        if (!session.isActive()) {
                            return;
                        }
                        // Execute auto-click actions if any, then extract content
                        if (autoClickActions != null && !autoClickActions.isEmpty()) {
                            Logger.d(TAG, "Executing auto-click actions...");
                            autoClickExecutor.executeActions(view, autoClickActions,
                                    session.getCancellationToken(),
                                    new AutoClickExecutor.ExecutionCallback() {
                                        @Override
                                        public void onComplete(int successCount, int failCount) {
                                            if (!session.isActive()) {
                                                return;
                                            }
                                            Logger.d(TAG, "Auto-click complete: " + successCount +
                                                    " success, " + failCount + " failed");
                                            // Wait for post-action delay, then extract content
                                            session.postDelayed(() -> extractHtmlContent(
                                                    view, session.startTime, session, extractorInterface),
                                                    postActionDelay);
                                        }
                                    });
                        } else {
                            // No auto-click actions, wait for post-action delay then extract
                            session.postDelayed(() -> extractHtmlContent(
                                    view, session.startTime, session, extractorInterface),
                                    postActionDelay);
                        }
                    }, pageLoadDelay);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request,
                                            WebResourceError error) {
                    if (!session.isActive()) {
                        return;
                    }
                    super.onReceivedError(view, request, error);

                    // Only handle main frame errors
                    if (request.isForMainFrame() && !hasError) {
                        hasError = true;
                        String errorMsg = "Page load error: " + error.getDescription();
                        Logger.e(TAG, errorMsg);
                        session.complete(FetchResult.failure(errorMsg,
                                System.currentTimeMillis() - session.startTime));
                    }
                }
            });

            session.startTimeout(url);

            // Load the URL
            if (!session.isActive()) {
                return;
            }
            webView.loadUrl(url);

        } catch (Exception e) {
            Logger.e(TAG, "Error in fetchWithWebView", e);
            session.complete(FetchResult.failure("WebView error: " + e.getMessage(),
                    System.currentTimeMillis() - session.startTime));
        }
    }

    /**
     * Configure WebView settings for content fetching.
     */
    private void configureWebView(@NonNull WebView webView) {
        WebSettings settings = webView.getSettings();

        // Enable JavaScript
        settings.setJavaScriptEnabled(true);

        // Enable DOM storage for modern web apps
        settings.setDomStorageEnabled(true);

        // Allow loading content from any origin
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);

        // Set user agent
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36 SiteWatcher/1.0");

        // Enable wide viewport for responsive sites
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Cache settings
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Disable images for faster loading (optional - can be enabled if needed)
        // settings.setLoadsImagesAutomatically(false);

        // Enable mixed content (HTTP on HTTPS pages)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    /**
     * Extract HTML content from the WebView after JavaScript execution.
     */
    private void extractHtmlContent(@NonNull WebView webView, long startTime,
                                     @NonNull FetchSession session,
                                     @NonNull HtmlExtractorInterface extractorInterface) {
        if (!session.isActive()) {
            return;
        }

        try {
            // JavaScript to extract the full HTML including changes made by JS
            String extractScript =
                    "javascript:" + JS_INTERFACE_NAME + ".receiveHtml(" +
                            "document.documentElement.outerHTML" +
                            ");";

            String extractionScript = "(function() { var html = document.documentElement.outerHTML; return html.length > " + MAX_EXTRACTED_HTML_CHARS + " ? \"" + PAGE_TOO_LARGE_MARKER + "\" : html; })()";
            if (!session.isActive()) {
                return;
            }
            webView.evaluateJavascript(
                    extractionScript,
                    value -> {
                        if (!session.isActive()) {
                            return;
                        }
                        mainHandler.post(() -> {
                            if (!session.isActive()) {
                                return;
                            }
                            try {
                                String html = value;

                                // Remove surrounding quotes from JSON string
                                if (html != null && html.startsWith("\"") && html.endsWith("\"")) {
                                    html = html.substring(1, html.length() - 1);
                                    // Unescape JSON string
                                    html = unescapeJson(html);
                                }

                                if (PAGE_TOO_LARGE_MARKER.equals(html)) {
                                    extractorInterface.setExtracted(true);
                                    session.complete(FetchResult.failure(
                                            "Page content exceeds the 5 MiB extraction limit",
                                            System.currentTimeMillis() - startTime));
                                    return;
                                }
                                if (html != null && html.length() > MAX_EXTRACTED_HTML_CHARS) {
                                    extractorInterface.setExtracted(true);
                                    session.complete(FetchResult.failure(
                                            "Page content exceeds the 5 MiB extraction limit",
                                            System.currentTimeMillis() - startTime));
                                    return;
                                }

                                if (html != null && !html.isEmpty() && !html.equals("null")) {
                                    extractorInterface.setExtracted(true);
                                    long responseTime = System.currentTimeMillis() - startTime;
                                    Logger.d(TAG, "Extracted " + html.length() +
                                            " bytes in " + responseTime + "ms");
                                    extractorInterface.setExtracted(true);
                                    session.complete(FetchResult.success(html, responseTime, 200));
                                } else {
                                    session.complete(FetchResult.failure("Failed to extract HTML content",
                                            System.currentTimeMillis() - startTime));
                                }
                            } catch (Exception e) {
                                Logger.e(TAG, "Error processing extracted HTML", e);
                                session.complete(FetchResult.failure("Error processing HTML: " + e.getMessage(),
                                        System.currentTimeMillis() - startTime));
                            }
                        });
                    }
            );
        } catch (Exception e) {
            Logger.e(TAG, "Error extracting HTML", e);
            session.complete(FetchResult.failure("Error extracting HTML: " + e.getMessage(),
                    System.currentTimeMillis() - startTime));
        }
    }

    /**
     * Clean up WebView resources.
     */
    private void cleanupWebView(@Nullable WebView webView) {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception e) {
                Logger.w(TAG, "Error cleaning up WebView: " + e.getMessage());
            }
        }
    }

    /**
     * Normalize URL by adding scheme if missing.
     */
    @NonNull
    private String normalizeUrl(@NonNull String url) {
        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "https://" + trimmedUrl;
        }
        return trimmedUrl;
    }

    /**
     * Unescape JSON string.
     */
    @NonNull
    private String unescapeJson(@NonNull String json) {
        return json
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\u0027", "'");
    }

    /**
     * JavaScript interface for extracting HTML content.
     */
    private static class HtmlExtractorInterface {
        private volatile boolean extracted = false;
        private volatile String html;

        @JavascriptInterface
        public void receiveHtml(String html) {
            this.html = html;
            this.extracted = true;
        }

        public boolean isExtracted() {
            return extracted;
        }

        public void setExtracted(boolean extracted) {
            this.extracted = extracted;
        }

        @Nullable
        public String getHtml() {
            return html;
        }
    }
}
