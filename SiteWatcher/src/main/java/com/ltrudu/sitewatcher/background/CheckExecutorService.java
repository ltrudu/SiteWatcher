package com.ltrudu.sitewatcher.background;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.util.Constants;
import com.ltrudu.sitewatcher.util.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton executor service for managing background site checks.
 * Provides a configurable thread pool for executing site check operations.
 */
public final class CheckExecutorService {

    private static final String TAG = "CheckExecutorService";
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final TimeUnit KEEP_ALIVE_UNIT = TimeUnit.SECONDS;

    private static volatile CheckExecutorService instance;

    private ExecutorService executorService;
    private int currentMaxThreads;
    private final AtomicBoolean isShutdown;
    private final Object lock = new Object();

    /**
     * Private constructor for singleton pattern.
     */
    private CheckExecutorService() {
        this.currentMaxThreads = Constants.DEFAULT_MAX_THREADS;
        this.isShutdown = new AtomicBoolean(false);
        createExecutor(currentMaxThreads);
    }

    /**
     * Get the singleton instance of CheckExecutorService.
     * @return The CheckExecutorService instance
     */
    @NonNull
    public static CheckExecutorService getInstance() {
        if (instance == null) {
            synchronized (CheckExecutorService.class) {
                if (instance == null) {
                    instance = new CheckExecutorService();
                }
            }
        }
        return instance;
    }

    /**
     * Create or recreate the executor service with the specified thread count.
     * @param maxThreads Maximum number of concurrent threads
     */
    private void createExecutor(int maxThreads) {
        synchronized (lock) {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }

            executorService = new ThreadPoolExecutor(
                    1, // Core pool size (minimum threads)
                    maxThreads, // Maximum pool size
                    KEEP_ALIVE_TIME,
                    KEEP_ALIVE_UNIT,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setName("SiteChecker-" + thread.getId());
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    }
            );

            isShutdown.set(false);
            Logger.d(TAG, "Executor created with max " + maxThreads + " threads");
        }
    }

    /**
     * Execute a site check on a background thread.
     * @param context Application context
     * @param site The WatchedSite to check
     * @param listener Callback for check completion
     */
    public void executeCheck(@NonNull Context context, @NonNull WatchedSite site,
            @NonNull SiteCheckWorker.OnCheckCompleteListener listener) {
        if (isShutdown.get()) {
            Logger.w(TAG, "Executor is shutdown, recreating");
            createExecutor(currentMaxThreads);
        }

        synchronized (lock) {
            if (executorService == null || executorService.isShutdown()) {
                createExecutor(currentMaxThreads);
            }

            SiteCheckWorker worker = new SiteCheckWorker(context, site, listener);

            try {
                executorService.execute(worker);
                Logger.d(TAG, "Submitted check for site " + site.getId());
            } catch (Exception e) {
                Logger.e(TAG, "Failed to submit check for site " + site.getId(), e);
                listener.onError(site.getId(), e);
            }
        }
    }

    /**
     * Execute a site check without a listener.
     * Errors will be logged but not reported to a callback.
     * @param context Application context
     * @param site The WatchedSite to check
     */
    public void executeCheck(@NonNull Context context, @NonNull WatchedSite site) {
        executeCheck(context, site, new SiteCheckWorker.OnCheckCompleteListener() {
            @Override
            public void onSuccess(long siteId, float changePercent, boolean thresholdExceeded) {
                Logger.d(TAG, "Check completed for site " + siteId +
                        ", change: " + changePercent + "%, threshold exceeded: " + thresholdExceeded);
            }

            @Override
            public void onError(long siteId, Throwable error) {
                Logger.e(TAG, "Check failed for site " + siteId, error);
            }
        });
    }

    /**
     * Update the maximum thread pool size.
     * @param maxThreads New maximum number of threads (1-15)
     */
    public void setMaxThreads(int maxThreads) {
        // Validate bounds
        if (maxThreads < 1) {
            maxThreads = 1;
        } else if (maxThreads > 15) {
            maxThreads = 15;
        }

        if (maxThreads == currentMaxThreads) {
            return;
        }

        currentMaxThreads = maxThreads;

        synchronized (lock) {
            if (executorService instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executorService;

                // Adjust pool size dynamically if possible
                if (maxThreads >= threadPool.getCorePoolSize()) {
                    threadPool.setMaximumPoolSize(maxThreads);
                } else {
                    // Need to recreate if reducing below current core size
                    createExecutor(maxThreads);
                }

                Logger.i(TAG, "Thread pool size updated to " + maxThreads);
            } else {
                createExecutor(maxThreads);
            }
        }
    }

    /**
     * Get the current maximum thread count.
     * @return Current max threads
     */
    public int getMaxThreads() {
        return currentMaxThreads;
    }

    /**
     * Get the current number of active threads.
     * @return Number of active threads, or 0 if executor is not available
     */
    public int getActiveCount() {
        synchronized (lock) {
            if (executorService instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executorService).getActiveCount();
            }
            return 0;
        }
    }

    /**
     * Get the current number of queued tasks.
     * @return Number of queued tasks, or 0 if executor is not available
     */
    public int getQueuedCount() {
        synchronized (lock) {
            if (executorService instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executorService).getQueue().size();
            }
            return 0;
        }
    }

    /**
     * Shutdown the executor service cleanly.
     * Attempts graceful shutdown with timeout, then forces shutdown if necessary.
     */
    public void shutdown() {
        synchronized (lock) {
            if (executorService == null || isShutdown.get()) {
                return;
            }

            isShutdown.set(true);
            Logger.i(TAG, "Shutting down executor service");

            executorService.shutdown();
            try {
                // Wait for existing tasks to complete
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    Logger.w(TAG, "Executor did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();

                    // Wait again for forced shutdown
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        Logger.e(TAG, "Executor did not terminate after force shutdown");
                    }
                }
            } catch (InterruptedException e) {
                Logger.w(TAG, "Shutdown interrupted, forcing immediate shutdown");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            Logger.i(TAG, "Executor service shutdown complete");
        }
    }

    /**
     * Check if the executor is shutdown.
     * @return true if shutdown
     */
    public boolean isShutdown() {
        return isShutdown.get();
    }
}
