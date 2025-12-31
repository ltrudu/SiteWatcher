package com.ltrudu.sitewatcher.ui.backups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.database.SiteWatcherDatabase;
import com.ltrudu.sitewatcher.data.dao.SiteHistoryDao;
import com.ltrudu.sitewatcher.data.dao.WatchedSiteDao;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for viewing backup content in a WebView.
 * Can display the content as rendered HTML or as source code.
 */
public class BackupViewerFragment extends Fragment {

    private static final String TAG = "BackupViewerFragment";
    private static final String ARG_HISTORY_ID = "historyId";
    private static final String ARG_SHOW_RENDERED = "showRendered";

    // Views
    private FrameLayout loadingContainer;
    private LinearLayout errorContainer;
    private LinearLayout contentContainer;
    private TextView textError;
    private Button btnRetry;
    private TextView textSiteUrl;
    private TextView textTimestamp;
    private TextView textViewMode;
    private MaterialButton btnToggleView;
    private WebView webViewContent;
    private ScrollView sourceScrollView;
    private TextView textSourceContent;

    private SiteHistoryDao siteHistoryDao;
    private WatchedSiteDao watchedSiteDao;
    private ExecutorService executorService;
    private SimpleDateFormat dateFormat;

    private long historyId = -1;
    private boolean showRendered = true;
    private String htmlContent;

    public BackupViewerFragment() {
        // Required empty public constructor
    }

    /**
     * Create a new instance of BackupViewerFragment.
     *
     * @param historyId    The ID of the history entry to view
     * @param showRendered Whether to show rendered HTML (true) or source (false)
     * @return A new instance of BackupViewerFragment
     */
    @NonNull
    public static BackupViewerFragment newInstance(long historyId, boolean showRendered) {
        BackupViewerFragment fragment = new BackupViewerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_HISTORY_ID, historyId);
        args.putBoolean(ARG_SHOW_RENDERED, showRendered);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SiteWatcherDatabase database = SiteWatcherDatabase.getInstance(requireContext());
        siteHistoryDao = database.siteHistoryDao();
        watchedSiteDao = database.watchedSiteDao();
        executorService = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Get arguments
        Bundle args = getArguments();
        if (args != null) {
            historyId = args.getLong(ARG_HISTORY_ID, -1);
            showRendered = args.getBoolean(ARG_SHOW_RENDERED, true);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_backup_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        loadingContainer = view.findViewById(R.id.loadingContainer);
        errorContainer = view.findViewById(R.id.errorContainer);
        contentContainer = view.findViewById(R.id.contentContainer);
        textError = view.findViewById(R.id.textError);
        btnRetry = view.findViewById(R.id.btnRetry);
        textSiteUrl = view.findViewById(R.id.textSiteUrl);
        textTimestamp = view.findViewById(R.id.textTimestamp);
        textViewMode = view.findViewById(R.id.textViewMode);
        btnToggleView = view.findViewById(R.id.btnToggleView);
        webViewContent = view.findViewById(R.id.webViewContent);
        sourceScrollView = view.findViewById(R.id.sourceScrollView);
        textSourceContent = view.findViewById(R.id.textSourceContent);

        // Setup WebView
        setupWebView();

        // Setup button listeners
        btnRetry.setOnClickListener(v -> loadBackupContent());
        btnToggleView.setOnClickListener(v -> toggleViewMode());

        // Load content
        loadBackupContent();
    }

    /**
     * Setup WebView with appropriate settings.
     */
    private void setupWebView() {
        WebSettings settings = webViewContent.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Fix for black screen / flickering issues
        webViewContent.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.sw_background));
        webViewContent.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Load the backup content from the history entry.
     */
    private void loadBackupContent() {
        if (historyId < 0) {
            showError("Invalid backup ID");
            return;
        }

        showLoading();

        executorService.execute(() -> {
            try {
                // Get the history entry
                SiteHistory history = siteHistoryDao.getById(historyId);
                if (history == null) {
                    requireActivity().runOnUiThread(() -> showError("Backup not found"));
                    return;
                }

                // Get the site info
                WatchedSite site = watchedSiteDao.getById(history.getSiteId());
                String siteUrl = site != null ? site.getUrl() : "Unknown site";

                // Load content from file
                String content = loadContentFromFile(history.getStoragePath());
                if (content == null) {
                    requireActivity().runOnUiThread(() -> showError("Failed to load backup content"));
                    return;
                }

                htmlContent = content;

                // Update UI on main thread
                final String url = siteUrl;
                final long checkTime = history.getCheckTime();

                requireActivity().runOnUiThread(() -> {
                    textSiteUrl.setText(url);
                    textTimestamp.setText(dateFormat.format(new Date(checkTime)));
                    displayContent();
                    showContent();
                });

            } catch (Exception e) {
                Logger.e(TAG, "Error loading backup content", e);
                requireActivity().runOnUiThread(() -> showError(e.getMessage()));
            }
        });
    }

    /**
     * Load content from a file path.
     */
    @Nullable
    private String loadContentFromFile(@Nullable String storagePath) {
        if (storagePath == null || storagePath.isEmpty()) {
            Logger.w(TAG, "Storage path is null or empty");
            return null;
        }

        File contentFile = new File(storagePath);
        if (!contentFile.exists()) {
            Logger.w(TAG, "Content file not found: " + storagePath);
            return null;
        }

        try {
            StringBuilder content = new StringBuilder();
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(contentFile), StandardCharsets.UTF_8)) {
                char[] buffer = new char[8192];
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, charsRead);
                }
            }
            return content.toString();
        } catch (IOException e) {
            Logger.e(TAG, "Error reading content file: " + storagePath, e);
            return null;
        }
    }

    /**
     * Display content based on current view mode.
     */
    private void displayContent() {
        if (htmlContent == null) {
            return;
        }

        if (showRendered) {
            // Show rendered HTML in WebView
            webViewContent.setVisibility(View.VISIBLE);
            sourceScrollView.setVisibility(View.GONE);
            textViewMode.setText(R.string.showing_rendered);
            webViewContent.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
        } else {
            // Show source code in TextView
            webViewContent.setVisibility(View.GONE);
            sourceScrollView.setVisibility(View.VISIBLE);
            textViewMode.setText(R.string.showing_source);
            textSourceContent.setText(htmlContent);
        }
    }

    /**
     * Toggle between rendered and source view modes.
     */
    private void toggleViewMode() {
        showRendered = !showRendered;
        displayContent();
    }

    /**
     * Show loading state.
     */
    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
        contentContainer.setVisibility(View.GONE);
    }

    /**
     * Show error state.
     */
    private void showError(@Nullable String message) {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
        if (message != null) {
            textError.setText(message);
        }
    }

    /**
     * Show content state.
     */
    private void showContent() {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
