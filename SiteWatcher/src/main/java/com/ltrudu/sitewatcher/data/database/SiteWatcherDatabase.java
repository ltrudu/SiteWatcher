package com.ltrudu.sitewatcher.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
    version = 5,
    exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class SiteWatcherDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "sitewatcher_database";

    private static volatile SiteWatcherDatabase INSTANCE;

    /**
     * Migration from version 1 to 2: Add min_text_length column.
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE watched_sites ADD COLUMN min_text_length INTEGER NOT NULL DEFAULT 10");
        }
    };

    /**
     * Migration from version 2 to 3: Add min_word_length column.
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE watched_sites ADD COLUMN min_word_length INTEGER NOT NULL DEFAULT 3");
        }
    };

    /**
     * Migration from version 3 to 4: Add fetch_mode column.
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE watched_sites ADD COLUMN fetch_mode TEXT NOT NULL DEFAULT 'STATIC'");
        }
    };

    /**
     * Migration from version 4 to 5: Add auto_click_actions column.
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE watched_sites ADD COLUMN auto_click_actions TEXT DEFAULT NULL");
        }
    };

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
