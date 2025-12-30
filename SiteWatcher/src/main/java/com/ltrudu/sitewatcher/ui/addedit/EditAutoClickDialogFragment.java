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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.ActionType;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * DialogFragment for adding or editing auto-click actions.
 * Supports both CLICK (click element by CSS selector) and WAIT (pause for duration) actions.
 */
public class EditAutoClickDialogFragment extends DialogFragment {

    private static final String TAG = "EditAutoClickDialog";
    private static final String ARG_ACTION_JSON = "action_json";
    private static final String ARG_SITE_URL = "site_url";

    public static final String RESULT_KEY = "edit_auto_click_result";
    public static final String RESULT_ACTION_JSON = "action_json";

    // Fragment result key for selector picker
    private static final String SELECTOR_RESULT_KEY = "autoClickSelectorResult";
    private static final String SELECTOR_KEY = "selector";

    // Views
    private Spinner actionTypeSpinner;
    private TextInputLayout labelInputLayout;
    private TextInputEditText labelEditText;
    private TextInputLayout selectorInputLayout;
    private TextInputEditText selectorEditText;
    private TextInputLayout waitInputLayout;
    private TextInputEditText waitSecondsEditText;
    private MaterialButton cancelButton;
    private MaterialButton saveButton;

    // Data
    private AutoClickAction existingAction;
    private String siteUrl;
    private boolean isEditMode = false;

    /**
     * Create a new instance for adding a new action.
     *
     * @return New dialog fragment instance
     */
    public static EditAutoClickDialogFragment newInstance() {
        return new EditAutoClickDialogFragment();
    }

    /**
     * Create a new instance for adding a new action with site URL for element picker.
     *
     * @param siteUrl The URL of the site for element picking
     * @return New dialog fragment instance
     */
    public static EditAutoClickDialogFragment newInstance(@Nullable String siteUrl) {
        EditAutoClickDialogFragment fragment = new EditAutoClickDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SITE_URL, siteUrl);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Create a new instance for editing an existing action.
     *
     * @param action  The action to edit
     * @param siteUrl The URL of the site for element picking
     * @return New dialog fragment instance
     */
    public static EditAutoClickDialogFragment newInstance(@NonNull AutoClickAction action, @Nullable String siteUrl) {
        EditAutoClickDialogFragment fragment = new EditAutoClickDialogFragment();
        Bundle args = new Bundle();
        try {
            args.putString(ARG_ACTION_JSON, action.toJson().toString());
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing action", e);
        }
        args.putString(ARG_SITE_URL, siteUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments
        if (getArguments() != null) {
            String actionJson = getArguments().getString(ARG_ACTION_JSON);
            siteUrl = getArguments().getString(ARG_SITE_URL);

            if (actionJson != null && !actionJson.isEmpty()) {
                try {
                    existingAction = AutoClickAction.fromJson(new JSONObject(actionJson));
                    isEditMode = true;
                } catch (JSONException e) {
                    Logger.e(TAG, "Error parsing action JSON", e);
                }
            }
        }

        // Set dialog style
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_auto_click, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupSpinner();
        setupListeners();
        populateFields();
        listenForSelectorResult();
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
        actionTypeSpinner = view.findViewById(R.id.spinnerActionType);
        labelInputLayout = view.findViewById(R.id.labelInputLayout);
        labelEditText = view.findViewById(R.id.editLabel);
        selectorInputLayout = view.findViewById(R.id.selectorInputLayout);
        selectorEditText = view.findViewById(R.id.editSelector);
        waitInputLayout = view.findViewById(R.id.waitInputLayout);
        waitSecondsEditText = view.findViewById(R.id.editWaitSeconds);
        cancelButton = view.findViewById(R.id.buttonCancel);
        saveButton = view.findViewById(R.id.buttonSave);
    }

    private void setupSpinner() {
        String[] actionTypes = new String[]{
                getString(R.string.action_type_click),
                getString(R.string.action_type_wait),
                getString(R.string.action_type_tap_coordinates)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                actionTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionTypeSpinner.setAdapter(adapter);

        actionTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ActionType type;
                switch (position) {
                    case 1:
                        type = ActionType.WAIT;
                        break;
                    case 2:
                        type = ActionType.TAP_COORDINATES;
                        break;
                    default:
                        type = ActionType.CLICK;
                        break;
                }
                updateFieldsVisibility(type);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Default to CLICK
                updateFieldsVisibility(ActionType.CLICK);
            }
        });
    }

    private void setupListeners() {
        // Cancel button
        cancelButton.setOnClickListener(v -> dismiss());

        // Save button
        saveButton.setOnClickListener(v -> saveAction());

        // Selector input end icon click (opens selector browser)
        selectorInputLayout.setEndIconOnClickListener(v -> navigateToSelectorBrowser());

        // Text watchers for validation
        labelEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        selectorEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        waitSecondsEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });
    }

    private void populateFields() {
        if (existingAction != null) {
            // Set action type
            int typePosition;
            switch (existingAction.getType()) {
                case WAIT:
                    typePosition = 1;
                    break;
                case TAP_COORDINATES:
                    typePosition = 2;
                    break;
                default:
                    typePosition = 0;
                    break;
            }
            actionTypeSpinner.setSelection(typePosition);

            // Set label
            labelEditText.setText(existingAction.getLabel());

            // Set type-specific fields
            if (existingAction.getType() == ActionType.CLICK) {
                selectorEditText.setText(existingAction.getSelector());
            } else if (existingAction.getType() == ActionType.WAIT) {
                waitSecondsEditText.setText(String.valueOf(existingAction.getWaitSeconds()));
            }
            // TAP_COORDINATES: coordinates are read-only, just show them in the summary
        }

        validateInput();
    }

    private void updateFieldsVisibility(@NonNull ActionType type) {
        if (type == ActionType.CLICK) {
            selectorInputLayout.setVisibility(View.VISIBLE);
            waitInputLayout.setVisibility(View.GONE);
        } else if (type == ActionType.WAIT) {
            selectorInputLayout.setVisibility(View.GONE);
            waitInputLayout.setVisibility(View.VISIBLE);
        } else {
            // TAP_COORDINATES - hide both, coordinates are set via picker
            selectorInputLayout.setVisibility(View.GONE);
            waitInputLayout.setVisibility(View.GONE);
        }
        validateInput();
    }

    private void validateInput() {
        boolean isValid = true;

        // Label is always required
        String label = getTextValue(labelEditText);
        if (TextUtils.isEmpty(label)) {
            isValid = false;
        }

        // Type-specific validation
        ActionType type = getSelectedActionType();
        if (type == ActionType.CLICK) {
            String selector = getTextValue(selectorEditText);
            if (TextUtils.isEmpty(selector)) {
                isValid = false;
            }
        } else if (type == ActionType.WAIT) {
            String waitSecondsStr = getTextValue(waitSecondsEditText);
            if (TextUtils.isEmpty(waitSecondsStr)) {
                isValid = false;
            } else {
                try {
                    int waitSeconds = Integer.parseInt(waitSecondsStr);
                    if (waitSeconds < 1) {
                        isValid = false;
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                }
            }
        } else if (type == ActionType.TAP_COORDINATES) {
            // TAP_COORDINATES: must be editing existing action with valid coordinates
            if (existingAction == null || existingAction.getType() != ActionType.TAP_COORDINATES) {
                // Can't create new TAP_COORDINATES from this dialog - use CoordinatePicker
                isValid = false;
            }
        }

        saveButton.setEnabled(isValid);
    }

    private void saveAction() {
        String label = getTextValue(labelEditText);
        ActionType type = getSelectedActionType();

        AutoClickAction action;
        if (isEditMode && existingAction != null) {
            // Update existing action
            action = existingAction;
            action.setLabel(label);
            action.setType(type);

            if (type == ActionType.CLICK) {
                action.setSelector(getTextValue(selectorEditText));
                action.setWaitSeconds(0);
            } else if (type == ActionType.WAIT) {
                action.setSelector(null);
                int waitSeconds = parseWaitSeconds();
                action.setWaitSeconds(waitSeconds);
            }
            // TAP_COORDINATES: keep existing coordinates, just update label
        } else {
            // Create new action
            if (type == ActionType.CLICK) {
                action = AutoClickAction.createClickAction(
                        java.util.UUID.randomUUID().toString(),
                        label,
                        getTextValue(selectorEditText),
                        true,  // enabled
                        false, // not built-in
                        0      // order will be set by parent
                );
            } else if (type == ActionType.WAIT) {
                action = AutoClickAction.createWaitAction(
                        java.util.UUID.randomUUID().toString(),
                        label,
                        parseWaitSeconds(),
                        true,  // enabled
                        0      // order will be set by parent
                );
            } else {
                // TAP_COORDINATES should not be created from this dialog
                // Return without saving
                dismiss();
                return;
            }
        }

        // Return result via Fragment Result API
        Bundle result = new Bundle();
        try {
            result.putString(RESULT_ACTION_JSON, action.toJson().toString());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            Logger.d(TAG, "Saved action: " + action.getLabel() + " (" + action.getType() + ")");
        } catch (JSONException e) {
            Logger.e(TAG, "Error serializing action for result", e);
        }

        dismiss();
    }

    private void navigateToSelectorBrowser() {
        if (TextUtils.isEmpty(siteUrl)) {
            // Show error - URL required
            selectorInputLayout.setError(getString(R.string.url_required_for_selector));
            return;
        }

        // Clear any previous error
        selectorInputLayout.setError(null);

        // Navigate to selector browser
        try {
            Bundle args = new Bundle();
            args.putString("url", siteUrl);

            // Dismiss dialog first, then navigate
            dismiss();

            // Navigate from the parent fragment's view
            if (getParentFragment() != null && getParentFragment().getView() != null) {
                NavController navController = Navigation.findNavController(getParentFragment().getView());

                // Try to determine which action to use based on parent fragment
                if (getParentFragment() instanceof EditActionsFragment) {
                    navController.navigate(R.id.action_editActions_to_selectorBrowser, args);
                } else {
                    navController.navigate(R.id.action_addEdit_to_selectorBrowser, args);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to selector browser", e);
        }
    }

    private void listenForSelectorResult() {
        // Listen for result from SelectorBrowserFragment
        getParentFragmentManager().setFragmentResultListener(
                SELECTOR_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    if (selector != null && !selector.isEmpty()) {
                        selectorEditText.setText(selector);
                        validateInput();
                    }
                }
        );
    }

    @NonNull
    private ActionType getSelectedActionType() {
        switch (actionTypeSpinner.getSelectedItemPosition()) {
            case 1:
                return ActionType.WAIT;
            case 2:
                return ActionType.TAP_COORDINATES;
            default:
                return ActionType.CLICK;
        }
    }

    @NonNull
    private String getTextValue(@Nullable TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private int parseWaitSeconds() {
        String value = getTextValue(waitSecondsEditText);
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
