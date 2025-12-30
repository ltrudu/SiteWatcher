package com.ltrudu.sitewatcher.ui.addedit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ltrudu.sitewatcher.R;
import com.ltrudu.sitewatcher.data.model.AutoClickAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for displaying standard auto-click action patterns
 * organized by category with section headers.
 */
public class StandardActionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    /**
     * Listener interface for pattern selection.
     */
    public interface OnPatternSelectedListener {
        /**
         * Called when a pattern is selected.
         *
         * @param action The selected pattern
         */
        void onPatternSelected(@NonNull AutoClickAction action);
    }

    private final List<Object> items = new ArrayList<>();

    @Nullable
    private OnPatternSelectedListener listener;

    /**
     * Default constructor.
     */
    public StandardActionsAdapter() {
    }

    /**
     * Set the listener for pattern selection.
     *
     * @param listener The listener to set
     */
    public void setOnPatternSelectedListener(@Nullable OnPatternSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * Set the patterns grouped by category.
     *
     * @param patternsByCategory Map of category name to list of patterns
     */
    public void setPatternsByCategory(@NonNull Map<String, List<AutoClickAction>> patternsByCategory) {
        items.clear();
        for (Map.Entry<String, List<AutoClickAction>> entry : patternsByCategory.entrySet()) {
            // Add header
            items.add(new HeaderItem(entry.getKey()));
            // Add patterns for this category
            items.addAll(entry.getValue());
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof HeaderItem) {
            return VIEW_TYPE_HEADER;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_standard_action_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_standard_action, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof HeaderViewHolder && item instanceof HeaderItem) {
            ((HeaderViewHolder) holder).bind((HeaderItem) item);
        } else if (holder instanceof ItemViewHolder && item instanceof AutoClickAction) {
            ((ItemViewHolder) holder).bind((AutoClickAction) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Simple wrapper for header items.
     */
    private static class HeaderItem {
        final String title;

        HeaderItem(String title) {
            this.title = title;
        }
    }

    /**
     * ViewHolder for section headers.
     */
    private static class HeaderViewHolder extends RecyclerView.ViewHolder {

        private final TextView textHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textHeader = itemView.findViewById(R.id.textHeader);
        }

        void bind(@NonNull HeaderItem item) {
            textHeader.setText(item.title);
        }
    }

    /**
     * ViewHolder for pattern items.
     */
    class ItemViewHolder extends RecyclerView.ViewHolder {

        private final TextView textLabel;
        private final TextView textSelector;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            textLabel = itemView.findViewById(R.id.textLabel);
            textSelector = itemView.findViewById(R.id.textSelector);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    Object item = items.get(position);
                    if (item instanceof AutoClickAction) {
                        listener.onPatternSelected((AutoClickAction) item);
                    }
                }
            });
        }

        void bind(@NonNull AutoClickAction action) {
            textLabel.setText(action.getLabel());
            String selector = action.getSelector();
            textSelector.setText(selector != null ? selector : "");
        }
    }
}
