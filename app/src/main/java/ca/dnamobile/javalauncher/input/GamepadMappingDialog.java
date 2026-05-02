package ca.dnamobile.javalauncher.input;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
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

import ca.dnamobile.javalauncher.controls.ControlsPreferences;
import ca.dnamobile.javalauncher.settings.LauncherPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java-only controller mapping dialog.
 *
 * Kept programmatic so this can be dropped into the current launcher without
 * adding another XML file. The layout uses card sections to match the cleaner
 * instance/create dialogs and collapses the long mapping lists behind headers.
 */
public final class GamepadMappingDialog {
    @Nullable private static AlertDialog activeDialog;

    private static final int COLOR_DIALOG_BG = Color.rgb(30, 34, 42);
    private static final int COLOR_CARD_BG = Color.rgb(38, 43, 53);
    private static final int COLOR_CARD_BG_PRESSED = Color.rgb(43, 49, 60);
    private static final int COLOR_CARD_STROKE = Color.rgb(54, 61, 74);
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(238, 241, 248);
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(198, 204, 216);
    private static final int COLOR_TEXT_MUTED = Color.rgb(150, 159, 176);
    private static final int COLOR_ACCENT = Color.rgb(37, 211, 128);
    private static final int COLOR_ACCENT_MUTED = Color.rgb(86, 135, 110);

    public interface OnSettingsSavedListener {
        void onSettingsSaved();
    }

    private GamepadMappingDialog() {
    }

    public static void show(@NonNull Activity activity) {
        show(activity, null);
    }

    public static void show(@NonNull Activity activity, @Nullable OnSettingsSavedListener listener) {
        GamepadMappingStore store = GamepadMappingStore.get(activity);

        final boolean originalHotbarDebug = ControlsPreferences.isHotbarHitboxDebugEnabled(activity);
        final boolean[] saved = new boolean[]{false};

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_DIALOG_BG);
        scrollView.setBackgroundColor(COLOR_DIALOG_BG);
        int padding = dp(activity, 18);
        root.setPadding(padding, padding, padding, dp(activity, 8));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(activity);
        title.setText("In-game Button Overlay");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 6));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView info = new TextView(activity);
        info.setText("Configure the launcher-side controller bridge, visual menu cursor, hotbar touch hitbox, and floating in-game overlay. "
                + "Mappings are saved to the selected controller profile when Android reports a physical controller.");
        info.setTextSize(14);
        info.setTextColor(COLOR_TEXT_SECONDARY);
        info.setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 12));
        root.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Map<GamepadButton, Spinner> gameSpinners = new EnumMap<>(GamepadButton.class);
        Map<GamepadButton, Spinner> menuSpinners = new EnumMap<>(GamepadButton.class);

        // Controller profile card.
        LinearLayout profileCard = addCard(activity, root);
        addCardTitle(activity, profileCard, "Controller profile");
        TextView profileInfo = addInfoText(activity, profileCard,
                "Use the attached controller profile below. If Android cannot identify a controller, the default profile is used.");

        List<DeviceProfile> profiles = discoverProfiles(activity, store);
        Spinner profileSpinner = new Spinner(activity);
        ArrayAdapter<DeviceProfile> profileAdapter = darkAdapter(activity, profiles);
        profileSpinner.setAdapter(profileAdapter);
        profileSpinner.setSelection(preferredProfileIndex(profiles, store.getActiveProfileKey()));
        profileCard.addView(profileSpinner, matchWrapWithTopMargin(activity, 6));
        profileInfo.setVisibility(profiles.size() > 1 ? View.VISIBLE : View.GONE);

        // General overlay card.
        LinearLayout overlayCard = addCard(activity, root);
        addCardTitle(activity, overlayCard, "Overlay behavior");

        CheckBox forceGameMode = addCheckBox(activity, overlayCard, "Force in-game mappings", store.isForceGameMode());
        addSmallHint(activity, overlayCard, "Only turn this on after entering a world if camera/WASD input does not start automatically.");

        CheckBox showCursorOverlay = addCheckBox(activity, overlayCard, "Show controller cursor overlay in menus", store.isShowCursorOverlay());
        addSmallHint(activity, overlayCard, "Visual only. It no longer becomes a touch target, so screen taps and Touch Controller input pass through to Minecraft.");
        CheckBox showFloatingSettingsButton = addCheckBox(activity, overlayCard, "Show floating settings button", LauncherPreferences.isShowInGameSettingsButton(activity));
        CheckBox showLogOverlay = addCheckBox(activity, overlayCard, "Show latest log on the left side", LauncherPreferences.isShowGameLogOverlay(activity));

        TextView menuSensitivityLabel = addSensitivityControl(
                activity,
                overlayCard,
                "Menu cursor sensitivity",
                store.getMenuCursorSensitivity()
        );
        SeekBar menuSensitivity = addSensitivitySeekBar(activity, overlayCard, store.getMenuCursorSensitivity(), menuSensitivityLabel, "Menu cursor sensitivity");

        TextView gameSensitivityLabel = addSensitivityControl(
                activity,
                overlayCard,
                "In-game camera sensitivity",
                store.getGameCameraSensitivity()
        );
        SeekBar gameSensitivity = addSensitivitySeekBar(activity, overlayCard, store.getGameCameraSensitivity(), gameSensitivityLabel, "In-game camera sensitivity");

        // Hotbar card.
        LinearLayout hotbarCard = addCard(activity, root);
        addCardTitle(activity, hotbarCard, "Hotbar touch hitbox");
        addInfoText(activity, hotbarCard,
                "Turn on the debug box to see the launcher hotbar touch area while in game. "
                        + "If Minecraft GUI Scale is Auto or 4 and taps are unreliable, set GUI scale override to 4.");

        CheckBox showHotbarHitbox = addCheckBox(activity, hotbarCard, "Show hotbar hitbox debug box", originalHotbarDebug);

        String[] guiScaleLabels = new String[]{"Auto estimate", "2", "3", "4", "5", "6", "7", "8"};
        int[] guiScaleValues = new int[]{0, 2, 3, 4, 5, 6, 7, 8};
        TextView guiScaleLabel = addPlainLabel(activity, hotbarCard, "GUI scale override");
        Spinner guiScaleSpinner = new Spinner(activity);
        ArrayAdapter<String> guiScaleAdapter = darkAdapter(activity, Arrays.asList(guiScaleLabels));
        guiScaleSpinner.setAdapter(guiScaleAdapter);
        guiScaleSpinner.setSelection(findGuiScaleIndex(guiScaleValues, ControlsPreferences.getHotbarGuiScaleOverride(activity)));
        hotbarCard.addView(guiScaleSpinner, matchWrapWithTopMargin(activity, 2));

        TextView hotbarWidthLabel = addFloatControl(activity, hotbarCard, "Hitbox width GUI px", ControlsPreferences.getHotbarWidthGui(activity));
        SeekBar hotbarWidth = addFloatSeekBar(activity, hotbarCard, 90, 260, ControlsPreferences.getHotbarWidthGui(activity), hotbarWidthLabel, "Hitbox width GUI px");

        TextView hotbarHeightLabel = addFloatControl(activity, hotbarCard, "Hitbox height GUI px", ControlsPreferences.getHotbarHeightGui(activity));
        SeekBar hotbarHeight = addFloatSeekBar(activity, hotbarCard, 12, 60, ControlsPreferences.getHotbarHeightGui(activity), hotbarHeightLabel, "Hitbox height GUI px");

        TextView hotbarXOffsetLabel = addFloatControl(activity, hotbarCard, "X offset dp", ControlsPreferences.getHotbarXOffsetDp(activity));
        SeekBar hotbarXOffset = addFloatSeekBar(activity, hotbarCard, -160, 160, ControlsPreferences.getHotbarXOffsetDp(activity), hotbarXOffsetLabel, "X offset dp");

        TextView hotbarYOffsetLabel = addFloatControl(activity, hotbarCard, "Y offset dp", ControlsPreferences.getHotbarYOffsetDp(activity));
        SeekBar hotbarYOffset = addFloatSeekBar(activity, hotbarCard, -80, 160, ControlsPreferences.getHotbarYOffsetDp(activity), hotbarYOffsetLabel, "Y offset dp");

        TextView hotbarPaddingLabel = addFloatControl(activity, hotbarCard, "Vertical padding dp", ControlsPreferences.getHotbarVerticalPaddingDp(activity));
        SeekBar hotbarPadding = addFloatSeekBar(activity, hotbarCard, 0, 80, ControlsPreferences.getHotbarVerticalPaddingDp(activity), hotbarPaddingLabel, "Vertical padding dp");

        // Mapping cards. Collapsed by default because the button lists are long.
        LinearLayout menuMappingCard = addCard(activity, root);
        LinearLayout menuMappingContent = new LinearLayout(activity);
        menuMappingContent.setOrientation(LinearLayout.VERTICAL);
        addCollapsibleHeader(activity, menuMappingCard, "Menu mappings", menuMappingContent, false);
        addInfoText(activity, menuMappingContent,
                "Used in Minecraft menus. D-pad cursor movement now only repeats when the selected D-pad action is a Cursor action.");
        addSection(activity, menuMappingContent, false, store, selectedProfileKey(profiles, profileSpinner), menuSpinners);
        menuMappingCard.addView(menuMappingContent);

        LinearLayout gameMappingCard = addCard(activity, root);
        LinearLayout gameMappingContent = new LinearLayout(activity);
        gameMappingContent.setOrientation(LinearLayout.VERTICAL);
        addCollapsibleHeader(activity, gameMappingCard, "In-game mappings", gameMappingContent, false);
        addInfoText(activity, gameMappingContent,
                "Used while Minecraft has grabbed the mouse or when Force in-game mappings is enabled.");
        addSection(activity, gameMappingContent, true, store, selectedProfileKey(profiles, profileSpinner), gameSpinners);
        gameMappingCard.addView(gameMappingContent);

        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String profileKey = selectedProfileKey(profiles, profileSpinner);
                store.setActiveProfileKey(profileKey);
                applySectionSelections(store, profileKey, false, menuSpinners);
                applySectionSelections(store, profileKey, true, gameSpinners);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Live preview only for the debug box so it appears immediately in-game.
        showHotbarHitbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ControlsPreferences.setHotbarHitboxDebugEnabled(activity, isChecked);
            notifySettingsChanged(activity, listener);
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scrollView)
                .setPositiveButton("Save", (dialogInterface, which) -> {
                    saved[0] = true;
                    String profileKey = selectedProfileKey(profiles, profileSpinner);
                    store.setActiveProfileKey(profileKey);
                    store.setForceGameMode(forceGameMode.isChecked());
                    store.setShowCursorOverlay(showCursorOverlay.isChecked());
                    LauncherPreferences.setShowInGameSettingsButton(activity, showFloatingSettingsButton.isChecked());
                    LauncherPreferences.setShowGameLogOverlay(activity, showLogOverlay.isChecked());
                    store.setMenuCursorSensitivity(progressToSensitivity(menuSensitivity.getProgress()));
                    store.setGameCameraSensitivity(progressToSensitivity(gameSensitivity.getProgress()));
                    ControlsPreferences.setHotbarHitboxDebugEnabled(activity, showHotbarHitbox.isChecked());
                    ControlsPreferences.setHotbarGuiScaleOverride(activity, selectedGuiScaleValue(guiScaleValues, guiScaleSpinner));
                    ControlsPreferences.setHotbarWidthGui(activity, progressToFloat(hotbarWidth.getProgress(), 90));
                    ControlsPreferences.setHotbarHeightGui(activity, progressToFloat(hotbarHeight.getProgress(), 12));
                    ControlsPreferences.setHotbarXOffsetDp(activity, progressToFloat(hotbarXOffset.getProgress(), -160));
                    ControlsPreferences.setHotbarYOffsetDp(activity, progressToFloat(hotbarYOffset.getProgress(), -80));
                    ControlsPreferences.setHotbarVerticalPaddingDp(activity, progressToFloat(hotbarPadding.getProgress(), 0));
                    saveSection(store, profileKey, true, gameSpinners);
                    saveSection(store, profileKey, false, menuSpinners);
                    notifySettingsChanged(activity, listener);
                })
                .setNeutralButton("Reset defaults", (dialogInterface, which) -> {
                    saved[0] = true;
                    store.resetDefaults();
                    ControlsPreferences.resetHotbarHitboxSettings(activity);
                    notifySettingsChanged(activity, listener);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        activeDialog = dialog;
        dialog.setOnDismissListener(dismissed -> {
            setActiveDialogPreviewAlpha(false);
            if (!saved[0]) {
                ControlsPreferences.setHotbarHitboxDebugEnabled(activity, originalHotbarDebug);
                notifySettingsChanged(activity, listener);
            }
            activeDialog = null;
        });
        dialog.show();
        styleDialogChrome(activity, dialog);
    }

    private static void styleDialogChrome(@NonNull Activity activity, @NonNull AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundedDrawable(activity, COLOR_DIALOG_BG, COLOR_DIALOG_BG, 22));
            window.setDimAmount(0.58f);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(COLOR_ACCENT);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_ACCENT);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(COLOR_ACCENT);
    }

    @NonNull
    private static GradientDrawable roundedDrawable(
            @NonNull Activity activity,
            int fillColor,
            int strokeColor,
            int cornerDp
    ) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(activity, cornerDp));
        bg.setStroke(dp(activity, 1), strokeColor);
        return bg;
    }

    @NonNull
    private static <T> ArrayAdapter<T> darkAdapter(@NonNull Activity activity, @NonNull List<T> items) {
        ArrayAdapter<T> adapter = new ArrayAdapter<T>(activity, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static void styleSpinnerText(@NonNull View view, boolean dropdown) {
        view.setBackgroundColor(dropdown ? COLOR_CARD_BG_PRESSED : Color.TRANSPARENT);
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(COLOR_TEXT_PRIMARY);
            textView.setTextSize(15);
            textView.setSingleLine(false);
            textView.setPadding(textView.getPaddingLeft(), dpFromView(textView, 8), textView.getPaddingRight(), dpFromView(textView, 8));
        }
    }

    private static void tintCheckBox(@NonNull CheckBox box) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] colors = new int[]{COLOR_ACCENT, COLOR_TEXT_MUTED};
        box.setButtonTintList(new ColorStateList(states, colors));
    }

    private static void tintSeekBar(@NonNull SeekBar seekBar) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(COLOR_CARD_STROKE));
    }

    private static int dpFromView(@NonNull View view, int value) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @NonNull
    private static LinearLayout addCard(@NonNull Activity activity, @NonNull LinearLayout root) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 14);
        card.setPadding(p, p, p, p);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD_BG);
        bg.setCornerRadius(dp(activity, 18));
        bg.setStroke(dp(activity, 1), COLOR_CARD_STROKE);
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(activity, 12));
        root.addView(card, lp);
        return card;
    }

    private static void addCardTitle(@NonNull Activity activity, @NonNull LinearLayout root, @NonNull String title) {
        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextSize(18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setPadding(0, 0, 0, dp(activity, 8));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private static void addCollapsibleHeader(
            @NonNull Activity activity,
            @NonNull LinearLayout card,
            @NonNull String title,
            @NonNull LinearLayout content,
            boolean expanded
    ) {
        TextView header = new TextView(activity);
        header.setText((expanded ? "▾  " : "▸  ") + title);
        header.setTextSize(18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(COLOR_TEXT_PRIMARY);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(activity, 8));
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean nowVisible = content.getVisibility() != View.VISIBLE;
            content.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
            header.setText((nowVisible ? "▾  " : "▸  ") + title);
        });
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    @NonNull
    private static TextView addInfoText(@NonNull Activity activity, @NonNull LinearLayout root, @NonNull String text) {
        TextView info = new TextView(activity);
        info.setText(text);
        info.setTextSize(13);
        info.setTextColor(COLOR_TEXT_SECONDARY);
        info.setPadding(0, 0, 0, dp(activity, 8));
        root.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return info;
    }

    private static void addSmallHint(@NonNull Activity activity, @NonNull LinearLayout root, @NonNull String text) {
        TextView hint = new TextView(activity);
        hint.setText(text);
        hint.setTextSize(12);
        hint.setTextColor(COLOR_TEXT_MUTED);
        hint.setPadding(dp(activity, 32), 0, 0, dp(activity, 6));
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    @NonNull
    private static CheckBox addCheckBox(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String label,
            boolean checked
    ) {
        CheckBox box = new CheckBox(activity);
        box.setText(label);
        box.setTextSize(15);
        box.setTextColor(COLOR_TEXT_SECONDARY);
        box.setChecked(checked);
        tintCheckBox(box);
        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return box;
    }

    @NonNull
    private static TextView addPlainLabel(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String title
    ) {
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextSize(15);
        label.setTextColor(COLOR_TEXT_SECONDARY);
        label.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        root.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return label;
    }

    @NonNull
    private static TextView addFloatControl(
            @NonNull Activity activity,
            @NonNull LinearLayout root,
            @NonNull String title,
            float value
    ) {
        TextView label = addPlainLabel(activity, root, title + ": " + Math.round(value));
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
        tintSeekBar(seekBar);
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
        return addPlainLabel(activity, root, title + ": " + sensitivity + "%");
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
        tintSeekBar(seekBar);
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
            boolean gameMode,
            @NonNull GamepadMappingStore store,
            @NonNull String profileKey,
            @NonNull Map<GamepadButton, Spinner> out
    ) {
        ArrayAdapter<GamepadAction> adapter = darkAdapter(activity, Arrays.asList(GamepadAction.values()));

        for (GamepadButton button : GamepadButton.values()) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

            TextView label = new TextView(activity);
            label.setText(button.toString());
            label.setTextSize(14);
            label.setTextColor(COLOR_TEXT_SECONDARY);

            Spinner spinner = new Spinner(activity);
            spinner.setAdapter(adapter);
            spinner.setSelection(store.getButtonAction(button, gameMode, profileKey).ordinal());

            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f));

            root.addView(row);
            out.put(button, spinner);
        }
    }

    private static void applySectionSelections(
            @NonNull GamepadMappingStore store,
            @NonNull String profileKey,
            boolean gameMode,
            @NonNull Map<GamepadButton, Spinner> spinners
    ) {
        for (Map.Entry<GamepadButton, Spinner> entry : spinners.entrySet()) {
            entry.getValue().setSelection(store.getButtonAction(entry.getKey(), gameMode, profileKey).ordinal());
        }
    }

    private static void saveSection(
            @NonNull GamepadMappingStore store,
            @NonNull String profileKey,
            boolean gameMode,
            @NonNull Map<GamepadButton, Spinner> spinners
    ) {
        for (Map.Entry<GamepadButton, Spinner> entry : spinners.entrySet()) {
            Object selected = entry.getValue().getSelectedItem();
            if (selected instanceof GamepadAction) {
                store.setButtonAction(entry.getKey(), (GamepadAction) selected, gameMode, profileKey);
            }
        }
    }

    @NonNull
    private static List<DeviceProfile> discoverProfiles(
            @NonNull Activity activity,
            @NonNull GamepadMappingStore store
    ) {
        LinkedHashMap<String, DeviceProfile> profiles = new LinkedHashMap<>();
        String active = store.getActiveProfileKey();

        profiles.put(store.getDefaultProfileKey(), new DeviceProfile(store.getDefaultProfileKey(), store.getProfileDisplayName(store.getDefaultProfileKey()), false));
        for (String known : store.getKnownProfileKeys()) {
            profiles.put(known, new DeviceProfile(known, store.getProfileDisplayName(known), false));
        }

        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || !isGamepadDevice(device)) continue;
            store.registerDevice(device);
            String key = store.profileKeyForDevice(device);
            profiles.put(key, new DeviceProfile(key, store.getProfileDisplayName(key), true));
        }

        store.setActiveProfileKey(active);
        return new ArrayList<>(profiles.values());
    }

    private static boolean isGamepadDevice(@NonNull InputDevice device) {
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private static int preferredProfileIndex(@NonNull List<DeviceProfile> profiles, @NonNull String profileKey) {
        int activeIndex = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).key.equals(profileKey)) {
                activeIndex = i;
                break;
            }
        }

        // When the user opens the mapper with one attached controller and no active
        // controller-specific profile yet, select the attached controller automatically.
        if (activeIndex == 0 && profiles.size() > 1) {
            for (int i = 1; i < profiles.size(); i++) {
                if (profiles.get(i).attached) return i;
            }
        }
        return activeIndex;
    }

    @NonNull
    private static String selectedProfileKey(@NonNull List<DeviceProfile> profiles, @NonNull Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0 || position >= profiles.size()) return profiles.get(0).key;
        return profiles.get(position).key;
    }

    private static int findGuiScaleIndex(@NonNull int[] guiScaleValues, int value) {
        for (int i = 0; i < guiScaleValues.length; i++) {
            if (guiScaleValues[i] == value) return i;
        }
        return 0;
    }

    private static int selectedGuiScaleValue(@NonNull int[] guiScaleValues, @NonNull Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        if (position < 0) return guiScaleValues[0];
        if (position >= guiScaleValues.length) return guiScaleValues[guiScaleValues.length - 1];
        return guiScaleValues[position];
    }

    private static LinearLayout.LayoutParams matchWrapWithTopMargin(@NonNull Activity activity, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(activity, topDp), 0, 0);
        return lp;
    }

    private static void notifySettingsChanged(
            @NonNull Activity activity,
            @Nullable OnSettingsSavedListener listener
    ) {
        if (listener != null) listener.onSettingsSaved();
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor != null) {
            decor.requestLayout();
            decor.invalidate();
            decor.postInvalidateOnAnimation();
            decor.post(() -> {
                decor.requestLayout();
                decor.invalidate();
                decor.postInvalidateOnAnimation();
            });
        }
    }

    private static int dp(@NonNull Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static final class DeviceProfile {
        @NonNull final String key;
        @NonNull final String label;
        final boolean attached;

        DeviceProfile(@NonNull String key, @NonNull String label, boolean attached) {
            this.key = key;
            this.label = label;
            this.attached = attached;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
