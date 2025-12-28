package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and evaluates search queries with AND, OR, NOT keywords and parentheses.
 *
 * Syntax:
 * - Default (space separated): treated as AND
 * - AND: both terms must match (e.g., "term1 AND term2")
 * - OR: either term can match (e.g., "term1 OR term2")
 * - NOT: excludes items containing the term (e.g., "NOT term")
 * - Parentheses: group expressions (e.g., "(term1 OR term2) AND term3")
 *
 * Operator precedence (highest to lowest):
 * 1. NOT
 * 2. AND
 * 3. OR
 *
 * Examples:
 * - "google" - matches sites containing "google"
 * - "google AND news" - matches sites containing both "google" and "news"
 * - "google OR bing" - matches sites containing "google" or "bing"
 * - "NOT facebook" - matches sites NOT containing "facebook"
 * - "(google OR bing) AND news" - matches sites with ("google" or "bing") and "news"
 * - "NOT (facebook OR twitter)" - matches sites without "facebook" and without "twitter"
 */
public class SearchQueryParser {

    private static final String TAG = "SearchQueryParser";

    // Minimum characters before filter activates
    public static final int MIN_QUERY_LENGTH = 3;

    // Token types
    private enum TokenType {
        AND, OR, NOT, LPAREN, RPAREN, TERM, EOF
    }

    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, @Nullable String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + (value != null ? "(" + value + ")" : "");
        }
    }

    // Expression tree nodes
    private interface Expression {
        boolean evaluate(String searchText);
    }

    private static class TermExpression implements Expression {
        final String term;

        TermExpression(String term) {
            this.term = term.toLowerCase();
        }

        @Override
        public boolean evaluate(String searchText) {
            return searchText.contains(term);
        }
    }

    private static class NotExpression implements Expression {
        final Expression operand;

        NotExpression(Expression operand) {
            this.operand = operand;
        }

        @Override
        public boolean evaluate(String searchText) {
            return !operand.evaluate(searchText);
        }
    }

    private static class AndExpression implements Expression {
        final Expression left;
        final Expression right;

        AndExpression(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean evaluate(String searchText) {
            return left.evaluate(searchText) && right.evaluate(searchText);
        }
    }

    private static class OrExpression implements Expression {
        final Expression left;
        final Expression right;

        OrExpression(Expression left, Expression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean evaluate(String searchText) {
            return left.evaluate(searchText) || right.evaluate(searchText);
        }
    }

    // Parser state
    private List<Token> tokens;
    private int position;

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

        try {
            SearchQueryParser parser = new SearchQueryParser();
            Expression expression = parser.parse(trimmedQuery);

            if (expression == null) {
                return new ArrayList<>(sites);
            }

            List<WatchedSite> result = new ArrayList<>();
            for (WatchedSite site : sites) {
                String searchText = getSearchableText(site);
                if (expression.evaluate(searchText)) {
                    result.add(site);
                }
            }

            return result;
        } catch (Exception e) {
            Logger.w(TAG, "Failed to parse query: " + query + "\nException: " + e.getMessage());
            return new ArrayList<>(sites);
        }
    }

    /**
     * Parse a query string into an expression tree.
     */
    @Nullable
    private Expression parse(@NonNull String query) {
        tokens = tokenize(query);
        position = 0;

        if (tokens.isEmpty() || (tokens.size() == 1 && tokens.get(0).type == TokenType.EOF)) {
            return null;
        }

        return parseOrExpression();
    }

    /**
     * Tokenize the search query into operators, parentheses, and terms.
     */
    @NonNull
    private List<Token> tokenize(@NonNull String query) {
        List<Token> result = new ArrayList<>();

        // Pattern to match parentheses, AND, OR, NOT keywords, quoted strings, or words
        Pattern pattern = Pattern.compile(
            "\\(|\\)|\\bAND\\b|\\bOR\\b|\\bNOT\\b|\"[^\"]+\"|[^\\s()]+",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(query);

        while (matcher.find()) {
            String match = matcher.group();
            String upperMatch = match.toUpperCase();

            switch (upperMatch) {
                case "(":
                    result.add(new Token(TokenType.LPAREN, null));
                    break;
                case ")":
                    result.add(new Token(TokenType.RPAREN, null));
                    break;
                case "AND":
                    result.add(new Token(TokenType.AND, null));
                    break;
                case "OR":
                    result.add(new Token(TokenType.OR, null));
                    break;
                case "NOT":
                    result.add(new Token(TokenType.NOT, null));
                    break;
                default:
                    // Remove quotes if present
                    String term = match;
                    if (term.startsWith("\"") && term.endsWith("\"") && term.length() > 2) {
                        term = term.substring(1, term.length() - 1);
                    }
                    result.add(new Token(TokenType.TERM, term));
                    break;
            }
        }

        result.add(new Token(TokenType.EOF, null));
        return result;
    }

    /**
     * Get the current token.
     */
    private Token currentToken() {
        if (position >= tokens.size()) {
            return new Token(TokenType.EOF, null);
        }
        return tokens.get(position);
    }

    /**
     * Consume the current token and advance.
     */
    private Token consume() {
        Token token = currentToken();
        position++;
        return token;
    }

    /**
     * Check if current token matches the expected type.
     */
    private boolean match(TokenType type) {
        return currentToken().type == type;
    }

    /**
     * Parse OR expression (lowest precedence).
     * or_expr := and_expr (OR and_expr)*
     */
    @Nullable
    private Expression parseOrExpression() {
        Expression left = parseAndExpression();
        if (left == null) return null;

        while (match(TokenType.OR)) {
            consume(); // consume OR
            Expression right = parseAndExpression();
            if (right == null) return left;
            left = new OrExpression(left, right);
        }

        return left;
    }

    /**
     * Parse AND expression.
     * and_expr := not_expr (AND not_expr)*
     * Note: implicit AND between consecutive terms
     */
    @Nullable
    private Expression parseAndExpression() {
        Expression left = parseNotExpression();
        if (left == null) return null;

        while (true) {
            if (match(TokenType.AND)) {
                consume(); // consume AND
                Expression right = parseNotExpression();
                if (right == null) return left;
                left = new AndExpression(left, right);
            } else if (match(TokenType.TERM) || match(TokenType.LPAREN) || match(TokenType.NOT)) {
                // Implicit AND between consecutive terms/expressions
                Expression right = parseNotExpression();
                if (right == null) return left;
                left = new AndExpression(left, right);
            } else {
                break;
            }
        }

        return left;
    }

    /**
     * Parse NOT expression (highest precedence).
     * not_expr := NOT? primary
     */
    @Nullable
    private Expression parseNotExpression() {
        if (match(TokenType.NOT)) {
            consume(); // consume NOT
            Expression operand = parseNotExpression(); // NOT can chain: NOT NOT x
            if (operand == null) return null;
            return new NotExpression(operand);
        }

        return parsePrimary();
    }

    /**
     * Parse primary expression (term or parenthesized expression).
     * primary := TERM | LPAREN or_expr RPAREN
     */
    @Nullable
    private Expression parsePrimary() {
        if (match(TokenType.LPAREN)) {
            consume(); // consume (
            Expression expr = parseOrExpression();
            if (match(TokenType.RPAREN)) {
                consume(); // consume )
            }
            return expr;
        }

        if (match(TokenType.TERM)) {
            Token token = consume();
            return new TermExpression(token.value);
        }

        return null;
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
