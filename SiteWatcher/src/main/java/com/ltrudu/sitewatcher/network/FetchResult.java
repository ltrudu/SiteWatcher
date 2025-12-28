package com.ltrudu.sitewatcher.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents the result of a content fetch operation.
 * Contains the fetched content, timing information, and error details.
 */
public class FetchResult {

    /**
     * The fetched content as a string. Null if fetch failed.
     */
    @Nullable
    private final String content;

    /**
     * The time taken to fetch the content in milliseconds.
     */
    private final long responseTimeMs;

    /**
     * Error message if the fetch failed. Null if successful.
     */
    @Nullable
    private final String error;

    /**
     * Whether the fetch was successful.
     */
    private final boolean success;

    /**
     * HTTP response code (e.g., 200, 404, 500).
     */
    private final int httpCode;

    /**
     * Private constructor. Use factory methods to create instances.
     */
    private FetchResult(@Nullable String content, long responseTimeMs,
                        @Nullable String error, boolean success, int httpCode) {
        this.content = content;
        this.responseTimeMs = responseTimeMs;
        this.error = error;
        this.success = success;
        this.httpCode = httpCode;
    }

    /**
     * Create a successful fetch result.
     *
     * @param content        The fetched content
     * @param responseTimeMs The time taken to fetch in milliseconds
     * @param httpCode       The HTTP response code
     * @return A successful FetchResult
     */
    @NonNull
    public static FetchResult success(@NonNull String content, long responseTimeMs, int httpCode) {
        return new FetchResult(content, responseTimeMs, null, true, httpCode);
    }

    /**
     * Create a failed fetch result.
     *
     * @param error          The error message
     * @param responseTimeMs The time taken before failure in milliseconds
     * @return A failed FetchResult
     */
    @NonNull
    public static FetchResult failure(@NonNull String error, long responseTimeMs) {
        return new FetchResult(null, responseTimeMs, error, false, -1);
    }

    /**
     * Create a failed fetch result with HTTP code.
     *
     * @param error          The error message
     * @param responseTimeMs The time taken before failure in milliseconds
     * @param httpCode       The HTTP response code
     * @return A failed FetchResult
     */
    @NonNull
    public static FetchResult failure(@NonNull String error, long responseTimeMs, int httpCode) {
        return new FetchResult(null, responseTimeMs, error, false, httpCode);
    }

    /**
     * Get the fetched content.
     *
     * @return The content, or null if fetch failed
     */
    @Nullable
    public String getContent() {
        return content;
    }

    /**
     * Get the response time in milliseconds.
     *
     * @return Response time in milliseconds
     */
    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    /**
     * Get the error message.
     *
     * @return Error message, or null if successful
     */
    @Nullable
    public String getError() {
        return error;
    }

    /**
     * Check if the fetch was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the HTTP response code.
     *
     * @return HTTP code, or -1 if not applicable
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * Get the content length in bytes.
     *
     * @return Content length, or 0 if no content
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }

    @NonNull
    @Override
    public String toString() {
        if (success) {
            return "FetchResult{success=true, contentLength=" + getContentLength() +
                    ", responseTimeMs=" + responseTimeMs + ", httpCode=" + httpCode + "}";
        } else {
            return "FetchResult{success=false, error='" + error +
                    "', responseTimeMs=" + responseTimeMs + ", httpCode=" + httpCode + "}";
        }
    }
}
