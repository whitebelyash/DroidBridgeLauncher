package ca.dnamobile.javalauncher.input;

import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import ca.dnamobile.javalauncher.feature.log.Logging;

/**
 * Built-in Android controller support.
 *
 * In menu mode:
 * - both sticks move visible cursor
 * - D-pad nudges visible cursor
 * - A/R2/D-pad center are guarded to left-click even if old saved prefs mapped them to Enter
 */
public final class GamepadInputController {
    private static final String TAG = "GamepadInputController";

    private static final float DEADZONE = 0.25f;
    private static final float TRIGGER_THRESHOLD = 0.50f;
    private static final float HAT_THRESHOLD = 0.85f;

    // Base values. User sensitivity prefs multiply these.
    private static final float BASE_GAME_CAMERA_SENSITIVITY = 18f;
    private static final float BASE_MENU_CURSOR_SENSITIVITY = 26f;
    private static final float BASE_DPAD_CURSOR_STEP = 14f;

    private static final int DIRECTION_NONE = -1;
    private static final int DIRECTION_EAST = 0;
    private static final int DIRECTION_NORTH_EAST = 1;
    private static final int DIRECTION_NORTH = 2;
    private static final int DIRECTION_NORTH_WEST = 3;
    private static final int DIRECTION_WEST = 4;
    private static final int DIRECTION_SOUTH_WEST = 5;
    private static final int DIRECTION_SOUTH = 6;
    private static final int DIRECTION_SOUTH_EAST = 7;

    public interface MappingRequestListener {
        void onRequestControllerMapping();
    }

    private final Choreographer choreographer = Choreographer.getInstance();
    private final GamepadMappingStore mappingStore;
    private final MappingRequestListener mappingRequestListener;

    private boolean removed;
    private long lastFrameNanos = System.nanoTime();

    private float leftX;
    private float leftY;
    private float rightX;
    private float rightY;

    private int currentDirection = DIRECTION_NONE;

    private boolean hatUp;
    private boolean hatDown;
    private boolean hatLeft;
    private boolean hatRight;
    private boolean leftTriggerDown;
    private boolean rightTriggerDown;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            tick(frameTimeNanos);
            if (!removed) {
                choreographer.postFrameCallback(this);
            }
        }
    };

    public GamepadInputController(@NonNull View hostView) {
        this(hostView, null);
    }

    public GamepadInputController(@NonNull View hostView, MappingRequestListener mappingRequestListener) {
        mappingStore = GamepadMappingStore.get(hostView.getContext());
        this.mappingRequestListener = mappingRequestListener;

        hostView.setFocusable(true);
        hostView.setFocusableInTouchMode(true);
        hostView.requestFocus();

        org.lwjgl.glfw.CallbackBridge.sendCursorPos(
                Math.max(1, org.lwjgl.glfw.CallbackBridge.windowWidth) / 2f,
                Math.max(1, org.lwjgl.glfw.CallbackBridge.windowHeight) / 2f
        );

        choreographer.postFrameCallback(frameCallback);
    }

    public void removeSelf() {
        removed = true;
        releaseDirection();
        sendMappedButton(GamepadButton.BUTTON_L2, false);
        sendMappedButton(GamepadButton.BUTTON_R2, false);
    }

    public boolean handleKeyEvent(@NonNull KeyEvent event) {
        if (!isGamepadKeyEvent(event)) return false;

        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return false;
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (action == KeyEvent.ACTION_UP && mappingRequestListener != null) {
                mappingRequestListener.onRequestControllerMapping();
            }
            return true;
        }

        GamepadButton button = GamepadButton.fromAndroidKeyCode(event.getKeyCode());
        if (button == null) return false;

        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0 && !button.name().startsWith("DPAD_")) {
            return true;
        }

        sendMappedButton(button, action == KeyEvent.ACTION_DOWN);
        return true;
    }

    public boolean handleMotionEvent(@NonNull MotionEvent event) {
        if (!isGamepadMotionEvent(event)) return false;

        InputDevice device = event.getDevice();
        if (device == null) return false;

        leftX = getCenteredAxis(event, device, MotionEvent.AXIS_X);
        leftY = getCenteredAxis(event, device, MotionEvent.AXIS_Y);

        rightX = getCenteredAxis(event, device, MotionEvent.AXIS_Z);
        rightY = getCenteredAxis(event, device, MotionEvent.AXIS_RZ);
        if (rightX == 0f) rightX = getCenteredAxis(event, device, MotionEvent.AXIS_RX);
        if (rightY == 0f) rightY = getCenteredAxis(event, device, MotionEvent.AXIS_RY);

        updateDirection();

        updateHatButton(GamepadButton.DPAD_LEFT, getCenteredAxis(event, device, MotionEvent.AXIS_HAT_X) < -HAT_THRESHOLD);
        updateHatButton(GamepadButton.DPAD_RIGHT, getCenteredAxis(event, device, MotionEvent.AXIS_HAT_X) > HAT_THRESHOLD);
        updateHatButton(GamepadButton.DPAD_UP, getCenteredAxis(event, device, MotionEvent.AXIS_HAT_Y) < -HAT_THRESHOLD);
        updateHatButton(GamepadButton.DPAD_DOWN, getCenteredAxis(event, device, MotionEvent.AXIS_HAT_Y) > HAT_THRESHOLD);

        updateTrigger(true, getCenteredAxis(event, device, MotionEvent.AXIS_LTRIGGER) > TRIGGER_THRESHOLD);
        updateTrigger(false, getCenteredAxis(event, device, MotionEvent.AXIS_RTRIGGER) > TRIGGER_THRESHOLD);

        return true;
    }

    private static boolean isGamepadKeyEvent(@NonNull KeyEvent event) {
        int source = event.getSource();
        InputDevice device = event.getDevice();

        boolean fromGamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (device != null && ((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK));

        if (fromGamepad) return true;

        return GamepadButton.fromAndroidKeyCode(event.getKeyCode()) != null
                || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU;
    }

    private static boolean isGamepadMotionEvent(@NonNull MotionEvent event) {
        int source = event.getSource();
        return ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                && event.getActionMasked() == MotionEvent.ACTION_MOVE;
    }

    private static float getCenteredAxis(@NonNull MotionEvent event, @NonNull InputDevice device, int axis) {
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) return 0f;

        float value = event.getAxisValue(axis);
        float flat = Math.max(range.getFlat(), DEADZONE);
        return Math.abs(value) > flat ? value : 0f;
    }

    private boolean isGameMode() {
        return org.lwjgl.glfw.CallbackBridge.isGrabbing() || mappingStore.isForceGameMode();
    }

    private void tick(long frameTimeNanos) {
        float deltaScale = (frameTimeNanos - lastFrameNanos) / 16_666_666f;
        if (deltaScale <= 0f || deltaScale > 4f) deltaScale = 1f;

        boolean gameMode = isGameMode();

        if (gameMode) {
            tickCamera(deltaScale);
        } else {
            tickMenuCursor(deltaScale);
        }

        lastFrameNanos = frameTimeNanos;
    }

    private void tickCamera(float deltaScale) {
        if (rightX == 0f && rightY == 0f) return;

        float magnitude = Math.min(1f, (float) Math.sqrt(rightX * rightX + rightY * rightY));
        float acceleration = magnitude * magnitude;

        float sensitivity = BASE_GAME_CAMERA_SENSITIVITY
                * mappingStore.getGameCameraSensitivityMultiplier();

        float deltaX = rightX * acceleration * sensitivity * deltaScale;
        float deltaY = rightY * acceleration * sensitivity * deltaScale;

        org.lwjgl.glfw.CallbackBridge.mouseX += deltaX;
        org.lwjgl.glfw.CallbackBridge.mouseY += deltaY;
        org.lwjgl.glfw.CallbackBridge.sendCursorPos(org.lwjgl.glfw.CallbackBridge.mouseX, org.lwjgl.glfw.CallbackBridge.mouseY);
    }

    private void tickMenuCursor(float deltaScale) {
        float x = Math.abs(rightX) > Math.abs(leftX) ? rightX : leftX;
        float y = Math.abs(rightY) > Math.abs(leftY) ? rightY : leftY;

        float dx = 0f;
        float dy = 0f;

        float sensitivityMultiplier = mappingStore.getMenuCursorSensitivityMultiplier();

        if (x != 0f || y != 0f) {
            float magnitude = Math.min(1f, (float) Math.sqrt(x * x + y * y));
            float acceleration = Math.max(0.35f, magnitude * magnitude);
            float sensitivity = BASE_MENU_CURSOR_SENSITIVITY * sensitivityMultiplier;
            dx += x * acceleration * sensitivity * deltaScale;
            dy += y * acceleration * sensitivity * deltaScale;
        }

        float dpadStep = BASE_DPAD_CURSOR_STEP * sensitivityMultiplier * deltaScale;
        if (hatLeft) dx -= dpadStep;
        if (hatRight) dx += dpadStep;
        if (hatUp) dy -= dpadStep;
        if (hatDown) dy += dpadStep;

        if (dx != 0f || dy != 0f) {
            GamepadAction.moveCursorBy(dx, dy);
        }
    }

    private void updateDirection() {
        if (!isGameMode()) {
            releaseDirection();
            return;
        }

        int newDirection = directionFor(leftX, leftY);
        if (newDirection == currentDirection) return;

        sendDirectional(currentDirection, false);
        currentDirection = newDirection;
        sendDirectional(currentDirection, true);
    }

    private void releaseDirection() {
        sendDirectional(currentDirection, false);
        currentDirection = DIRECTION_NONE;
    }

    private static int directionFor(float x, float y) {
        if (Math.sqrt(x * x + y * y) < DEADZONE) return DIRECTION_NONE;

        double angle = Math.toDegrees(Math.atan2(-y, x));
        if (angle < 0) angle += 360.0;

        return ((int) ((angle + 22.5) / 45.0)) % 8;
    }

    private void sendDirectional(int direction, boolean isDown) {
        switch (direction) {
            case DIRECTION_NORTH:
                GamepadAction.FORWARD.perform(isDown);
                break;
            case DIRECTION_NORTH_EAST:
                GamepadAction.FORWARD.perform(isDown);
                GamepadAction.RIGHT.perform(isDown);
                break;
            case DIRECTION_EAST:
                GamepadAction.RIGHT.perform(isDown);
                break;
            case DIRECTION_SOUTH_EAST:
                GamepadAction.RIGHT.perform(isDown);
                GamepadAction.BACKWARD.perform(isDown);
                break;
            case DIRECTION_SOUTH:
                GamepadAction.BACKWARD.perform(isDown);
                break;
            case DIRECTION_SOUTH_WEST:
                GamepadAction.BACKWARD.perform(isDown);
                GamepadAction.LEFT.perform(isDown);
                break;
            case DIRECTION_WEST:
                GamepadAction.LEFT.perform(isDown);
                break;
            case DIRECTION_NORTH_WEST:
                GamepadAction.FORWARD.perform(isDown);
                GamepadAction.LEFT.perform(isDown);
                break;
            case DIRECTION_NONE:
            default:
                break;
        }
    }

    private void sendMappedButton(@NonNull GamepadButton button, boolean isDown) {
        boolean gameMode = isGameMode();
        GamepadAction action = mappingStore.getButtonAction(button, gameMode);

        // Guard against old saved prefs from earlier patches where menu A/R2 were ENTER.
        // Those prefs survive reinstall/rebuild and make it look like A is not mapped to click.
        if (!gameMode && (button == GamepadButton.BUTTON_A
                || button == GamepadButton.BUTTON_R2
                || button == GamepadButton.DPAD_CENTER)
                && action == GamepadAction.ENTER) {
            Logging.i(TAG, "Overriding old menu " + button + " ENTER mapping to MOUSE_LEFT");
            action = GamepadAction.MOUSE_LEFT;
        }

        Logging.i(TAG, "Button=" + button + ", down=" + isDown
                + ", gameMode=" + gameMode
                + ", action=" + action.name()
                + ", cursor=" + org.lwjgl.glfw.CallbackBridge.mouseX + ","
                + org.lwjgl.glfw.CallbackBridge.mouseY);

        boolean pulseMenuMouseClick = !gameMode && action.isMouseButton();
        action.perform(isDown, pulseMenuMouseClick);
    }

    private void updateHatButton(@NonNull GamepadButton button, boolean isDown) {
        switch (button) {
            case DPAD_UP:
                if (hatUp == isDown) return;
                hatUp = isDown;
                sendMappedButton(button, isDown);
                break;
            case DPAD_DOWN:
                if (hatDown == isDown) return;
                hatDown = isDown;
                sendMappedButton(button, isDown);
                break;
            case DPAD_LEFT:
                if (hatLeft == isDown) return;
                hatLeft = isDown;
                sendMappedButton(button, isDown);
                break;
            case DPAD_RIGHT:
                if (hatRight == isDown) return;
                hatRight = isDown;
                sendMappedButton(button, isDown);
                break;
            default:
                break;
        }
    }

    private void updateTrigger(boolean left, boolean isDown) {
        if (left) {
            if (leftTriggerDown == isDown) return;
            leftTriggerDown = isDown;
            sendMappedButton(GamepadButton.BUTTON_L2, isDown);
        } else {
            if (rightTriggerDown == isDown) return;
            rightTriggerDown = isDown;
            sendMappedButton(GamepadButton.BUTTON_R2, isDown);
        }
    }
}
