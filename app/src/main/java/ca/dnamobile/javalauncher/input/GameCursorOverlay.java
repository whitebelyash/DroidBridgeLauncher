package ca.dnamobile.javalauncher.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.controls.ControlsPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Visible software cursor used for Minecraft menus.
 *
 * Important touch rule:
 * This class must never be a touch target. Earlier versions used a full-screen
 * View and then a small moving View. Both can still break Android hit testing:
 * when a child View is above the game surface, Android does not keep searching
 * lower siblings after that child rejects ACTION_DOWN.
 *
 * The Zalith-style safe approach is to keep this View gone/1x1 and draw the
 * cursor through the parent ViewGroupOverlay. ViewGroupOverlay is visual only,
 * so touchscreen taps, right-side camera swipes, Touch Controller buttons, and
 * Minecraft menu clicks continue to reach the game surface underneath.
 */
public final class GameCursorOverlay extends View {
    private static final float CURSOR_CANVAS_DP = 42f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cursorPath = new Path();
    private final GamepadMappingStore mappingStore;

    @Nullable private ViewGroup overlayParent;
    private boolean drawableAdded;
    private boolean removed;
    private boolean cursorVisible;

    private final Drawable cursorDrawable = new Drawable() {
        @Override
        public void draw(@NonNull Canvas canvas) {
            if (!cursorVisible) return;

            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(cursorPath, fillPaint);
            canvas.drawPath(cursorPath, strokePaint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    };

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            updateFromBridge();
            if (!removed) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    public GameCursorOverlay(@NonNull Context context) {
        super(context);
        mappingStore = GamepadMappingStore.get(context);

        // This view is only a lifecycle owner for the overlay drawable.
        // Keep it out of layout hit testing completely.
        setVisibility(GONE);
        setWillNotDraw(true);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setHapticFeedbackEnabled(false);
        setSoundEffectsEnabled(false);
        setEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.5f));

        buildPath();

        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setVisibility(GONE);
        attachDrawableToParent();
    }

    @Override
    protected void onDetachedFromWindow() {
        detachDrawableFromParent();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Stay effectively non-existent for hit testing/layout.
        setMeasuredDimension(1, 1);
    }

    public void removeSelf() {
        removed = true;
        cursorVisible = false;
        cursorDrawable.setBounds(0, 0, 0, 0);
        cursorDrawable.invalidateSelf();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        detachDrawableFromParent();
    }

    private void attachDrawableToParent() {
        if (drawableAdded) return;
        if (!(getParent() instanceof ViewGroup)) return;

        overlayParent = (ViewGroup) getParent();
        overlayParent.getOverlay().add(cursorDrawable);
        drawableAdded = true;
    }

    private void detachDrawableFromParent() {
        if (!drawableAdded || overlayParent == null) return;

        overlayParent.getOverlay().remove(cursorDrawable);
        drawableAdded = false;
        overlayParent = null;
    }

    private void buildPath() {
        float s = dp(1f);
        cursorPath.reset();

        // Classic simple arrow pointer.
        cursorPath.moveTo(0f, 0f);
        cursorPath.lineTo(0f, 22f * s);
        cursorPath.lineTo(6f * s, 16f * s);
        cursorPath.lineTo(10f * s, 27f * s);
        cursorPath.lineTo(15f * s, 25f * s);
        cursorPath.lineTo(11f * s, 14f * s);
        cursorPath.lineTo(19f * s, 14f * s);
        cursorPath.close();
    }

    private void updateFromBridge() {
        attachDrawableToParent();

        boolean showTouchVirtualCursor = ControlsPreferences.isVirtualMouseEnabled(getContext());
        boolean showControllerMenuCursor = mappingStore.isShowCursorOverlay()
                && !mappingStore.isForceGameMode()
                && !CallbackBridge.isGrabbing();
        boolean shouldShow = showTouchVirtualCursor || showControllerMenuCursor;

        cursorVisible = shouldShow;
        if (!shouldShow || overlayParent == null) {
            cursorDrawable.setBounds(0, 0, 0, 0);
            cursorDrawable.invalidateSelf();
            return;
        }

        int rootWidth = Math.max(1, overlayParent.getWidth());
        int rootHeight = Math.max(1, overlayParent.getHeight());
        int cursorSize = Math.max(1, Math.round(dp(CURSOR_CANVAS_DP)));

        float bridgeWidth = Math.max(1f, CallbackBridge.windowWidth > 0
                ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth);
        float bridgeHeight = Math.max(1f, CallbackBridge.windowHeight > 0
                ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight);

        float drawX = CallbackBridge.mouseX * rootWidth / bridgeWidth;
        float drawY = CallbackBridge.mouseY * rootHeight / bridgeHeight;

        drawX = clamp(drawX, 0f, rootWidth - cursorSize);
        drawY = clamp(drawY, 0f, rootHeight - cursorSize);

        int left = Math.round(drawX);
        int top = Math.round(drawY);
        cursorDrawable.setBounds(left, top, left + cursorSize, top + cursorSize);
        cursorDrawable.invalidateSelf();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Drawing happens through the parent ViewGroupOverlay instead.
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
