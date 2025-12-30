package com.ltrudu.sitewatcher.ui.settings;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.background.CheckScheduler;
import com.ltrudu.sitewatcher.data.model.WatchedSite;
import com.ltrudu.sitewatcher.data.repository.SiteRepository;
import com.ltrudu.sitewatcher.network.SiteChecker;
import com.ltrudu.sitewatcher.util.Logger;
import com.ltrudu.sitewatcher.util.SiteExporter;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for application settings.
 * Allows users to configure notification behavior, network preferences,
 * and other application-wide settings.
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private static final String MIME_TYPE = "application/octet-stream";

    private SettingsViewModel viewModel;
    private SiteRepository repository;
    private CheckScheduler checkScheduler;
    private SiteChecker siteChecker;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // UI components
    private MaterialButton btnRequestPermissions;
    private TextView tvPermissionStatus;
    private MaterialButton btnExportSites;
    private MaterialButton btnImportSites;
    private Spinner spinnerNotificationAction;
    private Spinner spinnerNetworkMode;
    private Slider sliderRetryCount;
    private TextView tvRetryCountValue;
    private Slider sliderMaxThreads;
    private TextView tvMaxThreadsValue;
    private Slider sliderHistoryCount;
    private TextView tvHistoryCountValue;
    private Slider sliderPageLoadDelay;
    private TextView tvPageLoadDelayValue;
    private Slider sliderPostActionDelay;
    private TextView tvPostActionDelayValue;
    private Spinner spinnerSearchEngine;
    private SwitchMaterial switchDebugMode;

    // Flag to prevent spinner callbacks during initial setup
    private boolean isInitializing = true;

    // Permission launcher for multiple permissions
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    // SAF launcher for creating export file
    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(MIME_TYPE),
                    this::onExportFileCreated);

    // SAF launcher for opening import file
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onImportFileSelected);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        repository = SiteRepository.getInstance(requireActivity().getApplication());
        checkScheduler = CheckScheduler.getInstance(requireContext());
        siteChecker = SiteChecker.getInstance(requireContext());

        initViews(view);
        setupListeners();
        observeViewModel();

        // Update permission status on view creation
        updatePermissionStatus();

        // Mark initialization complete after a short delay to allow spinners to settle
        view.post(() -> isInitializing = false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh permission status when returning to this fragment
        // (user may have changed it in Settings)
        updatePermissionStatus();
    }

    private void initViews(View view) {
        btnRequestPermissions = view.findViewById(R.id.btnRequestPermissions);
        tvPermissionStatus = view.findViewById(R.id.tvPermissionStatus);
        btnExportSites = view.findViewById(R.id.btnExportSites);
        btnImportSites = view.findViewById(R.id.btnImportSites);
        spinnerNotificationAction = view.findViewById(R.id.spinnerNotificationAction);
        spinnerNetworkMode = view.findViewById(R.id.spinnerNetworkMode);
        sliderRetryCount = view.findViewById(R.id.sliderRetryCount);
        tvRetryCountValue = view.findViewById(R.id.tvRetryCountValue);
        sliderMaxThreads = view.findViewById(R.id.sliderMaxThreads);
        tvMaxThreadsValue = view.findViewById(R.id.tvMaxThreadsValue);
        sliderHistoryCount = view.findViewById(R.id.sliderHistoryCount);
        tvHistoryCountValue = view.findViewById(R.id.tvHistoryCountValue);
        sliderPageLoadDelay = view.findViewById(R.id.sliderPageLoadDelay);
        tvPageLoadDelayValue = view.findViewById(R.id.tvPageLoadDelayValue);
        sliderPostActionDelay = view.findViewById(R.id.sliderPostActionDelay);
        tvPostActionDelayValue = view.findViewById(R.id.tvPostActionDelayValue);
        spinnerSearchEngine = view.findViewById(R.id.spinnerSearchEngine);
        switchDebugMode = view.findViewById(R.id.switchDebugMode);
    }

    private void setupListeners() {
        // Request Permissions button
        btnRequestPermissions.setOnClickListener(v -> requestPermissions());

        // Export/Import buttons
        btnExportSites.setOnClickListener(v -> startExport());
        btnImportSites.setOnClickListener(v -> startImport());

        // Notification Action spinner
        spinnerNotificationAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    viewModel.setNotificationActionByIndex(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed
            }
        });

        // Network Mode spinner
        spinnerNetworkMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    viewModel.setNetworkModeByIndex(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed
            }
        });

        // Retry Count slider
        sliderRetryCount.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvRetryCountValue.setText(String.valueOf(intValue));
            if (fromUser) {
                viewModel.setRetryCount(intValue);
            }
        });

        // Max Threads slider
        sliderMaxThreads.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvMaxThreadsValue.setText(String.valueOf(intValue));
            if (fromUser) {
                viewModel.setMaxThreads(intValue);
            }
        });

        // History Count slider
        sliderHistoryCount.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvHistoryCountValue.setText(String.valueOf(intValue));
            if (fromUser) {
                viewModel.setHistoryCount(intValue);
            }
        });

        // Page Load Delay slider
        sliderPageLoadDelay.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvPageLoadDelayValue.setText(getString(R.string.delay_seconds_value, intValue));
            if (fromUser) {
                viewModel.setPageLoadDelay(intValue);
            }
        });

        // Post-Action Delay slider
        sliderPostActionDelay.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvPostActionDelayValue.setText(getString(R.string.delay_seconds_value, intValue));
            if (fromUser) {
                viewModel.setPostActionDelay(intValue);
            }
        });

        // Search Engine spinner
        spinnerSearchEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    viewModel.setSearchEngineIndex(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed
            }
        });

        // Debug Mode switch
        switchDebugMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isInitializing) {
                viewModel.setDebugMode(isChecked);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getNotificationAction().observe(getViewLifecycleOwner(), action -> {
            if (action != null) {
                spinnerNotificationAction.setSelection(action.ordinal());
            }
        });

        viewModel.getNetworkMode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                spinnerNetworkMode.setSelection(mode.ordinal());
            }
        });

        viewModel.getRetryCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                sliderRetryCount.setValue(count);
                tvRetryCountValue.setText(String.valueOf(count));
            }
        });

        viewModel.getMaxThreads().observe(getViewLifecycleOwner(), threads -> {
            if (threads != null) {
                sliderMaxThreads.setValue(threads);
                tvMaxThreadsValue.setText(String.valueOf(threads));
            }
        });

        viewModel.getHistoryCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                sliderHistoryCount.setValue(count);
                tvHistoryCountValue.setText(String.valueOf(count));
            }
        });

        viewModel.getSearchEngineIndex().observe(getViewLifecycleOwner(), index -> {
            if (index != null) {
                spinnerSearchEngine.setSelection(index);
            }
        });

        viewModel.getDebugMode().observe(getViewLifecycleOwner(), enabled -> {
            if (enabled != null) {
                switchDebugMode.setChecked(enabled);
            }
        });

        viewModel.getPageLoadDelay().observe(getViewLifecycleOwner(), delay -> {
            if (delay != null) {
                sliderPageLoadDelay.setValue(delay);
                tvPageLoadDelayValue.setText(getString(R.string.delay_seconds_value, delay));
            }
        });

        viewModel.getPostActionDelay().observe(getViewLifecycleOwner(), delay -> {
            if (delay != null) {
                sliderPostActionDelay.setValue(delay);
                tvPostActionDelayValue.setText(getString(R.string.delay_seconds_value, delay));
            }
        });
    }

    /**
     * Requests the necessary permissions for the app to function properly.
     */
    private void requestPermissions() {
        List<String> runtimePermissions = new ArrayList<>();

        // Notification permission (Android 13+) - this is a runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Request runtime permissions first
        if (!runtimePermissions.isEmpty()) {
            permissionLauncher.launch(runtimePermissions.toArray(new String[0]));
        }

        // Check if exact alarm permission is needed (Android 12+)
        // This is NOT a runtime permission - user must grant in Settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                boolean canSchedule = alarmManager.canScheduleExactAlarms();
                Logger.d(TAG, "canScheduleExactAlarms: " + canSchedule);
                if (!canSchedule) {
                    Logger.d(TAG, "Showing exact alarm permission dialog");
                    showExactAlarmPermissionDialog();
                } else {
                    Logger.d(TAG, "Exact alarm permission already granted");
                }
            } else {
                Logger.e(TAG, "AlarmManager is null");
            }
        } else {
            Logger.d(TAG, "SDK version < S, exact alarm permission not needed");
        }

        // If no permissions needed
        if (runtimePermissions.isEmpty() && checkAllPermissions()) {
            Logger.d(TAG, "All permissions already granted");
            updatePermissionStatus();
        }
    }

    /**
     * Shows a dialog explaining why exact alarm permission is needed and
     * offers to open Settings.
     */
    private void showExactAlarmPermissionDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.alarm_permission_title)
                .setMessage(R.string.alarm_permission_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    openExactAlarmSettings();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Opens the system settings page for exact alarm permission.
     */
    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Logger.e(TAG, "Failed to open exact alarm settings", e);
                // Fallback to app settings
                openAppSettings();
            }
        }
    }

    /**
     * Opens the app's settings page as a fallback.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    /**
     * Handles the result of permission requests.
     */
    private void onPermissionsResult(Map<String, Boolean> results) {
        for (Map.Entry<String, Boolean> entry : results.entrySet()) {
            Logger.d(TAG, "Permission " + entry.getKey() + ": " +
                    (entry.getValue() ? "granted" : "denied"));
        }
        updatePermissionStatus();
    }

    /**
     * Updates the permission status text based on current permission states.
     */
    private void updatePermissionStatus() {
        boolean allGranted = checkAllPermissions();

        if (allGranted) {
            tvPermissionStatus.setText(R.string.permissions_granted);
            tvPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.holo_green_dark));
        } else {
            tvPermissionStatus.setText(R.string.permissions_missing);
            tvPermissionStatus.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.holo_red_dark));
        }
    }

    /**
     * Checks if all required permissions are granted.
     *
     * @return true if all permissions are granted, false otherwise
     */
    private boolean checkAllPermissions() {
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        // Exact alarm permission (Android 12+) - check via AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                return false;
            }
        }

        return true;
    }

    // ========================================
    // Export / Import Methods
    // ========================================

    /**
     * Starts the export process by checking if there are sites to export.
     */
    private void startExport() {
        executorService.execute(() -> {
            List<WatchedSite> sites = repository.getAllSitesSync();
            requireActivity().runOnUiThread(() -> {
                if (sites.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_sites_to_export, Toast.LENGTH_SHORT).show();
                } else {
                    // Launch SAF file picker with suggested filename
                    String filename = SiteExporter.generateFilename();
                    exportLauncher.launch(filename);
                }
            });
        });
    }

    /**
     * Starts the import process by opening the file picker.
     */
    private void startImport() {
        importLauncher.launch(new String[]{MIME_TYPE, "*/*"});
    }

    /**
     * Called when the user selects a location for the export file.
     */
    private void onExportFileCreated(@Nullable Uri uri) {
        if (uri == null) {
            Logger.d(TAG, "Export cancelled by user");
            return;
        }

        executorService.execute(() -> {
            try {
                List<WatchedSite> sites = repository.getAllSitesSync();
                String json = SiteExporter.exportToJson(sites);

                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }

                int count = sites.size();
                requireActivity().runOnUiThread(() -> {
                    String message = getString(R.string.export_success, count);
                    showSnackbar(message);
                    Logger.d(TAG, "Exported " + count + " sites to " + uri);
                });

            } catch (JSONException | IOException e) {
                Logger.e(TAG, "Export failed", e);
                requireActivity().runOnUiThread(() -> {
                    String message = getString(R.string.export_failed, e.getMessage());
                    showSnackbar(message);
                });
            }
        });
    }

    /**
     * Called when the user selects a file to import.
     */
    private void onImportFileSelected(@Nullable Uri uri) {
        if (uri == null) {
            Logger.d(TAG, "Import cancelled by user");
            return;
        }

        executorService.execute(() -> {
            try {
                String json = readTextFromUri(uri);
                List<WatchedSite> sites = SiteExporter.importFromJson(json);

                if (sites.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        String message = getString(R.string.import_failed, "No valid sites found");
                        showSnackbar(message);
                    });
                    return;
                }

                // Show confirmation dialog on UI thread
                requireActivity().runOnUiThread(() -> {
                    showImportConfirmDialog(sites);
                });

            } catch (JSONException | IOException e) {
                Logger.e(TAG, "Import failed", e);
                requireActivity().runOnUiThread(() -> {
                    String message = getString(R.string.import_failed, e.getMessage());
                    showSnackbar(message);
                });
            }
        });
    }

    /**
     * Shows a confirmation dialog before importing sites.
     */
    private void showImportConfirmDialog(List<WatchedSite> sites) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_confirm_title)
                .setMessage(getString(R.string.import_confirm_message, sites.size()))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    performImport(sites);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Performs the actual import of sites.
     */
    private void performImport(List<WatchedSite> sites) {
        executorService.execute(() -> {
            int importedCount = 0;
            int skippedCount = 0;

            for (WatchedSite site : sites) {
                // Check if site with same URL already exists
                if (repository.siteExistsWithUrl(site.getUrl())) {
                    skippedCount++;
                    Logger.d(TAG, "Skipping duplicate URL: " + site.getUrl());
                    continue;
                }

                // Insert the site synchronously
                try {
                    long insertedId = insertSiteSync(site);
                    if (insertedId > 0) {
                        site.setId(insertedId);
                        // Schedule alarm for enabled sites
                        if (site.isEnabled()) {
                            checkScheduler.scheduleCheck(site);
                        }
                        // Perform initial check to create backup data
                        performInitialCheck(site);
                        importedCount++;
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error importing site: " + site.getUrl(), e);
                }
            }

            int finalImportedCount = importedCount;
            int finalSkippedCount = skippedCount;
            requireActivity().runOnUiThread(() -> {
                String message = getString(R.string.import_success, finalImportedCount);
                if (finalSkippedCount > 0) {
                    message += " (" + finalSkippedCount + " skipped)";
                }
                showSnackbar(message);
                Logger.d(TAG, "Imported " + finalImportedCount + " sites, skipped " + finalSkippedCount);
            });
        });
    }

    /**
     * Synchronously inserts a site and returns the inserted ID.
     * Note: This is a simple wrapper - in production you'd want this in the repository.
     */
    private long insertSiteSync(WatchedSite site) throws Exception {
        final long[] result = {-1};
        final Exception[] error = {null};
        final Object lock = new Object();

        repository.insertSite(site, new SiteRepository.OnInsertCompleteListener() {
            @Override
            public void onSuccess(long insertedId) {
                result[0] = insertedId;
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {
                error[0] = exception;
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        synchronized (lock) {
            lock.wait(5000); // Wait up to 5 seconds
        }

        if (error[0] != null) {
            throw error[0];
        }

        return result[0];
    }

    /**
     * Performs an initial check on a newly imported site to create backup data.
     * This runs asynchronously and doesn't block the import process.
     *
     * @param site The site to check
     */
    private void performInitialCheck(@NonNull WatchedSite site) {
        Logger.d(TAG, "Performing initial check for imported site: " + site.getId());
        siteChecker.checkSite(site, new SiteChecker.CheckCallback() {
            @Override
            public void onCheckComplete(long siteId, float changePercent, boolean hasChanged) {
                Logger.d(TAG, "Initial check complete for imported site " + siteId);
            }

            @Override
            public void onCheckError(long siteId, @NonNull String error) {
                Logger.w(TAG, "Initial check failed for imported site " + siteId + ": " + error);
                // Don't report error to user, initial check failure is not critical
            }
        });
    }

    /**
     * Reads text content from a URI.
     */
    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Shows a snackbar with the given message.
     */
    private void showSnackbar(String message) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdown();
    }
}
