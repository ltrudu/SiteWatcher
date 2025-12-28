package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Utility class for validating URLs.
 * Provides static methods for URL validation using regex patterns.
 */
public final class UrlValidator {

    /**
     * Regex pattern for validating HTTP/HTTPS URLs.
     * Matches:
     * - Optional http:// or https:// prefix
     * - Domain with subdomain support (e.g., www.example.com, sub.domain.example.com)
     * - Optional path, query parameters, and fragments
     */
    private static final String URL_REGEX =
            "^(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);

    // Private constructor to prevent instantiation
    private UrlValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Check if a URL is valid.
     * @param url The URL to validate
     * @return true if the URL is valid, false otherwise
     */
    public static boolean isValidUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Normalize a URL by adding https:// prefix if missing.
     * @param url The URL to normalize
     * @return The normalized URL with https:// prefix, or null if URL is invalid
     */
    @Nullable
    public static String normalizeUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String trimmedUrl = url.trim();

        // If URL doesn't start with http:// or https://, add https://
        if (!trimmedUrl.toLowerCase().startsWith("http://") &&
                !trimmedUrl.toLowerCase().startsWith("https://")) {
            trimmedUrl = "https://" + trimmedUrl;
        }

        // Validate the normalized URL
        if (isValidUrl(trimmedUrl)) {
            return trimmedUrl;
        }

        return null;
    }

    /**
     * Extract the domain from a URL.
     * @param url The URL to extract domain from
     * @return The domain, or null if URL is invalid
     */
    @Nullable
    public static String extractDomain(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null) {
            return null;
        }

        try {
            // Remove protocol
            String domain = normalizedUrl.replaceFirst("^(https?://)", "");
            // Remove path and query
            int slashIndex = domain.indexOf('/');
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }
            return domain;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a URL uses HTTPS protocol.
     * @param url The URL to check
     * @return true if URL uses HTTPS, false otherwise
     */
    public static boolean isHttps(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.trim().toLowerCase().startsWith("https://");
    }

    /**
     * Get the URL regex pattern.
     * Useful for input validation in UI components.
     * @return The URL regex pattern string
     */
    @NonNull
    public static String getUrlRegex() {
        return URL_REGEX;
    }
}
