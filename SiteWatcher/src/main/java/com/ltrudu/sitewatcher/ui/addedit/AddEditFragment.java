package com.ltrudu.sitewatcher.ui.addedit;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.ScheduleType;
import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.Locale;

/**
 * Fragment for adding or editing a watched site.
 * Receives siteId argument: -1 for add mode, otherwise edit mode.
 */
public class AddEditFragment extends Fragment {

    private static final String ARG_SITE_ID = "siteId";
    private static final String BROWSER_RESULT_KEY = "browserResult";
    private static final String URL_KEY = "url";

    private AddEditViewModel viewModel;

    // Views
    private TextInputLayout urlInputLayout;
    private TextInputEditText urlEditText;
    private Spinner comparisonModeSpinner;
    private TextInputLayout cssSelectorInputLayout;
    private TextInputEditText cssSelectorEditText;
    private ToggleButton toggleSunday;
    private ToggleButton toggleMonday;
    private ToggleButton toggleTuesday;
    private ToggleButton toggleWednesday;
    private ToggleButton toggleThursday;
    private ToggleButton toggleFriday;
    private ToggleButton toggleSaturday;
    private Spinner scheduleTypeSpinner;
    private View intervalSection;
    private Slider intervalSlider;
    private View specificTimeSection;
    private MaterialButton timePickerButton;
    private Slider thresholdSlider;
    private MaterialButton cancelButton;
    private MaterialButton saveButton;
    private android.widget.TextView intervalValueLabel;
    private android.widget.TextView thresholdValueLabel;

    // Adapters
    private ArrayAdapter<String> comparisonModeAdapter;
    private ArrayAdapter<String> scheduleTypeAdapter;

    // Flag to prevent listener callbacks during initialization
    private boolean isInitializing = true;

    public AddEditFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AddEditViewModel.class);

        // Initialize with site ID from arguments
        long siteId = -1L;
        if (getArguments() != null) {
            siteId = getArguments().getLong(ARG_SITE_ID, -1L);
        }
        viewModel.initialize(siteId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupSpinners();
        setupListeners();
        observeViewModel();

        // Check for result from BrowserDiscoveryFragment
        checkForDiscoveredUrl();

        isInitializing = false;
    }

    private void initializeViews(View view) {
        urlInputLayout = view.findViewById(R.id.urlInputLayout);
        urlEditText = view.findViewById(R.id.urlEditText);
        comparisonModeSpinner = view.findViewById(R.id.comparisonModeSpinner);
        cssSelectorInputLayout = view.findViewById(R.id.cssSelectorInputLayout);
        cssSelectorEditText = view.findViewById(R.id.cssSelectorEditText);
        toggleSunday = view.findViewById(R.id.toggleSunday);
        toggleMonday = view.findViewById(R.id.toggleMonday);
        toggleTuesday = view.findViewById(R.id.toggleTuesday);
        toggleWednesday = view.findViewById(R.id.toggleWednesday);
        toggleThursday = view.findViewById(R.id.toggleThursday);
        toggleFriday = view.findViewById(R.id.toggleFriday);
        toggleSaturday = view.findViewById(R.id.toggleSaturday);
        scheduleTypeSpinner = view.findViewById(R.id.scheduleTypeSpinner);
        intervalSection = view.findViewById(R.id.intervalSection);
        intervalSlider = view.findViewById(R.id.intervalSlider);
        specificTimeSection = view.findViewById(R.id.specificTimeSection);
        timePickerButton = view.findViewById(R.id.timePickerButton);
        thresholdSlider = view.findViewById(R.id.thresholdSlider);
        cancelButton = view.findViewById(R.id.cancelButton);
        saveButton = view.findViewById(R.id.saveButton);
        intervalValueLabel = view.findViewById(R.id.intervalValueLabel);
        thresholdValueLabel = view.findViewById(R.id.thresholdValueLabel);

        // Set button text based on mode
        if (viewModel.isEditMode()) {
            saveButton.setText(R.string.save);
        } else {
            saveButton.setText(R.string.add);
        }
    }

    private void setupSpinners() {
        // Comparison mode spinner
        String[] comparisonModes = new String[]{
                getString(R.string.comparison_full_html),
                getString(R.string.comparison_text_only),
                getString(R.string.comparison_css_selector)
        };
        comparisonModeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                comparisonModes
        );
        comparisonModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        comparisonModeSpinner.setAdapter(comparisonModeAdapter);

        // Schedule type spinner
        String[] scheduleTypes = new String[]{
                getString(R.string.schedule_periodic),
                getString(R.string.schedule_specific_hour)
        };
        scheduleTypeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                scheduleTypes
        );
        scheduleTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scheduleTypeSpinner.setAdapter(scheduleTypeAdapter);
    }

    private void setupListeners() {
        // URL input with validation
        urlEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isInitializing) {
                    String urlText = s.toString();
                    viewModel.setUrl(urlText);
                    viewModel.validateUrl(urlText);
                }
            }
        });

        // Planet button (browse) click
        urlInputLayout.setEndIconOnClickListener(v -> navigateToBrowserDiscovery());

        // Comparison mode spinner
        comparisonModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    ComparisonMode mode = getComparisonModeFromPosition(position);
                    viewModel.setComparisonMode(mode);
                    updateCssSelectorVisibility(mode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // CSS Selector input
        cssSelectorEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isInitializing) {
                    viewModel.setCssSelector(s.toString());
                }
            }
        });

        // Day toggle buttons
        setupDayToggle(toggleSunday, WatchedSite.SUNDAY);
        setupDayToggle(toggleMonday, WatchedSite.MONDAY);
        setupDayToggle(toggleTuesday, WatchedSite.TUESDAY);
        setupDayToggle(toggleWednesday, WatchedSite.WEDNESDAY);
        setupDayToggle(toggleThursday, WatchedSite.THURSDAY);
        setupDayToggle(toggleFriday, WatchedSite.FRIDAY);
        setupDayToggle(toggleSaturday, WatchedSite.SATURDAY);

        // Schedule type spinner
        scheduleTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    ScheduleType type = position == 0 ? ScheduleType.PERIODIC : ScheduleType.SPECIFIC_HOUR;
                    viewModel.setScheduleType(type);
                    updateScheduleSectionVisibility(type);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Interval slider
        intervalSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isInitializing) {
                viewModel.setIntervalMinutes((int) value);
            }
            updateIntervalLabel((int) value);
        });

        // Time picker button
        timePickerButton.setOnClickListener(v -> showTimePicker());

        // Threshold slider
        thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isInitializing) {
                viewModel.setThresholdPercent((int) value);
            }
            updateThresholdLabel((int) value);
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> navigateBack());

        // Save button
        saveButton.setOnClickListener(v -> viewModel.save());
    }

    private void setupDayToggle(ToggleButton toggle, int dayBitmask) {
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isInitializing) {
                viewModel.setDayEnabled(dayBitmask, isChecked);
            }
        });
    }

    private void observeViewModel() {
        // Observe URL
        viewModel.getUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.equals(urlEditText.getText().toString())) {
                urlEditText.setText(url);
                urlEditText.setSelection(url.length());
            }
        });

        // Observe URL validation
        viewModel.getIsUrlValid().observe(getViewLifecycleOwner(), isValid -> {
            saveButton.setEnabled(isValid != null && isValid);
        });

        viewModel.getUrlError().observe(getViewLifecycleOwner(), error -> {
            urlInputLayout.setError(error);
        });

        // Observe enabled days
        viewModel.getEnabledDays().observe(getViewLifecycleOwner(), days -> {
            if (days != null) {
                updateDayToggles(days);
            }
        });

        // Observe comparison mode
        viewModel.getComparisonMode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                int position = getPositionFromComparisonMode(mode);
                if (comparisonModeSpinner.getSelectedItemPosition() != position) {
                    comparisonModeSpinner.setSelection(position);
                }
                updateCssSelectorVisibility(mode);
            }
        });

        // Observe CSS selector
        viewModel.getCssSelector().observe(getViewLifecycleOwner(), selector -> {
            if (selector != null && !selector.equals(cssSelectorEditText.getText().toString())) {
                cssSelectorEditText.setText(selector);
            }
        });

        // Observe schedule type
        viewModel.getScheduleType().observe(getViewLifecycleOwner(), type -> {
            if (type != null) {
                int position = type == ScheduleType.PERIODIC ? 0 : 1;
                if (scheduleTypeSpinner.getSelectedItemPosition() != position) {
                    scheduleTypeSpinner.setSelection(position);
                }
                updateScheduleSectionVisibility(type);
            }
        });

        // Observe interval
        viewModel.getIntervalMinutes().observe(getViewLifecycleOwner(), interval -> {
            if (interval != null) {
                intervalSlider.setValue(interval);
                updateIntervalLabel(interval);
            }
        });

        // Observe schedule time
        viewModel.getScheduleHour().observe(getViewLifecycleOwner(), hour -> updateTimePickerButton());
        viewModel.getScheduleMinute().observe(getViewLifecycleOwner(), minute -> updateTimePickerButton());

        // Observe threshold
        viewModel.getThresholdPercent().observe(getViewLifecycleOwner(), threshold -> {
            if (threshold != null) {
                thresholdSlider.setValue(threshold);
                updateThresholdLabel(threshold);
            }
        });

        // Observe save result
        viewModel.getSaveResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null && result.isSuccess()) {
                // Set result for calling fragment if needed
                if (result.getSite() != null) {
                    Bundle bundle = new Bundle();
                    bundle.putLong("siteId", result.getSite().getId());
                    getParentFragmentManager().setFragmentResult("addEditResult", bundle);
                }
                navigateBack();
            }
        });
    }

    private void checkForDiscoveredUrl() {
        // Listen for result from BrowserDiscoveryFragment
        getParentFragmentManager().setFragmentResultListener(
                BROWSER_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String discoveredUrl = result.getString(URL_KEY);
                    if (discoveredUrl != null && !discoveredUrl.isEmpty()) {
                        viewModel.setUrl(discoveredUrl);
                        viewModel.validateUrl(discoveredUrl);
                    }
                }
        );
    }

    private void updateDayToggles(int enabledDays) {
        toggleSunday.setChecked((enabledDays & WatchedSite.SUNDAY) != 0);
        toggleMonday.setChecked((enabledDays & WatchedSite.MONDAY) != 0);
        toggleTuesday.setChecked((enabledDays & WatchedSite.TUESDAY) != 0);
        toggleWednesday.setChecked((enabledDays & WatchedSite.WEDNESDAY) != 0);
        toggleThursday.setChecked((enabledDays & WatchedSite.THURSDAY) != 0);
        toggleFriday.setChecked((enabledDays & WatchedSite.FRIDAY) != 0);
        toggleSaturday.setChecked((enabledDays & WatchedSite.SATURDAY) != 0);
    }

    private void updateCssSelectorVisibility(ComparisonMode mode) {
        cssSelectorInputLayout.setVisibility(
                mode == ComparisonMode.CSS_SELECTOR ? View.VISIBLE : View.GONE
        );
    }

    private void updateScheduleSectionVisibility(ScheduleType type) {
        if (type == ScheduleType.PERIODIC) {
            intervalSection.setVisibility(View.VISIBLE);
            specificTimeSection.setVisibility(View.GONE);
        } else {
            intervalSection.setVisibility(View.GONE);
            specificTimeSection.setVisibility(View.VISIBLE);
        }
    }

    private void updateIntervalLabel(int minutes) {
        intervalValueLabel.setText(getString(R.string.check_interval_value, minutes));
    }

    private void updateThresholdLabel(int percent) {
        thresholdValueLabel.setText(getString(R.string.threshold_value, percent));
    }

    private void updateTimePickerButton() {
        Integer hour = viewModel.getScheduleHour().getValue();
        Integer minute = viewModel.getScheduleMinute().getValue();
        if (hour != null && minute != null) {
            timePickerButton.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
        }
    }

    private void showTimePicker() {
        Integer hour = viewModel.getScheduleHour().getValue();
        Integer minute = viewModel.getScheduleMinute().getValue();

        TimePickerDialog dialog = new TimePickerDialog(
                requireContext(),
                (view, selectedHour, selectedMinute) -> {
                    viewModel.setScheduleTime(selectedHour, selectedMinute);
                },
                hour != null ? hour : 9,
                minute != null ? minute : 0,
                true
        );
        dialog.show();
    }

    private ComparisonMode getComparisonModeFromPosition(int position) {
        switch (position) {
            case 0:
                return ComparisonMode.FULL_HTML;
            case 1:
                return ComparisonMode.TEXT_ONLY;
            case 2:
                return ComparisonMode.CSS_SELECTOR;
            default:
                return ComparisonMode.TEXT_ONLY;
        }
    }

    private int getPositionFromComparisonMode(ComparisonMode mode) {
        switch (mode) {
            case FULL_HTML:
                return 0;
            case TEXT_ONLY:
                return 1;
            case CSS_SELECTOR:
                return 2;
            default:
                return 1;
        }
    }

    private void navigateToBrowserDiscovery() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_browserDiscovery);
    }

    private void navigateBack() {
        NavController navController = Navigation.findNavController(requireView());
        navController.popBackStack();
    }
}
