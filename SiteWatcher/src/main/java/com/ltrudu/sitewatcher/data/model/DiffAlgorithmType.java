package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;

/**
 * Enum representing available diff algorithms for content comparison.
 *
 * <p>Each algorithm has different characteristics suited for different types of content:</p>
 *
 * <ul>
 *   <li>{@link #LINE} - Line-based LCS diff (default, like Git)</li>
 *   <li>{@link #WORD} - Word-based diff (better for prose/articles)</li>
 *   <li>{@link #CHARACTER} - Character-based diff (most sensitive)</li>
 * </ul>
 */
public enum DiffAlgorithmType {

    /**
     * Line-based diff using LCS algorithm.
     * This is the default and most common approach, used by Git and other diff tools.
     * Best for: Code, structured content, general purpose.
     */
    LINE("Line-based (Default)", "Standard diff like Git. Compares whole lines."),

    /**
     * Word-based diff using LCS algorithm.
     * Tokenizes content into words and compares at word level.
     * Best for: Articles, blog posts, product descriptions.
     */
    WORD("Word-based", "Compares individual words. Better for text/prose."),

    /**
     * Character-based diff using LCS algorithm.
     * Most granular comparison at the character level.
     * Best for: Prices, numbers, detecting typos.
     */
    CHARACTER("Character-based", "Compares individual characters. Most sensitive.");

    private final String displayName;
    private final String description;

    DiffAlgorithmType(@NonNull String displayName, @NonNull String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Get the display name for UI.
     */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description for UI.
     */
    @NonNull
    public String getDescription() {
        return description;
    }

    /**
     * Get all display names for use in a spinner adapter.
     */
    @NonNull
    public static String[] getDisplayNames() {
        DiffAlgorithmType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getDisplayName();
        }
        return names;
    }
}
