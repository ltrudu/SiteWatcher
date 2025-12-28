package com.ltrudu.sitewatcher.ui.dataviewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;

import java.text.DateFormat;
import java.util.Date;

/**
 * Fragment for viewing the comparison data of a watched site.
 * Displays the content that was captured during the last check along with
 * the comparison mode and timestamp information.
 */
public class DataViewerFragment extends Fragment {

    private static final String TAG = "DataViewerFragment";
    private static final String ARG_SITE_ID = "siteId";

    private DataViewerViewModel viewModel;

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

    private DateFormat dateTimeFormat;
    private String currentContent;

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

        // Observe ViewModel
        observeViewModel();
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
        // Store and set content
        currentContent = result.getContent();
        textContent.setText(currentContent);
        textContentWrapped.setText(currentContent);

        // Set comparison mode
        textMode.setText(getString(R.string.comparison_mode_label, result.getModeDescription()));

        // Set capture timestamp
        String formattedDate = formatTimestamp(result.getCapturedAt());
        textCaptured.setText(getString(R.string.data_captured_at, formattedDate));

        // Apply current word wrap setting
        boolean isWordWrapEnabled = checkWordWrap != null && checkWordWrap.isChecked();
        updateWordWrap(isWordWrapEnabled);
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
