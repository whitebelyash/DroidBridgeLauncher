package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.settings.LauncherPreferences;
import ca.dnamobile.javalauncher.controls.TouchHotbarHitbox;
import net.kdt.pojavlaunch.utils.JREUtils;

import org.libsdl.app.SDLControllerManager;
import org.lwjgl.glfw.CallbackBridge;

// Renders the game on Android's native SurfaceViews that are available giving the user options to
// render graphics on either Texture or native Surface
public class MinecraftGLSurface extends FrameLayout implements GrabListener {
    private View renderView;
    private TextureView textureView;
    private SurfaceView nativeSurfaceView;
    private Surface textureSurface;

    private int viewWidth = 1;
    private int viewHeight = 1;
    private int renderWidth = 1;
    private int renderHeight = 1;
    private float inputScaleX = 1.0f;
    private float inputScaleY = 1.0f;

    private SurfaceReadyListener surfaceReadyListener;
    private OnRenderingStartedListener renderingStartedListener;
    private boolean renderingStarted = false;
    private volatile boolean bridgeWindowAttached = false;
    private volatile boolean grabbed = false;

    private float lastTouchX;
    private float lastTouchY;
    private boolean trackingTouch;

    private final Handler touchHandler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private float touchDownX;
    private float touchDownY;
    private boolean touchMovedPastSlop;
    private boolean touchUiTapCandidate;
    private boolean touchLongPressAttackActive;
    @Nullable private Runnable touchLongPressRunnable;
    public static volatile boolean sdlEnabled = false;

    public MinecraftGLSurface(Context context) {
        this(context, null);
    }

    public MinecraftGLSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setFocusableInTouchMode(true);
        CallbackBridge.init(context);
        CallbackBridge.addGrabListener(this);
        setOnCapturedPointerListener(this::handleCapturedPointer);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    public void start(boolean isAlreadyRunning) {
        if (renderView != null) return;

        renderingStarted = false;
        boolean useNativeSurfaceView = LauncherPreferences.isUseNativeSurfaceView(getContext());

        if (useNativeSurfaceView) {
            startNativeSurfaceView(isAlreadyRunning);
        } else {
            startTextureView(isAlreadyRunning);
        }
    }

    private void startTextureView(boolean isAlreadyRunning) {
        textureView = new TextureView(getContext());
        renderView = textureView;

        textureView.setOpaque(true);
        textureView.setAlpha(1.0f);
        textureView.setFocusable(false);

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private boolean called = isAlreadyRunning;

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                RenderSize size = updateScaledSizeFromView(width, height);
                surface.setDefaultBufferSize(size.renderWidth, size.renderHeight);

                releaseTextureSurface();
                textureSurface = new Surface(surface);

                if (called) {
                    attachBridgeWindow(textureSurface, size);
                    return;
                }

                called = true;
                realStart(textureSurface, size, false);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                RenderSize size = updateScaledSizeFromView(width, height);
                surface.setDefaultBufferSize(size.renderWidth, size.renderHeight);
                refreshSize(size);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                bridgeWindowAttached = false;
                JREUtils.releaseBridgeWindow();
                releaseTextureSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                notifyRenderingStartedOnce();
            }
        });

        addRenderView(textureView);
    }

    private void startNativeSurfaceView(boolean isAlreadyRunning) {
        nativeSurfaceView = new SurfaceView(getContext());
        renderView = nativeSurfaceView;

        nativeSurfaceView.setFocusable(false);
        nativeSurfaceView.setZOrderOnTop(false);
        nativeSurfaceView.setZOrderMediaOverlay(false);

        nativeSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            private boolean called = isAlreadyRunning;

            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                RenderSize size = updateScaledSizeFromView(nativeSurfaceView.getWidth(), nativeSurfaceView.getHeight());
                applyNativeSurfaceBufferSize(holder, size);

                if (called) {
                    attachBridgeWindow(holder.getSurface(), size);
                    notifyRenderingStartedSoon();
                    return;
                }

                called = true;
                realStart(holder.getSurface(), size, true);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                RenderSize size = updateScaledSizeFromView(nativeSurfaceView.getWidth(), nativeSurfaceView.getHeight());
                applyNativeSurfaceBufferSize(holder, size);
                refreshSize(size);
                if (holder.getSurface().isValid()) {
                    JREUtils.setupBridgeWindow(holder.getSurface());
                    bridgeWindowAttached = true;
                    notifyRenderingStartedSoon();
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                bridgeWindowAttached = false;
                JREUtils.releaseBridgeWindow();
            }
        });

        addRenderView(nativeSurfaceView);
    }

    private void addRenderView(@NonNull View child) {
        addView(child, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void refreshSize() {
        int width = safeWidth(getWidth());
        int height = safeHeight(getHeight());

        if (width <= 1 && renderView != null) width = safeWidth(renderView.getWidth());
        if (height <= 1 && renderView != null) height = safeHeight(renderView.getHeight());

        RenderSize size = updateScaledSizeFromView(width, height);
        refreshSize(size);
    }

    private void refreshSize(@NonNull RenderSize size) {
        updateSizeFields(size);

        if (textureView != null && textureView.getSurfaceTexture() != null) {
            textureView.getSurfaceTexture().setDefaultBufferSize(size.renderWidth, size.renderHeight);
        }

        if (nativeSurfaceView != null) {
            applyNativeSurfaceBufferSize(nativeSurfaceView.getHolder(), size);
        }

        if (bridgeWindowAttached) {
            CallbackBridge.sendUpdateWindowSize(size.renderWidth, size.renderHeight);
        }
    }

    private void realStart(@NonNull Surface surface, @NonNull RenderSize size, boolean assumeRenderingStarted) {
        attachBridgeWindow(surface, size);

        if (assumeRenderingStarted) {
            notifyRenderingStartedSoon();
        }

        if (surfaceReadyListener != null) {
            new Thread(surfaceReadyListener::isReady, "JVM Main thread").start();
        }
    }

    private void attachBridgeWindow(@NonNull Surface surface, @NonNull RenderSize size) {
        JREUtils.setupBridgeWindow(surface);
        bridgeWindowAttached = true;

        updateSizeFields(size);
        CallbackBridge.mouseX = size.renderWidth / 2f;
        CallbackBridge.mouseY = size.renderHeight / 2f;
        CallbackBridge.sendUpdateWindowSize(size.renderWidth, size.renderHeight);
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
    }

    @NonNull
    private RenderSize updateScaledSizeFromView(int width, int height) {
        int safeViewWidth = safeWidth(width);
        int safeViewHeight = safeHeight(height);
        int percent = LauncherPreferences.getGameResolutionScalePercent(getContext());
        int safeRenderWidth = Math.max(1, Math.round(safeViewWidth * (percent / 100.0f)));
        int safeRenderHeight = Math.max(1, Math.round(safeViewHeight * (percent / 100.0f)));

        viewWidth = safeViewWidth;
        viewHeight = safeViewHeight;
        renderWidth = safeRenderWidth;
        renderHeight = safeRenderHeight;
        inputScaleX = renderWidth / (float) Math.max(1, viewWidth);
        inputScaleY = renderHeight / (float) Math.max(1, viewHeight);

        RenderSize size = new RenderSize(viewWidth, viewHeight, renderWidth, renderHeight);
        updateSizeFields(size);
        return size;
    }

    private void applyNativeSurfaceBufferSize(@NonNull SurfaceHolder holder, @NonNull RenderSize size) {
        try {
            if (size.renderWidth == size.viewWidth && size.renderHeight == size.viewHeight) {
                holder.setSizeFromLayout();
            } else {
                holder.setFixedSize(size.renderWidth, size.renderHeight);
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateSizeFields(@NonNull RenderSize size) {
        CallbackBridge.windowWidth = Math.max(1, size.renderWidth);
        CallbackBridge.windowHeight = Math.max(1, size.renderHeight);

        // Keep physical* in the same coordinate space currently used by the rest of this bridge.
        // Touch/mouse input is explicitly scaled from Android view coordinates into this render space below.
        CallbackBridge.physicalWidth = CallbackBridge.windowWidth;
        CallbackBridge.physicalHeight = CallbackBridge.windowHeight;
    }

    private int safeWidth(int width) {
        return Math.max(1, width > 0 ? width : getWidth());
    }

    private int safeHeight(int height) {
        return Math.max(1, height > 0 ? height : getHeight());
    }

    private float scaleInputX(float x) {
        return x * inputScaleX;
    }

    private float scaleInputY(float y) {
        return y * inputScaleY;
    }

    private float scaleDeltaX(float dx) {
        return dx * inputScaleX;
    }

    private float scaleDeltaY(float dy) {
        return dy * inputScaleY;
    }

    private static final class RenderSize {
        final int viewWidth;
        final int viewHeight;
        final int renderWidth;
        final int renderHeight;

        RenderSize(int viewWidth, int viewHeight, int renderWidth, int renderHeight) {
            this.viewWidth = Math.max(1, viewWidth);
            this.viewHeight = Math.max(1, viewHeight);
            this.renderWidth = Math.max(1, renderWidth);
            this.renderHeight = Math.max(1, renderHeight);
        }
    }

    private void notifyRenderingStartedSoon() {
        postDelayed(this::notifyRenderingStartedOnce, 100);
    }

    private void notifyRenderingStartedOnce() {
        if (renderingStarted) return;
        renderingStarted = true;
        if (renderingStartedListener != null) renderingStartedListener.isStarted();
    }

    private void releaseTextureSurface() {
        if (textureSurface != null) {
            textureSurface.release();
            textureSurface = null;
        }
    }

    /**
     * Entry point used by TouchControlsOverlay. Calling this directly avoids Android
     * routing the event into the TextureView/SurfaceView child and bypassing this bridge.
     */
    public boolean handleTouchFromOverlay(@NonNull MotionEvent event) {
        return handleTouchEventInternal(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleTouchEventInternal(event);
    }

    private boolean handleTouchEventInternal(@NonNull MotionEvent event) {
        requestFocusIfNeeded();

        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                trackingTouch = true;
                touchMovedPastSlop = false;
                touchLongPressAttackActive = false;
                touchDownX = x;
                touchDownY = y;
                lastTouchX = x;
                lastTouchY = y;
                touchUiTapCandidate = isLikelyHotbarTap(x, y);
                if (grabbed) {
                    // Critical: never send an absolute cursor position on ACTION_DOWN while
                    // Minecraft is grabbing the mouse. In grabbed mode, Minecraft treats
                    // cursor movement as camera movement, so warping to the finger location
                    // makes the camera jump toward the touched edge/corner. Store the finger
                    // position only; send relative deltas after the drag passes touch slop.
                    if (!touchUiTapCandidate) {
                        scheduleTouchLongPressAttack();
                    }
                } else {
                    // Menu/inventory mode behaves like a normal mouse click.
                    sendAbsoluteCursor(x, y);
                    sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!trackingTouch) {
                    trackingTouch = true;
                    touchMovedPastSlop = false;
                    touchLongPressAttackActive = false;
                    touchDownX = x;
                    touchDownY = y;
                    lastTouchX = x;
                    lastTouchY = y;
                    touchUiTapCandidate = isLikelyHotbarTap(x, y);
                    if (grabbed) {
                        if (!touchUiTapCandidate) {
                            scheduleTouchLongPressAttack();
                        }
                    } else {
                        sendAbsoluteCursor(x, y);
                    }
                    return true;
                }

                if (grabbed) {
                    float totalDx = x - touchDownX;
                    float totalDy = y - touchDownY;
                    if (!touchMovedPastSlop
                            && ((totalDx * totalDx) + (totalDy * totalDy)) > (touchSlop * touchSlop)) {
                        touchMovedPastSlop = true;
                        cancelTouchLongPressAttack(true);
                    }

                    if (touchMovedPastSlop) {
                        sendRelativeCursor(x - lastTouchX, y - lastTouchY);
                    }
                } else {
                    sendAbsoluteCursor(x, y);
                }

                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_UP:
                if (grabbed) {
                    cancelTouchLongPressAttack(false);
                    if (touchLongPressAttackActive) {
                        sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
                    } else if (trackingTouch && !touchMovedPastSlop) {
                        if (touchUiTapCandidate || isLikelyHotbarTap(x, y)) {
                            sendHotbarSlotIfNeeded(x, y);
                        } else {
                            sendAttackTap();
                        }
                    }
                } else {
                    sendAbsoluteCursor(x, y);
                    sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
                }
                resetTouchTracking();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (grabbed) {
                    cancelTouchLongPressAttack(true);
                } else {
                    sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
                }
                resetTouchTracking();
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isGamepadMotionEvent(event)) {
            if (sdlEnabled && SDLControllerManager.handleJoystickMotionEvent(event)) {
                return true;
            }
            return super.dispatchGenericMotionEvent(event);
        }

        int pointerIndex = findMousePointerIndex(event);
        if (pointerIndex < 0) {
            return super.dispatchGenericMotionEvent(event);
        }

        requestFocusIfNeeded();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                if (grabbed) {
                    float relX = getRelativeAxis(event, MotionEvent.AXIS_RELATIVE_X, pointerIndex);
                    float relY = getRelativeAxis(event, MotionEvent.AXIS_RELATIVE_Y, pointerIndex);
                    if (relX == 0f && relY == 0f) {
                        relX = event.getX(pointerIndex) - lastTouchX;
                        relY = event.getY(pointerIndex) - lastTouchY;
                    }
                    sendRelativeCursor(relX, relY);
                    lastTouchX = event.getX(pointerIndex);
                    lastTouchY = event.getY(pointerIndex);
                } else {
                    sendAbsoluteCursor(event.getX(pointerIndex), event.getY(pointerIndex));
                }
                return true;

            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;

            case MotionEvent.ACTION_BUTTON_PRESS:
                return sendMouseButtonUnconverted(event.getActionButton(), true);

            case MotionEvent.ACTION_BUTTON_RELEASE:
                return sendMouseButtonUnconverted(event.getActionButton(), false);

            default:
                return super.dispatchGenericMotionEvent(event);
        }
    }

    private boolean handleCapturedPointer(View view, MotionEvent event) {
        requestFocusIfNeeded();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                float relX = getRelativeAxis(event, MotionEvent.AXIS_RELATIVE_X, 0);
                float relY = getRelativeAxis(event, MotionEvent.AXIS_RELATIVE_Y, 0);
                if (relX == 0f && relY == 0f) {
                    relX = event.getX();
                    relY = event.getY();
                }
                sendRelativeCursor(relX, relY);
                return true;

            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;

            case MotionEvent.ACTION_BUTTON_PRESS:
                return sendMouseButtonUnconverted(event.getActionButton(), true);

            case MotionEvent.ACTION_BUTTON_RELEASE:
                return sendMouseButtonUnconverted(event.getActionButton(), false);

            default:
                return false;
        }
    }

    private static boolean isGamepadMotionEvent(@NonNull MotionEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    private static int findMousePointerIndex(@NonNull MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int toolType = event.getToolType(i);
            if (toolType == MotionEvent.TOOL_TYPE_MOUSE || toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                return i;
            }
        }

        int source = event.getSource();
        if ((source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                || (source & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
                || (source & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
            return 0;
        }

        return -1;
    }

    private static float getRelativeAxis(@NonNull MotionEvent event, int axis, int pointerIndex) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            float value = event.getAxisValue(axis, pointerIndex);
            if (value != 0f) return value;
        }
        return event.getAxisValue(axis);
    }

    private boolean isLikelyHotbarTap(float x, float y) {
        return hotbarSlotForTouch(x, y) >= 0;
    }

    private int hotbarSlotForTouch(float x, float y) {
        return TouchHotbarHitbox.slotForTouch(
                getContext(),
                getWidth(),
                getHeight(),
                CallbackBridge.physicalWidth,
                CallbackBridge.physicalHeight,
                x,
                y
        );
    }


    private void scheduleTouchLongPressAttack() {
        cancelTouchLongPressAttack(false);
        touchLongPressRunnable = () -> {
            if (!trackingTouch || touchMovedPastSlop || touchUiTapCandidate || touchLongPressAttackActive) {
                return;
            }
            touchLongPressAttackActive = true;
            sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        };
        touchHandler.postDelayed(touchLongPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelTouchLongPressAttack(boolean releaseActivePress) {
        if (touchLongPressRunnable != null) {
            touchHandler.removeCallbacks(touchLongPressRunnable);
            touchLongPressRunnable = null;
        }
        if (releaseActivePress && touchLongPressAttackActive) {
            sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
            touchLongPressAttackActive = false;
        }
    }

    private void sendAttackTap() {
        sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
    }

    private boolean sendHotbarSlotIfNeeded(float x, float y) {
        int slot = hotbarSlotForTouch(x, y);
        if (slot < 0) return false;
        sendKeyTap(49 + slot); // GLFW_KEY_1 through GLFW_KEY_9
        return true;
    }

    private void sendKeyTap(int keyCode) {
        CallbackBridge.setInputReady(true);
        CallbackBridge.sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        CallbackBridge.setModifiers(keyCode, true);
        CallbackBridge.sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
        CallbackBridge.setModifiers(keyCode, false);
    }

    private void sendTapClickAt(float x, float y) {
        CallbackBridge.setInputReady(true);
        float clampedX = clamp(scaleInputX(x), 0f, Math.max(1, CallbackBridge.windowWidth));
        float clampedY = clamp(scaleInputY(y), 0f, Math.max(1, CallbackBridge.windowHeight));
        // Keep the grabbed-mode invariant: do not call sendCursorPos() for a tap.
        // putMouseEventWithCoords() carries the click coordinates without first warping
        // the grabbed cursor, which prevents touch taps from snapping the camera.
        CallbackBridge.putMouseEventWithCoords(
                LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
                clampedX,
                clampedY
        );
    }

    private void resetTouchTracking() {
        trackingTouch = false;
        touchMovedPastSlop = false;
        touchUiTapCandidate = false;
        touchLongPressAttackActive = false;
        touchDownX = 0f;
        touchDownY = 0f;
        lastTouchX = 0f;
        lastTouchY = 0f;
    }

    private void sendAbsoluteCursor(float x, float y) {
        CallbackBridge.setInputReady(true);
        CallbackBridge.mouseX = clamp(scaleInputX(x), 0f, Math.max(1, CallbackBridge.windowWidth));
        CallbackBridge.mouseY = clamp(scaleInputY(y), 0f, Math.max(1, CallbackBridge.windowHeight));
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
    }

    private void sendRelativeCursor(float dx, float dy) {
        CallbackBridge.setInputReady(true);
        CallbackBridge.mouseX += scaleDeltaX(dx);
        CallbackBridge.mouseY += scaleDeltaY(dy);
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
    }

    public static boolean sendMouseButtonUnconverted(int button, boolean status) {
        int glfwButton;
        switch (button) {
            case MotionEvent.BUTTON_PRIMARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
            case MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
            default:
                return false;
        }
        sendMouseButton(glfwButton, status);
        return true;
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.setInputReady(true);
        CallbackBridge.sendMouseButton(button, status);
    }

    private void requestFocusIfNeeded() {
        if (!hasFocus()) requestFocus();
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        grabbed = isGrabbing;
        post(() -> {
            if (isGrabbing) {
                CallbackBridge.mouseX = CallbackBridge.windowWidth / 2f;
                CallbackBridge.mouseY = CallbackBridge.windowHeight / 2f;
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasWindowFocus()) {
                    requestPointerCapture();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                releasePointerCapture();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelTouchLongPressAttack(true);
        CallbackBridge.removeGrabListener(this);
        bridgeWindowAttached = false;
        JREUtils.releaseBridgeWindow();
        releaseTextureSurface();
        super.onDetachedFromWindow();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public interface SurfaceReadyListener {
        void isReady();
    }

    public void setSurfaceReadyListener(@Nullable SurfaceReadyListener listener) {
        this.surfaceReadyListener = listener;
    }

    public interface OnRenderingStartedListener {
        void isStarted();
    }

    public void setOnRenderingStartedListener(@Nullable OnRenderingStartedListener listener) {
        this.renderingStartedListener = listener;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (sdlEnabled && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (SDLControllerManager.handleJoystickMotionEvent(event)) {
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int deviceId = event.getDeviceId();
        if (sdlEnabled && SDLControllerManager.isDeviceSDLJoystick(deviceId)) {
            if (SDLControllerManager.onNativePadDown(deviceId, keyCode)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int deviceId = event.getDeviceId();
        if (sdlEnabled && SDLControllerManager.isDeviceSDLJoystick(deviceId)) {
            if (SDLControllerManager.onNativePadUp(deviceId, keyCode)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

}
