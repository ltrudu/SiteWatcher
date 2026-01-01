package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a schedule configuration for when site checks should occur.
 * Supports multiple schedule types: All The Time, Selected Day, Date Range, Every Weeks.
 */
public class Schedule {

    private String id;
    private CalendarScheduleType calendarType;
    private ScheduleType intervalType;
    private boolean enabled;
    private int order;

    // Interval settings (common to all types)
    private int intervalMinutes;      // For PERIODIC: minutes between checks
    private int scheduleHour;         // For SPECIFIC_HOUR: hour (0-23)
    private int scheduleMinute;       // For SPECIFIC_HOUR: minute (0-59)

    // SELECTED_DAY specific
    private long selectedDate;        // Timestamp of the selected date

    // DATE_RANGE specific
    private long fromDate;            // Start of date range
    private long toDate;              // End of date range

    // EVERY_WEEKS specific
    private int enabledDays;          // Bitmask for enabled days (Sun=1, Mon=2, etc.)
    private WeekParity weekParity;    // BOTH, EVEN, or ODD weeks

    // Day of week constants (same as WatchedSite)
    public static final int SUNDAY = 1;
    public static final int MONDAY = 2;
    public static final int TUESDAY = 4;
    public static final int WEDNESDAY = 8;
    public static final int THURSDAY = 16;
    public static final int FRIDAY = 32;
    public static final int SATURDAY = 64;
    public static final int ALL_DAYS = 127;

    /**
     * Default constructor.
     */
    public Schedule() {
        this.id = UUID.randomUUID().toString();
        this.calendarType = CalendarScheduleType.ALL_THE_TIME;
        this.intervalType = ScheduleType.PERIODIC;
        this.enabled = true;
        this.order = 0;
        this.intervalMinutes = 60;
        this.scheduleHour = 9;
        this.scheduleMinute = 0;
        this.selectedDate = System.currentTimeMillis();
        this.fromDate = System.currentTimeMillis();
        this.toDate = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); // 1 week later
        this.enabledDays = ALL_DAYS;
        this.weekParity = WeekParity.BOTH;
    }

    /**
     * Create an ALL_THE_TIME schedule with default settings.
     */
    @NonNull
    public static Schedule createAllTheTime() {
        Schedule schedule = new Schedule();
        schedule.calendarType = CalendarScheduleType.ALL_THE_TIME;
        return schedule;
    }

    /**
     * Create a SELECTED_DAY schedule.
     *
     * @param date The specific date for checks
     */
    @NonNull
    public static Schedule createSelectedDay(long date) {
        Schedule schedule = new Schedule();
        schedule.calendarType = CalendarScheduleType.SELECTED_DAY;
        schedule.selectedDate = date;
        return schedule;
    }

    /**
     * Create a DATE_RANGE schedule.
     *
     * @param fromDate Start date (inclusive)
     * @param toDate   End date (inclusive)
     */
    @NonNull
    public static Schedule createDateRange(long fromDate, long toDate) {
        Schedule schedule = new Schedule();
        schedule.calendarType = CalendarScheduleType.DATE_RANGE;
        schedule.fromDate = fromDate;
        schedule.toDate = toDate;
        return schedule;
    }

    /**
     * Create an EVERY_WEEKS schedule.
     *
     * @param enabledDays Bitmask of enabled days
     * @param weekParity  Week parity filter
     */
    @NonNull
    public static Schedule createEveryWeeks(int enabledDays, @NonNull WeekParity weekParity) {
        Schedule schedule = new Schedule();
        schedule.calendarType = CalendarScheduleType.EVERY_WEEKS;
        schedule.enabledDays = enabledDays;
        schedule.weekParity = weekParity;
        return schedule;
    }

    // Getters and Setters

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public CalendarScheduleType getCalendarType() {
        return calendarType;
    }

    public void setCalendarType(@NonNull CalendarScheduleType calendarType) {
        this.calendarType = calendarType;
    }

    @NonNull
    public ScheduleType getIntervalType() {
        return intervalType;
    }

    public void setIntervalType(@NonNull ScheduleType intervalType) {
        this.intervalType = intervalType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = Math.max(15, intervalMinutes);
    }

    public int getScheduleHour() {
        return scheduleHour;
    }

    public void setScheduleHour(int scheduleHour) {
        this.scheduleHour = Math.max(0, Math.min(23, scheduleHour));
    }

    public int getScheduleMinute() {
        return scheduleMinute;
    }

    public void setScheduleMinute(int scheduleMinute) {
        this.scheduleMinute = Math.max(0, Math.min(59, scheduleMinute));
    }

    public long getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(long selectedDate) {
        this.selectedDate = selectedDate;
    }

    public long getFromDate() {
        return fromDate;
    }

    public void setFromDate(long fromDate) {
        this.fromDate = fromDate;
    }

    public long getToDate() {
        return toDate;
    }

    public void setToDate(long toDate) {
        this.toDate = toDate;
    }

    public int getEnabledDays() {
        return enabledDays;
    }

    public void setEnabledDays(int enabledDays) {
        this.enabledDays = enabledDays;
    }

    @NonNull
    public WeekParity getWeekParity() {
        return weekParity;
    }

    public void setWeekParity(@NonNull WeekParity weekParity) {
        this.weekParity = weekParity;
    }

    /**
     * Check if a specific day is enabled.
     *
     * @param dayBitmask The day bitmask (use SUNDAY, MONDAY, etc. constants)
     * @return true if the day is enabled
     */
    public boolean isDayEnabled(int dayBitmask) {
        return (enabledDays & dayBitmask) != 0;
    }

    /**
     * Enable or disable a specific day.
     *
     * @param dayBitmask The day bitmask
     * @param enabled    Whether to enable or disable
     */
    public void setDayEnabled(int dayBitmask, boolean enabled) {
        if (enabled) {
            enabledDays |= dayBitmask;
        } else {
            enabledDays &= ~dayBitmask;
        }
    }

    /**
     * Get a display label for this schedule.
     *
     * @return User-friendly label
     */
    @NonNull
    public String getLabel() {
        switch (calendarType) {
            case ALL_THE_TIME:
                return "All The Time";
            case SELECTED_DAY:
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                return sdf.format(new Date(selectedDate));
            case DATE_RANGE:
                SimpleDateFormat rangeSdf = new SimpleDateFormat("MMM d", Locale.getDefault());
                SimpleDateFormat yearSdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                return rangeSdf.format(new Date(fromDate)) + " - " + yearSdf.format(new Date(toDate));
            case EVERY_WEEKS:
                return getDaysLabel() + getWeekParityLabel();
            default:
                return "Unknown";
        }
    }

    /**
     * Get the days label for EVERY_WEEKS type.
     */
    @NonNull
    private String getDaysLabel() {
        if (enabledDays == ALL_DAYS) {
            return "Every day";
        }

        StringBuilder sb = new StringBuilder();
        if (isDayEnabled(SUNDAY)) sb.append("Sun, ");
        if (isDayEnabled(MONDAY)) sb.append("Mon, ");
        if (isDayEnabled(TUESDAY)) sb.append("Tue, ");
        if (isDayEnabled(WEDNESDAY)) sb.append("Wed, ");
        if (isDayEnabled(THURSDAY)) sb.append("Thu, ");
        if (isDayEnabled(FRIDAY)) sb.append("Fri, ");
        if (isDayEnabled(SATURDAY)) sb.append("Sat, ");

        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2); // Remove trailing ", "
        }
        return sb.toString();
    }

    /**
     * Get the week parity label.
     */
    @NonNull
    private String getWeekParityLabel() {
        switch (weekParity) {
            case EVEN:
                return " (Even weeks)";
            case ODD:
                return " (Odd weeks)";
            default:
                return "";
        }
    }

    /**
     * Get a display summary for this schedule.
     *
     * @return Summary string showing interval
     */
    @NonNull
    public String getSummary() {
        if (intervalType == ScheduleType.SPECIFIC_HOUR) {
            return String.format(Locale.getDefault(), "At %02d:%02d", scheduleHour, scheduleMinute);
        } else {
            if (intervalMinutes >= 60) {
                int hours = intervalMinutes / 60;
                int mins = intervalMinutes % 60;
                if (mins == 0) {
                    return hours == 1 ? "Every hour" : "Every " + hours + " hours";
                } else {
                    return "Every " + hours + "h " + mins + "m";
                }
            } else {
                return "Every " + intervalMinutes + " min";
            }
        }
    }

    // JSON serialization

    /**
     * Convert this schedule to a JSON object.
     *
     * @return JSON representation
     * @throws JSONException if serialization fails
     */
    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("calendarType", calendarType.name());
        json.put("intervalType", intervalType.name());
        json.put("enabled", enabled);
        json.put("order", order);
        json.put("intervalMinutes", intervalMinutes);
        json.put("scheduleHour", scheduleHour);
        json.put("scheduleMinute", scheduleMinute);
        json.put("selectedDate", selectedDate);
        json.put("fromDate", fromDate);
        json.put("toDate", toDate);
        json.put("enabledDays", enabledDays);
        json.put("weekParity", weekParity.name());
        return json;
    }

    /**
     * Create a schedule from a JSON object.
     *
     * @param json JSON object
     * @return Schedule instance
     * @throws JSONException if parsing fails
     */
    @NonNull
    public static Schedule fromJson(@NonNull JSONObject json) throws JSONException {
        Schedule schedule = new Schedule();
        schedule.id = json.optString("id", UUID.randomUUID().toString());
        schedule.calendarType = CalendarScheduleType.valueOf(
                json.optString("calendarType", "ALL_THE_TIME"));
        schedule.intervalType = ScheduleType.valueOf(
                json.optString("intervalType", "PERIODIC"));
        schedule.enabled = json.optBoolean("enabled", true);
        schedule.order = json.optInt("order", 0);
        schedule.intervalMinutes = json.optInt("intervalMinutes", 60);
        schedule.scheduleHour = json.optInt("scheduleHour", 9);
        schedule.scheduleMinute = json.optInt("scheduleMinute", 0);
        schedule.selectedDate = json.optLong("selectedDate", System.currentTimeMillis());
        schedule.fromDate = json.optLong("fromDate", System.currentTimeMillis());
        schedule.toDate = json.optLong("toDate", System.currentTimeMillis());
        schedule.enabledDays = json.optInt("enabledDays", ALL_DAYS);
        schedule.weekParity = WeekParity.valueOf(
                json.optString("weekParity", "BOTH"));
        return schedule;
    }

    /**
     * Convert a list of schedules to a JSON string.
     *
     * @param schedules List of schedules
     * @return JSON string
     */
    @Nullable
    public static String toJsonString(@Nullable List<Schedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray();
            for (Schedule schedule : schedules) {
                array.put(schedule.toJson());
            }
            return array.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Parse a list of schedules from a JSON string.
     *
     * @param jsonString JSON string
     * @return List of schedules (empty if parsing fails)
     */
    @NonNull
    public static List<Schedule> fromJsonString(@Nullable String jsonString) {
        List<Schedule> schedules = new ArrayList<>();
        if (jsonString == null || jsonString.isEmpty()) {
            return schedules;
        }
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                schedules.add(fromJson(json));
            }
        } catch (JSONException e) {
            // Return empty list on error
        }
        return schedules;
    }

    /**
     * Create a default list with one ALL_THE_TIME schedule.
     *
     * @return List with default schedule
     */
    @NonNull
    public static List<Schedule> createDefaultList() {
        List<Schedule> list = new ArrayList<>();
        list.add(createAllTheTime());
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;
        return Objects.equals(id, schedule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Create a copy of this schedule with a new ID.
     *
     * @return Copy of the schedule
     */
    @NonNull
    public Schedule copy() {
        Schedule copy = new Schedule();
        copy.id = UUID.randomUUID().toString();
        copy.calendarType = this.calendarType;
        copy.intervalType = this.intervalType;
        copy.enabled = this.enabled;
        copy.order = this.order;
        copy.intervalMinutes = this.intervalMinutes;
        copy.scheduleHour = this.scheduleHour;
        copy.scheduleMinute = this.scheduleMinute;
        copy.selectedDate = this.selectedDate;
        copy.fromDate = this.fromDate;
        copy.toDate = this.toDate;
        copy.enabledDays = this.enabledDays;
        copy.weekParity = this.weekParity;
        return copy;
    }
}
