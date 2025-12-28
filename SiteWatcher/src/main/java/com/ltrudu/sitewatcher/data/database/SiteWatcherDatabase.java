package com.ltrudu.sitewatcher.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.ltrudu.sitewatcher.data.dao.CheckResultDao;
import com.ltrudu.sitewatcher.data.dao.SiteHistoryDao;
import com.ltrudu.sitewatcher.data.dao.WatchedSiteDao;
import com.ltrudu.sitewatcher.data.model.CheckResult;
import com.ltrudu.sitewatcher.data.model.SiteHistory;
import com.ltrudu.sitewatcher.data.model.WatchedSite;

/**
 * Room database for the SiteWatcher application.
 * Provides access to all DAOs and manages database lifecycle.
 */
@Database(
    entities = {
        WatchedSite.class,
        SiteHistory.class,
        CheckResult.class
    },
    version = 1,
    exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class SiteWatcherDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "sitewatcher_database";

    private static volatile SiteWatcherDatabase INSTANCE;

    /**
     * Get the WatchedSite DAO.
     *
     * @return WatchedSiteDao instance
     */
    public abstract WatchedSiteDao watchedSiteDao();

    /**
     * Get the SiteHistory DAO.
     *
     * @return SiteHistoryDao instance
     */
    public abstract SiteHistoryDao siteHistoryDao();

    /**
     * Get the CheckResult DAO.
     *
     * @return CheckResultDao instance
     */
    public abstract CheckResultDao checkResultDao();

    /**
     * Get the singleton database instance.
     * Uses double-checked locking for thread safety.
     *
     * @param context Application context
     * @return The singleton database instance
     */
    public static SiteWatcherDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SiteWatcherDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SiteWatcherDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Close the database instance.
     * Should be called when the application is being destroyed.
     */
    public static void destroyInstance() {
        if (INSTANCE != null) {
            synchronized (SiteWatcherDatabase.class) {
                if (INSTANCE != null && INSTANCE.isOpen()) {
                    INSTANCE.close();
                }
                INSTANCE = null;
            }
        }
    }
}
