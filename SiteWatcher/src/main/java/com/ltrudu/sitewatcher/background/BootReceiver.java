package com.ltrudu.sitewatcher.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver that handles device boot completion.
 * Reschedules all enabled site checks after the device restarts.
 *
 * This receiver works independently without callbacks, directly accessing
 * the database to reschedule alarms. This ensures alarms are rescheduled
 * even when the app was not running before boot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    // Executor for database operations
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Logger.w(TAG, "Received null intent");
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Logger.d(TAG, "Received non-boot intent: " + action);
            return;
        }

        Logger.i(TAG, "Boot completed, rescheduling site checks");

        // Use goAsync() to extend the time we can run
        final PendingResult pendingResult = goAsync();

        executor.execute(() -> {
            try {
                rescheduleAllSites(context.getApplicationContext());
            } catch (Exception e) {
                Logger.e(TAG, "Error rescheduling sites after boot", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    /**
     * Reschedule all enabled sites after boot.
     */
    private void rescheduleAllSites(Context context) {
        // Get repository to access enabled sites
        SiteRepository repository = SiteRepository.getInstance(
                (android.app.Application) context);

        List<WatchedSite> enabledSites = repository.getEnabledSites();

        if (enabledSites == null || enabledSites.isEmpty()) {
            Logger.d(TAG, "No enabled sites to reschedule after boot");
            return;
        }

        Logger.i(TAG, "Rescheduling " + enabledSites.size() + " enabled sites after boot");

        CheckScheduler scheduler = CheckScheduler.getInstance(context);

        for (WatchedSite site : enabledSites) {
            try {
                scheduler.scheduleCheck(site);
                Logger.d(TAG, "Rescheduled site: " + site.getId() + " - " + site.getUrl());
            } catch (Exception e) {
                Logger.e(TAG, "Failed to reschedule site: " + site.getId(), e);
            }
        }

        Logger.i(TAG, "Successfully rescheduled all enabled sites after boot");
    }
}
