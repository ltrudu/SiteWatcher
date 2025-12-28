package com.ltrudu.sitewatcher.network;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.util.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches content from websites using OkHttp.
 * Uses a singleton OkHttpClient for connection pooling and efficiency.
 * Thread-safe for concurrent use.
 */
public class SiteContentFetcher {

    private static final String TAG = "SiteContentFetcher";

    /**
     * Default timeout in seconds for all network operations.
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Maximum redirects to follow.
     */
    private static final int MAX_REDIRECTS = 10;

    /**
     * User agent string for requests.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36 SiteWatcher/1.0";

    // Singleton OkHttpClient with default configuration
    private static volatile OkHttpClient defaultClient;

    // Lock for thread-safe singleton initialization
    private static final Object CLIENT_LOCK = new Object();

    // Instance client (may have custom configuration)
    private final OkHttpClient client;

    /**
     * Create a SiteContentFetcher with default timeout.
     */
    public SiteContentFetcher() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Create a SiteContentFetcher with custom timeout.
     *
     * @param timeoutSeconds Timeout in seconds for connect, read, and write operations
     */
    public SiteContentFetcher(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }

        // Use default client if timeout matches, otherwise create custom client
        if (timeoutSeconds == DEFAULT_TIMEOUT_SECONDS) {
            this.client = getDefaultClient();
        } else {
            this.client = createClient(timeoutSeconds);
        }
    }

    /**
     * Get the singleton default OkHttpClient.
     *
     * @return The shared OkHttpClient instance
     */
    @NonNull
    private static OkHttpClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (CLIENT_LOCK) {
                if (defaultClient == null) {
                    defaultClient = createClient(DEFAULT_TIMEOUT_SECONDS);
                }
            }
        }
        return defaultClient;
    }

    /**
     * Create an OkHttpClient with specified timeout.
     *
     * @param timeoutSeconds Timeout in seconds
     * @return Configured OkHttpClient
     */
    @NonNull
    private static OkHttpClient createClient(int timeoutSeconds) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Fetch content from the specified URL.
     *
     * @param url The URL to fetch
     * @return FetchResult containing the content or error information
     */
    @NonNull
    public FetchResult fetchContent(@NonNull String url) {
        long startTime = System.currentTimeMillis();

        // Validate URL
        if (url.isEmpty()) {
            return FetchResult.failure("URL is empty", 0);
        }

        // Ensure URL has a scheme
        String normalizedUrl = normalizeUrl(url);

        Logger.d(TAG, "Fetching content from: " + normalizedUrl);

        Request request = new Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Connection", "keep-alive")
                .get()
                .build();

        try {
            Response response = client.newCall(request).execute();
            long responseTime = System.currentTimeMillis() - startTime;

            int httpCode = response.code();

            // Check for redirect loops
            if (response.priorResponse() != null) {
                int redirectCount = countRedirects(response);
                if (redirectCount >= MAX_REDIRECTS) {
                    response.close();
                    return FetchResult.failure("Too many redirects (" + redirectCount + ")",
                            responseTime, httpCode);
                }
            }

            // Check for successful response
            if (!response.isSuccessful()) {
                String errorMsg = "HTTP " + httpCode + ": " + response.message();
                response.close();
                return FetchResult.failure(errorMsg, responseTime, httpCode);
            }

            // Get response body
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                return FetchResult.failure("Empty response body", responseTime, httpCode);
            }

            String content = body.string();
            response.close();

            Logger.d(TAG, "Fetched " + content.length() + " bytes in " + responseTime + "ms");

            return FetchResult.success(content, responseTime, httpCode);

        } catch (SocketTimeoutException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "Timeout fetching URL: " + normalizedUrl, e);
            return FetchResult.failure("Connection timed out", responseTime);

        } catch (SSLHandshakeException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "SSL handshake failed: " + normalizedUrl, e);
            return FetchResult.failure("SSL certificate error: " + e.getMessage(), responseTime);

        } catch (SSLException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "SSL error: " + normalizedUrl, e);
            return FetchResult.failure("SSL error: " + e.getMessage(), responseTime);

        } catch (UnknownHostException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "Unknown host: " + normalizedUrl, e);
            return FetchResult.failure("Unknown host: " + e.getMessage(), responseTime);

        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "Network error fetching URL: " + normalizedUrl, e);
            return FetchResult.failure("Network error: " + e.getMessage(), responseTime);

        } catch (IllegalArgumentException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Logger.e(TAG, "Invalid URL: " + normalizedUrl, e);
            return FetchResult.failure("Invalid URL: " + e.getMessage(), responseTime);
        }
    }

    /**
     * Normalize URL by adding scheme if missing.
     *
     * @param url The URL to normalize
     * @return Normalized URL with scheme
     */
    @NonNull
    private String normalizeUrl(@NonNull String url) {
        String trimmedUrl = url.trim();

        // Add https if no scheme present
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return "https://" + trimmedUrl;
        }

        return trimmedUrl;
    }

    /**
     * Count the number of redirects in the response chain.
     *
     * @param response The final response
     * @return Number of redirects
     */
    private int countRedirects(@NonNull Response response) {
        int count = 0;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }

    /**
     * Shutdown the default client and release resources.
     * Call this when the application is shutting down.
     */
    public static void shutdown() {
        synchronized (CLIENT_LOCK) {
            if (defaultClient != null) {
                defaultClient.dispatcher().executorService().shutdown();
                defaultClient.connectionPool().evictAll();
                defaultClient = null;
            }
        }
    }
}
