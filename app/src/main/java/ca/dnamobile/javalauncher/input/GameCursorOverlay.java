package ca.dnamobile.javalauncher.input;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.NonNull;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Visible software cursor used for Minecraft menus.
 *
 * This view fills the whole game root and draws the cursor inside itself.
 * Do not make this view 36x36 or the cursor will be clamped into a tiny
 * square in the top-left corner.
 */
public final class GameCursorOverlay extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cursorPath = new Path();
    private final GamepadMappingStore mappingStore;

    private boolean removed;
    private float drawX;
    private float drawY;

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

        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.5f));

        buildPath();

        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void removeSelf() {
        removed = true;
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
        boolean shouldShow = mappingStore.isShowCursorOverlay()
                && !mappingStore.isForceGameMode()
                && !CallbackBridge.isGrabbing();

        setVisibility(shouldShow ? VISIBLE : GONE);
        if (!shouldShow) return;

        int rootWidth = Math.max(1, getWidth());
        int rootHeight = Math.max(1, getHeight());

        float bridgeWidth = Math.max(1f, CallbackBridge.windowWidth > 0
                ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth);
        float bridgeHeight = Math.max(1f, CallbackBridge.windowHeight > 0
                ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight);

        drawX = CallbackBridge.mouseX * rootWidth / bridgeWidth;
        drawY = CallbackBridge.mouseY * rootHeight / bridgeHeight;

        drawX = clamp(drawX, 0f, rootWidth - dp(4f));
        drawY = clamp(drawY, 0f, rootHeight - dp(4f));

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getVisibility() != VISIBLE) return;

        canvas.save();
        canvas.translate(drawX, drawY);
        canvas.drawPath(cursorPath, fillPaint);
        canvas.drawPath(cursorPath, strokePaint);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
