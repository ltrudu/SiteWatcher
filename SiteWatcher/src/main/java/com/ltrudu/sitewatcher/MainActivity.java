package com.ltrudu.sitewatcher;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.ltrudu.sitewatcher.util.Logger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private NavController navController;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private AppBarConfiguration appBarConfiguration;

    // Track if we're waiting for the user to return from Settings
    private boolean waitingForAlarmPermission = false;
    // Track if we've shown the dialog this session (don't keep nagging after cancel)
    private boolean alarmDialogShownThisSession = false;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Logger.d(TAG, "Notification permission granted");
            } else {
                Logger.w(TAG, "Notification permission denied");
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupNavigation();
        checkPermissions();
    }

    private void setupNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Configure the drawer to show hamburger icon on top-level destinations
            appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.siteListFragment
            )
            .setOpenableLayout(drawerLayout)
            .build();

            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
        }
    }

    private void checkPermissions() {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Check exact alarm permission (Android 12+)
        checkExactAlarmPermission();
    }

    /**
     * Checks if exact alarm permission is granted and shows dialog if not.
     */
    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = getSystemService(AlarmManager.class);
            if (alarmManager != null) {
                boolean canSchedule = alarmManager.canScheduleExactAlarms();
                Logger.d(TAG, "canScheduleExactAlarms: " + canSchedule +
                        ", waitingForAlarmPermission: " + waitingForAlarmPermission);

                if (!canSchedule) {
                    if (waitingForAlarmPermission) {
                        // User returned from Settings but didn't grant permission
                        // Show dialog again
                        Logger.w(TAG, "User returned without granting permission, showing dialog again");
                        waitingForAlarmPermission = false;
                        showExactAlarmPermissionDialog();
                    } else if (!alarmDialogShownThisSession) {
                        // First time showing dialog this session
                        Logger.w(TAG, "Exact alarm permission not granted, showing dialog");
                        showExactAlarmPermissionDialog();
                    }
                } else {
                    // Permission granted
                    waitingForAlarmPermission = false;
                    Logger.d(TAG, "Exact alarm permission granted");
                }
            }
        }
    }

    /**
     * Shows a dialog explaining why exact alarm permission is needed.
     */
    private void showExactAlarmPermissionDialog() {
        alarmDialogShownThisSession = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.alarm_permission_title)
                .setMessage(R.string.alarm_permission_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    waitingForAlarmPermission = true;
                    openExactAlarmSettings();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // User explicitly cancelled, don't nag again this session
                    // unless they go to Settings and come back without granting
                    waitingForAlarmPermission = false;
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Opens the system settings page for exact alarm permission.
     */
    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Logger.e(TAG, "Failed to open exact alarm settings", e);
                openAppSettings();
            }
        }
    }

    /**
     * Opens the app's settings page as a fallback.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check exact alarm permission when returning from Settings
        // If user didn't grant it, show dialog again
        checkExactAlarmPermission();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
