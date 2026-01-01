package com.ltrudu.sitewatcher.ui.sitelist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.background.CheckScheduler;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.SearchQueryParser;

import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for displaying watched sites.
 * Handles site item display, click interactions, and filtering.
 */
public class SiteListAdapter extends RecyclerView.Adapter<SiteListAdapter.SiteViewHolder> {

    /**
     * Listener interface for site item click events.
     */
    public interface OnSiteClickListener {
        /**
         * Called when a site item is clicked.
         * @param site The clicked site
         */
        void onClick(@NonNull WatchedSite site);

        /**
         * Called when a site item is long-clicked.
         * @param site The long-clicked site
         * @param view The view that was clicked (for positioning popup menu)
         */
        void onLongClick(@NonNull WatchedSite site, @NonNull View view);
    }

    private final List<WatchedSite> allSites = new ArrayList<>();
    private final List<WatchedSite> sites = new ArrayList<>();
    private final OnSiteClickListener listener;
    private final Set<Long> checkingSites = new HashSet<>();
    private String currentFilter = "";

    /**
     * Constructor for SiteListAdapter.
     * @param listener The click listener for site items
     */
    public SiteListAdapter(@NonNull OnSiteClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SiteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_site, parent, false);
        return new SiteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SiteViewHolder holder, int position) {
        WatchedSite site = sites.get(position);
        holder.bind(site);
    }

    @Override
    public int getItemCount() {
        return sites.size();
    }

    /**
     * Update the list of sites with DiffUtil for efficient updates.
     * @param newSites The new list of sites
     */
    public void submitList(@Nullable List<WatchedSite> newSites) {
        if (newSites == null) {
            newSites = new ArrayList<>();
        }

        // Store all sites for filtering
        allSites.clear();
        allSites.addAll(newSites);

        // Apply current filter
        applyFilter();
    }

    /**
     * Filter the list based on a search query.
     * @param query The search query (supports AND, OR, NOT keywords)
     */
    public void filter(@NonNull String query) {
        currentFilter = query;
        applyFilter();
    }

    /**
     * Apply the current filter to the sites list.
     */
    private void applyFilter() {
        List<WatchedSite> filteredSites = SearchQueryParser.filter(allSites, currentFilter);
        updateDisplayList(filteredSites);
    }

    /**
     * Update the displayed list with DiffUtil for efficient updates.
     */
    private void updateDisplayList(@NonNull List<WatchedSite> newSites) {
        List<WatchedSite> oldSites = new ArrayList<>(sites);
        List<WatchedSite> finalNewSites = newSites;

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldSites.size();
            }

            @Override
            public int getNewListSize() {
                return finalNewSites.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldSites.get(oldItemPosition).getId() ==
                        finalNewSites.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                WatchedSite oldSite = oldSites.get(oldItemPosition);
                WatchedSite newSite = finalNewSites.get(newItemPosition);

                return oldSite.getUrl().equals(newSite.getUrl()) &&
                        oldSite.getLastCheckTime() == newSite.getLastCheckTime() &&
                        oldSite.getLastChangePercent() == newSite.getLastChangePercent() &&
                        ((oldSite.getLastError() == null && newSite.getLastError() == null) ||
                         (oldSite.getLastError() != null &&
                          oldSite.getLastError().equals(newSite.getLastError())));
            }
        });

        sites.clear();
        sites.addAll(newSites);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Check if there are any sites in the unfiltered list.
     * @return true if there are sites, false otherwise
     */
    public boolean hasAnySites() {
        return !allSites.isEmpty();
    }

    /**
     * Check if the current filter produced no results.
     * @return true if filter is active and no results, false otherwise
     */
    public boolean isFilterEmpty() {
        return SearchQueryParser.isValidQuery(currentFilter) && sites.isEmpty() && !allSites.isEmpty();
    }

    /**
     * Mark a site as currently being checked.
     * @param siteId The ID of the site being checked
     */
    public void setChecking(long siteId) {
        checkingSites.add(siteId);
        notifyItemChanged(findPositionById(siteId));
    }

    /**
     * Mark a site as no longer being checked.
     * @param siteId The ID of the site that finished checking
     */
    public void clearChecking(long siteId) {
        checkingSites.remove(siteId);
        notifyItemChanged(findPositionById(siteId));
    }

    /**
     * Mark all sites as being checked.
     */
    public void setAllChecking() {
        for (WatchedSite site : sites) {
            if (site.isEnabled()) {
                checkingSites.add(site.getId());
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Clear checking state for all sites.
     */
    public void clearAllChecking() {
        checkingSites.clear();
        notifyDataSetChanged();
    }

    /**
     * Check if a site is currently being checked.
     * @param siteId The ID of the site to check
     * @return true if the site is being checked
     */
    public boolean isChecking(long siteId) {
        return checkingSites.contains(siteId);
    }

    /**
     * Find the position of a site by its ID.
     * @param siteId The site ID to find
     * @return The position in the list, or -1 if not found
     */
    private int findPositionById(long siteId) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).getId() == siteId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Calculate the next check time for a site using the new multi-schedule system.
     * Delegates to CheckScheduler which evaluates all enabled schedules.
     * @param context The context for accessing CheckScheduler
     * @param site The site to calculate for
     * @return The next check time in milliseconds, or -1 if unable to calculate
     */
    private long calculateNextCheckTime(@NonNull Context context, @NonNull WatchedSite site) {
        if (!site.isEnabled()) {
            return -1;
        }
        return CheckScheduler.getInstance(context).calculateNextAlarmTime(site);
    }

    /**
     * ViewHolder for site items.
     */
    class SiteViewHolder extends RecyclerView.ViewHolder {

        private final ProgressBar progressChecking;
        private final TextView textUrl;
        private final TextView textLastCheck;
        private final TextView textNextCheck;
        private final TextView textError;
        private final TextView textChangePercent;

        SiteViewHolder(@NonNull View itemView) {
            super(itemView);
            progressChecking = itemView.findViewById(R.id.progressChecking);
            textUrl = itemView.findViewById(R.id.textUrl);
            textLastCheck = itemView.findViewById(R.id.textLastCheck);
            textNextCheck = itemView.findViewById(R.id.textNextCheck);
            textError = itemView.findViewById(R.id.textError);
            textChangePercent = itemView.findViewById(R.id.textChangePercent);
        }

        /**
         * Bind a site to this ViewHolder.
         * @param site The site to display
         */
        void bind(@NonNull WatchedSite site) {
            Context context = itemView.getContext();

            // Show/hide checking indicator
            boolean isCurrentlyChecking = checkingSites.contains(site.getId());
            progressChecking.setVisibility(isCurrentlyChecking ? View.VISIBLE : View.GONE);

            // Set URL or display name
            textUrl.setText(site.getDisplayName());

            // Set last check time
            if (site.getLastCheckTime() > 0) {
                long now = System.currentTimeMillis();
                long diffMs = now - site.getLastCheckTime();
                long diffMinutes = diffMs / (1000 * 60);
                long diffHours = diffMinutes / 60;
                long diffDays = diffHours / 24;

                if (diffMinutes < 1) {
                    textLastCheck.setText(R.string.last_check_just_now);
                } else if (diffMinutes < 60) {
                    textLastCheck.setText(context.getString(R.string.last_check_minutes, (int) diffMinutes));
                } else if (diffHours < 24) {
                    int hours = (int) diffHours;
                    int mins = (int) (diffMinutes % 60);
                    textLastCheck.setText(context.getString(R.string.last_check_hours, hours, mins));
                } else {
                    textLastCheck.setText(context.getString(R.string.last_check_days, (int) diffDays));
                }
            } else {
                textLastCheck.setText(R.string.never_checked);
            }

            // Set next check time
            if (site.isEnabled()) {
                long nextCheckTime = calculateNextCheckTime(context, site);
                if (nextCheckTime > 0) {
                    long now = System.currentTimeMillis();
                    long diffMs = nextCheckTime - now;
                    long diffMinutes = diffMs / (1000 * 60);
                    long diffHours = diffMinutes / 60;
                    long diffDays = diffHours / 24;

                    if (diffMinutes < 0) {
                        // Overdue
                        textNextCheck.setText(R.string.next_check_overdue);
                    } else if (diffMinutes < 1) {
                        // Less than a minute
                        textNextCheck.setText(R.string.next_check_soon);
                    } else if (diffMinutes < 60) {
                        // Less than an hour, show minutes
                        textNextCheck.setText(context.getString(R.string.next_check_in, (int) diffMinutes));
                    } else if (diffHours < 24) {
                        // Less than a day, show hours and minutes
                        int hours = (int) diffHours;
                        int mins = (int) (diffMinutes % 60);
                        textNextCheck.setText(context.getString(R.string.next_check_in_hours, hours, mins));
                    } else if (diffDays <= 3) {
                        // 1-3 days, show days
                        textNextCheck.setText(context.getString(R.string.next_check_in_days, (int) diffDays));
                    } else {
                        // More than 3 days, show date with time
                        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
                                "MMM d 'at' HH:mm", java.util.Locale.getDefault());
                        String dateStr = dateFormat.format(new java.util.Date(nextCheckTime));
                        textNextCheck.setText(context.getString(R.string.next_check_on, dateStr));
                    }
                    textNextCheck.setVisibility(View.VISIBLE);
                } else {
                    textNextCheck.setVisibility(View.GONE);
                }
            } else {
                textNextCheck.setVisibility(View.GONE);
            }

            // Set error state
            String error = site.getLastError();
            if (error != null && !error.isEmpty()) {
                textError.setVisibility(View.VISIBLE);
                textError.setText(error);
            } else {
                textError.setVisibility(View.GONE);
            }

            // Set change percentage
            float changePercent = site.getLastChangePercent();
            textChangePercent.setText(context.getString(R.string.change_percent, changePercent));

            // Set click listeners
            itemView.setOnClickListener(v -> listener.onClick(site));
            itemView.setOnLongClickListener(v -> {
                listener.onLongClick(site, v);
                return true;
            });
        }
    }
}
