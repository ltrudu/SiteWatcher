package com.ltrudu.sitewatcher.ui.addedit;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FeedbackActionType;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * DialogFragment for adding or editing feedback actions.
 * Supports various feedback types: notification, SMS, alarm, sound, flash, and vibration.
 */
public class EditFeedbackDialogFragment extends DialogFragment {

    private static final String TAG = "EditFeedbackDialog";

    public static final String RESULT_KEY = "editFeedbackDialogResult";
    public static final String RESULT_ACTION_JSON = "action_json";
    public static final String ARG_ACTION_JSON = "action_json";
    public static final String ARG_ACTION_TYPE = "action_type";

    // Vibration patterns
    private static final long[] VIBRATION_SHORT = {0, 250};
    private static final long[] VIBRATION_LONG = {0, 500};
    private static final long[] VIBRATION_DOUBLE = {0, 250, 200, 250};
    private static final long[] VIBRATION_SOS = {0, 200, 100, 200, 100, 200, 300, 500, 100, 500, 100, 500, 300, 200, 100, 200, 100, 200};

    // Flash speed defaults (lower = faster blink)
    private static final int FLASH_SPEED_MIN = 50;
    private static final int FLASH_SPEED_MAX = 1000;
    private static final int FLASH_SPEED_DEFAULT = 200;

    // Views
    private Spinner spinnerFeedbackType;
    private TextInputLayout labelInputLayout;
    private TextInputEditText labelEditText;
    private TextInputLayout phoneInputLayout;
    private TextInputEditText phoneEditText;
    private TextInputLayout soundInputLayout;
    private TextInputEditText soundEditText;
    private LinearLayout vibrationSection;
    private Spinner spinnerVibrationPattern;
    private LinearLayout flashSection;
    private Slider sliderFlashDuration;
    private TextView textFlashDurationValue;
    private LinearLayout durationSection;
    private Slider sliderAlarmMinutes;
    private Slider sliderAlarmSeconds;
    private TextView textAlarmMinutesValue;
    private TextView textAlarmSecondsValue;
    private TextView textAlarmIndefiniteWarning;
    private MaterialButton cancelButton;
    private MaterialButton saveButton;

    // Data
    private FeedbackAction existingAction;
    private FeedbackActionType initialType;
    private boolean isEditMode = false;
    private String selectedSoundUri = null;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> phoneContactPickerLauncher;
    private ActivityResultLauncher<Intent> soundPickerLauncher;

    // Permission Request Launchers
    private ActivityResultLauncher<String> contactsPermissionLauncher;
    private ActivityResultLauncher<String> smsPermissionLauncher;
    private boolean pendingPhoneContactPick = false;

    /**
     * Create a new instance for adding a new action of a specific type.
     *
     * @param type The type of feedback action to create
     * @return New dialog fragment instance
     */
    @NonNull
    public static EditFeedbackDialogFragment newInstance(@NonNull FeedbackActionType type) {
        EditFeedbackDialogFragment fragment = new EditFeedbackDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACTION_TYPE, type.name());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Create a new instance for editing an existing action.
     *
     * @param action The action to edit
     * @return New dialog fragment instance
     */
    @NonNull
    public static EditFeedbackDialogFragment newInstance(@NonNull FeedbackAction action) {
        EditFeedbackDialogFragment fragment = new EditFeedbackDialogFragment();
        Bundle args = new Bundle();
        try {
            args.putString(ARG_ACTION_JSON, action.toJson().toString());
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing action", e);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments
        if (getArguments() != null) {
            String actionJson = getArguments().getString(ARG_ACTION_JSON);
            String actionType = getArguments().getString(ARG_ACTION_TYPE);

            if (actionJson != null && !actionJson.isEmpty()) {
                try {
                    existingAction = FeedbackAction.fromJson(new JSONObject(actionJson));
                    isEditMode = true;
                    initialType = existingAction.getType();
                } catch (JSONException e) {
                    Logger.e(TAG, "Error parsing action JSON", e);
                }
            } else if (actionType != null && !actionType.isEmpty()) {
                try {
                    initialType = FeedbackActionType.valueOf(actionType);
                } catch (IllegalArgumentException e) {
                    initialType = FeedbackActionType.NOTIFICATION;
                }
            } else {
                initialType = FeedbackActionType.NOTIFICATION;
            }
        } else {
            initialType = FeedbackActionType.NOTIFICATION;
        }

        // Set dialog style
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);

        // Register activity result launchers
        registerActivityResultLaunchers();
    }

    private void registerActivityResultLaunchers() {
        // Phone contact picker
        phoneContactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        handlePhoneContactResult(result.getData());
                    }
                }
        );

        // Sound picker
        soundPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        handleSoundPickerResult(result.getData());
                    }
                }
        );

        // Contacts permission request
        contactsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        // Permission granted, launch the pending contact picker
                        if (pendingPhoneContactPick) {
                            pendingPhoneContactPick = false;
                            launchPhoneContactPicker();
                        }
                    } else {
                        // Permission denied
                        Toast.makeText(requireContext(), R.string.contacts_permission_required, Toast.LENGTH_SHORT).show();
                        pendingPhoneContactPick = false;
                    }
                }
        );

        // SMS permission request
        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        // Permission granted, proceed with save
                        performSave();
                    } else {
                        // Permission denied
                        Toast.makeText(requireContext(), R.string.sms_permission_required, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_feedback, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupTypeSpinner();
        setupVibrationSpinner();
        setupFlashSlider();
        setupDurationSliders();
        setupFieldListeners();
        setupContactPickers();
        setupSoundPicker();
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
        spinnerFeedbackType = view.findViewById(R.id.spinnerFeedbackType);
        labelInputLayout = view.findViewById(R.id.labelInputLayout);
        labelEditText = view.findViewById(R.id.labelEditText);
        phoneInputLayout = view.findViewById(R.id.phoneInputLayout);
        phoneEditText = view.findViewById(R.id.phoneEditText);
        soundInputLayout = view.findViewById(R.id.soundInputLayout);
        soundEditText = view.findViewById(R.id.soundEditText);
        vibrationSection = view.findViewById(R.id.vibrationSection);
        spinnerVibrationPattern = view.findViewById(R.id.spinnerVibrationPattern);
        flashSection = view.findViewById(R.id.flashSection);
        sliderFlashDuration = view.findViewById(R.id.sliderFlashDuration);
        textFlashDurationValue = view.findViewById(R.id.textFlashDurationValue);
        durationSection = view.findViewById(R.id.durationSection);
        sliderAlarmMinutes = view.findViewById(R.id.sliderAlarmMinutes);
        sliderAlarmSeconds = view.findViewById(R.id.sliderAlarmSeconds);
        textAlarmMinutesValue = view.findViewById(R.id.textAlarmMinutesValue);
        textAlarmSecondsValue = view.findViewById(R.id.textAlarmSecondsValue);
        textAlarmIndefiniteWarning = view.findViewById(R.id.textAlarmIndefiniteWarning);
        cancelButton = view.findViewById(R.id.buttonCancel);
        saveButton = view.findViewById(R.id.buttonSave);
    }

    private void setupTypeSpinner() {
        String[] feedbackTypes = new String[]{
                getString(R.string.feedback_notification),
                getString(R.string.feedback_send_sms),
                getString(R.string.feedback_trigger_alarm),
                getString(R.string.feedback_play_sound),
                getString(R.string.feedback_camera_flash),
                getString(R.string.feedback_vibrate)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                feedbackTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFeedbackType.setAdapter(adapter);

        spinnerFeedbackType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                FeedbackActionType type = getTypeFromPosition(position);
                updateFieldsVisibility(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateFieldsVisibility(FeedbackActionType.NOTIFICATION);
            }
        });

        // Set initial type
        spinnerFeedbackType.setSelection(getPositionFromType(initialType));
    }

    private void setupVibrationSpinner() {
        String[] vibrationPatterns = new String[]{
                getString(R.string.vibration_short),
                getString(R.string.vibration_long),
                getString(R.string.vibration_double),
                getString(R.string.vibration_sos)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                vibrationPatterns
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVibrationPattern.setAdapter(adapter);
    }

    private void setupFlashSlider() {
        // Flash speed slider: 50-1000ms in 50ms steps (lower = faster blink)
        sliderFlashDuration.setValueFrom(1f);
        sliderFlashDuration.setValueTo(20f);  // 20 steps: 50, 100, 150, ..., 1000
        sliderFlashDuration.setStepSize(1f);
        sliderFlashDuration.setValue(sliderValueFromSpeedMs(FLASH_SPEED_DEFAULT));

        updateFlashSpeedText(FLASH_SPEED_DEFAULT);

        sliderFlashDuration.addOnChangeListener((slider, value, fromUser) -> {
            int speedMs = speedMsFromSliderValue((int) value);
            updateFlashSpeedText(speedMs);
        });
    }

    private int sliderValueFromSpeedMs(int ms) {
        // Convert ms (50-1000) to slider value (1-20)
        return Math.max(1, Math.min(20, (ms - FLASH_SPEED_MIN) / 50 + 1));
    }

    private int speedMsFromSliderValue(int value) {
        // Convert slider value (1-20) to ms (50-1000)
        return FLASH_SPEED_MIN + (value - 1) * 50;
    }

    private void updateFlashSpeedText(int speedMs) {
        // Display speed in milliseconds with description
        String speedDesc;
        if (speedMs <= 100) {
            speedDesc = getString(R.string.flash_speed_fast);
        } else if (speedMs <= 300) {
            speedDesc = getString(R.string.flash_speed_medium);
        } else {
            speedDesc = getString(R.string.flash_speed_slow);
        }
        String text = String.format(java.util.Locale.getDefault(), "%d ms (%s)", speedMs, speedDesc);
        textFlashDurationValue.setText(text);
    }

    private void setupDurationSliders() {
        // Initialize sliders with default values (10 seconds = 0 min, 10 sec)
        sliderAlarmMinutes.setValue(0);
        sliderAlarmSeconds.setValue(10);
        updateDurationDisplay();

        // Minutes slider listener
        sliderAlarmMinutes.addOnChangeListener((slider, value, fromUser) -> {
            textAlarmMinutesValue.setText(String.valueOf((int) value));
            updateDurationDisplay();
            updateIndefiniteWarning();
        });

        // Seconds slider listener
        sliderAlarmSeconds.addOnChangeListener((slider, value, fromUser) -> {
            textAlarmSecondsValue.setText(String.valueOf((int) value));
            updateDurationDisplay();
            updateIndefiniteWarning();
        });
    }

    private void updateDurationDisplay() {
        int minutes = (int) sliderAlarmMinutes.getValue();
        int seconds = (int) sliderAlarmSeconds.getValue();
        textAlarmMinutesValue.setText(String.valueOf(minutes));
        textAlarmSecondsValue.setText(String.valueOf(seconds));
    }

    private void updateIndefiniteWarning() {
        int totalSeconds = getTotalDurationSeconds();
        FeedbackActionType type = getSelectedFeedbackType();
        // Only show indefinite warning for TRIGGER_ALARM when duration is 0
        boolean showWarning = (type == FeedbackActionType.TRIGGER_ALARM && totalSeconds == 0);
        textAlarmIndefiniteWarning.setVisibility(showWarning ? View.VISIBLE : View.GONE);
    }

    private int getTotalDurationSeconds() {
        int minutes = (int) sliderAlarmMinutes.getValue();
        int seconds = (int) sliderAlarmSeconds.getValue();
        return minutes * 60 + seconds;
    }

    private void setDurationSlidersFromSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        // Clamp values to slider ranges
        minutes = Math.min(60, Math.max(0, minutes));
        seconds = Math.min(59, Math.max(0, seconds));
        sliderAlarmMinutes.setValue(minutes);
        sliderAlarmSeconds.setValue(seconds);
        updateDurationDisplay();
        updateIndefiniteWarning();
    }

    private void setupFieldListeners() {
        // Cancel button
        cancelButton.setOnClickListener(v -> dismiss());

        // Save button
        saveButton.setOnClickListener(v -> handleSave());

        // Text watchers for validation
        labelEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        phoneEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });
    }

    private void setupContactPickers() {
        // Phone contact picker
        phoneInputLayout.setEndIconOnClickListener(v -> {
            if (hasContactsPermission()) {
                launchPhoneContactPicker();
            } else {
                pendingPhoneContactPick = true;
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void launchPhoneContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            phoneContactPickerLauncher.launch(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Error launching phone contact picker", e);
        }
    }

    private void setupSoundPicker() {
        soundInputLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            FeedbackActionType type = getSelectedFeedbackType();

            if (type == FeedbackActionType.TRIGGER_ALARM) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_alarm_sound));
            } else {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_notification_sound));
            }

            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

            if (selectedSoundUri != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedSoundUri));
            }

            try {
                soundPickerLauncher.launch(intent);
            } catch (Exception e) {
                Logger.e(TAG, "Error launching sound picker", e);
            }
        });

        // Make sound edit text not editable but clickable
        soundEditText.setFocusable(false);
        soundEditText.setClickable(true);
        soundEditText.setOnClickListener(v -> pickSound());
    }

    private void pickSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        FeedbackActionType type = getSelectedFeedbackType();

        if (type == FeedbackActionType.TRIGGER_ALARM) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_alarm_sound));
        } else {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_notification_sound));
        }

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        if (selectedSoundUri != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedSoundUri));
        }

        try {
            soundPickerLauncher.launch(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Error launching sound picker", e);
        }
    }

    private void handlePhoneContactResult(Intent data) {
        Uri contactUri = data.getData();
        if (contactUri == null) return;

        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor cursor = requireContext().getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (phoneIndex >= 0) {
                    String phone = cursor.getString(phoneIndex);
                    phoneEditText.setText(phone);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error reading phone contact", e);
        }
    }

    private void handleSoundPickerResult(Intent data) {
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri != null) {
            selectedSoundUri = uri.toString();
            String title = RingtoneManager.getRingtone(requireContext(), uri).getTitle(requireContext());
            soundEditText.setText(getString(R.string.sound_selected, title));
        } else {
            selectedSoundUri = null;
            soundEditText.setText(getString(R.string.default_sound));
        }
        validateInput();
    }

    private void populateFields() {
        if (existingAction != null) {
            // Set type
            spinnerFeedbackType.setSelection(getPositionFromType(existingAction.getType()));

            // Set label
            labelEditText.setText(existingAction.getLabel());

            // Set type-specific fields
            switch (existingAction.getType()) {
                case SEND_SMS:
                    phoneEditText.setText(existingAction.getPhoneNumber());
                    break;

                case TRIGGER_ALARM:
                case PLAY_SOUND:
                    selectedSoundUri = existingAction.getSoundUri();
                    if (selectedSoundUri != null && !selectedSoundUri.isEmpty()) {
                        try {
                            Uri uri = Uri.parse(selectedSoundUri);
                            String title = RingtoneManager.getRingtone(requireContext(), uri).getTitle(requireContext());
                            soundEditText.setText(getString(R.string.sound_selected, title));
                        } catch (Exception e) {
                            soundEditText.setText(getString(R.string.default_sound));
                        }
                    } else {
                        soundEditText.setText(getString(R.string.default_sound));
                    }
                    // Initialize duration sliders from action
                    setDurationSlidersFromSeconds(existingAction.getDurationSeconds());
                    break;

                case CAMERA_FLASH:
                    sliderFlashDuration.setValue(sliderValueFromSpeedMs(existingAction.getFlashSpeedMs()));
                    updateFlashSpeedText(existingAction.getFlashSpeedMs());
                    // Initialize duration sliders from action
                    setDurationSlidersFromSeconds(existingAction.getDurationSeconds());
                    break;

                case VIBRATE:
                    int vibrationIndex = getVibrationPatternIndex(existingAction.getVibrationPattern());
                    spinnerVibrationPattern.setSelection(vibrationIndex);
                    // Initialize duration sliders from action
                    setDurationSlidersFromSeconds(existingAction.getDurationSeconds());
                    break;

                default:
                    break;
            }
        }

        validateInput();
    }

    private void updateFieldsVisibility(@NonNull FeedbackActionType type) {
        // Hide all type-specific fields first
        phoneInputLayout.setVisibility(View.GONE);
        soundInputLayout.setVisibility(View.GONE);
        vibrationSection.setVisibility(View.GONE);
        flashSection.setVisibility(View.GONE);
        durationSection.setVisibility(View.GONE);

        // Show relevant fields based on type
        switch (type) {
            case SEND_SMS:
                phoneInputLayout.setVisibility(View.VISIBLE);
                break;

            case TRIGGER_ALARM:
                soundInputLayout.setVisibility(View.VISIBLE);
                durationSection.setVisibility(View.VISIBLE);
                updateIndefiniteWarning();
                break;

            case PLAY_SOUND:
                soundInputLayout.setVisibility(View.VISIBLE);
                durationSection.setVisibility(View.VISIBLE);
                updateIndefiniteWarning();
                break;

            case CAMERA_FLASH:
                flashSection.setVisibility(View.VISIBLE);
                durationSection.setVisibility(View.VISIBLE);
                updateIndefiniteWarning();
                break;

            case VIBRATE:
                vibrationSection.setVisibility(View.VISIBLE);
                durationSection.setVisibility(View.VISIBLE);
                updateIndefiniteWarning();
                break;

            case NOTIFICATION:
            default:
                // No additional fields for notification
                break;
        }

        validateInput();
    }

    private boolean validateInput() {
        boolean isValid = true;

        // Label is always required
        String label = getTextValue(labelEditText);
        if (TextUtils.isEmpty(label)) {
            isValid = false;
        }

        // Type-specific validation
        FeedbackActionType type = getSelectedFeedbackType();
        switch (type) {
            case SEND_SMS:
                String phone = getTextValue(phoneEditText);
                if (TextUtils.isEmpty(phone)) {
                    isValid = false;
                }
                break;

            case TRIGGER_ALARM:
            case PLAY_SOUND:
            case CAMERA_FLASH:
            case VIBRATE:
            case NOTIFICATION:
            default:
                // These types don't require additional validation
                break;
        }

        saveButton.setEnabled(isValid);
        return isValid;
    }

    private FeedbackAction buildAction() {
        String label = getTextValue(labelEditText);
        FeedbackActionType type = getSelectedFeedbackType();

        FeedbackAction action;
        if (isEditMode && existingAction != null) {
            // Update existing action
            action = existingAction;
            action.setLabel(label);
            action.setType(type);
        } else {
            // Create new action
            action = new FeedbackAction();
            action.setLabel(label);
            action.setType(type);
        }

        // Set type-specific fields
        switch (type) {
            case SEND_SMS:
                action.setPhoneNumber(getTextValue(phoneEditText));
                action.setSoundUri(null);
                break;

            case TRIGGER_ALARM:
            case PLAY_SOUND:
                action.setSoundUri(selectedSoundUri);
                action.setPhoneNumber(null);
                // Save duration from sliders
                action.setDurationSeconds(getTotalDurationSeconds());
                break;

            case CAMERA_FLASH:
                int speedMs = speedMsFromSliderValue((int) sliderFlashDuration.getValue());
                action.setFlashSpeedMs(speedMs);
                action.setPhoneNumber(null);
                action.setSoundUri(null);
                // Save duration from sliders
                action.setDurationSeconds(getTotalDurationSeconds());
                break;

            case VIBRATE:
                long[] pattern = getSelectedVibrationPattern();
                action.setVibrationPattern(pattern);
                action.setPhoneNumber(null);
                action.setSoundUri(null);
                // Save duration from sliders
                action.setDurationSeconds(getTotalDurationSeconds());
                break;

            case NOTIFICATION:
            default:
                action.setPhoneNumber(null);
                action.setSoundUri(null);
                break;
        }

        return action;
    }

    /**
     * Handles the save button click. Checks for SMS permission if needed.
     */
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        FeedbackActionType type = getSelectedFeedbackType();

        // Check SMS permission for SMS actions
        if (type == FeedbackActionType.SEND_SMS && !hasSmsPermission()) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            return;
        }

        performSave();
    }

    /**
     * Actually performs the save after permission checks pass.
     */
    private void performSave() {
        if (!validateInput()) {
            return;
        }

        FeedbackAction action = buildAction();

        // Return result via Fragment Result API
        Bundle result = new Bundle();
        try {
            result.putString(RESULT_ACTION_JSON, action.toJson().toString());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            Logger.d(TAG, "Saved feedback action: " + action.getLabel() + " (" + action.getType() + ")");
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing action for result", e);
        }

        dismiss();
    }

    @NonNull
    private FeedbackActionType getSelectedFeedbackType() {
        return getTypeFromPosition(spinnerFeedbackType.getSelectedItemPosition());
    }

    @NonNull
    private FeedbackActionType getTypeFromPosition(int position) {
        switch (position) {
            case 1:
                return FeedbackActionType.SEND_SMS;
            case 2:
                return FeedbackActionType.TRIGGER_ALARM;
            case 3:
                return FeedbackActionType.PLAY_SOUND;
            case 4:
                return FeedbackActionType.CAMERA_FLASH;
            case 5:
                return FeedbackActionType.VIBRATE;
            default:
                return FeedbackActionType.NOTIFICATION;
        }
    }

    private int getPositionFromType(@NonNull FeedbackActionType type) {
        switch (type) {
            case SEND_SMS:
                return 1;
            case TRIGGER_ALARM:
                return 2;
            case PLAY_SOUND:
                return 3;
            case CAMERA_FLASH:
                return 4;
            case VIBRATE:
                return 5;
            default:
                return 0;
        }
    }

    @NonNull
    private long[] getSelectedVibrationPattern() {
        int position = spinnerVibrationPattern.getSelectedItemPosition();
        switch (position) {
            case 1:
                return VIBRATION_LONG.clone();
            case 2:
                return VIBRATION_DOUBLE.clone();
            case 3:
                return VIBRATION_SOS.clone();
            default:
                return VIBRATION_SHORT.clone();
        }
    }

    private int getVibrationPatternIndex(@Nullable long[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return 0;
        }
        if (java.util.Arrays.equals(pattern, VIBRATION_LONG)) {
            return 1;
        } else if (java.util.Arrays.equals(pattern, VIBRATION_DOUBLE)) {
            return 2;
        } else if (java.util.Arrays.equals(pattern, VIBRATION_SOS)) {
            return 3;
        }
        return 0;
    }

    @NonNull
    private String getTextValue(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    /**
     * Simple TextWatcher implementation that only requires afterTextChanged.
     */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Not needed
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Not needed
        }
    }
}
