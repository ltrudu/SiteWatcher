package com.ltrudu.sitewatcher.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.ltrudu.sitewatcher.BuildConfig;

/**
 * Centralized logging utility for SiteWatcher.
 * Provides controlled debug logging with optional file output.
 * Thread-safe implementation with dynamic enable/disable capability.
 */
public final class Logger {

    private static final String TAG = "SiteWatcher";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    // Default to disabled in release, enabled in debug
    private static volatile boolean isEnabled = BuildConfig.DEBUG;

    // Private constructor to prevent instantiation
    private Logger() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Enable or disable logging.
     * @param enabled true to enable logging, false to disable
     */
    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    /**
     * Check if logging is enabled.
     * @return true if logging is enabled
     */
    public static boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Log a debug message.
     * @param tag Log tag
     * @param message Log message
     */
    public static void d(@NonNull String tag, @NonNull String message) {
        if (!isEnabled) return;
        Log.d(TAG + ":" + tag, message);
    }

    /**
     * Log an info message.
     * @param tag Log tag
     * @param message Log message
     */
    public static void i(@NonNull String tag, @NonNull String message) {
        if (!isEnabled) return;
        Log.i(TAG + ":" + tag, message);
    }

    /**
     * Log a warning message.
     * @param tag Log tag
     * @param message Log message
     */
    public static void w(@NonNull String tag, @NonNull String message) {
        if (!isEnabled) return;
        Log.w(TAG + ":" + tag, message);
    }

    /**
     * Log an error message.
     * @param tag Log tag
     * @param message Log message
     */
    public static void e(@NonNull String tag, @NonNull String message) {
        if (!isEnabled) return;
        Log.e(TAG + ":" + tag, message);
    }

    /**
     * Log an error message with exception.
     * @param tag Log tag
     * @param message Log message
     * @param throwable The exception to log
     */
    public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        if (!isEnabled) return;
        Log.e(TAG + ":" + tag, message, throwable);
    }

    /**
     * Write a message to the log file.
     * This is useful for issue reporting when users need to provide logs.
     * @param context Application context
     * @param message Message to write
     */
    public static void writeToFile(@NonNull Context context, @NonNull String message) {
        if (!isEnabled) return;

        synchronized (LOCK) {
            File logFile = getLogFile(context);
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(logFile, true))) {
                String timestamp = DATE_FORMAT.format(new Date());
                writer.write(timestamp + " | " + message);
                writer.newLine();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to log file", e);
            }
        }
    }

    /**
     * Get the log file.
     * @param context Application context
     * @return The log file
     */
    @NonNull
    public static File getLogFile(@NonNull Context context) {
        return new File(context.getFilesDir(), Constants.LOG_FILE_NAME);
    }

    /**
     * Clear the log file.
     * @param context Application context
     */
    public static void clearLog(@NonNull Context context) {
        synchronized (LOCK) {
            File logFile = getLogFile(context);
            if (logFile.exists()) {
                if (!logFile.delete()) {
                    Log.w(TAG, "Failed to delete log file");
                }
            }
        }
    }

    /**
     * Log and write to file simultaneously.
     * Useful for important events that should be both logged and recorded.
     * @param context Application context
     * @param tag Log tag
     * @param message Log message
     */
    public static void logAndWrite(@NonNull Context context, @NonNull String tag,
            @NonNull String message) {
        if (!isEnabled) return;
        d(tag, message);
        writeToFile(context, tag + ": " + message);
    }
}
