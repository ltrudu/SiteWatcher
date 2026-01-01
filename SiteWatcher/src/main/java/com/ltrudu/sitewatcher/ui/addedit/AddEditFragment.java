package com.ltrudu.sitewatcher.ui.addedit;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.data.model.ComparisonMode;
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
    private static final String URL_KEY = "url";
    private static final String SELECTOR_KEY = "selector";

    private AddEditViewModel viewModel;

    // Views
    private TextInputLayout urlInputLayout;
    private TextInputEditText urlEditText;
    private Spinner fetchModeSpinner;
    private android.widget.TextView fetchModeHint;
    private Spinner comparisonModeSpinner;
    private TextInputLayout cssSelectorInputLayout;
    private TextInputEditText cssSelectorEditText;
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

    // Auto-click views
    private View autoClickSection;
    private android.widget.TextView actionsCountLabel;
    private MaterialButton buttonEditActions;

    // Calendar schedules views
    private MaterialCardView schedulesSection;
    private android.widget.TextView schedulesCountLabel;
    private MaterialButton buttonEditSchedules;

    // Adapters
    private ArrayAdapter<String> fetchModeAdapter;
    private ArrayAdapter<String> comparisonModeAdapter;

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
        setupListeners();
        observeViewModel();

        // Check for result from BrowserDiscoveryFragment
        checkForDiscoveredUrl();

        // Check for result from SelectorBrowserFragment
        checkForSelectedSelector();

        // Listen for result from EditActionsFragment
        listenForEditActionsResult();

        // Listen for result from EditSchedulesFragment
        listenForEditSchedulesResult();

        isInitializing = false;
    }

    private void initializeViews(View view) {
        urlInputLayout = view.findViewById(R.id.urlInputLayout);
        urlEditText = view.findViewById(R.id.urlEditText);
        fetchModeSpinner = view.findViewById(R.id.fetchModeSpinner);
        fetchModeHint = view.findViewById(R.id.fetchModeHint);
        comparisonModeSpinner = view.findViewById(R.id.comparisonModeSpinner);
        cssSelectorInputLayout = view.findViewById(R.id.cssSelectorInputLayout);
        cssSelectorEditText = view.findViewById(R.id.cssSelectorEditText);
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

        // Auto-click views
        autoClickSection = view.findViewById(R.id.autoClickSection);
        actionsCountLabel = view.findViewById(R.id.actionsCountLabel);
        buttonEditActions = view.findViewById(R.id.buttonEditActions);

        // Calendar schedules views
        schedulesSection = view.findViewById(R.id.schedulesSection);
        schedulesCountLabel = view.findViewById(R.id.schedulesCountLabel);
        buttonEditSchedules = view.findViewById(R.id.buttonEditSchedules);

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

        // CSS Selector picker button (touch icon)
        cssSelectorInputLayout.setEndIconOnClickListener(v -> navigateToSelectorBrowser());

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

        // Observe CSS selector
        viewModel.getCssSelector().observe(getViewLifecycleOwner(), selector -> {
            if (selector != null && !selector.equals(cssSelectorEditText.getText().toString())) {
                cssSelectorEditText.setText(selector);
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

        // Observe auto-click actions
        viewModel.getAutoClickActions().observe(getViewLifecycleOwner(), actions -> {
            updateActionsCountLabel(actions);
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

    private void checkForSelectedSelector() {
        // Listen for result from SelectorBrowserFragment
        getParentFragmentManager().setFragmentResultListener(
                SELECTOR_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    if (selector != null && !selector.isEmpty()) {
                        viewModel.setCssSelector(selector);
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

    private void updateCssSelectorVisibility(ComparisonMode mode) {
        cssSelectorInputLayout.setVisibility(
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

    private void navigateToBrowserDiscovery() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_browserDiscovery);
    }

    private void navigateToSelectorBrowser() {
        String url = viewModel.getUrl().getValue();
        if (url == null || url.isEmpty()) {
            urlInputLayout.setError(getString(R.string.url_required_for_selector));
            return;
        }

        // Validate URL first
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Bundle args = new Bundle();
        args.putString("url", url);
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_addEdit_to_selectorBrowser, args);
    }

    private void navigateBack() {
        NavController navController = Navigation.findNavController(requireView());
        navController.popBackStack();
    }
}
