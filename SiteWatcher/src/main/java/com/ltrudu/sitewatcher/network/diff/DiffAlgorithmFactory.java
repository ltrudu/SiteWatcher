package com.ltrudu.sitewatcher.network.diff;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.DiffAlgorithmType;

/**
 * Factory for creating diff algorithm instances.
 *
 * <p>This factory uses the Strategy pattern to allow selection of different
 * diff algorithms at runtime based on user preference or content type.</p>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>
 * // Get algorithm based on user preference
 * DiffAlgorithm algorithm = DiffAlgorithmFactory.create(site.getDiffAlgorithm());
 *
 * // Compute diff
 * DiffAlgorithm.DiffResult result = algorithm.computeDiff(oldContent, newContent);
 *
 * // Use results
 * float changePercent = result.getChangePercent();
 * String description = result.getDescription();
 * </pre>
 *
 * <h2>Algorithm Selection Guide:</h2>
 *
 * <table border="1">
 *   <tr><th>Content Type</th><th>Recommended Algorithm</th></tr>
 *   <tr><td>Code, HTML, structured</td><td>LINE (default)</td></tr>
 *   <tr><td>Articles, blog posts</td><td>WORD</td></tr>
 *   <tr><td>Prices, stock values</td><td>CHARACTER</td></tr>
 *   <tr><td>Unknown/general</td><td>LINE</td></tr>
 * </table>
 *
 * @see DiffAlgorithm
 * @see DiffAlgorithmType
 */
public final class DiffAlgorithmFactory {

    // Singleton instances for each algorithm (they're stateless)
    private static final LcsDiffAlgorithm LINE_ALGORITHM = new LcsDiffAlgorithm();
    private static final WordDiffAlgorithm WORD_ALGORITHM = new WordDiffAlgorithm();
    private static final CharacterDiffAlgorithm CHARACTER_ALGORITHM = new CharacterDiffAlgorithm();

    private DiffAlgorithmFactory() {
        // Prevent instantiation
    }

    /**
     * Create a diff algorithm instance based on the specified type.
     *
     * @param type The algorithm type to create
     * @return The corresponding DiffAlgorithm instance
     */
    @NonNull
    public static DiffAlgorithm create(@NonNull DiffAlgorithmType type) {
        switch (type) {
            case WORD:
                return WORD_ALGORITHM;
            case CHARACTER:
                return CHARACTER_ALGORITHM;
            case LINE:
            default:
                return LINE_ALGORITHM;
        }
    }

    /**
     * Get the default algorithm (LINE-based LCS).
     *
     * @return The default DiffAlgorithm instance
     */
    @NonNull
    public static DiffAlgorithm getDefault() {
        return LINE_ALGORITHM;
    }

    /**
     * Get all available algorithms.
     *
     * @return Array of all DiffAlgorithm instances
     */
    @NonNull
    public static DiffAlgorithm[] getAllAlgorithms() {
        return new DiffAlgorithm[]{
                LINE_ALGORITHM,
                WORD_ALGORITHM,
                CHARACTER_ALGORITHM
        };
    }
}
