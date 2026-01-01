package com.ltrudu.sitewatcher.ui.addedit;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.Schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecyclerView adapter for displaying schedules.
 * Supports drag-and-drop reordering, enable/disable toggle, edit and delete operations.
 */
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    /**
     * Listener interface for schedule item interactions.
     */
    public interface OnScheduleListener {
        /**
         * Called when a schedule's enabled state is toggled.
         *
         * @param schedule The schedule that was toggled
         * @param enabled  The new enabled state
         */
        void onToggleEnabled(@NonNull Schedule schedule, boolean enabled);

        /**
         * Called when a schedule's edit is requested.
         *
         * @param schedule The schedule to edit
         */
        void onEdit(@NonNull Schedule schedule);

        /**
         * Called when a schedule's delete is requested.
         *
         * @param schedule The schedule to delete
         */
        void onDelete(@NonNull Schedule schedule);

        /**
         * Called when the drag handle is touched to start drag operation.
         *
         * @param viewHolder The ViewHolder being dragged
         */
        void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);

        /**
         * Called when a schedule item is long-pressed.
         *
         * @param anchor   The view to anchor the popup menu to
         * @param schedule The schedule that was long-pressed
         */
        void onLongPress(@NonNull View anchor, @NonNull Schedule schedule);
    }

    private final List<Schedule> schedules = new ArrayList<>();

    @Nullable
    private OnScheduleListener listener;

    /**
     * Default constructor.
     */
    public ScheduleAdapter() {
    }

    /**
     * Set the listener for schedule interactions.
     *
     * @param listener The listener to set
     */
    public void setOnScheduleListener(@Nullable OnScheduleListener listener) {
        this.listener = listener;
    }

    /**
     * Update the list of schedules.
     *
     * @param newSchedules The new list of schedules
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setSchedules(@Nullable List<Schedule> newSchedules) {
        schedules.clear();
        if (newSchedules != null) {
            schedules.addAll(newSchedules);
        }
        notifyDataSetChanged();
    }

    /**
     * Get the current list of schedules.
     *
     * @return The list of schedules
     */
    @NonNull
    public List<Schedule> getSchedules() {
        return new ArrayList<>(schedules);
    }

    /**
     * Move an item from one position to another for drag reorder.
     *
     * @param from The source position
     * @param to   The destination position
     */
    public void moveItem(int from, int to) {
        if (from < 0 || from >= schedules.size() || to < 0 || to >= schedules.size()) {
            return;
        }

        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(schedules, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(schedules, i, i - 1);
            }
        }

        // Update order values
        for (int i = 0; i < schedules.size(); i++) {
            schedules.get(i).setOrder(i);
        }

        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Schedule schedule = schedules.get(position);
        holder.bind(schedule);
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    /**
     * ViewHolder for schedule items.
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
         * Bind a schedule to this ViewHolder.
         *
         * @param schedule The schedule to display
         */
        void bind(@NonNull Schedule schedule) {
            // Set label
            textLabel.setText(schedule.getLabel());

            // Set summary
            textSummary.setText(schedule.getSummary());

            // Set enabled switch without triggering listener
            switchEnabled.setOnCheckedChangeListener(null);
            switchEnabled.setChecked(schedule.isEnabled());
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleEnabled(schedule, isChecked);
                }
            });

            // Hide edit and delete buttons - use long press menu instead
            buttonEdit.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);

            // Set up click listener to open edit view
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(schedule);
                }
            });

            // Set up long press listener for context menu
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onLongPress(itemView, schedule);
                }
                return true;
            });
        }
    }
}
