package com.ltrudu.sitewatcher.ui.backups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.ui.backups.BackupsViewModel.BackupItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying backup items.
 * Uses DiffUtil for efficient list updates.
 */
public class BackupsAdapter extends ListAdapter<BackupItem, BackupsAdapter.BackupViewHolder> {

    /**
     * Callback interface for backup item interactions.
     */
    public interface OnBackupClickListener {
        /**
         * Called when a backup item is clicked (to view content).
         *
         * @param backup The clicked backup item
         */
        void onBackupClick(@NonNull BackupItem backup);

        /**
         * Called when the delete button is clicked.
         *
         * @param backup The backup item to delete
         */
        void onDeleteClick(@NonNull BackupItem backup);
    }

    private static final DiffUtil.ItemCallback<BackupItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BackupItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull BackupItem oldItem, @NonNull BackupItem newItem) {
                    return oldItem.getHistoryId() == newItem.getHistoryId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull BackupItem oldItem, @NonNull BackupItem newItem) {
                    return oldItem.getHistoryId() == newItem.getHistoryId()
                            && oldItem.getCheckTime() == newItem.getCheckTime()
                            && oldItem.getContentSize() == newItem.getContentSize()
                            && oldItem.getSiteUrl().equals(newItem.getSiteUrl());
                }
            };

    @Nullable
    private OnBackupClickListener listener;
    private final SimpleDateFormat dateFormat;

    public BackupsAdapter() {
        super(DIFF_CALLBACK);
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
    }

    /**
     * Set the click listener for backup items.
     *
     * @param listener The listener to set
     */
    public void setOnBackupClickListener(@Nullable OnBackupClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public BackupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_backup, parent, false);
        return new BackupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BackupViewHolder holder, int position) {
        BackupItem backup = getItem(position);
        holder.bind(backup);
    }

    /**
     * ViewHolder for backup items.
     */
    class BackupViewHolder extends RecyclerView.ViewHolder {
        private final TextView textSiteUrl;
        private final TextView textDate;
        private final TextView textSize;
        private final ImageButton btnDelete;

        BackupViewHolder(@NonNull View itemView) {
            super(itemView);
            textSiteUrl = itemView.findViewById(R.id.textSiteUrl);
            textDate = itemView.findViewById(R.id.textDate);
            textSize = itemView.findViewById(R.id.textSize);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(@NonNull BackupItem backup) {
            // Set site URL
            textSiteUrl.setText(backup.getSiteUrl());

            // Set formatted date
            textDate.setText(dateFormat.format(new Date(backup.getCheckTime())));

            // Set formatted size
            textSize.setText(formatSize(backup.getContentSize()));

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBackupClick(backup);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(backup);
                }
            });
        }

        /**
         * Format file size for display.
         *
         * @param bytes Size in bytes
         * @return Formatted size string (e.g., "1.5 KB", "2.3 MB")
         */
        @NonNull
        private String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}
