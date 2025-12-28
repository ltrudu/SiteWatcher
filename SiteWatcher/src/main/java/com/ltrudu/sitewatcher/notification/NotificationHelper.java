package com.ltrudu.sitewatcher.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import com.ltrudu.sitewatcher.MainActivity;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.NotificationAction;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Constants;
import com.ltrudu.sitewatcher.util.PreferencesManager;

public class NotificationHelper {

    private static int notificationId = 1000;

    public static void showSiteChangedNotification(Context context, WatchedSite site, float changePercent) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        // Determine tap action from settings
        PreferencesManager prefs = PreferencesManager.getInstance(context);
        NotificationAction action = prefs.getNotificationAction();

        PendingIntent pendingIntent;
        if (action == NotificationAction.OPEN_BROWSER) {
            // Open URL in browser
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(site.getUrl()));
            pendingIntent = PendingIntent.getActivity(context, (int) site.getId(),
                browserIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            // Open app (default)
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.putExtra("siteId", site.getId());
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            pendingIntent = PendingIntent.getActivity(context, (int) site.getId(),
                appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        // Build notification
        String title = context.getString(R.string.notification_title);
        String displayName = site.getDisplayName();
        String text = context.getString(R.string.notification_text, displayName, changePercent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_planet)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        notificationManager.notify(notificationId++, builder.build());
    }

    public static void cancelNotification(Context context, int notificationId) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }
    }

    public static void cancelAllNotifications(Context context) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }
}
