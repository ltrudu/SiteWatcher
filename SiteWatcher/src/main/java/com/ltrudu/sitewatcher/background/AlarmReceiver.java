package com.ltrudu.sitewatcher.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.network.SiteChecker;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver that handles alarm triggers for site checks.
 * Receives the alarm, starts the check execution, and reschedules the next check.
 *
 * This receiver works independently without callbacks, directly accessing
 * the database and executing checks. This ensures checks work even when
 * the app process was killed by the system.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    // Executor for database operations (BroadcastReceiver has ~10 second limit)
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Logger.w(TAG, "Received null intent");
            return;
        }

        long siteId = intent.getLongExtra(CheckScheduler.getExtraSiteId(), -1);
        if (siteId == -1) {
            Logger.w(TAG, "Received alarm without valid site ID");
            return;
        }

        Logger.i(TAG, "Alarm received for site ID: " + siteId);

        // Use goAsync() to extend the time we can run (up to ~30 seconds)
        final PendingResult pendingResult = goAsync();

        executor.execute(() -> {
            try {
                processAlarm(context.getApplicationContext(), siteId);
            } catch (Exception e) {
                Logger.e(TAG, "Error processing alarm for site " + siteId, e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    /**
     * Process the alarm by loading site data, executing the check, and rescheduling.
     */
    private void processAlarm(Context context, long siteId) {
        // Get the site directly from repository
        SiteRepository repository = SiteRepository.getInstance(
                (android.app.Application) context);

        WatchedSite site = repository.getSiteByIdSync(siteId);
        if (site == null) {
            Logger.w(TAG, "Site not found for ID: " + siteId);
            return;
        }

        if (!site.isEnabled()) {
            Logger.d(TAG, "Site " + siteId + " is disabled, skipping check");
            return;
        }

        // Execute the check
        try {
            SiteChecker checker = SiteChecker.getInstance(context);
            checker.checkSite(site, new SiteChecker.CheckCallback() {
                @Override
                public void onCheckComplete(long checkedSiteId, float changePercent, boolean hasChanged) {
                    Logger.d(TAG, "Check complete for site " + checkedSiteId +
                            ": " + changePercent + "% change");
                }

                @Override
                public void onCheckError(long checkedSiteId, String error) {
                    Logger.e(TAG, "Check error for site " + checkedSiteId + ": " + error);
                }
            });
            Logger.d(TAG, "Check execution started for site " + siteId);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to start check execution for site " + siteId, e);
        }

        // Reschedule the next check for this site
        try {
            CheckScheduler scheduler = CheckScheduler.getInstance(context);
            scheduler.scheduleCheck(site);
            Logger.d(TAG, "Rescheduled next check for site " + siteId);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to reschedule check for site " + siteId, e);
        }
    }
}
