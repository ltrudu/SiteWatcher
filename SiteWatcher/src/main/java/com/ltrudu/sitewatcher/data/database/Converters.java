package com.ltrudu.sitewatcher.data.database;

import androidx.room.TypeConverter;

import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.NetworkMode;
import com.ltrudu.sitewatcher.data.model.ScheduleType;

/**
 * Type converters for Room database.
 * Handles conversion between enum types and their string representations for storage.
 */
public class Converters {

    // ScheduleType converters

    /**
     * Convert ScheduleType enum to String for database storage.
     *
     * @param scheduleType The enum value
     * @return String representation, or null if input is null
     */
    @TypeConverter
    public static String fromScheduleType(ScheduleType scheduleType) {
        return scheduleType == null ? null : scheduleType.name();
    }

    /**
     * Convert String from database to ScheduleType enum.
     *
     * @param value The stored string value
     * @return ScheduleType enum, or null if input is null
     */
    @TypeConverter
    public static ScheduleType toScheduleType(String value) {
        return value == null ? null : ScheduleType.valueOf(value);
    }

    // ComparisonMode converters

    /**
     * Convert ComparisonMode enum to String for database storage.
     *
     * @param comparisonMode The enum value
     * @return String representation, or null if input is null
     */
    @TypeConverter
    public static String fromComparisonMode(ComparisonMode comparisonMode) {
        return comparisonMode == null ? null : comparisonMode.name();
    }

    /**
     * Convert String from database to ComparisonMode enum.
     *
     * @param value The stored string value
     * @return ComparisonMode enum, or null if input is null
     */
    @TypeConverter
    public static ComparisonMode toComparisonMode(String value) {
        return value == null ? null : ComparisonMode.valueOf(value);
    }

    // NetworkMode converters

    /**
     * Convert NetworkMode enum to String for database storage.
     *
     * @param networkMode The enum value
     * @return String representation, or null if input is null
     */
    @TypeConverter
    public static String fromNetworkMode(NetworkMode networkMode) {
        return networkMode == null ? null : networkMode.name();
    }

    /**
     * Convert String from database to NetworkMode enum.
     *
     * @param value The stored string value
     * @return NetworkMode enum, or null if input is null
     */
    @TypeConverter
    public static NetworkMode toNetworkMode(String value) {
        return value == null ? null : NetworkMode.valueOf(value);
    }
}
