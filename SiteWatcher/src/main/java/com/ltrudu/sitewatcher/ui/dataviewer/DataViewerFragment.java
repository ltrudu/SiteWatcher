package com.ltrudu.sitewatcher.ui.dataviewer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Fragment for viewing the comparison data of a watched site.
 * Displays the content that was captured during the last check along with
 * the comparison mode and timestamp information.
 *
 * For FULL_HTML or CSS_SELECTOR with empty include selector:
 * - Defaults to rendered WebView display
 * - Toggle button allows switching to raw source view
 *
 * For TEXT_ONLY or CSS_SELECTOR with defined include selector:
 * - Only displays raw data (no WebView rendering)
 */
public class DataViewerFragment extends Fragment {

    private static final String TAG = "DataViewerFragment";
    private static final String ARG_SITE_ID = "siteId";

    private DataViewerViewModel viewModel;

    // View modes
    private enum ViewMode { RENDERED, RAW_DATA }
    private ViewMode currentViewMode = ViewMode.RENDERED;

    // Views
    private ProgressBar progressLoading;
    private TextView textContent;
    private TextView textContentWrapped;
    private TextView textMode;
    private TextView textCaptured;
    private LinearLayout contentLayout;
    private LinearLayout emptyState;
    private LinearLayout errorLayout;
    private TextView errorText;
    private View retryButton;
    private CheckBox checkWordWrap;
    private HorizontalScrollView horizontalScrollView;
    private ScrollView wrappedScrollView;

    // New views for rendered mode
    private MaterialButton btnToggleView;
    private FrameLayout renderedContainer;
    private FrameLayout rawDataContainer;
    private LinearLayout rawDataContent;
    private ProgressBar rawDataLoading;
    private WebView webViewRendered;

    // Excluded elements views
    private LinearLayout excludedElementsRow;
    private TextView textExcludedCount;
    private MaterialButton btnViewExcluded;

    private DateFormat dateTimeFormat;
    private DataViewerViewModel.DataContent currentData;
    private Handler mainHandler;
    private ProgressBar webViewLoading;
    private boolean rawTextLoaded = false;

    public DataViewerFragment() {
        // Required empty public constructor
    }

    /**
     * Create a new instance of DataViewerFragment.
     *
     * @param siteId The ID of the site to view data for
     * @return A new instance of DataViewerFragment
     */
    @NonNull
    public static DataViewerFragment newInstance(long siteId) {
        DataViewerFragment fragment = new DataViewerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SITE_ID, siteId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DataViewerViewModel.class);

        // Initialize date format
        dateTimeFormat = DateFormat.getDateTimeInstance();

        // Get site ID from arguments
        Bundle args = getArguments();
        if (args != null) {
            long siteId = args.getLong(ARG_SITE_ID, -1L);
            if (siteId != -1L) {
                Logger.d(TAG, "Loading data for site ID: " + siteId);
                viewModel.setSiteId(siteId);
            } else {
                Logger.e(TAG, "Invalid site ID in arguments");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_data_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        progressLoading = view.findViewById(R.id.progressLoading);
        textContent = view.findViewById(R.id.textContent);
        textContentWrapped = view.findViewById(R.id.textContentWrapped);
        textMode = view.findViewById(R.id.textMode);
        textCaptured = view.findViewById(R.id.textCaptured);
        contentLayout = view.findViewById(R.id.contentLayout);
        emptyState = view.findViewById(R.id.emptyState);
        errorLayout = view.findViewById(R.id.errorLayout);
        errorText = view.findViewById(R.id.errorText);
        retryButton = view.findViewById(R.id.retryButton);
        checkWordWrap = view.findViewById(R.id.checkWordWrap);
        horizontalScrollView = view.findViewById(R.id.horizontalScrollView);
        wrappedScrollView = view.findViewById(R.id.wrappedScrollView);

        // New views for rendered mode
        btnToggleView = view.findViewById(R.id.btnToggleView);
        renderedContainer = view.findViewById(R.id.renderedContainer);
        rawDataContainer = view.findViewById(R.id.rawDataContainer);
        rawDataContent = view.findViewById(R.id.rawDataContent);
        rawDataLoading = view.findViewById(R.id.rawDataLoading);
        webViewRendered = view.findViewById(R.id.webViewRendered);
        webViewLoading = view.findViewById(R.id.webViewLoading);

        // Excluded elements views
        excludedElementsRow = view.findViewById(R.id.excludedElementsRow);
        textExcludedCount = view.findViewById(R.id.textExcludedCount);
        btnViewExcluded = view.findViewById(R.id.btnViewExcluded);

        // Initialize handler for async operations
        mainHandler = new Handler(Looper.getMainLooper());

        // Setup WebView
        setupWebView(webViewRendered);

        // Set up retry button
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> viewModel.retry());
        }

        // Set up word wrap checkbox
        if (checkWordWrap != null) {
            checkWordWrap.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateWordWrap(isChecked);
            });
        }

        // Set up toggle button
        if (btnToggleView != null) {
            btnToggleView.setOnClickListener(v -> toggleViewMode());
        }

        // Set up view excluded elements button
        if (btnViewExcluded != null) {
            btnViewExcluded.setOnClickListener(v -> showExcludedElementsDialog());
        }

        // Observe ViewModel
        observeViewModel();
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
        webView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sw_background));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Setup WebViewClient to handle page load events
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Hide loading indicator when page is loaded
                if (webViewLoading != null) {
                    webViewLoading.setVisibility(View.GONE);
                }
                Logger.d(TAG, "WebView page finished loading");
            }
        });
    }

    /**
     * Set up observers for ViewModel LiveData.
     */
    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), this::updateUIState);

        viewModel.getDataContent().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                displayDataContent(result);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && errorText != null) {
                errorText.setText(message);
            }
        });
    }

    /**
     * Update UI based on the current state.
     */
    private void updateUIState(@NonNull DataViewerViewModel.ViewerState state) {
        // Hide all views first
        progressLoading.setVisibility(View.GONE);
        contentLayout.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        if (errorLayout != null) {
            errorLayout.setVisibility(View.GONE);
        }

        switch (state) {
            case LOADING:
                progressLoading.setVisibility(View.VISIBLE);
                break;
            case CONTENT_READY:
                contentLayout.setVisibility(View.VISIBLE);
                break;
            case NO_DATA:
                emptyState.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                if (errorLayout != null) {
                    errorLayout.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    /**
     * Display the data content in the UI.
     */
    private void displayDataContent(@NonNull DataViewerViewModel.DataContent result) {
        // Store current data for view mode switching
        currentData = result;
        rawTextLoaded = false; // Reset for new data

        // DON'T set text content here - it blocks UI for large content
        // Text will be set lazily when switching to RAW_DATA mode

        // Set comparison mode
        textMode.setText(getString(R.string.comparison_mode_label, result.getModeDescription()));

        // Set capture timestamp
        String formattedDate = formatTimestamp(result.getCapturedAt());
        textCaptured.setText(getString(R.string.data_captured_at, formattedDate));

        // Show excluded elements count if there are any
        List<String> excludedSelectors = result.getExcludedSelectors();
        if (excludedSelectors != null && !excludedSelectors.isEmpty()) {
            excludedElementsRow.setVisibility(View.VISIBLE);
            int count = excludedSelectors.size();
            textExcludedCount.setText(getResources().getQuantityString(
                    R.plurals.elements_excluded_count, count, count));
        } else {
            excludedElementsRow.setVisibility(View.GONE);
        }

        // Determine initial view mode based on comparison mode
        if (result.supportsRendering()) {
            // FULL_HTML or CSS_SELECTOR with empty include: default to rendered view
            currentViewMode = ViewMode.RENDERED;
            btnToggleView.setVisibility(View.VISIBLE);
        } else {
            // TEXT_ONLY or CSS_SELECTOR with defined include: raw data only
            currentViewMode = ViewMode.RAW_DATA;
            btnToggleView.setVisibility(View.GONE);
        }

        // Update the view mode
        updateViewMode();
    }

    /**
     * Toggle between rendered and raw data view modes.
     */
    private void toggleViewMode() {
        if (currentViewMode == ViewMode.RENDERED) {
            currentViewMode = ViewMode.RAW_DATA;
        } else {
            currentViewMode = ViewMode.RENDERED;
        }
        updateViewMode();
    }

    /**
     * Update the view based on current mode.
     */
    private void updateViewMode() {
        // Hide all containers first
        renderedContainer.setVisibility(View.GONE);
        rawDataContainer.setVisibility(View.GONE);

        switch (currentViewMode) {
            case RENDERED:
                renderedContainer.setVisibility(View.VISIBLE);
                // Button shows next mode (RAW_DATA)
                btnToggleView.setText(R.string.show_source);
                // Load HTML in WebView asynchronously to avoid blocking main thread
                if (currentData != null) {
                    // Show loading indicator
                    if (webViewLoading != null) {
                        webViewLoading.setVisibility(View.VISIBLE);
                    }
                    // Use getHtmlForRendering() which returns filtered HTML if exclude filter is applied
                    final String htmlToRender = currentData.getHtmlForRendering();
                    // Defer WebView loading to allow UI to respond (e.g., back button)
                    mainHandler.post(() -> {
                        if (webViewRendered != null && isAdded()) {
                            webViewRendered.loadDataWithBaseURL(null, htmlToRender,
                                    "text/html", "UTF-8", null);
                        }
                    });
                }
                break;

            case RAW_DATA:
                rawDataContainer.setVisibility(View.VISIBLE);
                // Button shows next mode (RENDERED) if rendering is supported
                if (currentData != null && currentData.supportsRendering()) {
                    btnToggleView.setText(R.string.show_rendered);
                }
                // Lazily load raw text content (only once)
                if (!rawTextLoaded && currentData != null) {
                    // Show loading indicator, hide content
                    if (rawDataLoading != null) {
                        rawDataLoading.setVisibility(View.VISIBLE);
                    }
                    if (rawDataContent != null) {
                        rawDataContent.setVisibility(View.GONE);
                    }

                    final String content = currentData.getContent();
                    // Use postDelayed to ensure loading indicator is visible before heavy operation
                    mainHandler.postDelayed(() -> {
                        if (isAdded() && textContent != null && textContentWrapped != null) {
                            textContent.setText(content);
                            textContentWrapped.setText(content);
                            rawTextLoaded = true;

                            // Hide loading indicator, show content
                            if (rawDataLoading != null) {
                                rawDataLoading.setVisibility(View.GONE);
                            }
                            if (rawDataContent != null) {
                                rawDataContent.setVisibility(View.VISIBLE);
                            }
                            // Apply word wrap after content is loaded
                            boolean wordWrapEnabled = checkWordWrap != null && checkWordWrap.isChecked();
                            updateWordWrap(wordWrapEnabled);
                        }
                    }, 100); // Small delay to allow loading indicator to show
                } else {
                    // Content already loaded, just show it
                    if (rawDataLoading != null) {
                        rawDataLoading.setVisibility(View.GONE);
                    }
                    if (rawDataContent != null) {
                        rawDataContent.setVisibility(View.VISIBLE);
                    }
                    // Apply current word wrap setting
                    boolean isWordWrapEnabled = checkWordWrap != null && checkWordWrap.isChecked();
                    updateWordWrap(isWordWrapEnabled);
                }
                break;
        }
    }

    /**
     * Update the word wrap state.
     * Toggles between horizontal scrolling (no wrap) and word wrap modes.
     *
     * @param enabled true to enable word wrap, false to disable
     */
    private void updateWordWrap(boolean enabled) {
        if (horizontalScrollView != null && wrappedScrollView != null) {
            if (enabled) {
                // Word wrap enabled: show wrapped scroll view, hide horizontal scroll
                horizontalScrollView.setVisibility(View.GONE);
                wrappedScrollView.setVisibility(View.VISIBLE);
            } else {
                // Word wrap disabled: show horizontal scroll, hide wrapped scroll view
                horizontalScrollView.setVisibility(View.VISIBLE);
                wrappedScrollView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Format a timestamp for display using DateFormat.getDateTimeInstance().
     */
    @NonNull
    private String formatTimestamp(long timestamp) {
        return dateTimeFormat.format(new Date(timestamp));
    }

    /**
     * Show dialog with the list of excluded CSS selectors.
     */
    private void showExcludedElementsDialog() {
        if (currentData == null) return;

        List<String> excludedSelectors = currentData.getExcludedSelectors();
        if (excludedSelectors == null || excludedSelectors.isEmpty()) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_view_excluded_elements, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerExcludedElements);
        TextView emptyState = dialogView.findViewById(R.id.textEmptyState);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnDialogClose);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        ExcludedElementsAdapter adapter = new ExcludedElementsAdapter(excludedSelectors);
        recyclerView.setAdapter(adapter);

        // Show empty state if needed
        if (excludedSelectors.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        }

        // Create and show dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Adapter for displaying excluded CSS selectors in a read-only list.
     */
    private static class ExcludedElementsAdapter extends RecyclerView.Adapter<ExcludedElementsAdapter.ViewHolder> {

        private final List<String> selectors;

        ExcludedElementsAdapter(List<String> selectors) {
            this.selectors = selectors;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_excluded_element, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textSelector.setText(selectors.get(position));
        }

        @Override
        public int getItemCount() {
            return selectors.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textSelector;

            ViewHolder(View itemView) {
                super(itemView);
                textSelector = itemView.findViewById(R.id.textSelector);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up handler callbacks
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        // Stop WebView loading
        if (webViewRendered != null) {
            webViewRendered.stopLoading();
        }
    }
}
