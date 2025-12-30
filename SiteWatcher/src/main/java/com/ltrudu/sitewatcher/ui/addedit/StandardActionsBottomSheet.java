package com.ltrudu.sitewatcher.ui.addedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;
import com.ltrudu.sitewatcher.network.BuiltInClickPatterns;

import org.json.JSONException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bottom sheet dialog for selecting standard auto-click action patterns.
 * Displays all built-in cookie consent patterns grouped by category.
 * Returns the selected pattern via Fragment Result API.
 */
public class StandardActionsBottomSheet extends BottomSheetDialogFragment
        implements StandardActionsAdapter.OnPatternSelectedListener {

    public static final String TAG = "StandardActionsBottomSheet";
    public static final String REQUEST_KEY = "standardActionRequest";
    public static final String RESULT_KEY = "standardActionResult";
    public static final String BUNDLE_KEY_ACTION_JSON = "action_json";

    private StandardActionsAdapter adapter;

    /**
     * Create a new instance of the bottom sheet.
     *
     * @return New instance
     */
    @NonNull
    public static StandardActionsBottomSheet newInstance() {
        return new StandardActionsBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_standard_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set up RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerStandardActions);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new StandardActionsAdapter();
        adapter.setOnPatternSelectedListener(this);
        recyclerView.setAdapter(adapter);

        // Load patterns by category
        Map<String, List<AutoClickAction>> patternsByCategory =
                BuiltInClickPatterns.getPatternsByCategory(requireContext());
        adapter.setPatternsByCategory(patternsByCategory);
    }

    @Override
    public void onPatternSelected(@NonNull AutoClickAction action) {
        // Create a copy with new ID and enabled state
        AutoClickAction copy = action.copy();
        copy.setId(UUID.randomUUID().toString());
        copy.setEnabled(true);
        copy.setBuiltIn(false);

        // Return the selected action via Fragment Result API
        try {
            String actionJson = copy.toJson().toString();
            Bundle result = new Bundle();
            result.putString(BUNDLE_KEY_ACTION_JSON, actionJson);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        } catch (JSONException e) {
            // Unlikely, but handle gracefully by just dismissing
        }

        dismiss();
    }
}
