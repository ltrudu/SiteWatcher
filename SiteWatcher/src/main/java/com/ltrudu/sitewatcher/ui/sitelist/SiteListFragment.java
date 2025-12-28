package com.ltrudu.sitewatcher.ui.sitelist;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Logger;
import com.ltrudu.sitewatcher.util.SearchQueryParser;

/**
 * Fragment displaying the list of watched sites.
 * Main screen of the application showing all monitored websites.
 */
public class SiteListFragment extends Fragment implements SiteListAdapter.OnSiteClickListener {

    private static final String TAG = "SiteListFragment";
    private static final long REFRESH_INTERVAL_MS = 60_000; // 1 minute
    private static final long SEARCH_DEBOUNCE_MS = 300; // 300ms debounce

    private SiteListViewModel viewModel;
    private SiteListAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private TextView emptyStateText;
    private TextView emptyStateHint;
    private FloatingActionButton fab;
    private TextInputEditText editSearch;

    // Handler for search debounce
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Handler for periodic UI refresh
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            // Refresh the adapter to update "Last check" times
            if (adapter != null) {
                adapter.notifyDataSetChanged();
                Logger.d(TAG, "Refreshed site list UI");
            }
            // Schedule next refresh
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SiteListViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_site_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupSearch();
        setupFab();
        observeData();

        Logger.d(TAG, "SiteListFragment view created");
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start periodic refresh timer
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        Logger.d(TAG, "Started refresh timer");
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop periodic refresh timer
        refreshHandler.removeCallbacks(refreshRunnable);
        // Cancel any pending search
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        Logger.d(TAG, "Stopped refresh timer");
    }

    /**
     * Initialize view references.
     */
    private void initViews(@NonNull View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyState = view.findViewById(R.id.emptyState);
        fab = view.findViewById(R.id.fab);
        editSearch = view.findViewById(R.id.editSearch);

        // Find text views inside empty state
        emptyStateText = emptyState.findViewById(R.id.emptyStateText);
        emptyStateHint = emptyState.findViewById(R.id.emptyStateHint);
    }

    /**
     * Set up the RecyclerView with adapter.
     */
    private void setupRecyclerView() {
        adapter = new SiteListAdapter(this);
        recyclerView.setAdapter(adapter);
    }

    /**
     * Set up the search functionality with debouncing.
     */
    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel any pending search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();

                // Create a new runnable for the search
                searchRunnable = () -> {
                    Logger.d(TAG, "Filtering with query: " + query);
                    adapter.filter(query);
                    updateEmptyState();
                };

                // Debounce: wait 300ms before executing
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    /**
     * Set up the FAB click listener.
     */
    private void setupFab() {
        fab.setOnClickListener(v -> navigateToAddEdit(-1L));
    }

    /**
     * Observe LiveData from ViewModel.
     */
    private void observeData() {
        viewModel.getAllSites().observe(getViewLifecycleOwner(), sites -> {
            Logger.d(TAG, "Sites updated: " + (sites != null ? sites.size() : 0) + " sites");
            adapter.submitList(sites);
            updateEmptyState();
        });
    }

    /**
     * Update the empty state visibility based on adapter state.
     */
    private void updateEmptyState() {
        boolean hasAnySites = adapter.hasAnySites();
        boolean isFilterEmpty = adapter.isFilterEmpty();
        boolean showEmptyState = !hasAnySites || isFilterEmpty;

        if (showEmptyState) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);

            // Update empty state text based on whether it's a filter result or no sites
            if (isFilterEmpty) {
                emptyStateText.setText(R.string.search_no_results);
                emptyStateHint.setVisibility(View.GONE);
            } else {
                emptyStateText.setText(R.string.no_sites);
                emptyStateHint.setVisibility(View.VISIBLE);
                emptyStateHint.setText(R.string.no_sites_hint);
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    /**
     * Navigate to the add/edit fragment.
     * @param siteId The site ID to edit, or -1 for add mode
     */
    private void navigateToAddEdit(long siteId) {
        Bundle args = new Bundle();
        args.putLong("siteId", siteId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_siteList_to_addEdit, args);
    }

    /**
     * Navigate to the diff viewer fragment.
     * @param siteId The site ID to view diffs for
     */
    private void navigateToDiffViewer(long siteId) {
        Bundle args = new Bundle();
        args.putLong("siteId", siteId);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_siteList_to_diffViewer, args);
    }

    @Override
    public void onClick(@NonNull WatchedSite site) {
        Logger.d(TAG, "Site clicked: " + site.getId());
        navigateToAddEdit(site.getId());
    }

    @Override
    public void onLongClick(@NonNull WatchedSite site, @NonNull View view) {
        Logger.d(TAG, "Site long-clicked: " + site.getId());
        showContextMenu(site, view);
    }

    /**
     * Show the context menu for a site.
     * @param site The site to show menu for
     * @param anchorView The view to anchor the popup menu to
     */
    private void showContextMenu(@NonNull WatchedSite site, @NonNull View anchorView) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        popupMenu.inflate(R.menu.menu_site_context);

        popupMenu.setOnMenuItemClickListener(item -> handleContextMenuClick(site, item));
        popupMenu.show();
    }

    /**
     * Handle context menu item clicks.
     * @param site The site the menu action is for
     * @param item The clicked menu item
     * @return true if handled
     */
    private boolean handleContextMenuClick(@NonNull WatchedSite site, @NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_edit) {
            navigateToAddEdit(site.getId());
            return true;
        } else if (itemId == R.id.action_duplicate) {
            viewModel.duplicateSite(site);
            return true;
        } else if (itemId == R.id.action_check_now) {
            viewModel.checkSiteNow(site);
            return true;
        } else if (itemId == R.id.action_view_diff) {
            navigateToDiffViewer(site.getId());
            return true;
        } else if (itemId == R.id.action_delete) {
            showDeleteConfirmation(site);
            return true;
        }

        return false;
    }

    /**
     * Show delete confirmation dialog.
     * @param site The site to potentially delete
     */
    private void showDeleteConfirmation(@NonNull WatchedSite site) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    viewModel.deleteSite(site);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
