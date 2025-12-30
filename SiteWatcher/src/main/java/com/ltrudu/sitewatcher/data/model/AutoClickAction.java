package com.ltrudu.sitewatcher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an auto-click action that can be executed when a page loads.
 * Actions can be either CLICK (click an element) or WAIT (pause for specified time).
 */
public class AutoClickAction {

    private String id;
    private ActionType type;
    private String label;
    private String selector;      // CSS selector for CLICK type
    private int waitSeconds;      // Wait duration for WAIT type
    private float tapX;           // X coordinate (0.0-1.0 percentage) for TAP_COORDINATES type
    private float tapY;           // Y coordinate (0.0-1.0 percentage) for TAP_COORDINATES type
    private boolean enabled;
    private boolean builtIn;
    private int order;

    /**
     * Default constructor.
     */
    public AutoClickAction() {
        this.id = UUID.randomUUID().toString();
        this.type = ActionType.CLICK;
        this.label = "";
        this.selector = "";
        this.waitSeconds = 1;
        this.tapX = 0f;
        this.tapY = 0f;
        this.enabled = true;
        this.builtIn = false;
        this.order = 0;
    }

    /**
     * Create a CLICK action.
     *
     * @param id       Unique identifier
     * @param label    User-friendly label
     * @param selector CSS selector to click
     * @param enabled  Whether the action is enabled
     * @param builtIn  Whether this is a built-in pattern
     * @param order    Execution order
     */
    public static AutoClickAction createClickAction(
            @NonNull String id,
            @NonNull String label,
            @NonNull String selector,
            boolean enabled,
            boolean builtIn,
            int order) {
        AutoClickAction action = new AutoClickAction();
        action.id = id;
        action.type = ActionType.CLICK;
        action.label = label;
        action.selector = selector;
        action.waitSeconds = 0;
        action.enabled = enabled;
        action.builtIn = builtIn;
        action.order = order;
        return action;
    }

    /**
     * Create a WAIT action.
     *
     * @param id          Unique identifier
     * @param label       User-friendly label
     * @param waitSeconds Duration to wait in seconds
     * @param enabled     Whether the action is enabled
     * @param order       Execution order
     */
    public static AutoClickAction createWaitAction(
            @NonNull String id,
            @NonNull String label,
            int waitSeconds,
            boolean enabled,
            int order) {
        AutoClickAction action = new AutoClickAction();
        action.id = id;
        action.type = ActionType.WAIT;
        action.label = label;
        action.selector = null;
        action.waitSeconds = Math.max(1, waitSeconds);
        action.enabled = enabled;
        action.builtIn = false;
        action.order = order;
        return action;
    }

    /**
     * Create a TAP_COORDINATES action.
     *
     * @param id      Unique identifier
     * @param label   User-friendly label
     * @param tapX    X coordinate as percentage (0.0-1.0) of WebView width
     * @param tapY    Y coordinate as percentage (0.0-1.0) of WebView height
     * @param enabled Whether the action is enabled
     * @param order   Execution order
     */
    public static AutoClickAction createTapCoordinatesAction(
            @NonNull String id,
            @NonNull String label,
            float tapX,
            float tapY,
            boolean enabled,
            int order) {
        AutoClickAction action = new AutoClickAction();
        action.id = id;
        action.type = ActionType.TAP_COORDINATES;
        action.label = label;
        action.selector = null;
        action.waitSeconds = 0;
        action.tapX = Math.max(0f, Math.min(1f, tapX));
        action.tapY = Math.max(0f, Math.min(1f, tapY));
        action.enabled = enabled;
        action.builtIn = false;
        action.order = order;
        return action;
    }

    // Getters and setters

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public ActionType getType() {
        return type;
    }

    public void setType(@NonNull ActionType type) {
        this.type = type;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public void setLabel(@NonNull String label) {
        this.label = label;
    }

    @Nullable
    public String getSelector() {
        return selector;
    }

    public void setSelector(@Nullable String selector) {
        this.selector = selector;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = Math.max(1, waitSeconds);
    }

    public float getTapX() {
        return tapX;
    }

    public void setTapX(float tapX) {
        this.tapX = Math.max(0f, Math.min(1f, tapX));
    }

    public float getTapY() {
        return tapY;
    }

    public void setTapY(float tapY) {
        this.tapY = Math.max(0f, Math.min(1f, tapY));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Get a display summary for the action.
     *
     * @return Summary string
     */
    @NonNull
    public String getSummary() {
        if (type == ActionType.WAIT) {
            return "Wait " + waitSeconds + "s";
        } else if (type == ActionType.TAP_COORDINATES) {
            int xPercent = Math.round(tapX * 100);
            int yPercent = Math.round(tapY * 100);
            return "Tap at (" + xPercent + "%, " + yPercent + "%)";
        } else {
            return selector != null ? selector : "";
        }
    }

    // JSON serialization

    /**
     * Convert this action to a JSON object.
     *
     * @return JSON representation
     * @throws JSONException if serialization fails
     */
    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type.name());
        json.put("label", label);
        json.put("selector", selector);
        json.put("waitSeconds", waitSeconds);
        json.put("tapX", tapX);
        json.put("tapY", tapY);
        json.put("enabled", enabled);
        json.put("builtIn", builtIn);
        json.put("order", order);
        return json;
    }

    /**
     * Create an action from a JSON object.
     *
     * @param json JSON object
     * @return AutoClickAction instance
     * @throws JSONException if parsing fails
     */
    @NonNull
    public static AutoClickAction fromJson(@NonNull JSONObject json) throws JSONException {
        AutoClickAction action = new AutoClickAction();
        action.id = json.optString("id", UUID.randomUUID().toString());
        action.type = ActionType.valueOf(json.optString("type", "CLICK"));
        action.label = json.optString("label", "");
        action.selector = json.optString("selector", null);
        action.waitSeconds = json.optInt("waitSeconds", 1);
        action.tapX = (float) json.optDouble("tapX", 0.0);
        action.tapY = (float) json.optDouble("tapY", 0.0);
        action.enabled = json.optBoolean("enabled", true);
        action.builtIn = json.optBoolean("builtIn", false);
        action.order = json.optInt("order", 0);
        return action;
    }

    /**
     * Convert a list of actions to a JSON string.
     *
     * @param actions List of actions
     * @return JSON string
     */
    @Nullable
    public static String toJsonString(@Nullable List<AutoClickAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray();
            for (AutoClickAction action : actions) {
                array.put(action.toJson());
            }
            return array.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Parse a list of actions from a JSON string.
     *
     * @param jsonString JSON string
     * @return List of actions (empty if parsing fails)
     */
    @NonNull
    public static List<AutoClickAction> fromJsonString(@Nullable String jsonString) {
        List<AutoClickAction> actions = new ArrayList<>();
        if (jsonString == null || jsonString.isEmpty()) {
            return actions;
        }
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                actions.add(fromJson(json));
            }
        } catch (JSONException e) {
            // Return empty list on error
        }
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoClickAction that = (AutoClickAction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Create a copy of this action with a new ID.
     *
     * @return Copy of the action
     */
    @NonNull
    public AutoClickAction copy() {
        AutoClickAction copy = new AutoClickAction();
        copy.id = UUID.randomUUID().toString();
        copy.type = this.type;
        copy.label = this.label;
        copy.selector = this.selector;
        copy.waitSeconds = this.waitSeconds;
        copy.tapX = this.tapX;
        copy.tapY = this.tapY;
        copy.enabled = this.enabled;
        copy.builtIn = false; // Copies are never built-in
        copy.order = this.order;
        return copy;
    }
}
