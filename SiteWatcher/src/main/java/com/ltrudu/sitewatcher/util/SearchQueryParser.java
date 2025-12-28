package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and evaluates search queries with AND, OR, NOT keywords.
 *
 * Syntax:
 * - Default (space separated): treated as AND
 * - AND: both terms must match (e.g., "term1 AND term2")
 * - OR: either term can match (e.g., "term1 OR term2")
 * - NOT: excludes items containing the term (e.g., "NOT term")
 *
 * Examples:
 * - "google" - matches sites containing "google"
 * - "google AND news" - matches sites containing both "google" and "news"
 * - "google OR bing" - matches sites containing "google" or "bing"
 * - "NOT facebook" - matches sites NOT containing "facebook"
 * - "google AND NOT ads" - matches sites with "google" but not "ads"
 */
public class SearchQueryParser {

    private static final String TAG = "SearchQueryParser";

    // Minimum characters before filter activates
    public static final int MIN_QUERY_LENGTH = 3;

    // Token types
    private enum TokenType {
        AND, OR, NOT, TERM
    }

    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Filter a list of sites based on a search query.
     *
     * @param sites The list of sites to filter
     * @param query The search query
     * @return Filtered list of sites matching the query
     */
    @NonNull
    public static List<WatchedSite> filter(@NonNull List<WatchedSite> sites, @NonNull String query) {
        String trimmedQuery = query.trim();

        // Return all sites if query is too short
        if (trimmedQuery.length() < MIN_QUERY_LENGTH) {
            return new ArrayList<>(sites);
        }

        List<Token> tokens = tokenize(trimmedQuery);
        List<WatchedSite> result = new ArrayList<>();

        for (WatchedSite site : sites) {
            if (matchesSite(site, tokens)) {
                result.add(site);
            }
        }

        return result;
    }

    /**
     * Tokenize the search query into operators and terms.
     */
    @NonNull
    private static List<Token> tokenize(@NonNull String query) {
        List<Token> tokens = new ArrayList<>();

        // Pattern to match AND, OR, NOT keywords (case insensitive) or quoted strings or words
        Pattern pattern = Pattern.compile(
            "\\bAND\\b|\\bOR\\b|\\bNOT\\b|\"[^\"]+\"|\\S+",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(query);

        while (matcher.find()) {
            String match = matcher.group();
            String upperMatch = match.toUpperCase();

            switch (upperMatch) {
                case "AND":
                    tokens.add(new Token(TokenType.AND, null));
                    break;
                case "OR":
                    tokens.add(new Token(TokenType.OR, null));
                    break;
                case "NOT":
                    tokens.add(new Token(TokenType.NOT, null));
                    break;
                default:
                    // Remove quotes if present
                    String term = match;
                    if (term.startsWith("\"") && term.endsWith("\"") && term.length() > 2) {
                        term = term.substring(1, term.length() - 1);
                    }
                    tokens.add(new Token(TokenType.TERM, term.toLowerCase()));
                    break;
            }
        }

        return tokens;
    }

    /**
     * Check if a site matches the tokenized query.
     */
    private static boolean matchesSite(@NonNull WatchedSite site, @NonNull List<Token> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }

        // Get searchable text from site (title and URL)
        String searchText = getSearchableText(site);

        // Process tokens with operator precedence
        // NOT has highest precedence, then AND, then OR

        List<Boolean> results = new ArrayList<>();
        List<TokenType> operators = new ArrayList<>();

        boolean negateNext = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            switch (token.type) {
                case NOT:
                    negateNext = true;
                    break;

                case AND:
                    operators.add(TokenType.AND);
                    break;

                case OR:
                    operators.add(TokenType.OR);
                    break;

                case TERM:
                    boolean matches = searchText.contains(token.value);
                    if (negateNext) {
                        matches = !matches;
                        negateNext = false;
                    }
                    results.add(matches);
                    break;
            }
        }

        // If no terms, return true
        if (results.isEmpty()) {
            return true;
        }

        // Evaluate with operators (left to right, OR has lower precedence)
        // First pass: evaluate AND operations
        List<Boolean> afterAnd = new ArrayList<>();
        List<TokenType> afterAndOps = new ArrayList<>();

        afterAnd.add(results.get(0));

        for (int i = 0; i < operators.size() && i + 1 < results.size(); i++) {
            TokenType op = operators.get(i);
            boolean nextResult = results.get(i + 1);

            if (op == TokenType.AND) {
                // Combine with previous using AND
                int lastIdx = afterAnd.size() - 1;
                afterAnd.set(lastIdx, afterAnd.get(lastIdx) && nextResult);
            } else {
                // OR - keep separate for now
                afterAnd.add(nextResult);
                afterAndOps.add(TokenType.OR);
            }
        }

        // Second pass: evaluate OR operations
        boolean finalResult = afterAnd.get(0);
        for (int i = 1; i < afterAnd.size(); i++) {
            finalResult = finalResult || afterAnd.get(i);
        }

        return finalResult;
    }

    /**
     * Get the searchable text from a site (display name + URL).
     */
    @NonNull
    private static String getSearchableText(@NonNull WatchedSite site) {
        StringBuilder sb = new StringBuilder();

        // Add display name
        String displayName = site.getDisplayName();
        if (displayName != null) {
            sb.append(displayName.toLowerCase());
        }

        sb.append(" ");

        // Add URL
        String url = site.getUrl();
        if (url != null) {
            sb.append(url.toLowerCase());
        }

        return sb.toString();
    }

    /**
     * Check if a query is valid (has minimum length).
     */
    public static boolean isValidQuery(@NonNull String query) {
        return query.trim().length() >= MIN_QUERY_LENGTH;
    }
}
