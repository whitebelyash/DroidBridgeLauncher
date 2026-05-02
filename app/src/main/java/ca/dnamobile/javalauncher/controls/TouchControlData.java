package ca.dnamobile.javalauncher.controls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

/** One visible on-screen control button. */
public final class TouchControlData {
    public static final int SPECIAL_KEYBOARD = -1;
    public static final int SPECIAL_TOGGLE_CONTROLS = -2;
    public static final int SPECIAL_MOUSE_LEFT = -3;
    public static final int SPECIAL_MOUSE_RIGHT = -4;
    public static final int SPECIAL_VIRTUAL_MOUSE = -5;
    public static final int SPECIAL_MOUSE_MIDDLE = -6;
    public static final int SPECIAL_SCROLL_UP = -7;
    public static final int SPECIAL_SCROLL_DOWN = -8;
    public static final int SPECIAL_MENU = -9;

    @NonNull public String id = UUID.randomUUID().toString();
    @NonNull public String label = "Button";
    @NonNull public String action = TouchControlActions.KEY;
    public int keyCode = 32;
    @NonNull public int[] keyCodes = new int[0];
    public int mouseButton = 0;
    public int scrollY = 0;
    public float x = 32f;
    public float y = 32f;
    public float width = 64f;
    public float height = 48f;
    public float sizePercent = 100f;
    public float opacity = 0.72f;
    public float cornerRadius = 16f;
    public float strokeWidth = 2f;
    public int strokeColor = 0x99FFFFFF;
    public int backgroundColor = 0x66000000;
    public boolean toggle;
    public boolean visibleInGame = true;
    public boolean visibleInMenu = true;
    public boolean joystickAbsolute;
    public boolean joystickForwardLock;

    @Nullable public String rawX;
    @Nullable public String rawY;

    @NonNull
    public static TouchControlData key(@NonNull String label, int keyCode, float x, float y, float width, float height) {
        TouchControlData data = new TouchControlData();
        data.label = label;
        data.action = TouchControlActions.KEY;
        data.keyCode = keyCode;
        data.keyCodes = new int[]{keyCode};
        data.x = x;
        data.y = y;
        data.width = width;
        data.height = height;
        return data;
    }

    @NonNull
    public static TouchControlData mouse(@NonNull String label, int mouseButton, float x, float y) {
        TouchControlData data = new TouchControlData();
        data.label = label;
        data.action = TouchControlActions.MOUSE;
        data.mouseButton = mouseButton;
        data.x = x;
        data.y = y;
        data.width = 58f;
        data.height = 58f;
        return data;
    }

    @NonNull
    public static TouchControlData joystick(@NonNull String label, float x, float y, float width, float height) {
        TouchControlData data = new TouchControlData();
        data.label = label;
        data.action = TouchControlActions.JOYSTICK;
        data.x = x;
        data.y = y;
        data.width = width;
        data.height = height;
        data.opacity = 0.55f;
        data.visibleInGame = true;
        data.visibleInMenu = false;
        data.cornerRadius = 999f;
        return data;
    }

    @NonNull
    public TouchControlData copy() {
        TouchControlData copy = new TouchControlData();
        copy.id = UUID.randomUUID().toString();
        copy.label = label;
        copy.action = action;
        copy.keyCode = keyCode;
        copy.keyCodes = keyCodes != null ? keyCodes.clone() : new int[0];
        copy.mouseButton = mouseButton;
        copy.scrollY = scrollY;
        copy.x = x;
        copy.y = y;
        copy.width = width;
        copy.height = height;
        copy.sizePercent = sizePercent;
        copy.opacity = opacity;
        copy.cornerRadius = cornerRadius;
        copy.strokeWidth = strokeWidth;
        copy.strokeColor = strokeColor;
        copy.backgroundColor = backgroundColor;
        copy.toggle = toggle;
        copy.visibleInGame = visibleInGame;
        copy.visibleInMenu = visibleInMenu;
        copy.joystickAbsolute = joystickAbsolute;
        copy.joystickForwardLock = joystickForwardLock;
        copy.rawX = rawX;
        copy.rawY = rawY;
        return copy;
    }

    @NonNull
    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label);
        json.put("action", action);
        json.put("keyCode", keyCode);
        JSONArray keys = new JSONArray();
        for (int code : normalizedKeyCodes()) keys.put(code);
        json.put("keyCodes", keys);
        json.put("mouseButton", mouseButton);
        json.put("scrollY", scrollY);
        json.put("x", x);
        json.put("y", y);
        json.put("width", width);
        json.put("height", height);
        json.put("sizePercent", sizePercent);
        json.put("opacity", opacity);
        json.put("cornerRadius", cornerRadius);
        json.put("strokeWidth", strokeWidth);
        json.put("strokeColor", strokeColor);
        json.put("backgroundColor", backgroundColor);
        json.put("toggle", toggle);
        json.put("visibleInGame", visibleInGame);
        json.put("visibleInMenu", visibleInMenu);
        json.put("joystickAbsolute", joystickAbsolute);
        json.put("joystickForwardLock", joystickForwardLock);
        if (rawX != null) json.put("rawX", rawX);
        if (rawY != null) json.put("rawY", rawY);
        return json;
    }

    @NonNull
    public static TouchControlData fromJson(@NonNull JSONObject json) {
        TouchControlData data = new TouchControlData();
        data.id = sanitizeId(json.optString("id", data.id));
        data.label = json.optString("label", json.optString("name", data.label));
        data.action = json.optString("action", data.action);
        data.keyCode = json.optInt("keyCode", data.keyCode);
        data.keyCodes = readKeyCodes(json.optJSONArray("keyCodes"), data.keyCode);
        data.mouseButton = json.optInt("mouseButton", data.mouseButton);
        data.scrollY = json.optInt("scrollY", data.scrollY);
        data.x = (float) json.optDouble("x", data.x);
        data.y = (float) json.optDouble("y", data.y);
        data.width = (float) json.optDouble("width", data.width);
        data.height = (float) json.optDouble("height", data.height);
        data.sizePercent = clampSizePercent((float) json.optDouble("sizePercent", data.sizePercent));
        data.opacity = (float) json.optDouble("opacity", data.opacity);
        data.cornerRadius = (float) json.optDouble("cornerRadius", data.cornerRadius);
        data.strokeWidth = (float) json.optDouble("strokeWidth", data.strokeWidth);
        data.strokeColor = json.optInt("strokeColor", data.strokeColor);
        data.backgroundColor = json.optInt("backgroundColor", json.optInt("bgColor", data.backgroundColor));
        data.toggle = json.optBoolean("toggle", json.optBoolean("isToggle", data.toggle));
        data.visibleInGame = json.optBoolean("visibleInGame", json.optBoolean("displayInGame", data.visibleInGame));
        data.visibleInMenu = json.optBoolean("visibleInMenu", json.optBoolean("displayInMenu", data.visibleInMenu));
        data.joystickAbsolute = json.optBoolean("joystickAbsolute", json.optBoolean("absolute", data.joystickAbsolute));
        data.joystickForwardLock = json.optBoolean("joystickForwardLock", json.optBoolean("forwardLock", data.joystickForwardLock));
        data.rawX = optNullableString(json, "rawX", optNullableString(json, "dynamicX", null));
        data.rawY = optNullableString(json, "rawY", optNullableString(json, "dynamicY", null));
        return data;
    }

    @NonNull
    public static TouchControlData fromPojavControl(@NonNull JSONObject json) {
        TouchControlData data = new TouchControlData();
        data.id = sanitizeId(json.optString("id", data.id));
        data.label = json.optString("name", json.optString("label", "Button"));
        data.width = (float) json.optDouble("width", 64d);
        data.height = (float) json.optDouble("height", 48d);
        data.opacity = (float) json.optDouble("opacity", 0.72d);
        data.cornerRadius = (float) json.optDouble("cornerRadius", data.cornerRadius);
        data.strokeWidth = (float) json.optDouble("strokeWidth", data.strokeWidth);
        data.strokeColor = json.optInt("strokeColor", data.strokeColor);
        data.backgroundColor = json.optInt("bgColor", json.optInt("backgroundColor", data.backgroundColor));
        data.toggle = json.optBoolean("isToggle", json.optBoolean("toggle", false));
        data.visibleInGame = json.optBoolean("displayInGame", json.optBoolean("visibleInGame", true));
        data.visibleInMenu = json.optBoolean("displayInMenu", json.optBoolean("visibleInMenu", true));
        data.rawX = optNullableString(json, "dynamicX", optNullableString(json, "rawX", null));
        data.rawY = optNullableString(json, "dynamicY", optNullableString(json, "rawY", null));

        int[] importedKeys = readKeyCodes(json.optJSONArray("keycodes"), 32);
        int firstKey = firstUsableKey(importedKeys, 32);
        applyImportedKey(data, firstKey, importedKeys);
        data.x = (float) json.optDouble("x", data.x);
        data.y = (float) json.optDouble("y", data.y);
        return data;
    }

    @NonNull
    public static TouchControlData fromPojavJoystick(@NonNull JSONObject json) {
        TouchControlData data = joystick(
                json.optString("name", json.optString("label", "Joystick")),
                (float) json.optDouble("x", 32d),
                (float) json.optDouble("y", 360d),
                (float) json.optDouble("width", 120d),
                (float) json.optDouble("height", 120d)
        );
        data.id = sanitizeId(json.optString("id", data.id));
        data.opacity = (float) json.optDouble("opacity", data.opacity);
        data.cornerRadius = (float) json.optDouble("cornerRadius", data.cornerRadius);
        data.strokeWidth = (float) json.optDouble("strokeWidth", data.strokeWidth);
        data.strokeColor = json.optInt("strokeColor", data.strokeColor);
        data.backgroundColor = json.optInt("bgColor", json.optInt("backgroundColor", data.backgroundColor));
        data.visibleInGame = json.optBoolean("displayInGame", true);
        data.visibleInMenu = json.optBoolean("displayInMenu", false);
        data.rawX = optNullableString(json, "dynamicX", null);
        data.rawY = optNullableString(json, "dynamicY", null);
        data.joystickAbsolute = json.optBoolean("absolute", false);
        data.joystickForwardLock = json.optBoolean("forwardLock", false);
        return data;
    }

    @NonNull
    public int[] normalizedKeyCodes() {
        if (keyCodes != null && keyCodes.length > 0) return keyCodes;
        return new int[]{keyCode};
    }

    public void setKeyCodes(@NonNull int[] codes) {
        keyCodes = codes.length == 0 ? new int[]{keyCode} : codes.clone();
        keyCode = keyCodes[0];
    }

    @NonNull
    private static int[] readKeyCodes(@Nullable JSONArray array, int fallback) {
        if (array == null || array.length() == 0) return new int[]{fallback};
        ArrayList<Integer> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            int value = array.optInt(i, Integer.MIN_VALUE);
            if (value == Integer.MIN_VALUE) continue;
            values.add(value);
        }
        if (values.isEmpty()) return new int[]{fallback};
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static int firstUsableKey(@NonNull int[] keycodes, int fallback) {
        for (int key : keycodes) {
            if (key != 0) return key;
        }
        return fallback;
    }

    @Nullable
    private static String optNullableString(@NonNull JSONObject json, @NonNull String key, @Nullable String fallback) {
        if (!json.has(key) || json.isNull(key)) return fallback;
        String value = json.optString(key, null);
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim()) ? fallback : value;
    }

    @NonNull
    private static String sanitizeId(@Nullable String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    private static float clampSizePercent(float value) {
        return Math.max(30f, Math.min(250f, value));
    }

    private static void applyImportedKey(@NonNull TouchControlData data, int key, @NonNull int[] allKeys) {
        switch (key) {
            case SPECIAL_TOGGLE_CONTROLS:
                data.action = TouchControlActions.TOGGLE_CONTROLS;
                return;
            case SPECIAL_MOUSE_LEFT:
                data.action = TouchControlActions.MOUSE;
                data.mouseButton = 0;
                return;
            case SPECIAL_MOUSE_RIGHT:
                data.action = TouchControlActions.MOUSE;
                data.mouseButton = 1;
                return;
            case SPECIAL_MOUSE_MIDDLE:
                data.action = TouchControlActions.MOUSE;
                data.mouseButton = 2;
                return;
            case SPECIAL_SCROLL_UP:
                data.action = TouchControlActions.SCROLL;
                data.scrollY = 1;
                return;
            case SPECIAL_SCROLL_DOWN:
                data.action = TouchControlActions.SCROLL;
                data.scrollY = -1;
                return;
            case SPECIAL_MENU:
                data.action = TouchControlActions.MENU;
                return;
            case SPECIAL_KEYBOARD:
                data.action = TouchControlActions.KEYBOARD;
                return;
            case SPECIAL_VIRTUAL_MOUSE:
                data.action = TouchControlActions.VIRTUAL_MOUSE;
                return;
            default:
                data.action = TouchControlActions.KEY;
                data.keyCode = key;
                data.keyCodes = allKeys;
        }
    }
}
