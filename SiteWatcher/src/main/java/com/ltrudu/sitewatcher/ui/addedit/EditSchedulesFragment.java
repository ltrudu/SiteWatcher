package com.ltrudu.sitewatcher.ui.addedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

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
import com.ltrudu.sitewatcher.data.model.CalendarScheduleType;
import com.ltrudu.sitewatcher.data.model.Schedule;
import com.ltrudu.sitewatcher.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing calendar schedules.
 * Provides functionality to add, edit, delete, and reorder schedules.
 * Returns the updated schedules via Fragment Result API.
 */
public class EditSchedulesFragment extends Fragment {

    private static final String TAG = "EditSchedulesFragment";
    private static final String ARG_SCHEDULES_JSON = "schedules_json";

    public static final String RESULT_KEY = "editSchedulesResult";
    public static final String RESULT_SCHEDULES_JSON = "schedules_json";

    // Views
    private RecyclerView recyclerSchedules;
    private LinearLayout emptyState;
    private MaterialButton buttonClose;
    private FloatingActionButton fabAddSchedule;

    // Data
    private List<Schedule> schedules;
    private ScheduleAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    public EditSchedulesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse arguments
        if (getArguments() != null) {
            String schedulesJson = getArguments().getString(ARG_SCHEDULES_JSON);
            schedules = Schedule.fromJsonString(schedulesJson);
        } else {
            schedules = new ArrayList<>();
        }

        // Ensure we always have at least one schedule
        if (schedules.isEmpty()) {
            schedules.add(Schedule.createAllTheTime());
        }

        Logger.d(TAG, "onCreate: schedules=" + schedules.size());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_schedules, container, false);
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
        recyclerSchedules = view.findViewById(R.id.recyclerSchedules);
        emptyState = view.findViewById(R.id.emptyState);
        buttonClose = view.findViewById(R.id.buttonClose);
        fabAddSchedule = view.findViewById(R.id.fabAddSchedule);
    }

    private void setupRecyclerView() {
        adapter = new ScheduleAdapter();
        adapter.setSchedules(schedules);

        adapter.setOnScheduleListener(new ScheduleAdapter.OnScheduleListener() {
            @Override
            public void onToggleEnabled(@NonNull Schedule schedule, boolean enabled) {
                schedule.setEnabled(enabled);
                Logger.d(TAG, "Schedule toggled: " + schedule.getLabel() + " enabled=" + enabled);
            }

            @Override
            public void onEdit(@NonNull Schedule schedule) {
                showEditScheduleDialog(schedule);
            }

            @Override
            public void onDelete(@NonNull Schedule schedule) {
                showDeleteConfirmation(schedule);
            }

            @Override
            public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }

            @Override
            public void onLongPress(@NonNull View anchor, @NonNull Schedule schedule) {
                showLongPressMenu(anchor, schedule);
            }
        });

        recyclerSchedules.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerSchedules.setAdapter(adapter);

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
        itemTouchHelper.attachToRecyclerView(recyclerSchedules);
    }

    private void setupFab() {
        fabAddSchedule.setOnClickListener(v -> showFabMenu(v));
    }

    private void showFabMenu(@NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.schedule_all_the_time);
        popup.getMenu().add(0, 2, 1, R.string.schedule_selected_day);
        popup.getMenu().add(0, 3, 2, R.string.schedule_date_range);
        popup.getMenu().add(0, 4, 3, R.string.schedule_every_weeks);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    addAllTheTimeSchedule();
                    return true;
                case 2:
                    addSelectedDaySchedule();
                    return true;
                case 3:
                    addDateRangeSchedule();
                    return true;
                case 4:
                    addEveryWeeksSchedule();
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    private void addAllTheTimeSchedule() {
        // Check if an ALL_THE_TIME schedule already exists
        for (Schedule schedule : schedules) {
            if (schedule.getCalendarType() == CalendarScheduleType.ALL_THE_TIME) {
                Toast.makeText(requireContext(), R.string.schedule_already_exists, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Create new ALL_THE_TIME schedule
        Schedule schedule = Schedule.createAllTheTime();
        schedule.setOrder(schedules.size());
        schedules.add(schedule);

        Logger.d(TAG, "Added ALL_THE_TIME schedule");
        refreshSchedulesList();
    }

    private void addSelectedDaySchedule() {
        Schedule schedule = Schedule.createSelectedDay(System.currentTimeMillis());
        schedule.setOrder(schedules.size());
        schedules.add(schedule);

        Logger.d(TAG, "Added SELECTED_DAY schedule");
        refreshSchedulesList();

        // Open the edit dialog for the new schedule
        showEditScheduleDialog(schedule);
    }

    private void addDateRangeSchedule() {
        Schedule schedule = Schedule.createDateRange(
                System.currentTimeMillis(),
                System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000) // 1 week later
        );
        schedule.setOrder(schedules.size());
        schedules.add(schedule);

        Logger.d(TAG, "Added DATE_RANGE schedule");
        refreshSchedulesList();

        // Open the edit dialog for the new schedule
        showEditScheduleDialog(schedule);
    }

    private void addEveryWeeksSchedule() {
        Schedule schedule = Schedule.createEveryWeeks(Schedule.ALL_DAYS,
                com.ltrudu.sitewatcher.data.model.WeekParity.BOTH);
        schedule.setOrder(schedules.size());
        schedules.add(schedule);

        Logger.d(TAG, "Added EVERY_WEEKS schedule");
        refreshSchedulesList();

        // Open the edit dialog for the new schedule
        showEditScheduleDialog(schedule);
    }

    private void setupListeners() {
        buttonClose.setOnClickListener(v -> {
            saveSchedulesResult();
            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    private void setupFragmentResultListeners() {
        // Listen for EditScheduleDialogFragment result
        getChildFragmentManager().setFragmentResultListener(
                EditScheduleDialogFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String scheduleJson = result.getString(EditScheduleDialogFragment.RESULT_SCHEDULE_JSON);
                    handleEditScheduleResult(scheduleJson);
                }
        );
    }

    private void handleEditScheduleResult(@Nullable String scheduleJson) {
        if (scheduleJson == null || scheduleJson.isEmpty()) {
            return;
        }

        try {
            Schedule updatedSchedule = Schedule.fromJson(new JSONObject(scheduleJson));

            // Find and update existing schedule
            boolean found = false;
            for (int i = 0; i < schedules.size(); i++) {
                if (schedules.get(i).getId().equals(updatedSchedule.getId())) {
                    schedules.set(i, updatedSchedule);
                    found = true;
                    Logger.d(TAG, "Updated schedule: " + updatedSchedule.getLabel());
                    break;
                }
            }

            if (!found) {
                // New schedule - set order and add
                updatedSchedule.setOrder(schedules.size());
                schedules.add(updatedSchedule);
                Logger.d(TAG, "Added new schedule: " + updatedSchedule.getLabel());
            }

            refreshSchedulesList();
        } catch (JSONException e) {
            Logger.e(TAG, "Error parsing schedule JSON", e);
        }
    }

    private void showEditScheduleDialog(@NonNull Schedule schedule) {
        EditScheduleDialogFragment dialog = EditScheduleDialogFragment.newInstance(schedule);
        dialog.show(getChildFragmentManager(), "editScheduleDialog");
    }

    private void showDeleteConfirmation(@NonNull Schedule schedule) {
        // Check if this is the last schedule
        if (schedules.size() <= 1) {
            Toast.makeText(requireContext(), R.string.cannot_delete_last_schedule, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_schedule_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteSchedule(schedule);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteSchedule(@NonNull Schedule schedule) {
        schedules.remove(schedule);

        // Update order for remaining schedules
        for (int i = 0; i < schedules.size(); i++) {
            schedules.get(i).setOrder(i);
        }

        Logger.d(TAG, "Deleted schedule: " + schedule.getLabel());
        refreshSchedulesList();
    }

    /**
     * Show context menu for long press on schedule item.
     *
     * @param anchor   The view to anchor the menu to
     * @param schedule The schedule for the menu
     */
    public void showLongPressMenu(@NonNull View anchor, @NonNull Schedule schedule) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1, R.string.duplicate);
        popup.getMenu().add(0, 3, 2, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showEditScheduleDialog(schedule);
                    return true;
                case 2:
                    duplicateSchedule(schedule);
                    return true;
                case 3:
                    showDeleteConfirmation(schedule);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    private void duplicateSchedule(@NonNull Schedule schedule) {
        Schedule copy = schedule.copy();
        copy.setOrder(schedules.size());
        schedules.add(copy);

        Logger.d(TAG, "Duplicated schedule: " + schedule.getLabel());
        refreshSchedulesList();
    }

    private void refreshSchedulesList() {
        // Update adapter with current schedules list
        adapter.setSchedules(schedules);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (schedules == null || schedules.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerSchedules.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerSchedules.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save schedules when leaving the fragment
        saveSchedulesResult();
    }

    private void saveSchedulesResult() {
        // Get the latest schedules (may have been reordered)
        schedules = adapter.getSchedules();

        // Update order values
        for (int i = 0; i < schedules.size(); i++) {
            schedules.get(i).setOrder(i);
        }

        // Create result bundle
        Bundle result = new Bundle();
        result.putString(RESULT_SCHEDULES_JSON, Schedule.toJsonString(schedules));

        // Set fragment result
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);

        Logger.d(TAG, "Saved schedules result with " + schedules.size() + " schedules");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerSchedules = null;
        emptyState = null;
        buttonClose = null;
        fabAddSchedule = null;
    }
}
