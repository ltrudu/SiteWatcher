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

import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.ltrudu.sitewatcher.ui.sitelist.SiteListViewModel;
import com.ltrudu.sitewatcher.util.Logger;
import com.ltrudu.sitewatcher.util.ThemeManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private NavController navController;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private AppBarConfiguration appBarConfiguration;
    private SiteListViewModel siteListViewModel;

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
        // Apply saved theme before super.onCreate() and setContentView()
        ThemeManager themeManager = new ThemeManager(this);
        themeManager.applyTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize shared ViewModel at Activity scope so it's shared with fragments
        siteListViewModel = new ViewModelProvider(this).get(SiteListViewModel.class);

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

            // Handle custom menu items (non-navigation actions)
            navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.action_check_all) {
                    // Handle Check All action
                    checkAllSites();
                    drawerLayout.closeDrawers();
                    return true;
                }

                // For navigation items, use the default behavior
                boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
                if (handled) {
                    drawerLayout.closeDrawers();
                }
                return handled;
            });
        }
    }

    /**
     * Check all enabled sites immediately.
     */
    private void checkAllSites() {
        Logger.d(TAG, "Check all sites requested from drawer menu");
        Toast.makeText(this, R.string.checking_all_sites, Toast.LENGTH_SHORT).show();

        // Use shared ViewModel so the checking events are observed by the fragment
        siteListViewModel.checkAllSites();
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
