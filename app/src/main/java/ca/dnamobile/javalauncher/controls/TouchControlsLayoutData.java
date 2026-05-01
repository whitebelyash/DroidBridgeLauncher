package ca.dnamobile.javalauncher.controls;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Serializable JavaLauncher touch-control layout. */
public final class TouchControlsLayoutData {
    public int version = 2;
    @NonNull public String name = "Touch Controls";
    /** Zalith/Pojav-style layouts store dynamicX/dynamicY formulas using px(...) / 100 * preferred_scale. */
    public float preferredScale = 100f;
    @NonNull public final List<TouchControlData> controls = new ArrayList<>();

    @NonNull
    public JSONObject toJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "JavaLauncherTouchControls");
        root.put("version", version);
        root.put("name", name);
        root.put("preferredScale", preferredScale);
        JSONArray array = new JSONArray();
        for (TouchControlData control : controls) {
            array.put(control.toJson());
        }
        root.put("controls", array);
        return root;
    }

    @NonNull
    public static TouchControlsLayoutData fromJson(@NonNull JSONObject root) throws Exception {
        if (root.has("controls")) {
            TouchControlsLayoutData data = new TouchControlsLayoutData();
            data.version = root.optInt("version", 2);
            data.name = root.optString("name", "Touch Controls");
            data.preferredScale = (float) root.optDouble("preferredScale", root.optDouble("scaledAt", 100d));
            JSONArray controls = root.optJSONArray("controls");
            if (controls != null) {
                for (int i = 0; i < controls.length(); i++) {
                    JSONObject object = controls.optJSONObject(i);
                    if (object != null) data.controls.add(TouchControlData.fromJson(object));
                }
            }
            return data;
        }

        // Pojav/Zalith/Mojo/Amethyst-derived layouts normally carry mControlDataList.
        // Zalith also has mJoystickDataList and mDrawerDataList, so import those too.
        if (root.has("mControlDataList") || root.has("mJoystickDataList") || root.has("mDrawerDataList")) {
            return fromPojavLikeJson(root);
        }

        // Some launchers export a flat buttons array. Treat it as best-effort.
        if (root.has("buttons")) {
            TouchControlsLayoutData data = new TouchControlsLayoutData();
            data.name = root.optString("name", "Imported Controls");
            data.preferredScale = (float) root.optDouble("preferredScale", root.optDouble("scaledAt", 100d));
            JSONArray buttons = root.optJSONArray("buttons");
            if (buttons != null) {
                for (int i = 0; i < buttons.length(); i++) {
                    JSONObject object = buttons.optJSONObject(i);
                    if (object != null) data.controls.add(TouchControlData.fromJson(object));
                }
            }
            return data;
        }

        throw new IllegalArgumentException("Unsupported touch control layout format.");
    }

    @NonNull
    private static TouchControlsLayoutData fromPojavLikeJson(@NonNull JSONObject root) {
        TouchControlsLayoutData data = new TouchControlsLayoutData();
        data.name = root.optString("name", "Imported Pojav/Zalith Controls");
        data.preferredScale = (float) root.optDouble("scaledAt", root.optDouble("preferredScale", 100d));

        JSONArray controls = root.optJSONArray("mControlDataList");
        if (controls != null) {
            for (int i = 0; i < controls.length(); i++) {
                JSONObject object = controls.optJSONObject(i);
                if (object != null) data.controls.add(TouchControlData.fromPojavControl(object));
            }
        }

        JSONArray joysticks = root.optJSONArray("mJoystickDataList");
        if (joysticks != null) {
            for (int i = 0; i < joysticks.length(); i++) {
                JSONObject object = joysticks.optJSONObject(i);
                if (object != null) data.controls.add(TouchControlData.fromPojavJoystick(object));
            }
        }

        // Drawers are more complex in Zalith/Mojo because sub-buttons are revealed by
        // a parent drawer. JavaLauncher's first-pass layout does not have drawer state,
        // so import the parent and sub-buttons as normal buttons rather than dropping them.
        JSONArray drawers = root.optJSONArray("mDrawerDataList");
        if (drawers != null) {
            for (int i = 0; i < drawers.length(); i++) {
                JSONObject drawer = drawers.optJSONObject(i);
                if (drawer == null) continue;

                JSONObject properties = drawer.optJSONObject("properties");
                if (properties != null) data.controls.add(TouchControlData.fromPojavControl(properties));

                JSONArray subButtons = drawer.optJSONArray("buttonProperties");
                if (subButtons != null) {
                    for (int j = 0; j < subButtons.length(); j++) {
                        JSONObject sub = subButtons.optJSONObject(j);
                        if (sub != null) data.controls.add(TouchControlData.fromPojavControl(sub));
                    }
                }
            }
        }

        return data;
    }

    @NonNull
    public static TouchControlsLayoutData defaultLayout() {
        TouchControlsLayoutData data = new TouchControlsLayoutData();
        data.name = "Default Touch Controls";
        data.controls.add(TouchControlData.key("W", 87, 96, 520, 56, 48));
        data.controls.add(TouchControlData.key("A", 65, 36, 580, 56, 48));
        data.controls.add(TouchControlData.key("S", 83, 96, 580, 56, 48));
        data.controls.add(TouchControlData.key("D", 68, 156, 580, 56, 48));
        data.controls.add(TouchControlData.key("Jump", 32, 1560, 590, 96, 58));
        data.controls.add(TouchControlData.key("Sneak", 340, 1448, 590, 96, 58));
        data.controls.add(TouchControlData.key("Inv", 69, 1672, 446, 78, 54));
        data.controls.add(TouchControlData.key("Esc", 256, 34, 34, 72, 52));
        data.controls.add(TouchControlData.mouse("Hit", 0, 1630, 300));
        data.controls.add(TouchControlData.mouse("Use", 1, 1740, 300));

        TouchControlData menu = new TouchControlData();
        menu.label = "Menu";
        menu.action = TouchControlActions.MENU;
        menu.x = 118;
        menu.y = 34;
        menu.width = 82;
        menu.height = 52;
        data.controls.add(menu);
        return data;
    }
}
