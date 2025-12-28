package com.ltrudu.sitewatcher;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.ltrudu.sitewatcher.data.database.SiteWatcherDatabase;
import com.ltrudu.sitewatcher.util.Constants;
import com.ltrudu.sitewatcher.util.Logger;

public class SiteWatcherApplication extends Application {

    private static SiteWatcherApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize logger based on build type
        Logger.setEnabled(BuildConfig.DEBUG);
        Logger.d("SiteWatcherApp", "Application started");

        // Create notification channel
        createNotificationChannel();

        // Initialize database (lazy, but warm it up)
        SiteWatcherDatabase.getInstance(this);
    }

    public static SiteWatcherApplication getInstance() {
        return instance;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(getString(R.string.notification_channel_description));

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
