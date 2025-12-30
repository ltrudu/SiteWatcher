package com.ltrudu.sitewatcher.ui.addedit;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.ActionType;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for displaying auto-click actions.
 * Supports drag-and-drop reordering, enable/disable toggle, edit and delete operations.
 */
public class AutoClickActionAdapter extends RecyclerView.Adapter<AutoClickActionAdapter.ViewHolder> {

    /**
     * Listener interface for action item interactions.
     */
    public interface OnActionListener {
        /**
         * Called when an action's enabled state is toggled.
         *
         * @param action  The action that was toggled
         * @param enabled The new enabled state
         */
        void onToggleEnabled(@NonNull AutoClickAction action, boolean enabled);

        /**
         * Called when an action's edit button is clicked.
         *
         * @param action The action to edit
         */
        void onEdit(@NonNull AutoClickAction action);

        /**
         * Called when an action's delete button is clicked.
         *
         * @param action The action to delete
         */
        void onDelete(@NonNull AutoClickAction action);

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
        void onLongPress(@NonNull View anchor, @NonNull AutoClickAction action);
    }

    private final List<AutoClickAction> actions = new ArrayList<>();

    @Nullable
    private OnActionListener listener;

    /**
     * Default constructor.
     */
    public AutoClickActionAdapter() {
    }

    /**
     * Set the listener for action interactions.
     *
     * @param listener The listener to set
     */
    public void setOnActionListener(@Nullable OnActionListener listener) {
        this.listener = listener;
    }

    /**
     * Update the list of actions.
     *
     * @param newActions The new list of actions
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setActions(@Nullable List<AutoClickAction> newActions) {
        actions.clear();
        if (newActions != null) {
            actions.addAll(newActions);
        }
        notifyDataSetChanged();
    }

    /**
     * Get the current list of actions.
     *
     * @return The list of actions
     */
    @NonNull
    public List<AutoClickAction> getActions() {
        return new ArrayList<>(actions);
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
                .inflate(R.layout.item_auto_click_action, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AutoClickAction action = actions.get(position);
        holder.bind(action);
    }

    @Override
    public int getItemCount() {
        return actions.size();
    }

    /**
     * ViewHolder for auto-click action items.
     */
    class ViewHolder extends RecyclerView.ViewHolder {

        private final SwitchMaterial switchEnabled;
        private final TextView textLabel;
        private final TextView textSummary;
        private final ImageButton buttonEdit;
        private final ImageButton buttonDelete;
        private final ImageView dragHandle;

        @SuppressLint("ClickableViewAccessibility")
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
            textLabel = itemView.findViewById(R.id.textLabel);
            textSummary = itemView.findViewById(R.id.textSummary);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
            dragHandle = itemView.findViewById(R.id.dragHandle);

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
         * Bind an action to this ViewHolder.
         *
         * @param action The action to display
         */
        void bind(@NonNull AutoClickAction action) {
            // Set label
            textLabel.setText(action.getLabel());

            // Set summary based on action type
            if (action.getType() == ActionType.WAIT) {
                textSummary.setText(itemView.getContext().getString(
                        R.string.wait_summary, action.getWaitSeconds()));
            } else if (action.getType() == ActionType.TAP_COORDINATES) {
                // TAP_COORDINATES action - show coordinates
                int xPercent = Math.round(action.getTapX() * 100);
                int yPercent = Math.round(action.getTapY() * 100);
                textSummary.setText(itemView.getContext().getString(
                        R.string.tap_coordinates_summary, xPercent, yPercent));
            } else {
                // CLICK action - show selector
                String selector = action.getSelector();
                textSummary.setText(selector != null ? selector : "");
            }

            // Set enabled switch without triggering listener
            switchEnabled.setOnCheckedChangeListener(null);
            switchEnabled.setChecked(action.isEnabled());
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleEnabled(action, isChecked);
                }
            });

            // Hide edit and delete buttons - use long press menu instead
            buttonEdit.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);

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
