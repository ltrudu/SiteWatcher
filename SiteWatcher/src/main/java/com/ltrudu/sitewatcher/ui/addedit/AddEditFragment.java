package com.ltrudu.sitewatcher.ui.addedit;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ltrudu.sitewatcher.R;
import com.google.android.material.card.MaterialCardView;
import com.ltrudu.sitewatcher.background.CheckScheduler;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
import com.ltrudu.sitewatcher.data.model.DiffAlgorithmType;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FeedbackPlayMode;
import com.ltrudu.sitewatcher.data.model.FetchMode;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.data.model.WatchedSite;

import java.util.List;

/**
 * Fragment for adding or editing a watched site.
 * Receives siteId argument: -1 for add mode, otherwise edit mode.
 */
public class AddEditFragment extends Fragment {

    private static final String ARG_SITE_ID = "siteId";
    private static final String BROWSER_RESULT_KEY = "browserResult";
    private static final String SELECTOR_RESULT_KEY = "selectorResult";
    private static final String INCLUDE_SELECTOR_RESULT_KEY = "includeSelectorResult";
    private static final String EXCLUDE_SELECTOR_RESULT_KEY = "excludeSelectorResult";
    private static final String URL_KEY = "url";
    private static final String SELECTOR_KEY = "selector";

    private AddEditViewModel viewModel;

    // Views
    private TextInputLayout urlInputLayout;
    private TextInputEditText urlEditText;
    private Spinner fetchModeSpinner;
    private android.widget.TextView fetchModeHint;
    private Spinner comparisonModeSpinner;
    private android.widget.TextView comparisonModeHint;
    private LinearLayout cssSelectorSection;
    private TextInputLayout cssIncludeInputLayout;
    private TextInputEditText cssIncludeEditText;
    private TextInputLayout cssExcludeInputLayout;
    private TextInputEditText cssExcludeEditText;
    private Slider thresholdSlider;
    private MaterialButton cancelButton;
    private MaterialButton saveButton;
    private android.widget.TextView thresholdValueLabel;
    private View minTextLengthSection;
    private Slider minTextLengthSlider;
    private android.widget.TextView minTextLengthValueLabel;
    private View minWordLengthSection;
    private Slider minWordLengthSlider;
    private android.widget.TextView minWordLengthValueLabel;
    private Spinner diffAlgorithmSpinner;
    private android.widget.TextView diffAlgorithmHint;

    // Auto-click views
    private View autoClickSection;
    private android.widget.TextView actionsCountLabel;
    private MaterialButton buttonEditActions;

    // Calendar schedules views
    private MaterialCardView schedulesSection;
    private android.widget.TextView schedulesCountLabel;
    private MaterialButton buttonEditSchedules;

    // Feedback actions views
    private MaterialCardView feedbackSection;
    private android.widget.TextView feedbackCountLabel;
    private MaterialButton buttonEditFeedback;
    private Spinner spinnerFeedbackPlayMode;
    private android.widget.TextView feedbackPlayModeHint;

    // Adapters
    private ArrayAdapter<String> fetchModeAdapter;
    private ArrayAdapter<String> comparisonModeAdapter;
    private ArrayAdapter<String> diffAlgorithmAdapter;
    private ArrayAdapter<CharSequence> feedbackPlayModeAdapter;

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
        setupAutoClickSection();
        setupSchedulesSection();
        setupFeedbackSection();
        setupListeners();
        observeViewModel();

        // Check for result from BrowserDiscoveryFragment
        checkForDiscoveredUrl();

        // Check for result from SelectorBrowserFragment (legacy)
        checkForSelectedSelector();

        // Check for results from include/exclude selector pickers
        checkForIncludeSelector();
        checkForExcludeSelector();

        // Listen for result from EditActionsFragment
        listenForEditActionsResult();

        // Listen for result from EditSchedulesFragment
        listenForEditSchedulesResult();

        // Listen for result from EditFeedbackFragment
        listenForEditFeedbackResult();

        isInitializing = false;
    }

    private void initializeViews(View view) {
        urlInputLayout = view.findViewById(R.id.urlInputLayout);
        urlEditText = view.findViewById(R.id.urlEditText);
        fetchModeSpinner = view.findViewById(R.id.fetchModeSpinner);
        fetchModeHint = view.findViewById(R.id.fetchModeHint);
        comparisonModeSpinner = view.findViewById(R.id.comparisonModeSpinner);
        comparisonModeHint = view.findViewById(R.id.comparisonModeHint);
        cssSelectorSection = view.findViewById(R.id.cssSelectorSection);
        cssIncludeInputLayout = view.findViewById(R.id.cssIncludeInputLayout);
        cssIncludeEditText = view.findViewById(R.id.cssIncludeEditText);
        cssExcludeInputLayout = view.findViewById(R.id.cssExcludeInputLayout);
        cssExcludeEditText = view.findViewById(R.id.cssExcludeEditText);
        thresholdSlider = view.findViewById(R.id.thresholdSlider);
        cancelButton = view.findViewById(R.id.cancelButton);
        saveButton = view.findViewById(R.id.saveButton);
        thresholdValueLabel = view.findViewById(R.id.thresholdValueLabel);
        minTextLengthSection = view.findViewById(R.id.minTextLengthSection);
        minTextLengthSlider = view.findViewById(R.id.minTextLengthSlider);
        minTextLengthValueLabel = view.findViewById(R.id.minTextLengthValueLabel);
        minWordLengthSection = view.findViewById(R.id.minWordLengthSection);
        minWordLengthSlider = view.findViewById(R.id.minWordLengthSlider);
        minWordLengthValueLabel = view.findViewById(R.id.minWordLengthValueLabel);
        diffAlgorithmSpinner = view.findViewById(R.id.diffAlgorithmSpinner);
        diffAlgorithmHint = view.findViewById(R.id.diffAlgorithmHint);

        // Auto-click views
        autoClickSection = view.findViewById(R.id.autoClickSection);
        actionsCountLabel = view.findViewById(R.id.actionsCountLabel);
        buttonEditActions = view.findViewById(R.id.buttonEditActions);

        // Calendar schedules views
        schedulesSection = view.findViewById(R.id.schedulesSection);
        schedulesCountLabel = view.findViewById(R.id.schedulesCountLabel);
        buttonEditSchedules = view.findViewById(R.id.buttonEditSchedules);

        // Feedback actions views
        feedbackSection = view.findViewById(R.id.feedbackSection);
        feedbackCountLabel = view.findViewById(R.id.feedbackCountLabel);
        buttonEditFeedback = view.findViewById(R.id.buttonEditFeedback);
        spinnerFeedbackPlayMode = view.findViewById(R.id.spinnerFeedbackPlayMode);
        feedbackPlayModeHint = view.findViewById(R.id.feedbackPlayModeHint);

        // Set button text and title based on mode
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (viewModel.isEditMode()) {
            saveButton.setText(R.string.save);
            if (actionBar != null) {
                actionBar.setTitle(R.string.edit_site);
            }
        } else {
            saveButton.setText(R.string.add);
            if (actionBar != null) {
                actionBar.setTitle(R.string.add_site);
            }
        }
    }

    private void setupSpinners() {
        // Fetch mode spinner
        String[] fetchModes = new String[]{
                getString(R.string.fetch_mode_static),
                getString(R.string.fetch_mode_javascript)
        };
        fetchModeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                fetchModes
        );
        fetchModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fetchModeSpinner.setAdapter(fetchModeAdapter);

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

        // Diff algorithm spinner
        String[] diffAlgorithms = new String[]{
                getString(R.string.diff_algorithm_line),
                getString(R.string.diff_algorithm_word),
                getString(R.string.diff_algorithm_character)
        };
        diffAlgorithmAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                diffAlgorithms
        );
        diffAlgorithmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        diffAlgorithmSpinner.setAdapter(diffAlgorithmAdapter);

        // Feedback play mode spinner
        feedbackPlayModeAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.feedback_play_modes,
                android.R.layout.simple_spinner_item
        );
        feedbackPlayModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFeedbackPlayMode.setAdapter(feedbackPlayModeAdapter);
    }

    private void setupAutoClickSection() {
        buttonEditActions.setOnClickListener(v -> navigateToEditActions());
    }

    private void navigateToEditActions() {
        String url = viewModel.getUrl().getValue();
        if (url == null || url.isEmpty()) {
            urlInputLayout.setError(getString(R.string.url_required_for_selector));
            return;
        }

        // Normalize URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        List<AutoClickAction> actions = viewModel.getAutoClickActions().getValue();

        Bundle args = new Bundle();
        args.putString("url", url);
        args.putString("actions_json", AutoClickAction.toJsonString(actions));

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_editActions, args);
    }

    private void listenForEditActionsResult() {
        getParentFragmentManager().setFragmentResultListener(
                "editActionsResult",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String actionsJson = result.getString("actions_json");
                    if (actionsJson != null) {
                        List<AutoClickAction> actions = AutoClickAction.fromJsonString(actionsJson);
                        viewModel.setAutoClickActions(actions);
                    }
                }
        );
    }

    private void updateActionsCountLabel(List<AutoClickAction> actions) {
        int count = actions != null ? actions.size() : 0;
        actionsCountLabel.setText(getResources().getQuantityString(
                R.plurals.actions_registered, count, count));
    }

    private void updateAutoClickSectionVisibility(FetchMode mode) {
        boolean visible = mode == FetchMode.JAVASCRIPT;
        autoClickSection.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setupSchedulesSection() {
        buttonEditSchedules.setOnClickListener(v -> navigateToEditSchedules());
        // Initialize with default schedule count
        updateSchedulesCount(viewModel.getSchedulesJson());
    }

    private void navigateToEditSchedules() {
        String schedulesJson = viewModel.getSchedulesJson();

        Bundle args = new Bundle();
        args.putString("schedules_json", schedulesJson);

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_editSchedules, args);
    }

    private void listenForEditSchedulesResult() {
        getParentFragmentManager().setFragmentResultListener(
                EditSchedulesFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String schedulesJson = result.getString(EditSchedulesFragment.RESULT_SCHEDULES_JSON);
                    if (schedulesJson != null) {
                        viewModel.setSchedulesJson(schedulesJson);
                        updateSchedulesCount(schedulesJson);
                    }
                }
        );
    }

    private void updateSchedulesCount(String schedulesJson) {
        List<Schedule> schedules = Schedule.fromJsonString(schedulesJson);
        int count = schedules.size();
        schedulesCountLabel.setText(getString(R.string.schedules_count, count));
    }

    private void setupFeedbackSection() {
        buttonEditFeedback.setOnClickListener(v -> navigateToEditFeedback());
        // Initialize with current feedback count
        updateFeedbackCount(viewModel.getFeedbackActionsJson());
        // Initialize feedback play mode spinner selection
        updateFeedbackPlayModeSpinner(viewModel.getFeedbackPlayMode());
    }

    private void navigateToEditFeedback() {
        String feedbackJson = viewModel.getFeedbackActionsJson();

        Bundle args = new Bundle();
        args.putString(EditFeedbackFragment.ARG_FEEDBACK_JSON, feedbackJson);

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_editFeedback, args);
    }

    private void listenForEditFeedbackResult() {
        getParentFragmentManager().setFragmentResultListener(
                EditFeedbackFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String feedbackJson = result.getString(EditFeedbackFragment.RESULT_FEEDBACK_JSON);
                    viewModel.setFeedbackActionsJson(feedbackJson);
                    updateFeedbackCount(feedbackJson);
                }
        );
    }

    private void updateFeedbackCount(String feedbackJson) {
        List<FeedbackAction> actions = FeedbackAction.fromJsonString(feedbackJson);
        int count = actions.size();
        if (count == 0) {
            feedbackCountLabel.setText(R.string.feedback_default_notification);
        } else {
            feedbackCountLabel.setText(getResources().getQuantityString(
                    R.plurals.feedback_actions_count, count, count));
        }
    }

    private void updateFeedbackPlayModeSpinner(FeedbackPlayMode mode) {
        if (mode != null) {
            int position = mode.ordinal();
            if (spinnerFeedbackPlayMode.getSelectedItemPosition() != position) {
                spinnerFeedbackPlayMode.setSelection(position);
            }
        }
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

        // Fetch mode spinner
        fetchModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    FetchMode mode = position == 0 ? FetchMode.STATIC : FetchMode.JAVASCRIPT;
                    viewModel.setFetchMode(mode);
                }
                updateFetchModeHint(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Comparison mode spinner
        comparisonModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Always update hint text
                updateComparisonModeHint(position);
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

        // CSS Include Selector input
        cssIncludeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isInitializing) {
                    viewModel.setCssIncludeSelector(s.toString());
                }
            }
        });

        // CSS Include Selector picker button (touch icon)
        cssIncludeInputLayout.setEndIconOnClickListener(v -> navigateToIncludeSelectorBrowser());

        // CSS Exclude Selector input
        cssExcludeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isInitializing) {
                    viewModel.setCssExcludeSelector(s.toString());
                }
            }
        });

        // CSS Exclude Selector picker button (touch icon)
        cssExcludeInputLayout.setEndIconOnClickListener(v -> navigateToExcludeSelectorBrowser());

        // Threshold slider
        thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isInitializing) {
                viewModel.setThresholdPercent((int) value);
            }
            updateThresholdLabel((int) value);
        });

        // Min text length slider
        minTextLengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isInitializing) {
                viewModel.setMinTextLength((int) value);
            }
            updateMinTextLengthLabel((int) value);
        });

        // Min word length slider
        minWordLengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isInitializing) {
                viewModel.setMinWordLength((int) value);
            }
            updateMinWordLengthLabel((int) value);
        });

        // Diff algorithm spinner
        diffAlgorithmSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    DiffAlgorithmType algorithm = getDiffAlgorithmFromPosition(position);
                    viewModel.setDiffAlgorithm(algorithm);
                }
                updateDiffAlgorithmHint(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Feedback play mode spinner
        spinnerFeedbackPlayMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Always update hint text
                updateFeedbackPlayModeHint(position);
                if (!isInitializing) {
                    viewModel.setFeedbackPlayMode(FeedbackPlayMode.values()[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> navigateBack());

        // Save button
        saveButton.setOnClickListener(v -> viewModel.save());
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

        // Observe fetch mode
        viewModel.getFetchMode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                int position = mode == FetchMode.STATIC ? 0 : 1;
                if (fetchModeSpinner.getSelectedItemPosition() != position) {
                    fetchModeSpinner.setSelection(position);
                }
                updateFetchModeHint(position);
                updateAutoClickSectionVisibility(mode);
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

        // Observe CSS include selector
        viewModel.getCssIncludeSelector().observe(getViewLifecycleOwner(), selector -> {
            if (selector != null && !selector.equals(cssIncludeEditText.getText().toString())) {
                cssIncludeEditText.setText(selector);
            }
        });

        // Observe CSS exclude selector
        viewModel.getCssExcludeSelector().observe(getViewLifecycleOwner(), selector -> {
            if (selector != null && !selector.equals(cssExcludeEditText.getText().toString())) {
                cssExcludeEditText.setText(selector);
            }
        });

        // Observe threshold
        viewModel.getThresholdPercent().observe(getViewLifecycleOwner(), threshold -> {
            if (threshold != null) {
                thresholdSlider.setValue(threshold);
                updateThresholdLabel(threshold);
            }
        });

        // Observe min text length
        viewModel.getMinTextLength().observe(getViewLifecycleOwner(), minLength -> {
            if (minLength != null) {
                minTextLengthSlider.setValue(minLength);
                updateMinTextLengthLabel(minLength);
            }
        });

        // Observe min word length
        viewModel.getMinWordLength().observe(getViewLifecycleOwner(), minLength -> {
            if (minLength != null) {
                minWordLengthSlider.setValue(minLength);
                updateMinWordLengthLabel(minLength);
            }
        });

        // Observe diff algorithm
        viewModel.getDiffAlgorithm().observe(getViewLifecycleOwner(), algorithm -> {
            if (algorithm != null) {
                int position = getPositionFromDiffAlgorithm(algorithm);
                if (diffAlgorithmSpinner.getSelectedItemPosition() != position) {
                    diffAlgorithmSpinner.setSelection(position);
                }
                updateDiffAlgorithmHint(position);
            }
        });

        // Observe auto-click actions
        viewModel.getAutoClickActions().observe(getViewLifecycleOwner(), actions -> {
            updateActionsCountLabel(actions);
        });

        // Observe current site (for fields not exposed as LiveData)
        viewModel.getCurrentSite().observe(getViewLifecycleOwner(), site -> {
            if (site != null) {
                // Update feedback play mode spinner when site is loaded
                updateFeedbackPlayModeSpinner(site.getFeedbackPlayMode());
                // Update feedback count when site is loaded
                updateFeedbackCount(site.getFeedbackActionsJson());
                // Update schedules count when site is loaded
                updateSchedulesCount(site.getSchedulesJson());
            }
        });

        // Observe save result
        viewModel.getSaveResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null && result.isSuccess()) {
                // Reschedule the check with updated schedule configuration
                if (result.getSite() != null) {
                    CheckScheduler.getInstance(requireContext()).scheduleCheck(result.getSite());

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

    private void checkForSelectedSelector() {
        // Listen for result from SelectorBrowserFragment (legacy, for backward compatibility)
        getParentFragmentManager().setFragmentResultListener(
                SELECTOR_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    if (selector != null && !selector.isEmpty()) {
                        // Default to include selector for backward compatibility
                        viewModel.setCssIncludeSelector(selector);
                    }
                }
        );
    }

    private void checkForIncludeSelector() {
        // Listen for result from SelectorBrowserFragment for include selector
        getParentFragmentManager().setFragmentResultListener(
                INCLUDE_SELECTOR_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    if (selector != null && !selector.isEmpty()) {
                        viewModel.setCssIncludeSelector(selector);
                    }
                }
        );
    }

    private void checkForExcludeSelector() {
        // Listen for result from SelectorBrowserFragment for exclude selector
        getParentFragmentManager().setFragmentResultListener(
                EXCLUDE_SELECTOR_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    if (selector != null && !selector.isEmpty()) {
                        viewModel.setCssExcludeSelector(selector);
                    }
                }
        );
    }

    private void updateFetchModeHint(int position) {
        if (position == 0) {
            fetchModeHint.setText(R.string.fetch_mode_static_desc);
        } else {
            fetchModeHint.setText(R.string.fetch_mode_javascript_desc);
        }
    }

    private void updateComparisonModeHint(int position) {
        switch (position) {
            case 0: // Full HTML
                comparisonModeHint.setText(R.string.comparison_mode_full_html_desc);
                break;
            case 1: // Text Only
                comparisonModeHint.setText(R.string.comparison_mode_text_only_desc);
                break;
            case 2: // CSS Selector
                comparisonModeHint.setText(R.string.comparison_mode_css_selector_desc);
                break;
            default:
                comparisonModeHint.setText(R.string.comparison_mode_full_html_desc);
                break;
        }
    }

    private void updateFeedbackPlayModeHint(int position) {
        if (position == 0) { // Sequential
            feedbackPlayModeHint.setText(R.string.feedback_play_mode_sequential_desc);
        } else { // All At Once
            feedbackPlayModeHint.setText(R.string.feedback_play_mode_parallel_desc);
        }
    }

    private void updateCssSelectorVisibility(ComparisonMode mode) {
        cssSelectorSection.setVisibility(
                mode == ComparisonMode.CSS_SELECTOR ? View.VISIBLE : View.GONE
        );
        minTextLengthSection.setVisibility(
                mode == ComparisonMode.TEXT_ONLY ? View.VISIBLE : View.GONE
        );
        minWordLengthSection.setVisibility(
                mode == ComparisonMode.TEXT_ONLY ? View.VISIBLE : View.GONE
        );
    }

    private void updateThresholdLabel(int percent) {
        thresholdValueLabel.setText(getString(R.string.threshold_value, percent));
    }

    private void updateMinTextLengthLabel(int length) {
        minTextLengthValueLabel.setText(getString(R.string.min_text_length_value, length));
    }

    private void updateMinWordLengthLabel(int length) {
        minWordLengthValueLabel.setText(getString(R.string.min_word_length_value, length));
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

    private DiffAlgorithmType getDiffAlgorithmFromPosition(int position) {
        switch (position) {
            case 0:
                return DiffAlgorithmType.LINE;
            case 1:
                return DiffAlgorithmType.WORD;
            case 2:
                return DiffAlgorithmType.CHARACTER;
            default:
                return DiffAlgorithmType.LINE;
        }
    }

    private int getPositionFromDiffAlgorithm(DiffAlgorithmType algorithm) {
        switch (algorithm) {
            case LINE:
                return 0;
            case WORD:
                return 1;
            case CHARACTER:
                return 2;
            default:
                return 0;
        }
    }

    private void updateDiffAlgorithmHint(int position) {
        switch (position) {
            case 0:
                diffAlgorithmHint.setText(R.string.diff_algorithm_line_desc);
                break;
            case 1:
                diffAlgorithmHint.setText(R.string.diff_algorithm_word_desc);
                break;
            case 2:
                diffAlgorithmHint.setText(R.string.diff_algorithm_character_desc);
                break;
            default:
                diffAlgorithmHint.setText(R.string.diff_algorithm_line_desc);
                break;
        }
    }

    private void navigateToBrowserDiscovery() {
        NavController navController = Navigation.findNavController(requireView());

        // If URL is set, pass it to the browser to navigate directly
        String url = viewModel.getUrl().getValue();
        if (url != null && !url.isEmpty()) {
            // Normalize URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Bundle args = new Bundle();
            args.putString("initial_url", url);
            navController.navigate(R.id.action_addEdit_to_browserDiscovery, args);
        } else {
            navController.navigate(R.id.action_addEdit_to_browserDiscovery);
        }
    }

    private void navigateToIncludeSelectorBrowser() {
        String url = viewModel.getUrl().getValue();
        if (url == null || url.isEmpty()) {
            urlInputLayout.setError(getString(R.string.url_required_for_selector));
            return;
        }

        // Normalize URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Bundle args = new Bundle();
        args.putString("url", url);
        args.putString("result_key", INCLUDE_SELECTOR_RESULT_KEY);

        // Pass auto-click actions so they execute before element selection
        List<AutoClickAction> actions = viewModel.getAutoClickActions().getValue();
        if (actions != null && !actions.isEmpty()) {
            String actionsJson = AutoClickAction.toJsonString(actions);
            args.putString("actions_json", actionsJson);
        }

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_selectorBrowser, args);
    }

    private void navigateToExcludeSelectorBrowser() {
        String url = viewModel.getUrl().getValue();
        if (url == null || url.isEmpty()) {
            urlInputLayout.setError(getString(R.string.url_required_for_selector));
            return;
        }

        // Normalize URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Bundle args = new Bundle();
        args.putString("url", url);
        args.putString("result_key", EXCLUDE_SELECTOR_RESULT_KEY);

        // Pass auto-click actions so they execute before element selection
        List<AutoClickAction> actions = viewModel.getAutoClickActions().getValue();
        if (actions != null && !actions.isEmpty()) {
            String actionsJson = AutoClickAction.toJsonString(actions);
            args.putString("actions_json", actionsJson);
        }

        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_selectorBrowser, args);
    }

    private void navigateBack() {
        NavController navController = Navigation.findNavController(requireView());
        navController.popBackStack();
    }
}
