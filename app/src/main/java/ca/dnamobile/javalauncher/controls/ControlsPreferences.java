package ca.dnamobile.javalauncher.controls;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Small preference wrapper for touch controls. Kept separate from LauncherPreferences
 * so this system can grow without bloating the normal launcher settings class.
 */
public final class ControlsPreferences {
    private static final String PREFS = "java_launcher_touch_controls";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SELECTED_LAYOUT = "selected_layout";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_EDIT_GRID = "edit_grid";
    private static final String KEY_SNAP_CONTROLS = "snap_controls";
    private static final String KEY_SIZE_PREVIEW_PERCENT = "size_preview_percent";
    private static final String KEY_VIRTUAL_MOUSE_ENABLED = "virtual_mouse_enabled";

    private static final String KEY_HOTBAR_HITBOX_DEBUG = "hotbar_hitbox_debug";
    private static final String KEY_HOTBAR_GUI_SCALE_OVERRIDE = "hotbar_gui_scale_override";
    private static final String KEY_HOTBAR_WIDTH_GUI = "hotbar_width_gui";
    private static final String KEY_HOTBAR_HEIGHT_GUI = "hotbar_height_gui";
    private static final String KEY_HOTBAR_X_OFFSET_DP = "hotbar_x_offset_dp";
    private static final String KEY_HOTBAR_Y_OFFSET_DP = "hotbar_y_offset_dp";
    private static final String KEY_HOTBAR_VERTICAL_PADDING_DP = "hotbar_vertical_padding_dp";

    public static final float DEFAULT_HOTBAR_WIDTH_GUI = 180f;
    public static final float DEFAULT_HOTBAR_HEIGHT_GUI = 20f;
    public static final float DEFAULT_HOTBAR_X_OFFSET_DP = 0f;
    public static final float DEFAULT_HOTBAR_Y_OFFSET_DP = 0f;
    public static final float DEFAULT_HOTBAR_VERTICAL_PADDING_DP = 18f;

    private ControlsPreferences() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isTouchControlsEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setTouchControlsEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    @Nullable
    public static String getSelectedLayoutPath(@NonNull Context context) {
        String value = prefs(context).getString(KEY_SELECTED_LAYOUT, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static void setSelectedLayoutPath(@NonNull Context context, @Nullable String path) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (path == null || path.trim().isEmpty()) {
            editor.remove(KEY_SELECTED_LAYOUT);
        } else {
            editor.putString(KEY_SELECTED_LAYOUT, path);
        }
        editor.apply();
    }

    public static float getGlobalOpacity(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_OPACITY, 1f), 0.15f, 1f);
    }

    public static void setGlobalOpacity(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_OPACITY, clamp(value, 0.15f, 1f)).apply();
    }

    public static boolean isEditGridEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_EDIT_GRID, true);
    }

    public static void setEditGridEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_EDIT_GRID, enabled).apply();
    }

    public static boolean isSnapControlsEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SNAP_CONTROLS, true);
    }

    public static void setSnapControlsEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SNAP_CONTROLS, enabled).apply();
    }

    public static boolean isSizePreviewPercentEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SIZE_PREVIEW_PERCENT, true);
    }

    public static void setSizePreviewPercentEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SIZE_PREVIEW_PERCENT, enabled).apply();
    }

    public static boolean isVirtualMouseEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_VIRTUAL_MOUSE_ENABLED, false);
    }

    public static void setVirtualMouseEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VIRTUAL_MOUSE_ENABLED, enabled).apply();
    }

    public static boolean isHotbarHitboxDebugEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_HOTBAR_HITBOX_DEBUG, false);
    }

    public static void setHotbarHitboxDebugEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HOTBAR_HITBOX_DEBUG, enabled).apply();
    }

    /**
     * 0 means launcher auto-estimate. Use 2/3/4/etc. to match Minecraft's GUI scale.
     */
    public static int getHotbarGuiScaleOverride(@NonNull Context context) {
        int value = prefs(context).getInt(KEY_HOTBAR_GUI_SCALE_OVERRIDE, 0);
        return Math.max(0, Math.min(8, value));
    }

    public static void setHotbarGuiScaleOverride(@NonNull Context context, int scale) {
        prefs(context).edit().putInt(KEY_HOTBAR_GUI_SCALE_OVERRIDE, Math.max(0, Math.min(8, scale))).apply();
    }

    public static float getHotbarWidthGui(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_HOTBAR_WIDTH_GUI, DEFAULT_HOTBAR_WIDTH_GUI), 90f, 260f);
    }

    public static void setHotbarWidthGui(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_HOTBAR_WIDTH_GUI, clamp(value, 90f, 260f)).apply();
    }

    public static float getHotbarHeightGui(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_HOTBAR_HEIGHT_GUI, DEFAULT_HOTBAR_HEIGHT_GUI), 12f, 60f);
    }

    public static void setHotbarHeightGui(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_HOTBAR_HEIGHT_GUI, clamp(value, 12f, 60f)).apply();
    }

    public static float getHotbarXOffsetDp(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_HOTBAR_X_OFFSET_DP, DEFAULT_HOTBAR_X_OFFSET_DP), -160f, 160f);
    }

    public static void setHotbarXOffsetDp(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_HOTBAR_X_OFFSET_DP, clamp(value, -160f, 160f)).apply();
    }

    public static float getHotbarYOffsetDp(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_HOTBAR_Y_OFFSET_DP, DEFAULT_HOTBAR_Y_OFFSET_DP), -80f, 160f);
    }

    public static void setHotbarYOffsetDp(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_HOTBAR_Y_OFFSET_DP, clamp(value, -80f, 160f)).apply();
    }

    public static float getHotbarVerticalPaddingDp(@NonNull Context context) {
        return clamp(prefs(context).getFloat(KEY_HOTBAR_VERTICAL_PADDING_DP, DEFAULT_HOTBAR_VERTICAL_PADDING_DP), 0f, 80f);
    }

    public static void setHotbarVerticalPaddingDp(@NonNull Context context, float value) {
        prefs(context).edit().putFloat(KEY_HOTBAR_VERTICAL_PADDING_DP, clamp(value, 0f, 80f)).apply();
    }

    public static void resetHotbarHitboxSettings(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_HOTBAR_HITBOX_DEBUG)
                .remove(KEY_HOTBAR_GUI_SCALE_OVERRIDE)
                .remove(KEY_HOTBAR_WIDTH_GUI)
                .remove(KEY_HOTBAR_HEIGHT_GUI)
                .remove(KEY_HOTBAR_X_OFFSET_DP)
                .remove(KEY_HOTBAR_Y_OFFSET_DP)
                .remove(KEY_HOTBAR_VERTICAL_PADDING_DP)
                .apply();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
