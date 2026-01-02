package com.ltrudu.sitewatcher.ui.addedit;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.FeedbackAction;
import com.ltrudu.sitewatcher.data.model.FeedbackActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for displaying feedback actions.
 * Supports drag-and-drop reordering, enable/disable toggle, edit and delete operations.
 */
public class FeedbackActionAdapter extends RecyclerView.Adapter<FeedbackActionAdapter.ViewHolder> {

    /**
     * Listener interface for feedback action item interactions.
     */
    public interface OnFeedbackActionListener {
        /**
         * Called when an action's enabled state is toggled.
         *
         * @param action  The action that was toggled
         * @param enabled The new enabled state
         */
        void onToggleEnabled(@NonNull FeedbackAction action, boolean enabled);

        /**
         * Called when an action's edit button is clicked.
         *
         * @param action The action to edit
         */
        void onEdit(@NonNull FeedbackAction action);

        /**
         * Called when an action's delete button is clicked.
         *
         * @param action The action to delete
         */
        void onDelete(@NonNull FeedbackAction action);

        /**
         * Called when the drag handle is touched to start drag operation.
         *
         * @param viewHolder The ViewHolder being dragged
         */
        void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);

        /**
         * Called when an action item is long-pressed.
         *
         * @param anchor The view to anchor the popup menu to
         * @param action The action that was long-pressed
         */
        void onLongPress(@NonNull View anchor, @NonNull FeedbackAction action);
    }

    private final List<FeedbackAction> actions = new ArrayList<>();

    @Nullable
    private OnFeedbackActionListener listener;

    /**
     * Default constructor.
     */
    public FeedbackActionAdapter() {
    }

    /**
     * Set the listener for feedback action interactions.
     *
     * @param listener The listener to set
     */
    public void setListener(@Nullable OnFeedbackActionListener listener) {
        this.listener = listener;
    }

    /**
     * Update the list of actions.
     *
     * @param newActions The new list of actions
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setActions(@Nullable List<FeedbackAction> newActions) {
        actions.clear();
        if (newActions != null) {
            actions.addAll(newActions);
        }
        notifyDataSetChanged();
    }

    /**
     * Get the current list of actions.
     *
     * @return A copy of the list of actions
     */
    @NonNull
    public List<FeedbackAction> getActions() {
        return new ArrayList<>(actions);
    }

    /**
     * Add an action to the list.
     *
     * @param action The action to add
     */
    public void addAction(@NonNull FeedbackAction action) {
        actions.add(action);
        notifyItemInserted(actions.size() - 1);
    }

    /**
     * Remove an action from the list.
     *
     * @param action The action to remove
     */
    public void removeAction(@NonNull FeedbackAction action) {
        int index = actions.indexOf(action);
        if (index >= 0) {
            actions.remove(index);
            notifyItemRemoved(index);
        }
    }

    /**
     * Move an item from one position to another for drag reorder.
     *
     * @param from The source position
     * @param to   The destination position
     */
    public void moveItem(int from, int to) {
        if (from < 0 || from >= actions.size() || to < 0 || to >= actions.size()) {
            return;
        }

        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(actions, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(actions, i, i - 1);
            }
        }

        // Update order values
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setOrder(i);
        }

        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feedback_action, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedbackAction action = actions.get(position);
        holder.bind(action);
    }

    @Override
    public int getItemCount() {
        return actions.size();
    }

    /**
     * Get the icon resource ID for a given feedback action type.
     *
     * @param type The feedback action type
     * @return The drawable resource ID
     */
    private int getIconForType(@NonNull FeedbackActionType type) {
        switch (type) {
            case NOTIFICATION:
                return R.drawable.ic_notifications;
            case SEND_SMS:
                return R.drawable.ic_sms;
            case TRIGGER_ALARM:
                return R.drawable.ic_alarm;
            case PLAY_SOUND:
                return R.drawable.ic_volume_up;
            case CAMERA_FLASH:
                return R.drawable.ic_flash_on;
            case VIBRATE:
                return R.drawable.ic_vibration;
            default:
                return R.drawable.ic_info;
        }
    }

    /**
     * ViewHolder for feedback action items.
     */
    class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView iconType;
        private final ImageView dragHandle;
        private final TextView textLabel;
        private final TextView textSummary;
        private final SwitchMaterial switchEnabled;

        @SuppressLint("ClickableViewAccessibility")
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconType = itemView.findViewById(R.id.iconType);
            dragHandle = itemView.findViewById(R.id.dragHandle);
            textLabel = itemView.findViewById(R.id.textLabel);
            textSummary = itemView.findViewById(R.id.textSummary);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);

            // Set up drag handle touch listener
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (listener != null) {
                        listener.onStartDrag(ViewHolder.this);
                    }
                }
                return false;
            });
        }

        /**
         * Bind a feedback action to this ViewHolder.
         *
         * @param action The action to display
         */
        void bind(@NonNull FeedbackAction action) {
            // Set icon based on action type
            iconType.setImageResource(getIconForType(action.getType()));

            // Set label
            textLabel.setText(action.getLabel());

            // Set summary from action
            textSummary.setText(action.getSummary());

            // Set enabled switch without triggering listener
            switchEnabled.setOnCheckedChangeListener(null);
            switchEnabled.setChecked(action.isEnabled());
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleEnabled(action, isChecked);
                }
            });

            // Set up click listener to open edit view
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(action);
                }
            });

            // Set up long press listener for context menu
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onLongPress(itemView, action);
                }
                return true;
            });
        }
    }
}
