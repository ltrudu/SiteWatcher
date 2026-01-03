package com.ltrudu.sitewatcher.ui.addedit;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;

import java.util.UUID;

/**
 * DialogFragment for adding a sleep (wait) action.
 * Provides a simple form with label and duration inputs.
 */
public class AddSleepDialogFragment extends DialogFragment {

    private static final String TAG = "AddSleepDialog";

    public static final String RESULT_KEY = "sleepActionResult";
    public static final String RESULT_ACTION_JSON = "action_json";

    // Views
    private TextInputLayout labelInputLayout;
    private TextInputEditText labelEditText;
    private TextInputLayout durationInputLayout;
    private TextInputEditText durationEditText;
    private MaterialButton cancelButton;
    private MaterialButton addButton;

    /**
     * Create a new instance of AddSleepDialogFragment.
     *
     * @return New dialog fragment instance
     */
    public static AddSleepDialogFragment newInstance() {
        return new AddSleepDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_add_sleep, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupListeners();
        validateInput();
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
        labelInputLayout = view.findViewById(R.id.labelInputLayout);
        labelEditText = view.findViewById(R.id.editLabel);
        durationInputLayout = view.findViewById(R.id.durationInputLayout);
        durationEditText = view.findViewById(R.id.editDuration);
        cancelButton = view.findViewById(R.id.buttonCancel);
        addButton = view.findViewById(R.id.buttonAdd);

        // Pre-fill label with "Sleep"
        labelEditText.setText(R.string.action_sleep);
    }

    private void setupListeners() {
        // Cancel button
        cancelButton.setOnClickListener(v -> dismiss());

        // Add button
        addButton.setOnClickListener(v -> addAction());

        // Text watchers for validation
        TextWatcher validationWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        };

        labelEditText.addTextChangedListener(validationWatcher);
        durationEditText.addTextChangedListener(validationWatcher);
    }

    private void validateInput() {
        boolean isValid = true;

        // Label is required and must not be empty
        String label = getTextValue(labelEditText);
        if (TextUtils.isEmpty(label)) {
            isValid = false;
        }

        // Duration must be >= 1
        String durationStr = getTextValue(durationEditText);
        if (TextUtils.isEmpty(durationStr)) {
            isValid = false;
        } else {
            try {
                int duration = Integer.parseInt(durationStr);
                if (duration < 1) {
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                isValid = false;
            }
        }

        addButton.setEnabled(isValid);
    }

    private void addAction() {
        String label = getTextValue(labelEditText);
        int duration = parseDuration();

        // Create the wait action
        AutoClickAction action = AutoClickAction.createWaitAction(
                UUID.randomUUID().toString(),
                label,
                duration,
                true,  // enabled
                0      // order will be set by parent
        );

        // Return result via Fragment Result API
        Bundle result = new Bundle();
        try {
            result.putString(RESULT_ACTION_JSON, action.toJson().toString());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            Logger.d(TAG, "Created sleep action: " + label + " (" + duration + "s)");
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing action for result", e);
        }

        dismiss();
    }

    @NonNull
    private String getTextValue(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private int parseDuration() {
        String value = getTextValue(durationEditText);
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 1;
        }
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
