package ca.dnamobile.javalauncher.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent controller mapping store.
 *
 * Mappings are now stored per physical controller profile when Android gives us
 * a stable InputDevice. The old global keys are still read as a fallback so
 * existing saved mappings are not lost.
 *
 * Important:
 * Older JavaLauncher controller patches defaulted menu A/R2/DPAD_CENTER to ENTER.
 * That old saved value can survive rebuilds and make the cursor move while A does not
 * left-click Minecraft buttons. This class migrates those old saved values back to
 * Mouse Left Click once.
 */
public final class GamepadMappingStore {
    private static final String PREFS_NAME = "gamepad_mapping";
    private static final String GAME_PREFIX = "game.";
    private static final String MENU_PREFIX = "menu.";
    private static final String PROFILE_PREFIX = "profile.";
    private static final String PROFILE_NAME_PREFIX = "profile_name.";
    private static final String KNOWN_PROFILES = "known_profiles";
    private static final String ACTIVE_PROFILE = "active_profile";
    private static final String DEFAULT_PROFILE = "default";

    private static final String FORCE_GAME_MODE = "force_game_mode";
    private static final String SHOW_CURSOR_OVERLAY = "show_cursor_overlay";
    private static final String PREF_VERSION = "pref_version";

    private static final String MENU_CURSOR_SENSITIVITY = "menu_cursor_sensitivity";
    private static final String GAME_CAMERA_SENSITIVITY = "game_camera_sensitivity";

    private static final int CURRENT_PREF_VERSION = 6;
    private static final int DEFAULT_SENSITIVITY = 100;
    public static final int MIN_SENSITIVITY = 25;
    public static final int MAX_SENSITIVITY = 200;

    private static volatile GamepadMappingStore instance;

    private final SharedPreferences prefs;

    private GamepadMappingStore(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateIfNeeded();
    }

    @NonNull
    public static GamepadMappingStore get(@NonNull Context context) {
        GamepadMappingStore local = instance;
        if (local == null) {
            synchronized (GamepadMappingStore.class) {
                local = instance;
                if (local == null) {
                    local = new GamepadMappingStore(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    private void migrateIfNeeded() {
        int version = prefs.getInt(PREF_VERSION, 0);
        if (version >= CURRENT_PREF_VERSION) return;

        SharedPreferences.Editor editor = prefs.edit();

        // These three were previously defaulted to ENTER in one experimental patch.
        // Remove only these menu entries so they fall back to the correct Mouse Left Click default.
        editor.remove(keyFor(GamepadButton.BUTTON_A, false));
        editor.remove(keyFor(GamepadButton.BUTTON_R2, false));
        editor.remove(keyFor(GamepadButton.DPAD_CENTER, false));
        editor.remove(keyFor(DEFAULT_PROFILE, GamepadButton.BUTTON_A, false));
        editor.remove(keyFor(DEFAULT_PROFILE, GamepadButton.BUTTON_R2, false));
        editor.remove(keyFor(DEFAULT_PROFILE, GamepadButton.DPAD_CENTER, false));

        editor.putBoolean(SHOW_CURSOR_OVERLAY, prefs.getBoolean(SHOW_CURSOR_OVERLAY, true));
        editor.putInt(MENU_CURSOR_SENSITIVITY, clampSensitivity(prefs.getInt(MENU_CURSOR_SENSITIVITY, DEFAULT_SENSITIVITY)));
        editor.putInt(GAME_CAMERA_SENSITIVITY, clampSensitivity(prefs.getInt(GAME_CAMERA_SENSITIVITY, DEFAULT_SENSITIVITY)));
        editor.putString(ACTIVE_PROFILE, prefs.getString(ACTIVE_PROFILE, DEFAULT_PROFILE));
        editor.putInt(PREF_VERSION, CURRENT_PREF_VERSION);
        editor.apply();
    }

    public boolean isForceGameMode() {
        return prefs.getBoolean(FORCE_GAME_MODE, false);
    }

    public void setForceGameMode(boolean force) {
        prefs.edit().putBoolean(FORCE_GAME_MODE, force).apply();
    }

    public boolean isShowCursorOverlay() {
        return prefs.getBoolean(SHOW_CURSOR_OVERLAY, true);
    }

    public void setShowCursorOverlay(boolean show) {
        prefs.edit().putBoolean(SHOW_CURSOR_OVERLAY, show).apply();
    }

    public int getMenuCursorSensitivity() {
        return clampSensitivity(prefs.getInt(MENU_CURSOR_SENSITIVITY, DEFAULT_SENSITIVITY));
    }

    public void setMenuCursorSensitivity(int sensitivity) {
        prefs.edit().putInt(MENU_CURSOR_SENSITIVITY, clampSensitivity(sensitivity)).apply();
    }

    public int getGameCameraSensitivity() {
        return clampSensitivity(prefs.getInt(GAME_CAMERA_SENSITIVITY, DEFAULT_SENSITIVITY));
    }

    public void setGameCameraSensitivity(int sensitivity) {
        prefs.edit().putInt(GAME_CAMERA_SENSITIVITY, clampSensitivity(sensitivity)).apply();
    }

    public float getMenuCursorSensitivityMultiplier() {
        return getMenuCursorSensitivity() / 100f;
    }

    public float getGameCameraSensitivityMultiplier() {
        return getGameCameraSensitivity() / 100f;
    }

    @NonNull
    public String getDefaultProfileKey() {
        return DEFAULT_PROFILE;
    }

    @NonNull
    public String getActiveProfileKey() {
        String key = prefs.getString(ACTIVE_PROFILE, DEFAULT_PROFILE);
        return isValidProfileKey(key) ? key : DEFAULT_PROFILE;
    }

    public void setActiveProfileKey(@Nullable String profileKey) {
        String safeKey = isValidProfileKey(profileKey) ? profileKey : DEFAULT_PROFILE;
        prefs.edit().putString(ACTIVE_PROFILE, safeKey).apply();
    }

    @NonNull
    public Set<String> getKnownProfileKeys() {
        Set<String> stored = prefs.getStringSet(KNOWN_PROFILES, Collections.emptySet());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(DEFAULT_PROFILE);
        if (stored != null) result.addAll(stored);
        return result;
    }

    @NonNull
    public String getProfileDisplayName(@Nullable String profileKey) {
        if (!isValidProfileKey(profileKey) || DEFAULT_PROFILE.equals(profileKey)) {
            return "Default controller";
        }
        String name = prefs.getString(PROFILE_NAME_PREFIX + profileKey, null);
        return name == null || name.trim().isEmpty() ? "Controller " + profileKey : name;
    }

    public void registerDevice(@Nullable InputDevice device) {
        if (device == null) return;

        String profileKey = profileKeyForDevice(device);
        if (DEFAULT_PROFILE.equals(profileKey)) return;

        LinkedHashSet<String> known = new LinkedHashSet<>(getKnownProfileKeys());
        known.add(profileKey);

        prefs.edit()
                .putStringSet(KNOWN_PROFILES, known)
                .putString(PROFILE_NAME_PREFIX + profileKey, displayNameForDevice(device))
                .apply();
    }

    public void rememberDevice(@Nullable InputDevice device) {
        if (device == null) return;
        registerDevice(device);
        setActiveProfileKey(profileKeyForDevice(device));
    }

    @NonNull
    public String profileKeyForDevice(@Nullable InputDevice device) {
        if (device == null) return DEFAULT_PROFILE;

        String raw = device.getDescriptor();
        if (raw == null || raw.trim().isEmpty()) {
            raw = device.getName() + ":" + device.getVendorId() + ":" + device.getProductId();
        }
        if (raw == null || raw.trim().isEmpty()) return DEFAULT_PROFILE;

        return "device_" + Integer.toHexString(raw.hashCode());
    }

    @NonNull
    public GamepadAction getButtonAction(@NonNull GamepadButton button, boolean gameMode) {
        return getButtonAction(button, gameMode, getActiveProfileKey());
    }

    @NonNull
    public GamepadAction getButtonAction(
            @NonNull GamepadButton button,
            boolean gameMode,
            @Nullable InputDevice device
    ) {
        return getButtonAction(button, gameMode, profileKeyForDevice(device));
    }

    @NonNull
    public GamepadAction getButtonAction(
            @NonNull GamepadButton button,
            boolean gameMode,
            @Nullable String profileKey
    ) {
        String safeProfile = isValidProfileKey(profileKey) ? profileKey : DEFAULT_PROFILE;

        GamepadAction action = readStoredAction(keyFor(safeProfile, button, gameMode));
        if (action != null) return action;

        // Legacy/global fallback so saved mappings from older builds still work.
        if (!DEFAULT_PROFILE.equals(safeProfile)) {
            action = readStoredAction(keyFor(button, gameMode));
            if (action != null) return action;
        }

        return gameMode ? defaultGameAction(button) : defaultMenuAction(button);
    }

    public void setButtonAction(
            @NonNull GamepadButton button,
            @NonNull GamepadAction action,
            boolean gameMode
    ) {
        setButtonAction(button, action, gameMode, getActiveProfileKey());
    }

    public void setButtonAction(
            @NonNull GamepadButton button,
            @NonNull GamepadAction action,
            boolean gameMode,
            @Nullable String profileKey
    ) {
        String safeProfile = isValidProfileKey(profileKey) ? profileKey : DEFAULT_PROFILE;
        prefs.edit()
                .putString(keyFor(safeProfile, button, gameMode), action.name())
                .apply();
    }

    public void resetDefaults() {
        prefs.edit()
                .clear()
                .putInt(PREF_VERSION, CURRENT_PREF_VERSION)
                .putBoolean(SHOW_CURSOR_OVERLAY, true)
                .putString(ACTIVE_PROFILE, DEFAULT_PROFILE)
                .putInt(MENU_CURSOR_SENSITIVITY, DEFAULT_SENSITIVITY)
                .putInt(GAME_CAMERA_SENSITIVITY, DEFAULT_SENSITIVITY)
                .apply();
    }

    @Nullable
    private GamepadAction readStoredAction(@NonNull String key) {
        String stored = prefs.getString(key, null);
        if (stored == null) return null;

        try {
            return GamepadAction.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @NonNull
    private static String keyFor(@NonNull GamepadButton button, boolean gameMode) {
        return (gameMode ? GAME_PREFIX : MENU_PREFIX) + button.name();
    }

    @NonNull
    private static String keyFor(
            @NonNull String profileKey,
            @NonNull GamepadButton button,
            boolean gameMode
    ) {
        if (DEFAULT_PROFILE.equals(profileKey)) {
            return keyFor(button, gameMode);
        }
        return PROFILE_PREFIX + profileKey + "." + (gameMode ? GAME_PREFIX : MENU_PREFIX) + button.name();
    }

    private static boolean isValidProfileKey(@Nullable String profileKey) {
        return profileKey != null && !profileKey.trim().isEmpty();
    }

    @NonNull
    private static String displayNameForDevice(@NonNull InputDevice device) {
        String name = device.getName();
        if (name == null || name.trim().isEmpty()) name = "Controller";

        int vendorId = device.getVendorId();
        int productId = device.getProductId();
        if (vendorId > 0 || productId > 0) {
            return name + " (" + vendorId + ":" + productId + ")";
        }
        return name;
    }

    private static int clampSensitivity(int sensitivity) {
        return Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, sensitivity));
    }

    @NonNull
    private static GamepadAction defaultGameAction(@NonNull GamepadButton button) {
        switch (button) {
            case BUTTON_A:
                return GamepadAction.JUMP;
            case BUTTON_B:
                return GamepadAction.DROP;
            case BUTTON_X:
                return GamepadAction.INVENTORY;
            case BUTTON_Y:
                return GamepadAction.OFFHAND;

            case BUTTON_L1:
                return GamepadAction.SCROLL_UP;
            case BUTTON_R1:
                return GamepadAction.SCROLL_DOWN;
            case BUTTON_L2:
                return GamepadAction.MOUSE_RIGHT;
            case BUTTON_R2:
                return GamepadAction.MOUSE_LEFT;

            case BUTTON_THUMBL:
                return GamepadAction.SPRINT;
            case BUTTON_THUMBR:
                return GamepadAction.SNEAK;

            case BUTTON_START:
                return GamepadAction.ESCAPE;
            case BUTTON_SELECT:
                return GamepadAction.TAB;

            case DPAD_UP:
                return GamepadAction.SNEAK;
            case DPAD_DOWN:
                return GamepadAction.KEY_O;
            case DPAD_LEFT:
                return GamepadAction.KEY_J;
            case DPAD_RIGHT:
                return GamepadAction.KEY_K;
            case DPAD_CENTER:
                return GamepadAction.NONE;

            default:
                return GamepadAction.NONE;
        }
    }

    @NonNull
    private static GamepadAction defaultMenuAction(@NonNull GamepadButton button) {
        switch (button) {
            case BUTTON_A:
            case BUTTON_R2:
            case DPAD_CENTER:
                return GamepadAction.MOUSE_LEFT;

            case BUTTON_X:
            case BUTTON_L2:
                return GamepadAction.MOUSE_RIGHT;

            case BUTTON_B:
            case BUTTON_START:
            case BUTTON_SELECT:
                return GamepadAction.ESCAPE;

            case BUTTON_L1:
                return GamepadAction.SCROLL_UP;
            case BUTTON_R1:
                return GamepadAction.SCROLL_DOWN;

            case DPAD_UP:
                return GamepadAction.CURSOR_UP;
            case DPAD_DOWN:
                return GamepadAction.CURSOR_DOWN;
            case DPAD_LEFT:
                return GamepadAction.CURSOR_LEFT;
            case DPAD_RIGHT:
                return GamepadAction.CURSOR_RIGHT;

            default:
                return GamepadAction.NONE;
        }
    }
}
