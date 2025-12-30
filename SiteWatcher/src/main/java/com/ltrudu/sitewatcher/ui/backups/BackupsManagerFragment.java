package com.ltrudu.sitewatcher.ui.backups;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.util.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fragment for managing backup entries across all watched sites.
 * Shows a list of all stored content snapshots with options to view or delete them.
 */
public class BackupsManagerFragment extends Fragment implements BackupsAdapter.OnBackupClickListener {

    private static final String TAG = "BackupsManagerFragment";

    private BackupsViewModel viewModel;
    private BackupsAdapter adapter;

    // Views
    private FrameLayout loadingContainer;
    private LinearLayout emptyContainer;
    private RecyclerView recyclerBackups;

    private SimpleDateFormat dateFormat;

    public BackupsManagerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BackupsViewModel.class);
        dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_backups_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        loadingContainer = view.findViewById(R.id.loadingContainer);
        emptyContainer = view.findViewById(R.id.emptyContainer);
        recyclerBackups = view.findViewById(R.id.recyclerBackups);

        // Set up RecyclerView
        setupRecyclerView();

        // Observe ViewModel
        observeViewModel();
    }

    /**
     * Set up the RecyclerView with adapter and layout manager.
     */
    private void setupRecyclerView() {
        adapter = new BackupsAdapter();
        adapter.setOnBackupClickListener(this);

        recyclerBackups.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerBackups.setAdapter(adapter);
    }

    /**
     * Set up observers for ViewModel LiveData.
     */
    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), this::updateUIState);

        viewModel.getBackups().observe(getViewLifecycleOwner(), backups -> {
            adapter.submitList(backups);
        });

        viewModel.getDeleteSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                Toast.makeText(requireContext(), R.string.backup_deleted, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Update UI based on the current state.
     */
    private void updateUIState(@NonNull BackupsViewModel.BackupsState state) {
        loadingContainer.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.GONE);
        recyclerBackups.setVisibility(View.GONE);

        switch (state) {
            case LOADING:
                loadingContainer.setVisibility(View.VISIBLE);
                break;
            case CONTENT_READY:
                recyclerBackups.setVisibility(View.VISIBLE);
                break;
            case EMPTY:
                emptyContainer.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                emptyContainer.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onBackupClick(@NonNull BackupsViewModel.BackupItem backup) {
        // Navigate to diff viewer to see the content
        // For now, we can navigate to the diff viewer with the site ID
        Logger.d(TAG, "Backup clicked: " + backup.getHistoryId() + " for site: " + backup.getSiteId());

        // Navigate to diff viewer
        try {
            Bundle args = new Bundle();
            args.putLong("siteId", backup.getSiteId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_backups_to_diffViewer, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to diff viewer", e);
            Toast.makeText(requireContext(), R.string.error_navigation, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackupLongClick(@NonNull BackupsViewModel.BackupItem backup, @NonNull View view) {
        Logger.d(TAG, "Backup long-clicked: " + backup.getHistoryId());
        showContextMenu(backup, view);
    }

    @Override
    public void onDeleteClick(@NonNull BackupsViewModel.BackupItem backup) {
        showDeleteConfirmationDialog(backup);
    }

    /**
     * Show context menu for backup item.
     */
    private void showContextMenu(@NonNull BackupsViewModel.BackupItem backup, @NonNull View anchorView) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        popupMenu.inflate(R.menu.menu_backup_context);

        popupMenu.setOnMenuItemClickListener(item -> handleContextMenuClick(backup, item));
        popupMenu.show();
    }

    /**
     * Handle context menu item clicks.
     */
    private boolean handleContextMenuClick(@NonNull BackupsViewModel.BackupItem backup, @NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_view_rendered) {
            navigateToBackupViewer(backup.getHistoryId(), true);
            return true;
        } else if (itemId == R.id.action_view_source) {
            navigateToBackupViewer(backup.getHistoryId(), false);
            return true;
        } else if (itemId == R.id.action_delete_backup) {
            showDeleteConfirmationDialog(backup);
            return true;
        }

        return false;
    }

    /**
     * Navigate to backup viewer fragment.
     *
     * @param historyId    The history entry ID
     * @param showRendered Whether to show rendered HTML or source
     */
    private void navigateToBackupViewer(long historyId, boolean showRendered) {
        try {
            Bundle args = new Bundle();
            args.putLong("historyId", historyId);
            args.putBoolean("showRendered", showRendered);
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_backups_to_backupViewer, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to backup viewer", e);
            Toast.makeText(requireContext(), R.string.error_navigation, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before deleting a backup.
     */
    private void showDeleteConfirmationDialog(@NonNull BackupsViewModel.BackupItem backup) {
        String formattedDate = dateFormat.format(new Date(backup.getCheckTime()));
        String message = getString(R.string.confirm_delete_backup_message, backup.getSiteUrl(), formattedDate);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_backup)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    viewModel.deleteBackup(backup);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh backups when returning to this fragment
        viewModel.refresh();
    }
}
