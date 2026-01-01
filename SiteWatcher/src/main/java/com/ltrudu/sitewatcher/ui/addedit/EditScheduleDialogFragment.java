package com.ltrudu.sitewatcher.ui.addedit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.slider.Slider;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.CalendarScheduleType;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WeekParity;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * DialogFragment for adding or editing schedule configurations.
 * Supports multiple schedule types: All The Time, Selected Day, Date Range, Every Weeks.
 */
public class EditScheduleDialogFragment extends DialogFragment {

    private static final String TAG = "EditScheduleDialog";
    private static final String ARG_SCHEDULE_JSON = "schedule_json";

    public static final String RESULT_KEY = "edit_schedule_result";
    public static final String RESULT_SCHEDULE_JSON = "schedule_json";

    // Views - Schedule Type
    private Spinner spinnerScheduleType;
    private Spinner spinnerIntervalType;
    private Spinner spinnerWeekParity;

    // Views - Periodic Interval
    private View periodicSection;
    private Slider intervalSlider;
    private TextView intervalValueLabel;

    // Views - Specific Time
    private View specificTimeSection;
    private MaterialButton timePickerButton;

    // Views - Selected Day
    private View selectedDaySection;
    private MaterialButton datePickerButton;

    // Views - Date Range
    private View dateRangeSection;
    private MaterialButton fromDatePickerButton;
    private MaterialButton toDatePickerButton;

    // Views - Every Weeks
    private View everyWeeksSection;
    private ToggleButton toggleSunday;
    private ToggleButton toggleMonday;
    private ToggleButton toggleTuesday;
    private ToggleButton toggleWednesday;
    private ToggleButton toggleThursday;
    private ToggleButton toggleFriday;
    private ToggleButton toggleSaturday;

    // Views - Action Buttons
    private MaterialButton buttonCancel;
    private MaterialButton buttonSave;

    // Data
    private Schedule schedule;
    private boolean isEditMode = false;
    private SimpleDateFormat dateFormat;

    /**
     * Create a new instance for adding a new schedule.
     *
     * @return New dialog fragment instance
     */
    public static EditScheduleDialogFragment newInstance() {
        return new EditScheduleDialogFragment();
    }

    /**
     * Create a new instance for editing an existing schedule.
     *
     * @param schedule The schedule to edit
     * @return New dialog fragment instance
     */
    public static EditScheduleDialogFragment newInstance(@NonNull Schedule schedule) {
        EditScheduleDialogFragment fragment = new EditScheduleDialogFragment();
        Bundle args = new Bundle();
        try {
            args.putString(ARG_SCHEDULE_JSON, schedule.toJson().toString());
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing schedule", e);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        // Parse arguments
        if (getArguments() != null) {
            String scheduleJson = getArguments().getString(ARG_SCHEDULE_JSON);
            if (scheduleJson != null && !scheduleJson.isEmpty()) {
                try {
                    schedule = Schedule.fromJson(new JSONObject(scheduleJson));
                    isEditMode = true;
                } catch (JSONException e) {
                    Logger.e(TAG, "Error parsing schedule JSON", e);
                }
            }
        }

        // Create default schedule if not editing
        if (schedule == null) {
            schedule = new Schedule();
        }

        // Set dialog style
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupSpinners();
        setupListeners();
        populateFields();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Set dialog width to match parent with margins
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void initializeViews(@NonNull View view) {
        // Spinners
        spinnerScheduleType = view.findViewById(R.id.spinnerScheduleType);
        spinnerIntervalType = view.findViewById(R.id.spinnerIntervalType);
        spinnerWeekParity = view.findViewById(R.id.spinnerWeekParity);

        // Periodic section
        periodicSection = view.findViewById(R.id.periodicSection);
        intervalSlider = view.findViewById(R.id.intervalSlider);
        intervalValueLabel = view.findViewById(R.id.intervalValueLabel);

        // Specific time section
        specificTimeSection = view.findViewById(R.id.specificTimeSection);
        timePickerButton = view.findViewById(R.id.timePickerButton);

        // Selected day section
        selectedDaySection = view.findViewById(R.id.selectedDaySection);
        datePickerButton = view.findViewById(R.id.datePickerButton);

        // Date range section
        dateRangeSection = view.findViewById(R.id.dateRangeSection);
        fromDatePickerButton = view.findViewById(R.id.fromDatePickerButton);
        toDatePickerButton = view.findViewById(R.id.toDatePickerButton);

        // Every weeks section
        everyWeeksSection = view.findViewById(R.id.everyWeeksSection);
        toggleSunday = view.findViewById(R.id.toggleSunday);
        toggleMonday = view.findViewById(R.id.toggleMonday);
        toggleTuesday = view.findViewById(R.id.toggleTuesday);
        toggleWednesday = view.findViewById(R.id.toggleWednesday);
        toggleThursday = view.findViewById(R.id.toggleThursday);
        toggleFriday = view.findViewById(R.id.toggleFriday);
        toggleSaturday = view.findViewById(R.id.toggleSaturday);

        // Action buttons
        buttonCancel = view.findViewById(R.id.buttonCancel);
        buttonSave = view.findViewById(R.id.buttonSave);

        // Set save button text based on mode
        buttonSave.setText(isEditMode ? R.string.update : R.string.add);
    }

    private void setupSpinners() {
        // Schedule Type Spinner (CalendarScheduleType)
        String[] scheduleTypes = new String[]{
                getString(R.string.schedule_all_the_time),
                getString(R.string.schedule_selected_day),
                getString(R.string.schedule_date_range),
                getString(R.string.schedule_every_weeks)
        };

        ArrayAdapter<String> scheduleTypeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                scheduleTypes
        );
        scheduleTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScheduleType.setAdapter(scheduleTypeAdapter);

        // Interval Type Spinner (ScheduleType)
        String[] intervalTypes = new String[]{
                getString(R.string.interval_periodic),
                getString(R.string.interval_specific_hour)
        };

        ArrayAdapter<String> intervalTypeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                intervalTypes
        );
        intervalTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIntervalType.setAdapter(intervalTypeAdapter);

        // Week Parity Spinner
        String[] weekParities = new String[]{
                getString(R.string.week_parity_both),
                getString(R.string.week_parity_even),
                getString(R.string.week_parity_odd)
        };

        ArrayAdapter<String> weekParityAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                weekParities
        );
        weekParityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWeekParity.setAdapter(weekParityAdapter);
    }

    private void setupListeners() {
        // Schedule type spinner listener
        spinnerScheduleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CalendarScheduleType type = getCalendarScheduleTypeFromPosition(position);
                schedule.setCalendarType(type);
                updateScheduleTypeVisibility(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateScheduleTypeVisibility(CalendarScheduleType.ALL_THE_TIME);
            }
        });

        // Interval type spinner listener
        spinnerIntervalType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ScheduleType type = position == 0 ? ScheduleType.PERIODIC : ScheduleType.SPECIFIC_HOUR;
                schedule.setIntervalType(type);
                updateIntervalTypeVisibility(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateIntervalTypeVisibility(ScheduleType.PERIODIC);
            }
        });

        // Week parity spinner listener
        spinnerWeekParity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WeekParity parity;
                switch (position) {
                    case 1:
                        parity = WeekParity.EVEN;
                        break;
                    case 2:
                        parity = WeekParity.ODD;
                        break;
                    default:
                        parity = WeekParity.BOTH;
                        break;
                }
                schedule.setWeekParity(parity);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                schedule.setWeekParity(WeekParity.BOTH);
            }
        });

        // Interval slider listener
        intervalSlider.addOnChangeListener((slider, value, fromUser) -> {
            int minutes = (int) value;
            schedule.setIntervalMinutes(minutes);
            updateIntervalLabel(minutes);
        });

        // Time picker button
        timePickerButton.setOnClickListener(v -> showTimePicker());

        // Date picker button (selected day)
        datePickerButton.setOnClickListener(v -> showDatePicker(DatePickerMode.SELECTED_DAY));

        // From date picker button
        fromDatePickerButton.setOnClickListener(v -> showDatePicker(DatePickerMode.FROM_DATE));

        // To date picker button
        toDatePickerButton.setOnClickListener(v -> showDatePicker(DatePickerMode.TO_DATE));

        // Day toggle buttons
        setupDayToggle(toggleSunday, Schedule.SUNDAY);
        setupDayToggle(toggleMonday, Schedule.MONDAY);
        setupDayToggle(toggleTuesday, Schedule.TUESDAY);
        setupDayToggle(toggleWednesday, Schedule.WEDNESDAY);
        setupDayToggle(toggleThursday, Schedule.THURSDAY);
        setupDayToggle(toggleFriday, Schedule.FRIDAY);
        setupDayToggle(toggleSaturday, Schedule.SATURDAY);

        // Cancel button
        buttonCancel.setOnClickListener(v -> dismiss());

        // Save button
        buttonSave.setOnClickListener(v -> saveSchedule());
    }

    private void setupDayToggle(@NonNull ToggleButton toggle, int dayBitmask) {
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Don't allow disabling all days
            if (!isChecked && countEnabledDays() <= 1) {
                // Revert the toggle - keep it checked
                toggle.setChecked(true);
                return;
            }
            schedule.setDayEnabled(dayBitmask, isChecked);
        });
    }

    private int countEnabledDays() {
        int count = 0;
        int enabledDays = schedule.getEnabledDays();
        while (enabledDays != 0) {
            count += enabledDays & 1;
            enabledDays >>= 1;
        }
        return count;
    }

    private void updateDayToggleState(@NonNull ToggleButton toggle, boolean enabled) {
        // ToggleButton handles visual state automatically via toggle_day_background drawable
        toggle.setChecked(enabled);
    }

    private void populateFields() {
        // Set schedule type spinner
        int scheduleTypePosition = getPositionFromCalendarScheduleType(schedule.getCalendarType());
        spinnerScheduleType.setSelection(scheduleTypePosition);

        // Set interval type spinner
        int intervalTypePosition = schedule.getIntervalType() == ScheduleType.PERIODIC ? 0 : 1;
        spinnerIntervalType.setSelection(intervalTypePosition);

        // Set week parity spinner
        int weekParityPosition;
        switch (schedule.getWeekParity()) {
            case EVEN:
                weekParityPosition = 1;
                break;
            case ODD:
                weekParityPosition = 2;
                break;
            default:
                weekParityPosition = 0;
                break;
        }
        spinnerWeekParity.setSelection(weekParityPosition);

        // Set interval slider
        intervalSlider.setValue(Math.max(15, Math.min(600, schedule.getIntervalMinutes())));
        updateIntervalLabel(schedule.getIntervalMinutes());

        // Set time picker button
        updateTimePickerButton();

        // Set date picker button (selected day)
        datePickerButton.setText(dateFormat.format(new Date(schedule.getSelectedDate())));

        // Set date range buttons
        fromDatePickerButton.setText(dateFormat.format(new Date(schedule.getFromDate())));
        toDatePickerButton.setText(dateFormat.format(new Date(schedule.getToDate())));

        // Set day toggles
        updateDayToggleState(toggleSunday, schedule.isDayEnabled(Schedule.SUNDAY));
        updateDayToggleState(toggleMonday, schedule.isDayEnabled(Schedule.MONDAY));
        updateDayToggleState(toggleTuesday, schedule.isDayEnabled(Schedule.TUESDAY));
        updateDayToggleState(toggleWednesday, schedule.isDayEnabled(Schedule.WEDNESDAY));
        updateDayToggleState(toggleThursday, schedule.isDayEnabled(Schedule.THURSDAY));
        updateDayToggleState(toggleFriday, schedule.isDayEnabled(Schedule.FRIDAY));
        updateDayToggleState(toggleSaturday, schedule.isDayEnabled(Schedule.SATURDAY));

        // Update visibility
        updateScheduleTypeVisibility(schedule.getCalendarType());
        updateIntervalTypeVisibility(schedule.getIntervalType());
    }

    private void updateScheduleTypeVisibility(@NonNull CalendarScheduleType type) {
        // Hide all calendar-specific sections first
        selectedDaySection.setVisibility(View.GONE);
        dateRangeSection.setVisibility(View.GONE);
        everyWeeksSection.setVisibility(View.GONE);

        // Show appropriate section
        switch (type) {
            case SELECTED_DAY:
                selectedDaySection.setVisibility(View.VISIBLE);
                break;
            case DATE_RANGE:
                dateRangeSection.setVisibility(View.VISIBLE);
                break;
            case EVERY_WEEKS:
                everyWeeksSection.setVisibility(View.VISIBLE);
                break;
            case ALL_THE_TIME:
            default:
                // No extra fields needed
                break;
        }
    }

    private void updateIntervalTypeVisibility(@NonNull ScheduleType type) {
        if (type == ScheduleType.PERIODIC) {
            periodicSection.setVisibility(View.VISIBLE);
            specificTimeSection.setVisibility(View.GONE);
        } else {
            periodicSection.setVisibility(View.GONE);
            specificTimeSection.setVisibility(View.VISIBLE);
        }
    }

    private void updateIntervalLabel(int minutes) {
        String text;
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins == 0) {
                text = hours == 1 ? getString(R.string.check_interval_value, 60) :
                        String.format(Locale.getDefault(), "%d hours", hours);
            } else {
                text = String.format(Locale.getDefault(), "%dh %dm", hours, mins);
            }
        } else {
            text = getString(R.string.check_interval_value, minutes);
        }
        intervalValueLabel.setText(text);
    }

    private void updateTimePickerButton() {
        String timeText = String.format(Locale.getDefault(), "%02d:%02d",
                schedule.getScheduleHour(), schedule.getScheduleMinute());
        timePickerButton.setText(timeText);
    }

    private void showTimePicker() {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(schedule.getScheduleHour())
                .setMinute(schedule.getScheduleMinute())
                .setTitleText(R.string.specific_time)
                .build();

        timePicker.addOnPositiveButtonClickListener(v -> {
            schedule.setScheduleHour(timePicker.getHour());
            schedule.setScheduleMinute(timePicker.getMinute());
            updateTimePickerButton();
        });

        timePicker.show(getParentFragmentManager(), "time_picker");
    }

    private enum DatePickerMode {
        SELECTED_DAY,
        FROM_DATE,
        TO_DATE
    }

    private void showDatePicker(@NonNull DatePickerMode mode) {
        long initialSelection;
        switch (mode) {
            case FROM_DATE:
                initialSelection = schedule.getFromDate();
                break;
            case TO_DATE:
                initialSelection = schedule.getToDate();
                break;
            case SELECTED_DAY:
            default:
                initialSelection = schedule.getSelectedDate();
                break;
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.date)
                .setSelection(initialSelection)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                switch (mode) {
                    case SELECTED_DAY:
                        schedule.setSelectedDate(selection);
                        datePickerButton.setText(dateFormat.format(new Date(selection)));
                        break;
                    case FROM_DATE:
                        schedule.setFromDate(selection);
                        fromDatePickerButton.setText(dateFormat.format(new Date(selection)));
                        // Ensure toDate is not before fromDate
                        if (schedule.getToDate() < selection) {
                            schedule.setToDate(selection);
                            toDatePickerButton.setText(dateFormat.format(new Date(selection)));
                        }
                        break;
                    case TO_DATE:
                        // Ensure toDate is not before fromDate
                        if (selection < schedule.getFromDate()) {
                            selection = schedule.getFromDate();
                        }
                        schedule.setToDate(selection);
                        toDatePickerButton.setText(dateFormat.format(new Date(selection)));
                        break;
                }
            }
        });

        datePicker.show(getParentFragmentManager(), "date_picker");
    }

    private void saveSchedule() {
        // Validate schedule
        if (!validateSchedule()) {
            return;
        }

        // Return result via Fragment Result API
        Bundle result = new Bundle();
        try {
            result.putString(RESULT_SCHEDULE_JSON, schedule.toJson().toString());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            Logger.d(TAG, "Saved schedule: " + schedule.getCalendarType() + " - " + schedule.getIntervalType());
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing schedule for result", e);
        }

        dismiss();
    }

    private boolean validateSchedule() {
        // For EVERY_WEEKS, ensure at least one day is enabled
        if (schedule.getCalendarType() == CalendarScheduleType.EVERY_WEEKS) {
            if (schedule.getEnabledDays() == 0) {
                // This shouldn't happen with our UI logic, but check anyway
                schedule.setEnabledDays(Schedule.ALL_DAYS);
            }
        }

        // For DATE_RANGE, ensure fromDate is not after toDate
        if (schedule.getCalendarType() == CalendarScheduleType.DATE_RANGE) {
            if (schedule.getFromDate() > schedule.getToDate()) {
                schedule.setToDate(schedule.getFromDate());
            }
        }

        return true;
    }

    @NonNull
    private CalendarScheduleType getCalendarScheduleTypeFromPosition(int position) {
        switch (position) {
            case 1:
                return CalendarScheduleType.SELECTED_DAY;
            case 2:
                return CalendarScheduleType.DATE_RANGE;
            case 3:
                return CalendarScheduleType.EVERY_WEEKS;
            default:
                return CalendarScheduleType.ALL_THE_TIME;
        }
    }

    private int getPositionFromCalendarScheduleType(@NonNull CalendarScheduleType type) {
        switch (type) {
            case SELECTED_DAY:
                return 1;
            case DATE_RANGE:
                return 2;
            case EVERY_WEEKS:
                return 3;
            default:
                return 0;
        }
    }
}
