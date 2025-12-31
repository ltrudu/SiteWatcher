package com.ltrudu.sitewatcher.ui.addedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.accessibility.AccessibilityHelper;
import com.ltrudu.sitewatcher.data.model.ActionType;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fragment for managing auto-click actions.
 * Provides functionality to add, edit, delete, reorder, and test actions.
 * Returns the updated actions via Fragment Result API.
 */
public class EditActionsFragment extends Fragment {

    private static final String TAG = "EditActionsFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_ACTIONS_JSON = "actions_json";

    public static final String RESULT_KEY = "editActionsResult";
    public static final String RESULT_ACTIONS_JSON = "actions_json";

    // Fragment result keys for child fragments
    private static final String INTERACTIVE_PICKER_RESULT_KEY = "interactivePickerResult";
    private static final String SELECTOR_KEY = "selector";
    private static final String COORDINATE_PICKER_RESULT_KEY = "coordinatePickerResult";
    private static final String COORDINATE_PICKER_EDIT_RESULT_KEY = "coordinatePickerEditResult";

    // Views
    private RecyclerView recyclerActions;
    private LinearLayout emptyState;
    private MaterialButton buttonTestActions;
    private MaterialButton buttonClose;
    private FloatingActionButton fabAddAction;

    // Data
    private String siteUrl;
    private List<AutoClickAction> actions;
    private AutoClickActionAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    public EditActionsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments
        if (getArguments() != null) {
            siteUrl = getArguments().getString(ARG_URL, "");
            String actionsJson = getArguments().getString(ARG_ACTIONS_JSON);
            actions = AutoClickAction.fromJsonString(actionsJson);
        } else {
            siteUrl = "";
            actions = new ArrayList<>();
        }

        Logger.d(TAG, "onCreate: url=" + siteUrl + ", actions=" + actions.size());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupRecyclerView();
        setupFab();
        setupListeners();
        setupFragmentResultListeners();
        updateEmptyState();
        updateTestButtonState();
    }

    private void initializeViews(@NonNull View view) {
        recyclerActions = view.findViewById(R.id.recyclerActions);
        emptyState = view.findViewById(R.id.emptyState);
        buttonTestActions = view.findViewById(R.id.buttonTestActions);
        buttonClose = view.findViewById(R.id.buttonClose);
        fabAddAction = view.findViewById(R.id.fabAddAction);
    }

    private void setupRecyclerView() {
        adapter = new AutoClickActionAdapter();
        adapter.setActions(actions);

        adapter.setOnActionListener(new AutoClickActionAdapter.OnActionListener() {
            @Override
            public void onToggleEnabled(@NonNull AutoClickAction action, boolean enabled) {
                action.setEnabled(enabled);
                Logger.d(TAG, "Action toggled: " + action.getLabel() + " enabled=" + enabled);
            }

            @Override
            public void onEdit(@NonNull AutoClickAction action) {
                showEditActionDialog(action);
            }

            @Override
            public void onDelete(@NonNull AutoClickAction action) {
                showDeleteConfirmation(action);
            }

            @Override
            public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }

            @Override
            public void onLongPress(@NonNull View anchor, @NonNull AutoClickAction action) {
                showLongPressMenu(anchor, action);
            }
        });

        recyclerActions.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerActions.setAdapter(adapter);

        // Setup drag-to-reorder
        setupDragToReorder();
    }

    private void setupDragToReorder() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe action
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // Drag is initiated from the drag handle, not long press
                return false;
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerActions);
    }

    private void setupFab() {
        fabAddAction.setOnClickListener(v -> showFabMenu(v));
    }

    private void showFabMenu(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.add_standard_action);
        popup.getMenu().add(0, 2, 1, R.string.add_custom_action);
        popup.getMenu().add(0, 3, 2, R.string.add_sleep_action);
        popup.getMenu().add(0, 4, 3, R.string.add_tap_at_coords);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showStandardActionsBottomSheet();
                    return true;
                case 2:
                    navigateToInteractivePicker();
                    return true;
                case 3:
                    showAddSleepDialog();
                    return true;
                case 4:
                    navigateToCoordinatePicker();
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    private void setupListeners() {
        buttonTestActions.setOnClickListener(v -> navigateToActionTester());
        buttonClose.setOnClickListener(v -> {
            saveActionsResult();
            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    private void setupFragmentResultListeners() {
        // Listen for EditAutoClickDialogFragment result
        getChildFragmentManager().setFragmentResultListener(
                EditAutoClickDialogFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String actionJson = result.getString(EditAutoClickDialogFragment.RESULT_ACTION_JSON);
                    handleEditActionResult(actionJson);
                }
        );

        // Listen for StandardActionsBottomSheet result
        getChildFragmentManager().setFragmentResultListener(
                StandardActionsBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String actionJson = result.getString(StandardActionsBottomSheet.BUNDLE_KEY_ACTION_JSON);
                    handleStandardActionResult(actionJson);
                }
        );

        // Listen for AddSleepDialogFragment result
        getChildFragmentManager().setFragmentResultListener(
                AddSleepDialogFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String actionJson = result.getString(AddSleepDialogFragment.RESULT_ACTION_JSON);
                    handleSleepActionResult(actionJson);
                }
        );

        // Listen for InteractivePickerFragment result (from parent fragment manager)
        getParentFragmentManager().setFragmentResultListener(
                INTERACTIVE_PICKER_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String selector = result.getString(SELECTOR_KEY);
                    handleInteractivePickerResult(selector);
                }
        );

        // Listen for CoordinatePickerFragment result (from parent fragment manager) - for NEW actions
        getParentFragmentManager().setFragmentResultListener(
                COORDINATE_PICKER_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    float tapX = result.getFloat("tapX", -1f);
                    float tapY = result.getFloat("tapY", -1f);
                    if (tapX >= 0 && tapY >= 0) {
                        addTapCoordinatesAction(tapX, tapY);
                    }
                }
        );

        // Listen for CoordinatePickerFragment EDIT result - for updating EXISTING actions
        getParentFragmentManager().setFragmentResultListener(
                COORDINATE_PICKER_EDIT_RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    float tapX = result.getFloat("tapX", -1f);
                    float tapY = result.getFloat("tapY", -1f);
                    String actionId = result.getString("action_id");
                    if (tapX >= 0 && tapY >= 0 && actionId != null) {
                        updateTapCoordinatesAction(actionId, tapX, tapY);
                    }
                }
        );
    }

    private void handleEditActionResult(@Nullable String actionJson) {
        if (actionJson == null || actionJson.isEmpty()) {
            return;
        }

        try {
            AutoClickAction updatedAction = AutoClickAction.fromJson(new JSONObject(actionJson));

            // Find and update existing action or add new one
            boolean found = false;
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).getId().equals(updatedAction.getId())) {
                    actions.set(i, updatedAction);
                    found = true;
                    Logger.d(TAG, "Updated action: " + updatedAction.getLabel());
                    break;
                }
            }

            if (!found) {
                // New action - set order and add
                updatedAction.setOrder(actions.size());
                actions.add(updatedAction);
                Logger.d(TAG, "Added new action: " + updatedAction.getLabel());
            }

            refreshActionsList();
        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing action JSON", e);
        }
    }

    private void handleStandardActionResult(@Nullable String actionJson) {
        if (actionJson == null || actionJson.isEmpty()) {
            return;
        }

        try {
            AutoClickAction action = AutoClickAction.fromJson(new JSONObject(actionJson));
            action.setOrder(actions.size());
            actions.add(action);
            Logger.d(TAG, "Added standard action: " + action.getLabel());
            refreshActionsList();
        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing standard action JSON", e);
        }
    }

    private void handleSleepActionResult(@Nullable String actionJson) {
        if (actionJson == null || actionJson.isEmpty()) {
            return;
        }

        try {
            AutoClickAction action = AutoClickAction.fromJson(new JSONObject(actionJson));
            action.setOrder(actions.size());
            actions.add(action);
            Logger.d(TAG, "Added sleep action: " + action.getLabel());
            refreshActionsList();
        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing sleep action JSON", e);
        }
    }

    private void handleInteractivePickerResult(@Nullable String selector) {
        if (selector == null || selector.isEmpty()) {
            return;
        }

        Logger.d(TAG, "Received selector from interactive picker: " + selector);

        // Create a new custom click action with the selected selector
        AutoClickAction action = AutoClickAction.createClickAction(
                UUID.randomUUID().toString(),
                getString(R.string.custom_click_action),
                selector,
                true,
                false,
                actions.size()
        );

        actions.add(action);
        refreshActionsList();
    }

    private void showStandardActionsBottomSheet() {
        StandardActionsBottomSheet bottomSheet = StandardActionsBottomSheet.newInstance();
        bottomSheet.show(getChildFragmentManager(), StandardActionsBottomSheet.TAG);
    }

    private void navigateToInteractivePicker() {
        if (siteUrl == null || siteUrl.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.url_required_for_selector)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        try {
            Bundle args = new Bundle();
            args.putString("url", siteUrl);
            args.putString("actions_json", AutoClickAction.toJsonString(actions));

            NavController navController = Navigation.findNavController(requireView());
            navController.navigate(R.id.interactivePickerFragment, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to interactive picker", e);
        }
    }

    private void showAddSleepDialog() {
        AddSleepDialogFragment dialog = AddSleepDialogFragment.newInstance();
        dialog.show(getChildFragmentManager(), "addSleepDialog");
    }

    private void navigateToCoordinatePicker() {
        if (siteUrl == null || siteUrl.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.url_required_for_selector)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        // Check if accessibility service is enabled
        if (!AccessibilityHelper.isAccessibilityServiceEnabled(requireContext())) {
            showAccessibilityRequiredDialog();
            return;
        }

        try {
            Bundle args = new Bundle();
            args.putString("url", siteUrl);
            // Pass current actions so they can be executed before coordinate selection
            args.putString("actions_json", AutoClickAction.toJsonString(actions));

            NavController navController = Navigation.findNavController(requireView());
            navController.navigate(R.id.coordinatePickerFragment, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to coordinate picker", e);
        }
    }

    private void showAccessibilityRequiredDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.accessibility_required)
                .setMessage(R.string.accessibility_required_message)
                .setPositiveButton(R.string.enable, (dialog, which) -> {
                    AccessibilityHelper.openAccessibilitySettings(requireContext());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addTapCoordinatesAction(float tapX, float tapY) {
        int xPercent = Math.round(tapX * 100);
        int yPercent = Math.round(tapY * 100);
        String label = getString(R.string.tap_coordinates_summary, xPercent, yPercent);

        AutoClickAction action = AutoClickAction.createTapCoordinatesAction(
                UUID.randomUUID().toString(),
                label,
                tapX,
                tapY,
                true,
                actions.size()
        );

        actions.add(action);
        Logger.d(TAG, "Added tap coordinates action: " + label);
        refreshActionsList();
    }

    /**
     * Update an existing TAP_COORDINATES action with new coordinates.
     *
     * @param actionId The ID of the action to update
     * @param tapX     New X coordinate (0.0-1.0)
     * @param tapY     New Y coordinate (0.0-1.0)
     */
    private void updateTapCoordinatesAction(String actionId, float tapX, float tapY) {
        for (AutoClickAction action : actions) {
            if (action.getId().equals(actionId)) {
                action.setTapX(tapX);
                action.setTapY(tapY);

                // Update label to reflect new coordinates
                int xPercent = Math.round(tapX * 100);
                int yPercent = Math.round(tapY * 100);
                action.setLabel(getString(R.string.tap_coordinates_summary, xPercent, yPercent));

                Logger.d(TAG, "Updated tap coordinates action: " + actionId + " to (" + xPercent + "%, " + yPercent + "%)");
                refreshActionsList();
                return;
            }
        }
        Logger.w(TAG, "Action not found for update: " + actionId);
    }

    private void navigateToActionTester() {
        if (siteUrl == null || siteUrl.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.url_required_for_selector)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        try {
            Bundle args = new Bundle();
            args.putString("url", siteUrl);
            args.putString("actions_json", AutoClickAction.toJsonString(actions));
            args.putBoolean("execute_actions", true);

            NavController navController = Navigation.findNavController(requireView());
            navController.navigate(R.id.actionTesterFragment, args);
        } catch (Exception e) {
            Logger.e(TAG, "Error navigating to action tester", e);
        }
    }

    private void showEditActionDialog(@NonNull AutoClickAction action) {
        EditAutoClickDialogFragment dialog = EditAutoClickDialogFragment.newInstance(action, siteUrl);
        dialog.show(getChildFragmentManager(), "editActionDialog");
    }

    private void showDeleteConfirmation(@NonNull AutoClickAction action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_action_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteAction(action);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAction(@NonNull AutoClickAction action) {
        actions.remove(action);

        // Update order for remaining actions
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setOrder(i);
        }

        Logger.d(TAG, "Deleted action: " + action.getLabel());
        refreshActionsList();
    }

    /**
     * Show context menu for long press on action item.
     *
     * @param anchor The view to anchor the menu to
     * @param action The action for the menu
     */
    public void showLongPressMenu(@NonNull View anchor, @NonNull AutoClickAction action) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.duplicate);
        popup.getMenu().add(0, 2, 1, R.string.edit);
        popup.getMenu().add(0, 3, 2, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    duplicateAction(action);
                    return true;
                case 2:
                    showEditActionDialog(action);
                    return true;
                case 3:
                    showDeleteConfirmation(action);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    private void duplicateAction(@NonNull AutoClickAction action) {
        AutoClickAction copy = action.copy();
        copy.setLabel(action.getLabel() + " (Copy)");
        copy.setOrder(actions.size());
        actions.add(copy);

        Logger.d(TAG, "Duplicated action: " + action.getLabel() + " -> " + copy.getLabel());
        refreshActionsList();
    }

    private void refreshActionsList() {
        // Update adapter with current actions list
        adapter.setActions(actions);
        updateEmptyState();
        updateTestButtonState();
    }

    private void updateEmptyState() {
        if (actions == null || actions.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerActions.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerActions.setVisibility(View.VISIBLE);
        }
    }

    private void updateTestButtonState() {
        buttonTestActions.setEnabled(actions != null && !actions.isEmpty());
    }

    private void saveAndReturn() {
        // Get the latest actions (may have been reordered)
        actions = adapter.getActions();

        // Update order values
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setOrder(i);
        }

        // Create result bundle
        Bundle result = new Bundle();
        result.putString(RESULT_ACTIONS_JSON, AutoClickAction.toJsonString(actions));

        // Set fragment result
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);

        Logger.d(TAG, "Saving and returning with " + actions.size() + " actions");

        // Pop back stack
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            Navigation.findNavController(requireView()).popBackStack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save actions when leaving the fragment
        saveActionsResult();
    }

    private void saveActionsResult() {
        // Get the latest actions (may have been reordered)
        actions = adapter.getActions();

        // Update order values
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setOrder(i);
        }

        // Create result bundle
        Bundle result = new Bundle();
        result.putString(RESULT_ACTIONS_JSON, AutoClickAction.toJsonString(actions));

        // Set fragment result
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);

        Logger.d(TAG, "Saved actions result with " + actions.size() + " actions");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerActions = null;
        emptyState = null;
        buttonTestActions = null;
        buttonClose = null;
        fabAddAction = null;
    }
}
