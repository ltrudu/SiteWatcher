package com.ltrudu.sitewatcher.ui.dataviewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;

import java.text.DateFormat;
import java.util.Date;

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
    private LinearLayout renderedContainer;
    private LinearLayout rawDataContainer;
    private WebView webViewRendered;

    private DateFormat dateTimeFormat;
    private DataViewerViewModel.DataContent currentData;

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
        webViewRendered = view.findViewById(R.id.webViewRendered);

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

        // Set content for raw data views
        textContent.setText(result.getContent());
        textContentWrapped.setText(result.getContent());

        // Set comparison mode
        textMode.setText(getString(R.string.comparison_mode_label, result.getModeDescription()));

        // Set capture timestamp
        String formattedDate = formatTimestamp(result.getCapturedAt());
        textCaptured.setText(getString(R.string.data_captured_at, formattedDate));

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
                // Load HTML in WebView
                if (currentData != null) {
                    webViewRendered.loadDataWithBaseURL(null, currentData.getRawHtml(),
                            "text/html", "UTF-8", null);
                }
                break;

            case RAW_DATA:
                rawDataContainer.setVisibility(View.VISIBLE);
                // Button shows next mode (RENDERED) if rendering is supported
                if (currentData != null && currentData.supportsRendering()) {
                    btnToggleView.setText(R.string.show_rendered);
                }
                // Apply current word wrap setting
                boolean isWordWrapEnabled = checkWordWrap != null && checkWordWrap.isChecked();
                updateWordWrap(isWordWrapEnabled);
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
}
