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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

        // Set timestamps
        textOldTimestamp.setText(formatTimestamp(result.getOldTimestamp()));
        textNewTimestamp.setText(formatTimestamp(result.getNewTimestamp()));

        // Set change statistics
        textAddedCount.setText(getString(R.string.diff_added_lines, result.getAddedLines()));
        textRemovedCount.setText(getString(R.string.diff_removed_lines, result.getRemovedLines()));

        // Display formatted diff content
        displayFormattedDiff(result.getDiffText());
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
}
