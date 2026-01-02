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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FeedbackActionType;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing feedback actions.
 * Provides functionality to add, edit, delete, and reorder feedback actions.
 * Returns the updated actions via Fragment Result API.
 */
public class EditFeedbackFragment extends Fragment {

    private static final String TAG = "EditFeedbackFragment";

    public static final String ARG_FEEDBACK_JSON = "feedback_json";
    public static final String RESULT_KEY = "editFeedbackResult";
    public static final String RESULT_FEEDBACK_JSON = "feedback_json";

    // Views
    private RecyclerView recyclerFeedback;
    private LinearLayout emptyState;
    private MaterialButton buttonClose;
    private FloatingActionButton fabAddFeedback;

    // Data
    private List<FeedbackAction> feedbackActions;
    private FeedbackActionAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    public EditFeedbackFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments
        if (getArguments() != null) {
            String feedbackJson = getArguments().getString(ARG_FEEDBACK_JSON);
            feedbackActions = FeedbackAction.fromJsonString(feedbackJson);
        } else {
            feedbackActions = new ArrayList<>();
        }

        Logger.d(TAG, "onCreate: feedbackActions=" + feedbackActions.size());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_feedback, container, false);
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
    }

    private void initializeViews(@NonNull View view) {
        recyclerFeedback = view.findViewById(R.id.recyclerFeedback);
        emptyState = view.findViewById(R.id.emptyState);
        buttonClose = view.findViewById(R.id.buttonClose);
        fabAddFeedback = view.findViewById(R.id.fabAddFeedback);
    }

    private void setupRecyclerView() {
        adapter = new FeedbackActionAdapter();
        adapter.setActions(feedbackActions);

        adapter.setListener(new FeedbackActionAdapter.OnFeedbackActionListener() {
            @Override
            public void onToggleEnabled(@NonNull FeedbackAction action, boolean enabled) {
                action.setEnabled(enabled);
                Logger.d(TAG, "Action toggled: " + action.getLabel() + " enabled=" + enabled);
            }

            @Override
            public void onEdit(@NonNull FeedbackAction action) {
                showEditFeedbackDialog(action);
            }

            @Override
            public void onDelete(@NonNull FeedbackAction action) {
                showDeleteConfirmation(action);
            }

            @Override
            public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }

            @Override
            public void onLongPress(@NonNull View anchor, @NonNull FeedbackAction action) {
                showLongPressMenu(anchor, action);
            }
        });

        recyclerFeedback.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFeedback.setAdapter(adapter);

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
        itemTouchHelper.attachToRecyclerView(recyclerFeedback);
    }

    private void setupFab() {
        fabAddFeedback.setOnClickListener(v -> showFabMenu(v));
    }

    private void showFabMenu(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.feedback_notification);
        popup.getMenu().add(0, 2, 1, R.string.feedback_send_sms);
        popup.getMenu().add(0, 3, 2, R.string.feedback_trigger_alarm);
        popup.getMenu().add(0, 4, 3, R.string.feedback_play_sound);
        popup.getMenu().add(0, 5, 4, R.string.feedback_camera_flash);
        popup.getMenu().add(0, 6, 5, R.string.feedback_vibrate);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    addFeedbackAction(FeedbackActionType.NOTIFICATION);
                    return true;
                case 2:
                    addFeedbackAction(FeedbackActionType.SEND_SMS);
                    return true;
                case 3:
                    addFeedbackAction(FeedbackActionType.TRIGGER_ALARM);
                    return true;
                case 4:
                    addFeedbackAction(FeedbackActionType.PLAY_SOUND);
                    return true;
                case 5:
                    addFeedbackAction(FeedbackActionType.CAMERA_FLASH);
                    return true;
                case 6:
                    addFeedbackAction(FeedbackActionType.VIBRATE);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    private void setupListeners() {
        buttonClose.setOnClickListener(v -> {
            saveFeedbackResult();
            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    private void setupFragmentResultListeners() {
        // Listen for EditFeedbackDialogFragment result
        getChildFragmentManager().setFragmentResultListener(
                EditFeedbackDialogFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String actionJson = result.getString(EditFeedbackDialogFragment.RESULT_ACTION_JSON);
                    handleEditFeedbackResult(actionJson);
                }
        );
    }

    private void handleEditFeedbackResult(@Nullable String actionJson) {
        if (actionJson == null || actionJson.isEmpty()) {
            return;
        }

        try {
            FeedbackAction updatedAction = FeedbackAction.fromJson(new JSONObject(actionJson));

            // Find and update existing action or add new one
            boolean found = false;
            for (int i = 0; i < feedbackActions.size(); i++) {
                if (feedbackActions.get(i).getId().equals(updatedAction.getId())) {
                    feedbackActions.set(i, updatedAction);
                    found = true;
                    Logger.d(TAG, "Updated action: " + updatedAction.getLabel());
                    break;
                }
            }

            if (!found) {
                // New action - set order and add
                updatedAction.setOrder(feedbackActions.size());
                feedbackActions.add(updatedAction);
                Logger.d(TAG, "Added new action: " + updatedAction.getLabel());
            }

            refreshActionsList();
        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing action JSON", e);
        }
    }

    /**
     * Add a new feedback action of the specified type.
     *
     * @param type The type of feedback action to add
     */
    private void addFeedbackAction(@NonNull FeedbackActionType type) {
        FeedbackAction action = createDefaultAction(type);
        showEditFeedbackDialog(action);
    }

    /**
     * Create a default feedback action with the specified type.
     *
     * @param type The type of feedback action
     * @return A new FeedbackAction with default values
     */
    @NonNull
    private FeedbackAction createDefaultAction(@NonNull FeedbackActionType type) {
        switch (type) {
            case NOTIFICATION:
                return FeedbackAction.createNotification(getString(R.string.feedback_notification));
            case SEND_SMS:
                return FeedbackAction.createSmsAction(getString(R.string.feedback_send_sms), "");
            case TRIGGER_ALARM:
                return FeedbackAction.createAlarmAction(getString(R.string.feedback_trigger_alarm), null, 10);
            case PLAY_SOUND:
                return FeedbackAction.createSoundAction(getString(R.string.feedback_play_sound), null);
            case CAMERA_FLASH:
                return FeedbackAction.createFlashAction(getString(R.string.feedback_camera_flash), 200);
            case VIBRATE:
                return FeedbackAction.createVibrateAction(getString(R.string.feedback_vibrate), null);
            default:
                return FeedbackAction.createNotification(getString(R.string.feedback_notification));
        }
    }

    /**
     * Show the edit dialog for a feedback action.
     *
     * @param action The action to edit
     */
    private void showEditFeedbackDialog(@NonNull FeedbackAction action) {
        EditFeedbackDialogFragment dialog = EditFeedbackDialogFragment.newInstance(action);
        dialog.show(getChildFragmentManager(), "editFeedbackDialog");
    }

    /**
     * Show confirmation dialog before deleting an action.
     *
     * @param action The action to delete
     */
    private void showDeleteConfirmation(@NonNull FeedbackAction action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_action_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteAction(action);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAction(@NonNull FeedbackAction action) {
        feedbackActions.remove(action);

        // Update order for remaining actions
        for (int i = 0; i < feedbackActions.size(); i++) {
            feedbackActions.get(i).setOrder(i);
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
    public void showLongPressMenu(@NonNull View anchor, @NonNull FeedbackAction action) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1, R.string.duplicate);
        popup.getMenu().add(0, 3, 2, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showEditFeedbackDialog(action);
                    return true;
                case 2:
                    duplicateAction(action);
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

    /**
     * Duplicate an existing feedback action.
     *
     * @param action The action to duplicate
     */
    private void duplicateAction(@NonNull FeedbackAction action) {
        FeedbackAction copy = action.copy();
        copy.setLabel(action.getLabel() + " (Copy)");
        copy.setOrder(feedbackActions.size());
        feedbackActions.add(copy);

        Logger.d(TAG, "Duplicated action: " + action.getLabel() + " -> " + copy.getLabel());
        refreshActionsList();
    }

    private void refreshActionsList() {
        // Update adapter with current actions list
        adapter.setActions(feedbackActions);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (feedbackActions == null || feedbackActions.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerFeedback.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerFeedback.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save feedback actions when leaving the fragment
        saveFeedbackResult();
    }

    private void saveFeedbackResult() {
        // Get the latest actions (may have been reordered)
        feedbackActions = adapter.getActions();

        // Update order values
        for (int i = 0; i < feedbackActions.size(); i++) {
            feedbackActions.get(i).setOrder(i);
        }

        // Create result bundle
        Bundle result = new Bundle();
        result.putString(RESULT_FEEDBACK_JSON, FeedbackAction.toJsonString(feedbackActions));

        // Set fragment result
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);

        Logger.d(TAG, "Saved feedback result with " + feedbackActions.size() + " actions");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerFeedback = null;
        emptyState = null;
        buttonClose = null;
        fabAddFeedback = null;
    }
}
