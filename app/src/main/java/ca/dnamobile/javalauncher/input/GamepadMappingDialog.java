package ca.dnamobile.javalauncher.input;

import android.app.Activity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import ca.dnamobile.javalauncher.settings.LauncherPreferences;
import ca.dnamobile.javalauncher.controls.ControlsPreferences;

import java.util.EnumMap;
import java.util.Map;

/**
 * Simple Java-only controller mapping dialog.
 */
public final class GamepadMappingDialog {
    @Nullable private static AlertDialog activeDialog;
    public interface OnSettingsSavedListener {
        void onSettingsSaved();
    }

    private GamepadMappingDialog() {
    }

    public static void show(@NonNull Activity activity) {
        show(activity, null);
    }

    public static void show(@NonNull Activity activity, OnSettingsSavedListener listener) {
        GamepadMappingStore store = GamepadMappingStore.get(activity);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 16);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView info = new TextView(activity);
        info.setText("In-game Button Overlay\n\n"
                + "Minecraft Java does not draw an Android cursor for us. "
                + "Show cursor overlay draws a launcher-side mouse pointer for menus. "
                + "Turn Force in-game mappings ON only after entering a world if camera/WASD input does not start. "
                + "The floating settings button can also hide/show this overlay button and the left-side log output.");
        info.setTextSize(16);
        root.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        CheckBox forceGameMode = new CheckBox(activity);
        forceGameMode.setText("Force in-game mappings");
        forceGameMode.setChecked(store.isForceGameMode());
        root.addView(forceGameMode);

        CheckBox showCursorOverlay = new CheckBox(activity);
        showCursorOverlay.setText("Show cursor overlay in menus");
        showCursorOverlay.setChecked(store.isShowCursorOverlay());
        root.addView(showCursorOverlay);

        CheckBox showFloatingSettingsButton = new CheckBox(activity);
        showFloatingSettingsButton.setText("Show floating settings button");
        showFloatingSettingsButton.setChecked(LauncherPreferences.isShowInGameSettingsButton(activity));
        root.addView(showFloatingSettingsButton);

        CheckBox showLogOverlay = new CheckBox(activity);
        showLogOverlay.setText("Show latest log on the left side");
        showLogOverlay.setChecked(LauncherPreferences.isShowGameLogOverlay(activity));
        root.addView(showLogOverlay);

        TextView menuSensitivityLabel = addSensitivityControl(
                activity,
                root,
                "Menu cursor sensitivity",
                store.getMenuCursorSensitivity()
        );
        SeekBar menuSensitivity = addSensitivitySeekBar(activity, root, store.getMenuCursorSensitivity(), menuSensitivityLabel, "Menu cursor sensitivity");

        TextView gameSensitivityLabel = addSensitivityControl(
                activity,
                root,
                "In-game camera sensitivity",
                store.getGameCameraSensitivity()
        );
        SeekBar gameSensitivity = addSensitivitySeekBar(activity, root, store.getGameCameraSensitivity(), gameSensitivityLabel, "In-game camera sensitivity");

        TextView hotbarHeader = new TextView(activity);
        hotbarHeader.setText("Hotbar touch hitbox");
        hotbarHeader.setTextSize(18);
        hotbarHeader.setGravity(Gravity.START);
        hotbarHeader.setPadding(0, dp(activity, 16), 0, dp(activity, 6));
        root.addView(hotbarHeader);

        TextView hotbarInfo = new TextView(activity);
        hotbarInfo.setText("Turn on the debug box to see the launcher hotbar touch area while in game. "
                + "If Minecraft GUI Scale is Auto or 4 and taps are unreliable, set GUI scale override to 4. "
                + "Use X/Y offset and vertical padding to line it up without rebuilding.");
        hotbarInfo.setTextSize(14);
        root.addView(hotbarInfo);

        CheckBox showHotbarHitbox = new CheckBox(activity);
        showHotbarHitbox.setText("Show hotbar hitbox debug box");
        showHotbarHitbox.setChecked(ControlsPreferences.isHotbarHitboxDebugEnabled(activity));
        root.addView(showHotbarHitbox);

        String[] guiScaleLabels = new String[]{"Auto estimate", "2", "3", "4", "5", "6", "7", "8"};
        int[] guiScaleValues = new int[]{0, 2, 3, 4, 5, 6, 7, 8};
        TextView guiScaleLabel = new TextView(activity);
        guiScaleLabel.setText("GUI scale override");
        guiScaleLabel.setTextSize(16);
        guiScaleLabel.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        root.addView(guiScaleLabel);

        Spinner guiScaleSpinner = new Spinner(activity);
        ArrayAdapter<String> guiScaleAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, guiScaleLabels);
        guiScaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        guiScaleSpinner.setAdapter(guiScaleAdapter);
        int selectedGuiScale = 0;
        int currentGuiScale = ControlsPreferences.getHotbarGuiScaleOverride(activity);
        for (int i = 0; i < guiScaleValues.length; i++) {
            if (guiScaleValues[i] == currentGuiScale) {
                selectedGuiScale = i;
                break;
            }
        }
        guiScaleSpinner.setSelection(selectedGuiScale);
        root.addView(guiScaleSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView hotbarWidthLabel = addFloatControl(activity, root, "Hitbox width GUI px", ControlsPreferences.getHotbarWidthGui(activity));
        SeekBar hotbarWidth = addFloatSeekBar(activity, root, 90, 260, ControlsPreferences.getHotbarWidthGui(activity), hotbarWidthLabel, "Hitbox width GUI px");

        TextView hotbarHeightLabel = addFloatControl(activity, root, "Hitbox height GUI px", ControlsPreferences.getHotbarHeightGui(activity));
        SeekBar hotbarHeight = addFloatSeekBar(activity, root, 12, 60, ControlsPreferences.getHotbarHeightGui(activity), hotbarHeightLabel, "Hitbox height GUI px");

        TextView hotbarXOffsetLabel = addFloatControl(activity, root, "X offset dp", ControlsPreferences.getHotbarXOffsetDp(activity));
        SeekBar hotbarXOffset = addFloatSeekBar(activity, root, -160, 160, ControlsPreferences.getHotbarXOffsetDp(activity), hotbarXOffsetLabel, "X offset dp");

        TextView hotbarYOffsetLabel = addFloatControl(activity, root, "Y offset dp", ControlsPreferences.getHotbarYOffsetDp(activity));
        SeekBar hotbarYOffset = addFloatSeekBar(activity, root, -80, 160, ControlsPreferences.getHotbarYOffsetDp(activity), hotbarYOffsetLabel, "Y offset dp");

        TextView hotbarPaddingLabel = addFloatControl(activity, root, "Vertical padding dp", ControlsPreferences.getHotbarVerticalPaddingDp(activity));
        SeekBar hotbarPadding = addFloatSeekBar(activity, root, 0, 80, ControlsPreferences.getHotbarVerticalPaddingDp(activity), hotbarPaddingLabel, "Vertical padding dp");

        Map<GamepadButton, Spinner> gameSpinners = new EnumMap<>(GamepadButton.class);
        Map<GamepadButton, Spinner> menuSpinners = new EnumMap<>(GamepadButton.class);

        addSection(activity, root, "In-game mappings", true, store, gameSpinners);
        addSection(activity, root, "Menu mappings", false, store, menuSpinners);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("In-game Button Overlay")
                .setView(scrollView)
                .setPositiveButton("Save", (dialogInterface, which) -> {
                    store.setForceGameMode(forceGameMode.isChecked());
                    store.setShowCursorOverlay(showCursorOverlay.isChecked());
                    LauncherPreferences.setShowInGameSettingsButton(activity, showFloatingSettingsButton.isChecked());
                    LauncherPreferences.setShowGameLogOverlay(activity, showLogOverlay.isChecked());
                    store.setMenuCursorSensitivity(progressToSensitivity(menuSensitivity.getProgress()));
                    store.setGameCameraSensitivity(progressToSensitivity(gameSensitivity.getProgress()));
                    ControlsPreferences.setHotbarHitboxDebugEnabled(activity, showHotbarHitbox.isChecked());
                    ControlsPreferences.setHotbarGuiScaleOverride(activity, guiScaleValues[Math.max(0, guiScaleSpinner.getSelectedItemPosition())]);
                    ControlsPreferences.setHotbarWidthGui(activity, progressToFloat(hotbarWidth.getProgress(), 90));
                    ControlsPreferences.setHotbarHeightGui(activity, progressToFloat(hotbarHeight.getProgress(), 12));
                    ControlsPreferences.setHotbarXOffsetDp(activity, progressToFloat(hotbarXOffset.getProgress(), -160));
                    ControlsPreferences.setHotbarYOffsetDp(activity, progressToFloat(hotbarYOffset.getProgress(), -80));
                    ControlsPreferences.setHotbarVerticalPaddingDp(activity, progressToFloat(hotbarPadding.getProgress(), 0));
                    saveSection(store, true, gameSpinners);
                    saveSection(store, false, menuSpinners);
                    if (listener != null) listener.onSettingsSaved();
                })
                .setNeutralButton("Reset defaults", (dialogInterface, which) -> {
                    store.resetDefaults();
                    ControlsPreferences.resetHotbarHitboxSettings(activity);
                    if (listener != null) listener.onSettingsSaved();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        activeDialog = dialog;
        dialog.setOnDismissListener(dismissed -> {
            setActiveDialogPreviewAlpha(false);
            activeDialog = null;
        });
        dialog.show();
    }

    @NonNull
    private static TextView addFloatControl(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String title,
            float value
    ) {
        TextView label = new TextView(activity);
        label.setText(title + ": " + Math.round(value));
        label.setTextSize(16);
        label.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        root.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return label;
    }

    @NonNull
    private static SeekBar addFloatSeekBar(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            int min,
            int max,
            float value,
            @NonNull TextView label,
            @NonNull String title
    ) {
        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(Math.max(1, max - min));
        seekBar.setProgress(floatToProgress(value, min, max));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + ": " + Math.round(progressToFloat(progress, min)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                setActiveDialogPreviewAlpha(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setActiveDialogPreviewAlpha(false);
            }
        });
        root.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return seekBar;
    }

    private static int floatToProgress(float value, int min, int max) {
        return Math.max(0, Math.min(max - min, Math.round(value) - min));
    }

    private static float progressToFloat(int progress, int min) {
        return min + progress;
    }

    @NonNull
    private static TextView addSensitivityControl(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String title,
            int sensitivity
    ) {
        TextView label = new TextView(activity);
        label.setText(title + ": " + sensitivity + "%");
        label.setTextSize(16);
        label.setPadding(0, dp(activity, 12), 0, dp(activity, 2));
        root.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return label;
    }

    @NonNull
    private static SeekBar addSensitivitySeekBar(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            int sensitivity,
            @NonNull TextView label,
            @NonNull String title
    ) {
        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(GamepadMappingStore.MAX_SENSITIVITY - GamepadMappingStore.MIN_SENSITIVITY);
        seekBar.setProgress(sensitivityToProgress(sensitivity));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(title + ": " + progressToSensitivity(progress) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                setActiveDialogPreviewAlpha(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setActiveDialogPreviewAlpha(false);
            }
        });
        root.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return seekBar;
    }

    private static void setActiveDialogPreviewAlpha(boolean previewing) {
        AlertDialog dialog = activeDialog;
        if (dialog == null || dialog.getWindow() == null) return;
        dialog.getWindow().setDimAmount(previewing ? 0.02f : 0.32f);
        dialog.getWindow().getDecorView().setAlpha(previewing ? 0.12f : 1.0f);
    }

    private static int sensitivityToProgress(int sensitivity) {
        return Math.max(0, Math.min(
                GamepadMappingStore.MAX_SENSITIVITY - GamepadMappingStore.MIN_SENSITIVITY,
                sensitivity - GamepadMappingStore.MIN_SENSITIVITY
        ));
    }

    private static int progressToSensitivity(int progress) {
        return GamepadMappingStore.MIN_SENSITIVITY + progress;
    }

    private static void addSection(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String title,
            boolean gameMode,
            @NonNull GamepadMappingStore store,
            @NonNull Map<GamepadButton, Spinner> out
    ) {
        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextSize(18);
        header.setGravity(Gravity.START);
        int top = dp(activity, 16);
        header.setPadding(0, top, 0, dp(activity, 6));
        root.addView(header);

        ArrayAdapter<GamepadAction> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                GamepadAction.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (GamepadButton button : GamepadButton.values()) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(activity, 3), 0, dp(activity, 3));

            TextView label = new TextView(activity);
            label.setText(button.toString());
            label.setTextSize(14);

            Spinner spinner = new Spinner(activity);
            spinner.setAdapter(adapter);
            spinner.setSelection(store.getButtonAction(button, gameMode).ordinal());

            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f));

            root.addView(row);
            out.put(button, spinner);
        }
    }

    private static void saveSection(
            @NonNull GamepadMappingStore store,
            boolean gameMode,
            @NonNull Map<GamepadButton, Spinner> spinners
    ) {
        for (Map.Entry<GamepadButton, Spinner> entry : spinners.entrySet()) {
            Object selected = entry.getValue().getSelectedItem();
            if (selected instanceof GamepadAction) {
                store.setButtonAction(entry.getKey(), (GamepadAction) selected, gameMode);
            }
        }
    }

    private static int dp(@NonNull Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
