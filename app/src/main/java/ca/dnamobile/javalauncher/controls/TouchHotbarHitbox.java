package ca.dnamobile.javalauncher.controls;

import android.content.Context;
import android.graphics.RectF;

import androidx.annotation.NonNull;

public final class TouchHotbarHitbox {
    public static final int SLOT_COUNT = 9;

    private TouchHotbarHitbox() {
    }

    public static final class Result {
        public final RectF hotbarBounds;
        public final RectF touchBounds;
        public final float scale;
        public final float slotWidth;

        private Result(@NonNull RectF hotbarBounds, @NonNull RectF touchBounds, float scale) {
            this.hotbarBounds = hotbarBounds;
            this.touchBounds = touchBounds;
            this.scale = scale;
            this.slotWidth = Math.max(1f, hotbarBounds.width() / SLOT_COUNT);
        }

        public int slotFor(float x, float y) {
            if (!touchBounds.contains(x, y)) return -1;

            // Keep horizontal slot math tied to the real hotbar width, not touch padding.
            // This avoids outside-left/outside-right touches bleeding into slot 1/9.
            if (x < hotbarBounds.left || x >= hotbarBounds.right) return -1;

            float localX = clamp(x - hotbarBounds.left, 0f, hotbarBounds.width() - 1f);
            int slot = (int) ((localX * SLOT_COUNT) / hotbarBounds.width());
            return Math.max(0, Math.min(SLOT_COUNT - 1, slot));
        }
    }

    @NonNull
    public static Result calculate(
            @NonNull Context context,
            float viewWidth,
            float viewHeight,
            float fallbackWidth,
            float fallbackHeight
    ) {
        float width = Math.max(1f, viewWidth > 0f ? viewWidth : fallbackWidth);
        float height = Math.max(1f, viewHeight > 0f ? viewHeight : fallbackHeight);

        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        int scaleOverride = ControlsPreferences.getHotbarGuiScaleOverride(context);
        float scale = scaleOverride > 0 ? scaleOverride : estimateHotbarGuiScale(width, height);

        float hotbarWidth = ControlsPreferences.getHotbarWidthGui(context) * scale;
        float hotbarHeight = ControlsPreferences.getHotbarHeightGui(context) * scale;
        float xOffset = ControlsPreferences.getHotbarXOffsetDp(context) * density;
        float yOffset = ControlsPreferences.getHotbarYOffsetDp(context) * density;
        float verticalPadding = Math.max(
                ControlsPreferences.getHotbarVerticalPaddingDp(context) * density,
                2f * scale
        );

        float left = (width / 2f) - (hotbarWidth / 2f) + xOffset;
        float top = height - hotbarHeight - yOffset;
        RectF hotbar = new RectF(left, top, left + hotbarWidth, top + hotbarHeight);

        // Keep horizontal strict. Only grow vertically for finger forgiveness.
        RectF touch = new RectF(hotbar.left, hotbar.top - verticalPadding, hotbar.right, hotbar.bottom + verticalPadding);
        return new Result(hotbar, touch, scale);
    }

    public static int slotForTouch(
            @NonNull Context context,
            float viewWidth,
            float viewHeight,
            float fallbackWidth,
            float fallbackHeight,
            float x,
            float y
    ) {
        return calculate(context, viewWidth, viewHeight, fallbackWidth, fallbackHeight).slotFor(x, y);
    }

    private static float estimateHotbarGuiScale(float width, float height) {
        // Existing safe default. In-game editor lets the user override this to match
        // Minecraft GUI Scale 3/4/Auto without rebuilding.
        float shortSide = Math.min(width, height);
        float byHeight = Math.max(1f, Math.round(shortSide / 360f));
        float vanillaMax = Math.max(1f, Math.min((float) Math.floor(width / 320f), (float) Math.floor(height / 240f)));
        return Math.max(1f, Math.min(byHeight, vanillaMax));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
