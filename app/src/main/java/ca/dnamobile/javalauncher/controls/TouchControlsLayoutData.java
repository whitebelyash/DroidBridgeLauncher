package ca.dnamobile.javalauncher.controls;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Serializable JavaLauncher touch-control layout. */
public final class TouchControlsLayoutData {
    public int version = 1;
    @NonNull public String name = "Touch Controls";
    /** Pojav/Zalith/Mojo dynamic formulas use px(...) / 100 * preferredScale. */
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
            data.version = root.optInt("version", 1);
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

        // Pojav/Zalith/Mojo/Amethyst derived layouts normally carry mControlDataList.
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

        // Defaults use dynamic formulas so they stay sane on phones, tablets, and
        // different display densities. Width/height are JavaLauncher layout units;
        // rawX/rawY formulas resolve to final Android pixels at draw time.
        TouchControlData keyboard = new TouchControlData();
        keyboard.label = "Keyboard";
        keyboard.action = TouchControlActions.KEYBOARD;
        keyboard.width = 80;
        keyboard.height = 30;
        keyboard.rawX = "${margin} * 3 + ${width} * 2";
        keyboard.rawY = "${margin}";
        data.controls.add(keyboard);

        TouchControlData chat = TouchControlData.key("Chat", 84, 0, 0, 80, 30);
        chat.rawX = "${margin} * 2 + ${width}";
        chat.rawY = "${margin}";
        data.controls.add(chat);

        TouchControlData debug = TouchControlData.key("Debug", 292, 0, 0, 80, 30);
        debug.rawX = "${margin}";
        debug.rawY = "${margin} * 2 + ${height}";
        data.controls.add(debug);

        TouchControlData perspective = TouchControlData.key("3rd", 294, 0, 0, 80, 30);
        perspective.rawX = "${margin} * 2 + ${width}";
        perspective.rawY = "${margin} * 2 + ${height}";
        data.controls.add(perspective);

        TouchControlData esc = TouchControlData.key("Esc", 256, 0, 0, 80, 30);
        esc.rawX = "${margin}";
        esc.rawY = "${margin}";
        data.controls.add(esc);

        TouchControlData w = TouchControlData.key("▲", 87, 0, 0, 50, 50);
        w.rawX = "${margin} * 2 + ${width}";
        w.rawY = "${bottom} - ${margin} * 3 - ${height} * 2";
        data.controls.add(w);

        TouchControlData a = TouchControlData.key("◀", 65, 0, 0, 50, 50);
        a.rawX = "${margin}";
        a.rawY = "${bottom} - ${margin} * 2 - ${height}";
        data.controls.add(a);

        TouchControlData s = TouchControlData.key("▼", 83, 0, 0, 50, 50);
        s.rawX = "${margin} * 2 + ${width}";
        s.rawY = "${bottom} - ${margin}";
        data.controls.add(s);

        TouchControlData d = TouchControlData.key("▶", 68, 0, 0, 50, 50);
        d.rawX = "${margin} * 3 + ${width} * 2";
        d.rawY = "${bottom} - ${margin} * 2 - ${height}";
        data.controls.add(d);

        TouchControlData sneak = TouchControlData.key("◇", 340, 0, 0, 50, 50);
        sneak.toggle = true;
        sneak.rawX = "${margin} * 2 + ${width}";
        sneak.rawY = "${bottom} - ${margin} * 4 - ${height} * 3";
        data.controls.add(sneak);

        TouchControlData jump = TouchControlData.key("⬛", 32, 0, 0, 50, 50);
        jump.rawX = "${right} - ${margin} * 2 - ${width}";
        jump.rawY = "${bottom} - ${margin} * 2 - ${height}";
        data.controls.add(jump);

        TouchControlData inventory = TouchControlData.key("Inv", 69, 0, 0, 50, 50);
        inventory.rawX = "${right} - ${margin}";
        inventory.rawY = "${bottom} - ${margin}";
        data.controls.add(inventory);

        TouchControlData hit = TouchControlData.mouse("Hit", 0, 0, 0);
        hit.rawX = "${right} - ${margin} * 3 - ${width} * 2";
        hit.rawY = "${bottom} - ${margin} * 4 - ${height} * 3";
        data.controls.add(hit);

        TouchControlData use = TouchControlData.mouse("Use", 1, 0, 0);
        use.rawX = "${right} - ${margin}";
        use.rawY = "${bottom} - ${margin} * 4 - ${height} * 3";
        data.controls.add(use);

        TouchControlData mouse = new TouchControlData();
        mouse.label = "Mouse";
        mouse.action = TouchControlActions.VIRTUAL_MOUSE;
        mouse.width = 80;
        mouse.height = 30;
        mouse.rawX = "${right}";
        mouse.rawY = "${margin}";
        data.controls.add(mouse);

        return data;
    }}
