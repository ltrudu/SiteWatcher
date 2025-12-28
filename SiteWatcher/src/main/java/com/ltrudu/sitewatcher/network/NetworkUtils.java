package com.ltrudu.sitewatcher.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.ltrudu.sitewatcher.data.model.NetworkMode;

/**
 * Utility class for network connectivity operations.
 * Provides methods to check network availability based on specified modes.
 */
public final class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    // Private constructor to prevent instantiation
    private NetworkUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Check if network is available for the specified mode.
     *
     * @param context Application context
     * @param mode    The network mode to check against
     * @return true if network matching the mode is available, false otherwise
     */
    public static boolean isNetworkAvailable(@NonNull Context context, @NonNull NetworkMode mode) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;

        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

        if (!hasInternet) return false;

        switch (mode) {
            case WIFI_ONLY:
                return isWifi;
            case DATA_ONLY:
                return isCellular;
            case WIFI_AND_DATA:
            default:
                return isWifi || isCellular;
        }
    }

    /**
     * Check if any network is available (WiFi or cellular).
     *
     * @param context Application context
     * @return true if any network is available, false otherwise
     */
    public static boolean isAnyNetworkAvailable(@NonNull Context context) {
        return isNetworkAvailable(context, NetworkMode.WIFI_AND_DATA);
    }

    /**
     * Check if WiFi is connected.
     *
     * @param context Application context
     * @return true if WiFi is connected, false otherwise
     */
    public static boolean isWifiConnected(@NonNull Context context) {
        return isNetworkAvailable(context, NetworkMode.WIFI_ONLY);
    }

    /**
     * Check if mobile data is connected.
     *
     * @param context Application context
     * @return true if mobile data is connected, false otherwise
     */
    public static boolean isMobileDataConnected(@NonNull Context context) {
        return isNetworkAvailable(context, NetworkMode.DATA_ONLY);
    }

    /**
     * Get a description of the current network state.
     *
     * @param context Application context
     * @return Human-readable description of network state
     */
    @NonNull
    public static String getNetworkStateDescription(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return "No connectivity service";

        Network network = cm.getActiveNetwork();
        if (network == null) return "No network";

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return "No network capabilities";

        StringBuilder sb = new StringBuilder();

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            sb.append("WiFi");
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            sb.append("Mobile Data");
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            sb.append("Ethernet");
        } else {
            sb.append("Unknown");
        }

        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            sb.append(" (validated)");
        }

        return sb.toString();
    }
}
