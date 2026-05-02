package ca.dnamobile.javalauncher.controls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Friendly key/mouse labels for the touch-control editor. */
final class TouchInputBinding {
    static final class Option {
        @NonNull final String label;
        final int value;

        Option(@NonNull String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final Option[] KEY_OPTIONS = new Option[]{
            new Option("W", 87),
            new Option("A", 65),
            new Option("S", 83),
            new Option("D", 68),
            new Option("Space / Jump", 32),
            new Option("Left Shift / Sneak", 340),
            new Option("Left Ctrl / Sprint", 341),
            new Option("Escape / Pause", 256),
            new Option("E / Inventory", 69),
            new Option("Q / Drop", 81),
            new Option("F / Swap Offhand", 70),
            new Option("T / Chat", 84),
            new Option("B", 66),
            new Option("C", 67),
            new Option("G", 71),
            new Option("H", 72),
            new Option("I", 73),
            new Option("J", 74),
            new Option("K", 75),
            new Option("L", 76),
            new Option("M", 77),
            new Option("N", 78),
            new Option("O", 79),
            new Option("P", 80),
            new Option("R", 82),
            new Option("U", 85),
            new Option("V", 86),
            new Option("X", 88),
            new Option("Y", 89),
            new Option("Z", 90),
            new Option("Slash / Command", 47),
            new Option("Tab / Player List", 258),
            new Option("Enter", 257),
            new Option("Backspace", 259),
            new Option("1 / Hotbar 1", 49),
            new Option("2 / Hotbar 2", 50),
            new Option("3 / Hotbar 3", 51),
            new Option("4 / Hotbar 4", 52),
            new Option("5 / Hotbar 5", 53),
            new Option("6 / Hotbar 6", 54),
            new Option("7 / Hotbar 7", 55),
            new Option("8 / Hotbar 8", 56),
            new Option("9 / Hotbar 9", 57),
            new Option("0", 48),
            new Option("Arrow Up", 265),
            new Option("Arrow Down", 264),
            new Option("Arrow Left", 263),
            new Option("Arrow Right", 262),
            new Option("F1", 290),
            new Option("F2 / Screenshot", 291),
            new Option("F3 / Debug", 292),
            new Option("F5 / Perspective", 294),
            new Option("Left Alt", 342),
            new Option("Right Shift", 344),
            new Option("Right Ctrl", 345)
    };

    private static final Option[] MOUSE_OPTIONS = new Option[]{
            new Option("Left click / Attack", 0),
            new Option("Right click / Use", 1),
            new Option("Middle click / Pick block", 2)
    };

    private static final Option[] SCROLL_OPTIONS = new Option[]{
            new Option("Scroll up", 1),
            new Option("Scroll down", -1)
    };

    private static final Option[] EMPTY_OPTIONS = new Option[]{
            new Option("No extra binding needed", 0)
    };

    private TouchInputBinding() {
    }

    @NonNull
    static String[] actionLabels() {
        return new String[]{
                "Keyboard key",
                "Mouse button",
                "Scroll wheel",
                "Open launcher menu",
                "Show / hide touch controls",
                "Open keyboard",
                "Joystick / WASD",
                "Toggle virtual cursor"
        };
    }

    @NonNull
    static String[] actionValues() {
        return new String[]{
                TouchControlActions.KEY,
                TouchControlActions.MOUSE,
                TouchControlActions.SCROLL,
                TouchControlActions.MENU,
                TouchControlActions.TOGGLE_CONTROLS,
                TouchControlActions.KEYBOARD,
                TouchControlActions.JOYSTICK,
                TouchControlActions.VIRTUAL_MOUSE
        };
    }

    static int actionIndex(@Nullable String action) {
        String[] values = actionValues();
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(action)) return i;
        }
        return 0;
    }

    @NonNull
    static Option[] optionsForAction(@Nullable String action) {
        if (TouchControlActions.MOUSE.equals(action)) return MOUSE_OPTIONS;
        if (TouchControlActions.SCROLL.equals(action)) return SCROLL_OPTIONS;
        if (TouchControlActions.KEY.equals(action)) return KEY_OPTIONS;
        if (TouchControlActions.JOYSTICK.equals(action)) return EMPTY_OPTIONS;
        if (TouchControlActions.VIRTUAL_MOUSE.equals(action)) return EMPTY_OPTIONS;
        return EMPTY_OPTIONS;
    }

    static int selectedOptionIndex(@Nullable String action, @NonNull TouchControlData data) {
        int value = data.keyCode;
        if (TouchControlActions.MOUSE.equals(action)) value = data.mouseButton;
        if (TouchControlActions.SCROLL.equals(action)) value = data.scrollY;

        Option[] options = optionsForAction(action);
        for (int i = 0; i < options.length; i++) {
            if (options[i].value == value) return i;
        }
        return 0;
    }

    static void applyOption(@NonNull TouchControlData data, @NonNull String action, @NonNull Option option) {
        data.action = action;
        if (TouchControlActions.KEY.equals(action)) {
            data.keyCode = option.value;
            data.keyCodes = new int[]{option.value};
        } else if (TouchControlActions.MOUSE.equals(action)) {
            data.mouseButton = option.value;
        } else if (TouchControlActions.SCROLL.equals(action)) {
            data.scrollY = option.value;
        }
    }

    @NonNull
    static String friendlyBinding(@NonNull TouchControlData data) {
        if (TouchControlActions.KEY.equals(data.action)) {
            return friendlyKeyCombo(data.normalizedKeyCodes());
        }

        Option[] options = optionsForAction(data.action);
        int index = selectedOptionIndex(data.action, data);
        if (index >= 0 && index < options.length) return options[index].label;
        if (TouchControlActions.MOUSE.equals(data.action)) return "Mouse button " + data.mouseButton;
        if (TouchControlActions.SCROLL.equals(data.action)) return "Scroll " + data.scrollY;
        if (TouchControlActions.JOYSTICK.equals(data.action)) return "Joystick / WASD";
        if (TouchControlActions.VIRTUAL_MOUSE.equals(data.action)) return "Toggle virtual cursor";
        return "No extra binding needed";
    }

    @NonNull
    static String friendlyKeyCombo(@NonNull int[] codes) {
        if (codes.length == 0) return "No keys bound";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            int code = codes[i];
            if (code == 0) continue;
            if (builder.length() > 0) builder.append(" + ");
            builder.append(labelForKeyCode(code));
        }
        return builder.length() == 0 ? "No keys bound" : builder.toString();
    }

    @NonNull
    static String labelForKeyCode(int keyCode) {
        for (Option option : KEY_OPTIONS) {
            if (option.value == keyCode) return option.label;
        }

        // Common GLFW keys not listed in the small editor spinner.
        if (keyCode >= 65 && keyCode <= 90) return String.valueOf((char) keyCode);
        if (keyCode >= 48 && keyCode <= 57) return String.valueOf((char) keyCode);

        switch (keyCode) {
            case 32: return "Space / Jump";
            case 256: return "Escape / Pause";
            case 257: return "Enter";
            case 258: return "Tab / Player List";
            case 259: return "Backspace";
            case 260: return "Insert";
            case 261: return "Delete";
            case 262: return "Arrow Right";
            case 263: return "Arrow Left";
            case 264: return "Arrow Down";
            case 265: return "Arrow Up";
            case 290: return "F1";
            case 291: return "F2 / Screenshot";
            case 292: return "F3 / Debug";
            case 293: return "F4";
            case 294: return "F5 / Perspective";
            case 340: return "Left Shift / Sneak";
            case 341: return "Left Ctrl / Sprint";
            case 342: return "Left Alt";
            case 344: return "Right Shift";
            case 345: return "Right Ctrl";
            default: return "Key " + keyCode;
        }
    }

    @NonNull
    static String joinCodes(@NonNull int[] codes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(codes[i]);
        }
        return builder.toString();
    }

    @NonNull
    static String[] optionLabels(@NonNull Option[] options) {
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = options[i].label;
        return labels;
    }

    static boolean isDefaultLabel(@Nullable String label) {
        if (label == null) return true;
        String value = label.trim();
        return value.isEmpty()
                || "Button".equalsIgnoreCase(value)
                || "Key".equalsIgnoreCase(value)
                || "Mouse".equalsIgnoreCase(value)
                || value.matches("Key\\s*-?\\d+")
                || value.matches("Mouse\\s*-?\\d+");
    }
}
