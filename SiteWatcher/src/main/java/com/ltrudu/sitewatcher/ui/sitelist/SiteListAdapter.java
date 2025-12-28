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
import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * RecyclerView adapter for displaying watched sites.
 * Handles site item display and click interactions.
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

    private final List<WatchedSite> sites = new ArrayList<>();
    private final OnSiteClickListener listener;

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
     * Calculate the next check time for a site.
     * @param site The site to calculate for
     * @return The next check time in milliseconds, or -1 if unable to calculate
     */
    private long calculateNextCheckTime(@NonNull WatchedSite site) {
        if (!site.isEnabled()) {
            return -1;
        }

        Calendar now = Calendar.getInstance();

        if (site.getScheduleType() == ScheduleType.PERIODIC) {
            return calculatePeriodicNextCheck(site, now);
        } else {
            return calculateSpecificHourNextCheck(site, now);
        }
    }

    /**
     * Calculate next check time for periodic schedule.
     */
    private long calculatePeriodicNextCheck(@NonNull WatchedSite site, @NonNull Calendar now) {
        int intervalMinutes = site.getPeriodicIntervalMinutes();
        if (intervalMinutes <= 0) {
            intervalMinutes = 60;
        }

        Calendar nextCheck = (Calendar) now.clone();

        if (site.getLastCheckTime() > 0) {
            nextCheck.setTimeInMillis(site.getLastCheckTime());
            nextCheck.add(Calendar.MINUTE, intervalMinutes);

            // If next check is in the past, calculate from now
            if (nextCheck.before(now)) {
                nextCheck = (Calendar) now.clone();
                nextCheck.add(Calendar.MINUTE, intervalMinutes);
            }
        } else {
            // Never checked, next check is interval from now
            nextCheck.add(Calendar.MINUTE, intervalMinutes);
        }

        return adjustForEnabledDays(site, nextCheck, intervalMinutes);
    }

    /**
     * Calculate next check time for specific hour schedule.
     */
    private long calculateSpecificHourNextCheck(@NonNull WatchedSite site, @NonNull Calendar now) {
        Calendar nextCheck = (Calendar) now.clone();
        nextCheck.set(Calendar.HOUR_OF_DAY, site.getScheduleHour());
        nextCheck.set(Calendar.MINUTE, site.getScheduleMinute());
        nextCheck.set(Calendar.SECOND, 0);
        nextCheck.set(Calendar.MILLISECOND, 0);

        if (nextCheck.before(now)) {
            nextCheck.add(Calendar.DAY_OF_YEAR, 1);
        }

        return findNextEnabledDay(site, nextCheck);
    }

    /**
     * Adjust for enabled days (periodic schedule).
     */
    private long adjustForEnabledDays(@NonNull WatchedSite site, @NonNull Calendar check, int intervalMinutes) {
        int enabledDays = site.getEnabledDays();
        if (enabledDays == WatchedSite.ALL_DAYS) {
            return check.getTimeInMillis();
        }

        int attempts = 0;
        while (attempts < 7) {
            int dayOfWeek = check.get(Calendar.DAY_OF_WEEK);
            int dayBitmask = getDayBitmask(dayOfWeek);

            if ((enabledDays & dayBitmask) != 0) {
                return check.getTimeInMillis();
            }

            check.add(Calendar.DAY_OF_YEAR, 1);
            check.set(Calendar.HOUR_OF_DAY, 0);
            check.set(Calendar.MINUTE, 0);
            check.set(Calendar.SECOND, 0);
            check.add(Calendar.MINUTE, intervalMinutes);
            attempts++;
        }

        return check.getTimeInMillis();
    }

    /**
     * Find next enabled day (specific hour schedule).
     */
    private long findNextEnabledDay(@NonNull WatchedSite site, @NonNull Calendar check) {
        int enabledDays = site.getEnabledDays();
        if (enabledDays == WatchedSite.ALL_DAYS) {
            return check.getTimeInMillis();
        }

        int attempts = 0;
        while (attempts < 7) {
            int dayOfWeek = check.get(Calendar.DAY_OF_WEEK);
            int dayBitmask = getDayBitmask(dayOfWeek);

            if ((enabledDays & dayBitmask) != 0) {
                return check.getTimeInMillis();
            }

            check.add(Calendar.DAY_OF_YEAR, 1);
            attempts++;
        }

        return check.getTimeInMillis();
    }

    /**
     * Convert Calendar day of week to WatchedSite day bitmask.
     */
    private int getDayBitmask(int calendarDay) {
        switch (calendarDay) {
            case Calendar.SUNDAY: return WatchedSite.SUNDAY;
            case Calendar.MONDAY: return WatchedSite.MONDAY;
            case Calendar.TUESDAY: return WatchedSite.TUESDAY;
            case Calendar.WEDNESDAY: return WatchedSite.WEDNESDAY;
            case Calendar.THURSDAY: return WatchedSite.THURSDAY;
            case Calendar.FRIDAY: return WatchedSite.FRIDAY;
            case Calendar.SATURDAY: return WatchedSite.SATURDAY;
            default: return 0;
        }
    }

    /**
     * ViewHolder for site items.
     */
    class SiteViewHolder extends RecyclerView.ViewHolder {

        private final TextView textUrl;
        private final TextView textLastCheck;
        private final TextView textNextCheck;
        private final TextView textError;
        private final TextView textChangePercent;

        SiteViewHolder(@NonNull View itemView) {
            super(itemView);
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
                long nextCheckTime = calculateNextCheckTime(site);
                if (nextCheckTime > 0) {
                    long now = System.currentTimeMillis();
                    long diffMs = nextCheckTime - now;
                    long diffMinutes = diffMs / (1000 * 60);

                    if (diffMinutes < 0) {
                        // Overdue
                        textNextCheck.setText(R.string.next_check_overdue);
                    } else if (diffMinutes < 1) {
                        // Less than a minute
                        textNextCheck.setText(R.string.next_check_soon);
                    } else if (diffMinutes < 60) {
                        // Less than an hour, show minutes
                        textNextCheck.setText(context.getString(R.string.next_check_in, (int) diffMinutes));
                    } else {
                        // More than an hour, show hours and minutes
                        int hours = (int) (diffMinutes / 60);
                        int mins = (int) (diffMinutes % 60);
                        textNextCheck.setText(context.getString(R.string.next_check_in_hours, hours, mins));
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
