package com.ltrudu.sitewatcher.ui.diff;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.util.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Fragment for displaying content differences between two versions of a watched site.
 * Shows added lines in green and removed lines in red with proper diff formatting.
 */
public class DiffViewerFragment extends Fragment {

    private static final String TAG = "DiffViewerFragment";
    private static final String ARG_SITE_ID = "siteId";

    private DiffViewerViewModel viewModel;

    // Views
    private FrameLayout loadingContainer;
    private LinearLayout emptyContainer;
    private LinearLayout errorContainer;
    private LinearLayout contentContainer;
    private TextView textError;
    private Button btnRetry;
    private TextView textSiteUrl;
    private TextView textOldTimestamp;
    private TextView textNewTimestamp;
    private TextView textAddedCount;
    private TextView textRemovedCount;
    private TextView textDiffContent;

    private View textDiffContainer;
    private MaterialButton btnToggleView;
    private LinearLayout changesOnlyContainer;
    private WebView webViewChangesOnly;

    private enum ViewMode { CHANGES_ONLY, TEXT_DIFF }
    private ViewMode currentViewMode = ViewMode.CHANGES_ONLY;
    private String oldHtmlContent;
    private String newHtmlContent;
    private ComparisonMode siteComparisonMode;
    private boolean cssIncludeSelectorEmpty;

    private int colorAdded;
    private int colorRemoved;
    private int colorAddedBackground;
    private int colorRemovedBackground;

    private SimpleDateFormat dateFormat;

    public DiffViewerFragment() {
        // Required empty public constructor
    }

    /**
     * Create a new instance of DiffViewerFragment.
     *
     * @param siteId The ID of the site to show diff for
     * @return A new instance of DiffViewerFragment
     */
    @NonNull
    public static DiffViewerFragment newInstance(long siteId) {
        DiffViewerFragment fragment = new DiffViewerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SITE_ID, siteId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DiffViewerViewModel.class);

        // Initialize colors
        colorAdded = ContextCompat.getColor(requireContext(), R.color.diff_added);
        colorRemoved = ContextCompat.getColor(requireContext(), R.color.diff_removed);
        colorAddedBackground = ContextCompat.getColor(requireContext(), R.color.diff_added_background);
        colorRemovedBackground = ContextCompat.getColor(requireContext(), R.color.diff_removed_background);

        // Initialize date format
        dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Get site ID from arguments
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_SITE_ID)) {
            long siteId = args.getLong(ARG_SITE_ID, -1);
            viewModel.setSiteId(siteId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diff_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        loadingContainer = view.findViewById(R.id.loadingContainer);
        emptyContainer = view.findViewById(R.id.emptyContainer);
        errorContainer = view.findViewById(R.id.errorContainer);
        contentContainer = view.findViewById(R.id.contentContainer);
        textError = view.findViewById(R.id.textError);
        btnRetry = view.findViewById(R.id.btnRetry);
        textSiteUrl = view.findViewById(R.id.textSiteUrl);
        textOldTimestamp = view.findViewById(R.id.textOldTimestamp);
        textNewTimestamp = view.findViewById(R.id.textNewTimestamp);
        textAddedCount = view.findViewById(R.id.textAddedCount);
        textRemovedCount = view.findViewById(R.id.textRemovedCount);
        textDiffContent = view.findViewById(R.id.textDiffContent);

        textDiffContainer = view.findViewById(R.id.textDiffContainer);
        btnToggleView = view.findViewById(R.id.btnToggleView);

        changesOnlyContainer = view.findViewById(R.id.changesOnlyContainer);
        webViewChangesOnly = view.findViewById(R.id.webViewChangesOnly);

        // Setup WebView for changes only view
        setupWebView(webViewChangesOnly);

        // Setup toggle button
        btnToggleView.setOnClickListener(v -> toggleViewMode());

        // Set up retry button
        btnRetry.setOnClickListener(v -> viewModel.retry());

        // Observe ViewModel
        observeViewModel();
    }

    /**
     * Set up observers for ViewModel LiveData.
     */
    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), this::updateUIState);

        viewModel.getDiffResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                displayDiffResult(result);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                textError.setText(message);
            }
        });
    }

    /**
     * Update UI based on the current state.
     */
    private void updateUIState(@NonNull DiffViewerViewModel.DiffState state) {
        loadingContainer.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        contentContainer.setVisibility(View.GONE);

        switch (state) {
            case LOADING:
                loadingContainer.setVisibility(View.VISIBLE);
                break;
            case CONTENT_READY:
                contentContainer.setVisibility(View.VISIBLE);
                break;
            case NO_PREVIOUS_VERSION:
                emptyContainer.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                errorContainer.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Display the diff result in the UI.
     */
    private void displayDiffResult(@NonNull DiffViewerViewModel.DiffResult result) {
        // Set site URL
        textSiteUrl.setText(result.getSiteUrl());

        // Store HTML content for WebView mode
        // Use filtered content for CSS_SELECTOR mode (excludes unwanted elements)
        // Fall back to raw content if filtered isn't available
        String filteredOld = result.getOldContentFiltered();
        String filteredNew = result.getNewContentFiltered();
        oldHtmlContent = (filteredOld != null && !filteredOld.isEmpty()) ? filteredOld : result.getOldContent();
        newHtmlContent = (filteredNew != null && !filteredNew.isEmpty()) ? filteredNew : result.getNewContent();

        // Store comparison mode and CSS include selector state
        siteComparisonMode = result.getComparisonMode();
        cssIncludeSelectorEmpty = result.isCssIncludeSelectorEmpty();

        // Set timestamps
        textOldTimestamp.setText(formatTimestamp(result.getOldTimestamp()));
        textNewTimestamp.setText(formatTimestamp(result.getNewTimestamp()));

        // Set change statistics
        textAddedCount.setText(getString(R.string.diff_added_lines, result.getAddedLines()));
        textRemovedCount.setText(getString(R.string.diff_removed_lines, result.getRemovedLines()));

        // Display formatted diff content (for TEXT_DIFF mode)
        displayFormattedDiff(result.getDiffText());

        // Set view mode based on comparison mode:
        // - FULL_HTML: CHANGES_ONLY and TEXT_DIFF available (RENDERED kept but not in rotation)
        // - CSS_SELECTOR with empty include: CHANGES_ONLY and TEXT_DIFF (full HTML minus excluded)
        // - CSS_SELECTOR with non-empty include: TEXT_DIFF only (partial HTML fragments)
        // - TEXT_ONLY: TEXT_DIFF only (no HTML to render)
        boolean supportsChangesOnly = (siteComparisonMode == ComparisonMode.FULL_HTML) ||
                (siteComparisonMode == ComparisonMode.CSS_SELECTOR && cssIncludeSelectorEmpty);

        if (supportsChangesOnly) {
            currentViewMode = ViewMode.CHANGES_ONLY;
            btnToggleView.setVisibility(View.VISIBLE);
        } else {
            // TEXT_ONLY and CSS_SELECTOR with includes only support TEXT_DIFF
            currentViewMode = ViewMode.TEXT_DIFF;
            btnToggleView.setVisibility(View.GONE);
        }

        // Update the view
        updateViewMode();
    }

    /**
     * Format a timestamp for display.
     */
    @NonNull
    private String formatTimestamp(long timestamp) {
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * Display the diff with color formatting.
     */
    private void displayFormattedDiff(@NonNull String diffText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = diffText.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int start = builder.length();

            if (line.startsWith("+ ")) {
                builder.append(line);
                int end = builder.length();
                builder.setSpan(new ForegroundColorSpan(colorAdded), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new BackgroundColorSpan(colorAddedBackground), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("- ")) {
                builder.append(line);
                int end = builder.length();
                builder.setSpan(new ForegroundColorSpan(colorRemoved), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new BackgroundColorSpan(colorRemovedBackground), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.append(line);
            }

            // Add newline for all lines except the last
            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }

        textDiffContent.setText(builder);
    }

    /**
     * Setup a WebView with appropriate settings.
     */
    private void setupWebView(@NonNull WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Fix for black screen / flickering issues
        webView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sw_background));
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Toggle between view modes: CHANGES_ONLY <-> TEXT_DIFF
     * Only available for modes that support Changes Only view.
     */
    private void toggleViewMode() {
        // Check if this mode supports toggling (FULL_HTML or CSS_SELECTOR with empty include)
        boolean supportsChangesOnly = (siteComparisonMode == ComparisonMode.FULL_HTML) ||
                (siteComparisonMode == ComparisonMode.CSS_SELECTOR && cssIncludeSelectorEmpty);

        if (!supportsChangesOnly) {
            return;
        }

        // Toggle between CHANGES_ONLY and TEXT_DIFF
        if (currentViewMode == ViewMode.CHANGES_ONLY) {
            currentViewMode = ViewMode.TEXT_DIFF;
        } else {
            currentViewMode = ViewMode.CHANGES_ONLY;
        }
        updateViewMode();
    }

    /**
     * Update the view based on current mode.
     */
    private void updateViewMode() {
        // Hide all containers first
        textDiffContainer.setVisibility(View.GONE);
        changesOnlyContainer.setVisibility(View.GONE);

        switch (currentViewMode) {
            case CHANGES_ONLY:
                changesOnlyContainer.setVisibility(View.VISIBLE);
                // Button shows next mode (TEXT_DIFF)
                btnToggleView.setText(R.string.show_text_diff);
                // Generate and load changes-only HTML
                String changesHtml = generateChangesOnlyHtml();
                webViewChangesOnly.loadDataWithBaseURL(null, changesHtml, "text/html", "UTF-8", null);
                break;

            case TEXT_DIFF:
                textDiffContainer.setVisibility(View.VISIBLE);
                // Button shows next mode (CHANGES_ONLY)
                btnToggleView.setText(R.string.show_changes_only);
                break;
        }
    }

    /**
     * Generate HTML showing the current version with changed elements highlighted.
     * Compares DOM elements between old and new HTML and highlights differences with red borders.
     */
    @NonNull
    private String generateChangesOnlyHtml() {
        if (oldHtmlContent == null || newHtmlContent == null) {
            return generateFallbackChangesHtml();
        }

        try {
            // Parse both HTML documents
            Document oldDoc = Jsoup.parse(oldHtmlContent);
            Document newDoc = Jsoup.parse(newHtmlContent);

            // Get text content from old document elements for comparison
            Set<String> oldTextContents = extractTextContents(oldDoc);

            // Mark changed elements in new document
            markChangedElements(newDoc, oldTextContents);

            // Inject CSS for highlighting changed elements
            injectHighlightStyles(newDoc);

            return newDoc.outerHtml();
        } catch (Exception e) {
            Logger.e(TAG, "Error generating changes-only HTML", e);
            return generateFallbackChangesHtml();
        }
    }

    /**
     * Extract all text contents from a document for comparison.
     */
    @NonNull
    private Set<String> extractTextContents(@NonNull Document doc) {
        Set<String> textContents = new HashSet<>();
        Elements allElements = doc.getAllElements();
        for (Element element : allElements) {
            String ownText = element.ownText().trim();
            if (!ownText.isEmpty()) {
                textContents.add(ownText);
            }
        }
        return textContents;
    }

    /**
     * Mark elements that have changed (new or modified text content) with a CSS class.
     */
    private void markChangedElements(@NonNull Document newDoc, @NonNull Set<String> oldTextContents) {
        Elements allElements = newDoc.getAllElements();
        for (Element element : allElements) {
            String ownText = element.ownText().trim();
            if (!ownText.isEmpty() && !oldTextContents.contains(ownText)) {
                // This text wasn't in the old version - mark as changed
                element.addClass("sw-changed-element");
            }
        }
    }

    /**
     * Inject CSS styles for highlighting changed elements.
     */
    private void injectHighlightStyles(@NonNull Document doc) {
        Element head = doc.head();
        if (head == null) {
            head = doc.appendElement("head");
        }

        // Add viewport meta for proper mobile rendering
        head.appendElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1.0");

        // Add highlight styles
        String highlightCss =
                ".sw-changed-element { " +
                "  outline: 3px solid #F44336 !important; " +
                "  outline-offset: 2px !important; " +
                "  background-color: rgba(244, 67, 54, 0.1) !important; " +
                "} " +
                ".sw-changed-element::before { " +
                "  content: 'CHANGED' !important; " +
                "  position: absolute !important; " +
                "  top: -20px !important; " +
                "  left: 0 !important; " +
                "  background: #F44336 !important; " +
                "  color: white !important; " +
                "  font-size: 10px !important; " +
                "  padding: 2px 6px !important; " +
                "  border-radius: 3px !important; " +
                "  font-family: sans-serif !important; " +
                "}";

        head.appendElement("style")
                .attr("type", "text/css")
                .text(highlightCss);
    }

    /**
     * Generate fallback HTML when DOM comparison fails.
     * Shows extracted text differences in a styled format.
     */
    @NonNull
    private String generateFallbackChangesHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("body { font-family: sans-serif; padding: 16px; margin: 0; }");
        html.append(".section { margin-bottom: 24px; }");
        html.append(".label { font-weight: bold; padding: 8px; margin-bottom: 8px; }");
        html.append(".removed { background-color: #FFEBEE; border: 2px solid #F44336; border-radius: 4px; padding: 12px; margin: 8px 0; }");
        html.append(".removed .label { background-color: #F44336; color: white; margin: -12px -12px 12px -12px; padding: 8px 12px; }");
        html.append(".added { background-color: #E8F5E9; border: 2px solid #4CAF50; border-radius: 4px; padding: 12px; margin: 8px 0; }");
        html.append(".added .label { background-color: #4CAF50; color: white; margin: -12px -12px 12px -12px; padding: 8px 12px; }");
        html.append(".content { white-space: pre-wrap; word-wrap: break-word; font-family: monospace; font-size: 12px; }");
        html.append("</style></head><body>");

        // Parse the diff text to extract added and removed lines
        if (viewModel.getDiffResult().getValue() != null) {
            String diffText = viewModel.getDiffResult().getValue().getDiffText();
            String[] lines = diffText.split("\n");

            StringBuilder removedLines = new StringBuilder();
            StringBuilder addedLines = new StringBuilder();

            for (String line : lines) {
                if (line.startsWith("- ")) {
                    if (removedLines.length() > 0) removedLines.append("\n");
                    removedLines.append(escapeHtml(line.substring(2)));
                } else if (line.startsWith("+ ")) {
                    if (addedLines.length() > 0) addedLines.append("\n");
                    addedLines.append(escapeHtml(line.substring(2)));
                }
            }

            // Show removed content (from previous version)
            if (removedLines.length() > 0) {
                html.append("<div class='removed'>");
                html.append("<div class='label'>Removed from previous version</div>");
                html.append("<div class='content'>").append(removedLines).append("</div>");
                html.append("</div>");
            }

            // Show added content (in current version)
            if (addedLines.length() > 0) {
                html.append("<div class='added'>");
                html.append("<div class='label'>Added in current version</div>");
                html.append("<div class='content'>").append(addedLines).append("</div>");
                html.append("</div>");
            }

            if (removedLines.length() == 0 && addedLines.length() == 0) {
                html.append("<p style='text-align:center; color: #666;'>No text changes detected</p>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Escape HTML special characters.
     */
    @NonNull
    private String escapeHtml(@NonNull String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
