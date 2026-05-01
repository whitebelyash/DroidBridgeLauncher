package ca.dnamobile.javalauncher.controls;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.util.SparseArray;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import org.lwjgl.glfw.CallbackBridge;

import ca.dnamobile.javalauncher.feature.log.Logging;
import net.kdt.pojavlaunch.MinecraftGLSurface;

/**
 * Runtime/editor overlay. It deliberately avoids XML so it can be injected over the
 * existing Minecraft surface without rewriting activity_game.xml.
 */
public final class TouchControlsOverlay extends FrameLayout implements TouchControlButtonView.Listener {
    public interface AppMenuListener {
        void onTouchControlsMenuRequested();
    }

    private static final String TAG = "TouchControlsOverlay";

    private boolean editMode;
    private boolean controlsVisible = true;
    private boolean rebuildPending;
    @Nullable private File layoutFile;
    @NonNull private TouchControlsLayoutData layoutData = TouchControlsLayoutData.defaultLayout();
    @Nullable private AppMenuListener appMenuListener;
    @Nullable private View passthroughTarget;
    @Nullable private TextView sizePreviewPercentBadge;

    /**
     * Runtime multi-touch routing:
     * - pointers that begin on a visible touch button are owned by that button
     * - the first pointer that begins on empty space is forwarded to MinecraftGLSurface
     *   as a clean single-pointer stream
     *
     * Android may reorder pointer indexes when a finger lifts/re-enters. Tracking by
     * pointer ID keeps the empty-space pointer stable while other fingers hold buttons.
     *
     * Do not forward the full MotionEvent or a split multi-pointer stream to Minecraft
     * while buttons are held. MinecraftGLSurface expects a normal DOWN/MOVE/UP sequence
     * for touch menus, hotbar taps and camera look; sending ACTION_POINTER_DOWN/UP while
     * another finger owns a virtual button can make the camera jump or block GUI taps.
     */
    private static final int NO_POINTER_ID = -1;
    private static final int MOUSE_BUTTON_LEFT = 0;

    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private final int cameraTouchSlop;

    /** Pointer ID for the right-thumb look/attack stream. */
    private int cameraPointerId = NO_POINTER_ID;
    private float cameraDownX;
    private float cameraDownY;
    private float cameraLastX;
    private float cameraLastY;
    private boolean cameraMovedPastSlop;
    private boolean cameraLongPressAttackActive;
    @Nullable private Runnable cameraLongPressRunnable;

    /** GUI fallback: used only when Minecraft is not grabbing the mouse. */
    private int passthroughPointerId = NO_POINTER_ID;
    private long passthroughDownTime;

    /** In-game hotbar touch routing, mirrors Zalith's separate HotbarView. */
    private int hotbarPointerId = NO_POINTER_ID;
    private int hotbarLastSlot = -1;

    @NonNull private final SparseArray<TouchControlButtonView> controlPointerTargets = new SparseArray<>();

    private final Paint hotbarDebugFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hotbarDebugStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hotbarDebugSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hotbarDebugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchControlsOverlay(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(true);
        setMotionEventSplittingEnabled(true);
        setWillNotDraw(false);

        hotbarDebugFillPaint.setColor(0x44FFEB3B);
        hotbarDebugFillPaint.setStyle(Paint.Style.FILL);

        hotbarDebugStrokePaint.setColor(Color.YELLOW);
        hotbarDebugStrokePaint.setStyle(Paint.Style.STROKE);
        hotbarDebugStrokePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);

        hotbarDebugSlotPaint.setColor(0xCCFF9800);
        hotbarDebugSlotPaint.setStyle(Paint.Style.STROKE);
        hotbarDebugSlotPaint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);

        hotbarDebugTextPaint.setColor(Color.WHITE);
        hotbarDebugTextPaint.setTextAlign(Paint.Align.CENTER);
        hotbarDebugTextPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        hotbarDebugTextPaint.setShadowLayer(3f, 0f, 0f, Color.BLACK);

        cameraTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPassthroughTarget(@Nullable View passthroughTarget) {
        this.passthroughTarget = passthroughTarget;
    }

    public void setAppMenuListener(@Nullable AppMenuListener appMenuListener) {
        this.appMenuListener = appMenuListener;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        setVisibility(VISIBLE);
        rebuildWhenSized();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setControlsVisible(boolean visible) {
        if (controlsVisible == visible) {
            setVisibility(VISIBLE);
            applyControlsVisualState();
            return;
        }

        controlsVisible = visible;

        // Keep the full-screen overlay attached even when the on-screen buttons are hidden.
        // The overlay owns the safe touch-to-mouse/camera bridge; setting this view to GONE
        // drops touch back to the raw Surface path, which breaks look/tap handling on some
        // controller/gamepad setups until the controls are shown again.
        setVisibility(VISIBLE);

        if (!visible && !editMode) {
            releaseRuntimeControlInputs();
        }

        applyControlsVisualState();
    }

    public void toggleControlVisible() {
        setControlsVisible(!controlsVisible);
        ControlsPreferences.setTouchControlsEnabled(getContext(), controlsVisible);
    }

    public void loadSelectedLayout() {
        layoutFile = TouchControlsStore.getSelectedLayoutFile(getContext());
        layoutData = TouchControlsStore.loadLayout(layoutFile);
        rebuildWhenSized();
    }

    public void loadLayout(@NonNull File file) {
        layoutFile = file;
        layoutData = TouchControlsStore.loadLayout(file);
        ControlsPreferences.setSelectedLayoutPath(getContext(), file.getAbsolutePath());
        rebuildWhenSized();
    }

    public void saveLayout() {
        try {
            File target = layoutFile != null ? layoutFile : TouchControlsStore.getSelectedLayoutFile(getContext());
            TouchControlsStore.saveLayout(target, layoutData);
            layoutFile = target;
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to save touch controls", throwable);
            Toast.makeText(getContext(), "Unable to save touch controls: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void addControl(@NonNull TouchControlData data) {
        layoutData.controls.add(data);
        saveLayout();
        rebuildWhenSized();
    }

    @NonNull
    public TouchControlsLayoutData getLayoutData() {
        return layoutData;
    }

    private void rebuildWhenSized() {
        if (getWidth() <= 1 || getHeight() <= 1) {
            if (!rebuildPending) {
                rebuildPending = true;
                post(() -> {
                    rebuildPending = false;
                    if (getWidth() > 1 && getHeight() > 1) {
                        rebuild();
                    }
                });
            }
            return;
        }
        rebuild();
    }

    private void rebuild() {
        if (getWidth() <= 1 || getHeight() <= 1) {
            rebuildWhenSized();
            return;
        }

        removeAllViews();
        int parentWidth = Math.max(1, getWidth());
        int parentHeight = Math.max(1, getHeight());
        for (TouchControlData control : layoutData.controls) {
            if (!editMode && !control.visibleInGame) continue;
            TouchControlButtonView button = new TouchControlButtonView(getContext(), control, this);
            button.setEditMode(editMode);
            button.setVisibility(editMode || controlsVisible ? VISIBLE : INVISIBLE);
            int width = Math.max(32, Math.round(control.width * getResources().getDisplayMetrics().density));
            int height = Math.max(32, Math.round(control.height * getResources().getDisplayMetrics().density));
            width = Math.min(width, parentWidth);
            height = Math.min(height, parentHeight);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
            addView(button, params);

            float density = getResources().getDisplayMetrics().density;

            // JavaLauncher-native layouts store X/Y in the same layout units as
            // Width/Height. Convert fallback X/Y to pixels only when drawing.
            // Imported Zalith/Mojo/Amethyst layouts keep rawX/rawY formulas, and
            // those formulas already resolve to final screen pixels.
            float fallbackX = control.rawX == null ? control.x * density : control.x;
            float fallbackY = control.rawY == null ? control.y * density : control.y;

            float resolvedX = ExpressionResolver.resolve(
                    control.rawX,
                    fallbackX,
                    parentWidth,
                    parentHeight,
                    width,
                    height,
                    density,
                    layoutData.preferredScale
            );
            float resolvedY = ExpressionResolver.resolve(
                    control.rawY,
                    fallbackY,
                    parentWidth,
                    parentHeight,
                    width,
                    height,
                    density,
                    layoutData.preferredScale
            );
            resolvedX = Math.max(0f, Math.min(Math.max(0, parentWidth - width), resolvedX));
            resolvedY = Math.max(0f, Math.min(Math.max(0, parentHeight - height), resolvedY));
            button.setX(resolvedX);
            button.setY(resolvedY);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rebuildWhenSized();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) rebuildWhenSized();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        drawHotbarHitboxDebug(canvas);
    }

    private void drawHotbarHitboxDebug(@NonNull Canvas canvas) {
        if (!ControlsPreferences.isHotbarHitboxDebugEnabled(getContext())) return;

        TouchHotbarHitbox.Result hitbox = TouchHotbarHitbox.calculate(
                getContext(),
                getWidth(),
                getHeight(),
                CallbackBridge.physicalWidth,
                CallbackBridge.physicalHeight
        );

        RectF touchBounds = hitbox.touchBounds;
        RectF hotbarBounds = hitbox.hotbarBounds;

        canvas.drawRect(touchBounds, hotbarDebugFillPaint);
        canvas.drawRect(touchBounds, hotbarDebugStrokePaint);

        for (int i = 0; i <= TouchHotbarHitbox.SLOT_COUNT; i++) {
            float x = hotbarBounds.left + (i * hitbox.slotWidth);
            canvas.drawLine(x, touchBounds.top, x, touchBounds.bottom, hotbarDebugSlotPaint);
        }

        float textY = touchBounds.top - (6f * getResources().getDisplayMetrics().density);
        if (textY < hotbarDebugTextPaint.getTextSize() + 2f) {
            textY = touchBounds.bottom + hotbarDebugTextPaint.getTextSize() + 4f;
        }
        canvas.drawText(
                "Hotbar hitbox  scale=" + hitbox.scale
                        + "  xOff=" + ControlsPreferences.getHotbarXOffsetDp(getContext())
                        + "dp  yOff=" + ControlsPreferences.getHotbarYOffsetDp(getContext()) + "dp",
                hotbarBounds.centerX(),
                textY,
                hotbarDebugTextPaint
        );

        for (int slot = 0; slot < TouchHotbarHitbox.SLOT_COUNT; slot++) {
            float centerX = hotbarBounds.left + (slot * hitbox.slotWidth) + (hitbox.slotWidth / 2f);
            canvas.drawText(String.valueOf(slot + 1), centerX, touchBounds.centerY() + (hotbarDebugTextPaint.getTextSize() / 3f), hotbarDebugTextPaint);
        }

        postInvalidateOnAnimation();
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        // Keep routing through this overlay whether the visual buttons are shown or hidden.
        // Empty-space touches are forwarded to MinecraftGLSurface as a clean single-pointer
        // stream so controller users can still tap menus/hotbar/use screen touch while the
        // visible virtual buttons continue to work independently.
        if (editMode) {
            return super.dispatchTouchEvent(event);
        }

        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if (ControlsPreferences.isHotbarHitboxDebugEnabled(getContext())) {
            invalidate();
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                clearRuntimeTouchRouting();
                return routePointerDown(event, actionIndex);

            case MotionEvent.ACTION_POINTER_DOWN:
                return routePointerDown(event, actionIndex) || hasRuntimeTouchRouting();

            case MotionEvent.ACTION_MOVE:
                // Critical for joystick controls: pointers owned by a TouchControlButtonView
                // must keep receiving MOVE events after ACTION_DOWN. Without this, the
                // joystick knob only updates once when the finger lands, so it feels like
                // the stick barely moves or does not move the player at all.
                dispatchActiveControlPointers(event, MotionEvent.ACTION_MOVE);
                dispatchActiveHotbarPointer(event);
                dispatchActiveCameraPointer(event);
                dispatchActivePassthroughPointer(event, MotionEvent.ACTION_MOVE);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                routePointerUp(event, actionIndex, false);
                return true;

            case MotionEvent.ACTION_UP:
                routePointerUp(event, actionIndex, true);
                return true;

            case MotionEvent.ACTION_CANCEL:
                dispatchCancelToControlPointers(event);
                cancelCameraPointer(true);
                dispatchActivePassthroughPointer(event, MotionEvent.ACTION_CANCEL);
                clearRuntimeTouchRouting();
                return true;

            default:
                dispatchActivePassthroughPointer(event, MotionEvent.ACTION_MOVE);
                return true;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        return editMode && super.onTouchEvent(event);
    }

    @Override
    public void onChanged() {
        saveLayout();
    }

    @Override
    public void onMoveRequested(
            @NonNull TouchControlButtonView view,
            @NonNull TouchControlData data,
            float proposedX,
            float proposedY
    ) {
        float[] snapped = resolveDraggedPosition(view, proposedX, proposedY);

        float density = getResources().getDisplayMetrics().density;
        data.x = snapped[0] / Math.max(1f, density);
        data.y = snapped[1] / Math.max(1f, density);
        data.rawX = null;
        data.rawY = null;

        // The Android view still moves in pixels; only the saved layout uses
        // user-facing layout units.
        view.setX(snapped[0]);
        view.setY(snapped[1]);
        saveLayout();
    }

    @Override
    public void onEditRequested(@NonNull TouchControlButtonView view, @NonNull TouchControlData data) {
        showEditDialog(view, data);
    }

    @Override
    public void onMenuRequested() {
        if (appMenuListener != null) {
            appMenuListener.onTouchControlsMenuRequested();
        }
    }

    @Override
    public void onToggleControlsRequested() {
        toggleControlVisible();
    }

    @NonNull
    private float[] resolveDraggedPosition(@NonNull View movingView, float proposedX, float proposedY) {
        int width = movingView.getWidth();
        int height = movingView.getHeight();
        float x = clamp(proposedX, 0f, Math.max(0, getWidth() - width));
        float y = clamp(proposedY, 0f, Math.max(0, getHeight() - height));

        if (!ControlsPreferences.isSnapControlsEnabled(getContext())) {
            return new float[]{x, y};
        }

        float threshold = 12f * getResources().getDisplayMetrics().density;
        float bestX = x;
        float bestY = y;
        float bestXDelta = threshold + 1f;
        float bestYDelta = threshold + 1f;

        // Screen edges are useful snap targets too.
        float[] screenXTargets = new float[]{0f, (getWidth() - width) / 2f, Math.max(0f, getWidth() - width)};
        for (float target : screenXTargets) {
            float delta = Math.abs(x - target);
            if (delta <= threshold && delta < bestXDelta) {
                bestX = target;
                bestXDelta = delta;
            }
        }
        float[] screenYTargets = new float[]{0f, (getHeight() - height) / 2f, Math.max(0f, getHeight() - height)};
        for (float target : screenYTargets) {
            float delta = Math.abs(y - target);
            if (delta <= threshold && delta < bestYDelta) {
                bestY = target;
                bestYDelta = delta;
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            View other = getChildAt(i);
            if (other == movingView || other.getVisibility() != VISIBLE) continue;

            float otherLeft = other.getX();
            float otherTop = other.getY();
            float otherRight = otherLeft + other.getWidth();
            float otherBottom = otherTop + other.getHeight();
            float otherCenterX = otherLeft + other.getWidth() / 2f;
            float otherCenterY = otherTop + other.getHeight() / 2f;

            float[] xTargets = new float[]{
                    otherLeft,
                    otherRight,
                    otherLeft - width,
                    otherRight - width,
                    otherCenterX - width / 2f
            };
            for (float target : xTargets) {
                float delta = Math.abs(x - target);
                if (delta <= threshold && delta < bestXDelta) {
                    bestX = target;
                    bestXDelta = delta;
                }
            }

            float[] yTargets = new float[]{
                    otherTop,
                    otherBottom,
                    otherTop - height,
                    otherBottom - height,
                    otherCenterY - height / 2f
            };
            for (float target : yTargets) {
                float delta = Math.abs(y - target);
                if (delta <= threshold && delta < bestYDelta) {
                    bestY = target;
                    bestYDelta = delta;
                }
            }
        }

        return new float[]{
                clamp(bestX, 0f, Math.max(0, getWidth() - width)),
                clamp(bestY, 0f, Math.max(0, getHeight() - height))
        };
    }

    private void showEditDialog(@NonNull TouchControlButtonView editingView, @NonNull TouchControlData data) {
        Context context = getContext();

        String originalLabel = data.label;
        String originalAction = data.action;
        int originalKeyCode = data.keyCode;
        int[] originalKeyCodes = data.normalizedKeyCodes().clone();
        int originalMouseButton = data.mouseButton;
        int originalScrollY = data.scrollY;
        float originalX = data.x;
        float originalY = data.y;
        float originalViewX = editingView.getX();
        float originalViewY = editingView.getY();
        float originalWidth = data.width;
        float originalHeight = data.height;
        float originalOpacity = data.opacity;
        float originalSizePercent = data.sizePercent;
        boolean originalToggle = data.toggle;
        boolean originalVisibleInGame = data.visibleInGame;
        boolean originalVisibleInMenu = data.visibleInMenu;
        String originalRawX = data.rawX;
        String originalRawY = data.rawY;

        float density = getResources().getDisplayMetrics().density;
        int parentWidth = Math.max(1, getWidth());
        int parentHeight = Math.max(1, getHeight());
        int parentWidthUnits = Math.max(1, Math.round(parentWidth / Math.max(0.1f, density)));
        int parentHeightUnits = Math.max(1, Math.round(parentHeight / Math.max(0.1f, density)));
        int maxControlWidthDp = Math.max(300, parentWidthUnits);
        int maxControlHeightDp = Math.max(300, parentHeightUnits);

        // Imported layouts may still have dynamic rawX/rawY formulas. If the user opens
        // one in the editor, show the current resolved screen position converted back to
        // layout units. Once they save, rawX/rawY are cleared and the layout becomes a
        // normal JavaLauncher-native layout using these X/Y units.
        float initialLayoutX = data.rawX == null ? data.x : editingView.getX() / Math.max(1f, density);
        float initialLayoutY = data.rawY == null ? data.y : editingView.getY() / Math.max(1f, density);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18f);
        layout.setPadding(padding, dp(10f), padding, dp(10f));
        scrollView.addView(layout, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(context);
        summary.setText("Editing: " + (data.label == null || data.label.trim().isEmpty() ? "Button" : data.label.trim()));
        summary.setTextSize(15f);
        summary.setTextColor(0xFFE0E0E0);
        summary.setPadding(0, 0, 0, dp(10f));
        layout.addView(summary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addSectionHeader(layout, "Identity", "Rename the control and choose what it sends to Minecraft.");

        EditText label = textField(context, "Button label", data.label, false);
        addFieldRow(layout, "Label", label);

        String[] actionLabels = TouchInputBinding.actionLabels();
        String[] actionValues = TouchInputBinding.actionValues();
        Spinner actionSpinner = new Spinner(context);
        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, actionLabels);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);
        actionSpinner.setSelection(TouchInputBinding.actionIndex(data.action));
        addSpinnerRow(layout, "Action", actionSpinner);

        Spinner bindingSpinner = new Spinner(context);
        addSpinnerRow(layout, "Binding", bindingSpinner);
        final TouchInputBinding.Option[][] currentOptions = new TouchInputBinding.Option[1][];

        AdapterView.OnItemSelectedListener actionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String action = actionValues[Math.max(0, Math.min(position, actionValues.length - 1))];
                TouchInputBinding.Option[] options = TouchInputBinding.optionsForAction(action);
                currentOptions[0] = options;
                ArrayAdapter<String> bindingAdapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_item,
                        TouchInputBinding.optionLabels(options)
                );
                bindingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                bindingSpinner.setAdapter(bindingAdapter);
                int selected = TouchControlActions.KEY.equals(action) && TouchControlActions.KEY.equals(data.action)
                        || TouchControlActions.MOUSE.equals(action) && TouchControlActions.MOUSE.equals(data.action)
                        || TouchControlActions.SCROLL.equals(action) && TouchControlActions.SCROLL.equals(data.action)
                        ? TouchInputBinding.selectedOptionIndex(action, data)
                        : 0;
                bindingSpinner.setSelection(selected, false);
                bindingSpinner.setEnabled(options.length > 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        actionSpinner.setOnItemSelectedListener(actionListener);
        actionListener.onItemSelected(actionSpinner, actionSpinner, actionSpinner.getSelectedItemPosition(), 0L);

        addSectionHeader(layout, "Position", "X/Y use the same layout units as Width/Height. Example: Width 100 and X 300 means three 100-unit widths from the left.");

        EditText x = textField(context, "X position", String.valueOf(Math.round(initialLayoutX)), true);
        addFieldRow(layout, "X position", x);
        SeekBar xSlider = addSlider(layout, parentWidthUnits, Math.round(initialLayoutX));

        EditText y = textField(context, "Y position", String.valueOf(Math.round(initialLayoutY)), true);
        addFieldRow(layout, "Y position", y);
        SeekBar ySlider = addSlider(layout, parentHeightUnits, Math.round(initialLayoutY));

        addSectionHeader(layout, "Size", "Resize both dimensions together, or tune width and height separately.");

        EditText width = textField(context, "Width", String.valueOf(Math.round(data.width)), true);
        addFieldRow(layout, "Width", width);
        SeekBar widthSlider = addSlider(layout, maxControlWidthDp, Math.round(data.width));

        EditText height = textField(context, "Height", String.valueOf(Math.round(data.height)), true);
        addFieldRow(layout, "Height", height);
        SeekBar heightSlider = addSlider(layout, maxControlHeightDp, Math.round(data.height));

        int initialPercent = Math.round(clamp(data.sizePercent, 30f, 250f));
        TextView sizeLabel = valueLabel(context, "Button size: " + initialPercent + "%");
        layout.addView(sizeLabel);
        SeekBar sizeSlider = addSlider(layout, 250, initialPercent);

        CheckBox showPercentPreview = new CheckBox(context);
        showPercentPreview.setText("Show size percent while resizing");
        showPercentPreview.setChecked(ControlsPreferences.isSizePreviewPercentEnabled(context));
        showPercentPreview.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ControlsPreferences.setSizePreviewPercentEnabled(context, isChecked);
            if (!isChecked) hideSizePreviewPercentBadge();
        });
        layout.addView(showPercentPreview);

        addSectionHeader(layout, "Appearance", "Adjust visibility and where this control appears.");

        EditText opacity = textField(context, "Opacity 0.15 - 1.0", String.valueOf(data.opacity), false);
        addFieldRow(layout, "Opacity", opacity);
        TextView opacityLabel = valueLabel(context, "Opacity: " + Math.round(clamp(data.opacity, 0.15f, 1f) * 100f) + "%");
        layout.addView(opacityLabel);
        SeekBar opacitySlider = addSlider(layout, 100, Math.round(clamp(data.opacity, 0.15f, 1f) * 100f));

        CheckBox toggle = new CheckBox(context);
        toggle.setText("Toggle button");
        toggle.setChecked(data.toggle);
        layout.addView(toggle);

        CheckBox visibleInGame = new CheckBox(context);
        visibleInGame.setText("Visible in game");
        visibleInGame.setChecked(data.visibleInGame);
        layout.addView(visibleInGame);

        CheckBox visibleInMenu = new CheckBox(context);
        visibleInMenu.setText("Visible in menu");
        visibleInMenu.setChecked(data.visibleInMenu);
        layout.addView(visibleInMenu);

        Button copyButton = new Button(context);
        copyButton.setText("Copy button");
        layout.addView(copyButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        final boolean[] accepted = new boolean[]{false};
        final boolean[] deleted = new boolean[]{false};
        final boolean[] sliderChangingText = new boolean[]{false};
        final boolean[] textChangingSliders = new boolean[]{false};
        final float[] currentPercent = new float[]{initialPercent};
        final AlertDialog[] dialogRef = new AlertDialog[1];

        float baseWidth = Math.max(24f, data.width * 100f / Math.max(1f, initialPercent));
        float baseHeight = Math.max(24f, data.height * 100f / Math.max(1f, initialPercent));

        Runnable applyTextPreview = () -> {
            data.x = parseFloat(x, data.x);
            data.y = parseFloat(y, data.y);
            data.width = Math.max(24f, parseFloat(width, data.width));
            data.height = Math.max(24f, parseFloat(height, data.height));
            data.opacity = Math.max(0.15f, Math.min(1f, parseFloat(opacity, data.opacity)));
            data.rawX = null;
            data.rawY = null;
            applyControlPreview(editingView, data);
            summary.setText("Editing: " + (label.getText() == null || label.getText().toString().trim().isEmpty()
                    ? "Button"
                    : label.getText().toString().trim()));
        };

        Runnable updateSlidersFromFields = () -> {
            if (sliderChangingText[0]) return;
            textChangingSliders[0] = true;
            setSliderProgress(xSlider, Math.round(parseFloat(x, data.x)));
            setSliderProgress(ySlider, Math.round(parseFloat(y, data.y)));
            setSliderProgress(widthSlider, Math.round(Math.max(24f, parseFloat(width, data.width))));
            setSliderProgress(heightSlider, Math.round(Math.max(24f, parseFloat(height, data.height))));
            int opacityPercent = Math.round(Math.max(0.15f, Math.min(1f, parseFloat(opacity, data.opacity))) * 100f);
            setSliderProgress(opacitySlider, opacityPercent);
            opacityLabel.setText("Opacity: " + opacityPercent + "%");
            textChangingSliders[0] = false;
        };

        TextWatcher livePreviewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!sliderChangingText[0]) {
                    float parsedWidth = Math.max(24f, parseFloat(width, data.width));
                    if (baseWidth > 0f) {
                        currentPercent[0] = clamp(Math.round(parsedWidth * 100f / baseWidth), 30f, 250f);
                        data.sizePercent = currentPercent[0];
                    }
                    sizeLabel.setText("Button size: custom");
                    applyTextPreview.run();
                    updateSlidersFromFields.run();
                }
            }
        };
        label.addTextChangedListener(livePreviewWatcher);
        x.addTextChangedListener(livePreviewWatcher);
        y.addTextChangedListener(livePreviewWatcher);
        width.addTextChangedListener(livePreviewWatcher);
        height.addTextChangedListener(livePreviewWatcher);
        opacity.addTextChangedListener(livePreviewWatcher);

        addPreviewSliderListener(xSlider, dialogRef, () -> {
            if (textChangingSliders[0]) return;
            sliderChangingText[0] = true;
            x.setText(String.valueOf(xSlider.getProgress()));
            sliderChangingText[0] = false;
            applyTextPreview.run();
        });

        addPreviewSliderListener(ySlider, dialogRef, () -> {
            if (textChangingSliders[0]) return;
            sliderChangingText[0] = true;
            y.setText(String.valueOf(ySlider.getProgress()));
            sliderChangingText[0] = false;
            applyTextPreview.run();
        });

        addPreviewSliderListener(widthSlider, dialogRef, () -> {
            if (textChangingSliders[0]) return;
            int value = Math.max(24, widthSlider.getProgress());
            sliderChangingText[0] = true;
            width.setText(String.valueOf(value));
            sliderChangingText[0] = false;
            sizeLabel.setText("Button size: custom");
            applyTextPreview.run();
        });

        addPreviewSliderListener(heightSlider, dialogRef, () -> {
            if (textChangingSliders[0]) return;
            int value = Math.max(24, heightSlider.getProgress());
            sliderChangingText[0] = true;
            height.setText(String.valueOf(value));
            sliderChangingText[0] = false;
            sizeLabel.setText("Button size: custom");
            applyTextPreview.run();
        });

        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = Math.max(30, progress);
                currentPercent[0] = percent;
                data.sizePercent = percent;
                sizeLabel.setText("Button size: " + percent + "%");
                if (fromUser && ControlsPreferences.isSizePreviewPercentEnabled(context)) {
                    showSizePreviewPercentBadge(editingView, percent);
                }
                if (fromUser) {
                    sliderChangingText[0] = true;
                    width.setText(String.valueOf(Math.max(24, Math.round(baseWidth * percent / 100f))));
                    height.setText(String.valueOf(Math.max(24, Math.round(baseHeight * percent / 100f))));
                    sliderChangingText[0] = false;
                    applyTextPreview.run();
                    updateSlidersFromFields.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                setEditDialogPreviewAlpha(dialogRef[0], true);
                if (ControlsPreferences.isSizePreviewPercentEnabled(context)) {
                    showSizePreviewPercentBadge(editingView, Math.round(currentPercent[0]));
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setEditDialogPreviewAlpha(dialogRef[0], false);
                hideSizePreviewPercentBadge();
            }
        });

        addPreviewSliderListener(opacitySlider, dialogRef, () -> {
            if (textChangingSliders[0]) return;
            int value = Math.max(15, opacitySlider.getProgress());
            opacityLabel.setText("Opacity: " + value + "%");
            sliderChangingText[0] = true;
            opacity.setText(String.valueOf(value / 100f));
            sliderChangingText[0] = false;
            applyTextPreview.run();
        });

        copyButton.setOnClickListener(view -> {
            try {
                TouchControlData copy = createCopyFromDialog(
                        data,
                        label,
                        actionSpinner,
                        actionValues,
                        bindingSpinner,
                        currentOptions[0],
                        x,
                        y,
                        width,
                        height,
                        opacity,
                        toggle,
                        visibleInGame,
                        visibleInMenu,
                        currentPercent[0]
                );
                accepted[0] = true;
                layoutData.controls.add(copy);
                saveLayout();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                Toast.makeText(getContext(), "Copied " + copy.label, Toast.LENGTH_SHORT).show();
                rebuildWhenSized();
            } catch (Throwable throwable) {
                Logging.e(TAG, "Unable to copy touch control", throwable);
                Toast.makeText(getContext(), "Copy failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Edit touch button")
                .setView(scrollView)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Delete", null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialogRef[0] = dialog;

        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                deleted[0] = true;
                layoutData.controls.remove(data);
                saveLayout();
                rebuildWhenSized();
                dialog.dismiss();
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String oldLabel = originalLabel;
                String newLabel = label.getText() == null ? oldLabel : label.getText().toString().trim();

                int actionPosition = Math.max(0, actionSpinner.getSelectedItemPosition());
                String action = actionValues[Math.min(actionPosition, actionValues.length - 1)];
                TouchInputBinding.Option[] options = currentOptions[0] != null
                        ? currentOptions[0]
                        : TouchInputBinding.optionsForAction(action);
                int bindingPosition = Math.max(0, bindingSpinner.getSelectedItemPosition());
                TouchInputBinding.Option option = options[Math.min(bindingPosition, options.length - 1)];
                TouchInputBinding.applyOption(data, action, option);

                if (TouchInputBinding.isDefaultLabel(newLabel) || oldLabel.equals(newLabel)) {
                    newLabel = option.label;
                    if (TouchControlActions.MENU.equals(action)) newLabel = "Menu";
                    if (TouchControlActions.TOGGLE_CONTROLS.equals(action)) newLabel = "Hide";
                    if (TouchControlActions.KEYBOARD.equals(action)) newLabel = "Keyboard";
                    if (TouchControlActions.JOYSTICK.equals(action)) newLabel = "Joystick";
                }
                data.label = newLabel.trim().isEmpty() ? "Button" : newLabel.trim();

                data.x = parseFloat(x, data.x);
                data.y = parseFloat(y, data.y);
                data.width = Math.max(24f, parseFloat(width, data.width));
                data.height = Math.max(24f, parseFloat(height, data.height));
                data.sizePercent = clamp(currentPercent[0], 30f, 250f);
                data.opacity = Math.max(0.15f, Math.min(1f, parseFloat(opacity, data.opacity)));
                data.toggle = toggle.isChecked();
                data.visibleInGame = visibleInGame.isChecked();
                data.visibleInMenu = visibleInMenu.isChecked();
                data.rawX = null;
                data.rawY = null;
                accepted[0] = true;
                saveLayout();
                rebuildWhenSized();
                dialog.dismiss();
            });
        });

        dialog.setOnDismissListener(dismissed -> {
            setEditDialogPreviewAlpha(dialog, false);
            hideSizePreviewPercentBadge();
            if (!accepted[0] && !deleted[0]) {
                restoreEditPreview(
                        editingView,
                        data,
                        originalLabel,
                        originalAction,
                        originalKeyCode,
                        originalKeyCodes,
                        originalMouseButton,
                        originalScrollY,
                        originalX,
                        originalY,
                        originalViewX,
                        originalViewY,
                        originalWidth,
                        originalHeight,
                        originalOpacity,
                        originalSizePercent,
                        originalToggle,
                        originalVisibleInGame,
                        originalVisibleInMenu,
                        originalRawX,
                        originalRawY
                );
            }
        });

        dialog.show();
    }

    private void addSectionHeader(@NonNull LinearLayout parent, @NonNull String title, @Nullable String subtitle) {
        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(16f);
        header.setTextColor(Color.WHITE);
        header.setPadding(0, dp(14f), 0, dp(2f));
        parent.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = new TextView(getContext());
            sub.setText(subtitle);
            sub.setTextSize(12f);
            sub.setTextColor(0xFFBDBDBD);
            sub.setPadding(0, 0, 0, dp(6f));
            parent.addView(sub, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    @NonNull
    private TextView valueLabel(@NonNull Context context, @NonNull String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(13f);
        label.setTextColor(0xFFE0E0E0);
        label.setPadding(0, dp(4f), 0, dp(2f));
        return label;
    }

    private void addFieldRow(@NonNull LinearLayout parent, @NonNull String title, @NonNull EditText field) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4f), 0, dp(4f));

        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextSize(13f);
        label.setTextColor(0xFFE0E0E0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f));
        row.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addSpinnerRow(@NonNull LinearLayout parent, @NonNull String title, @NonNull Spinner spinner) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4f), 0, dp(4f));

        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextSize(13f);
        label.setTextColor(0xFFE0E0E0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.85f));
        row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    @NonNull
    private SeekBar addSlider(@NonNull LinearLayout parent, int max, int value) {
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(Math.max(1, max));
        setSliderProgress(slider, Math.max(0, value));
        slider.setPadding(0, 0, 0, dp(2f));
        parent.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return slider;
    }

    private void addPreviewSliderListener(
            @NonNull SeekBar slider,
            @NonNull AlertDialog[] dialogRef,
            @NonNull Runnable onUserChange
    ) {
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onUserChange.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                setEditDialogPreviewAlpha(dialogRef[0], true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setEditDialogPreviewAlpha(dialogRef[0], false);
            }
        });
    }

    private void setSliderProgress(@NonNull SeekBar slider, int value) {
        int progress = Math.max(0, Math.min(slider.getMax(), value));
        if (slider.getProgress() != progress) {
            slider.setProgress(progress);
        }
    }
    
    private TouchControlData createCopyFromDialog(
             TouchControlData source,
             EditText labelField,
             Spinner actionSpinner,
             String[] actionValues,
             Spinner bindingSpinner,
             TouchInputBinding.Option[] currentOptions,
             EditText xField,
             EditText yField,
             EditText widthField,
             EditText heightField,
             EditText opacityField,
             CheckBox toggleField,
             CheckBox visibleInGameField,
             CheckBox visibleInMenuField,
            float currentPercent
    ) {
        TouchControlData copy = source.copy();

        int actionPosition = Math.max(0, actionSpinner.getSelectedItemPosition());
        String action = actionValues[Math.min(actionPosition, actionValues.length - 1)];
        TouchInputBinding.Option[] options = currentOptions != null
                ? currentOptions
                : TouchInputBinding.optionsForAction(action);
        int bindingPosition = Math.max(0, bindingSpinner.getSelectedItemPosition());
        TouchInputBinding.Option option = options[Math.min(bindingPosition, options.length - 1)];
        TouchInputBinding.applyOption(copy, action, option);

        String newLabel = labelField.getText() == null ? source.label : labelField.getText().toString().trim();
        if (TouchInputBinding.isDefaultLabel(newLabel)) {
            newLabel = option.label;
            if (TouchControlActions.MENU.equals(action)) newLabel = "Menu";
            if (TouchControlActions.TOGGLE_CONTROLS.equals(action)) newLabel = "Hide";
            if (TouchControlActions.KEYBOARD.equals(action)) newLabel = "Keyboard";
            if (TouchControlActions.JOYSTICK.equals(action)) newLabel = "Joystick";
        }
        copy.label = newLabel.trim().isEmpty() ? "Button" : newLabel.trim();

        float density = getResources().getDisplayMetrics().density;
        float copyWidth = Math.max(24f, parseFloat(widthField, source.width));
        float copyHeight = Math.max(24f, parseFloat(heightField, source.height));
        // Copy offset is in layout units too, so a copied 100-wide button can
        // still be reasoned about in the same unit system.
        float offsetUnits = 24f;
        float widthPx = Math.max(32f, copyWidth * density);
        float heightPx = Math.max(32f, copyHeight * density);
        float maxXUnits = Math.max(0f, (getWidth() - widthPx) / Math.max(1f, density));
        float maxYUnits = Math.max(0f, (getHeight() - heightPx) / Math.max(1f, density));

        copy.x = clamp(parseFloat(xField, source.x) + offsetUnits, 0f, maxXUnits);
        copy.y = clamp(parseFloat(yField, source.y) + offsetUnits, 0f, maxYUnits);
        copy.width = copyWidth;
        copy.height = copyHeight;
        copy.sizePercent = clamp(currentPercent, 30f, 250f);
        copy.opacity = Math.max(0.15f, Math.min(1f, parseFloat(opacityField, source.opacity)));
        copy.toggle = toggleField.isChecked();
        copy.visibleInGame = visibleInGameField.isChecked();
        copy.visibleInMenu = visibleInMenuField.isChecked();
        copy.rawX = null;
        copy.rawY = null;
        return copy;
    }

    private void showSizePreviewPercentBadge(@NonNull TouchControlButtonView editingView, int percent) {
        if (!ControlsPreferences.isSizePreviewPercentEnabled(getContext())) {
            hideSizePreviewPercentBadge();
            return;
        }

        TextView badge = sizePreviewPercentBadge;
        if (badge == null) {
            badge = new TextView(getContext());
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(26f);
            badge.setGravity(android.view.Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            badge.setPadding(dp(14f), dp(8f), dp(14f), dp(8f));
            badge.setBackground(makeSizePreviewBadgeBackground());
            badge.setClickable(false);
            badge.setFocusable(false);
            sizePreviewPercentBadge = badge;
            addView(badge, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        badge.setText(percent + "%");
        badge.setVisibility(VISIBLE);
        badge.bringToFront();

        final TextView badgeForPost = badge;
        badgeForPost.post(() -> {
            if (sizePreviewPercentBadge != badgeForPost) return;

            float badgeWidth = badgeForPost.getWidth() > 0 ? badgeForPost.getWidth() : dp(86f);
            float badgeHeight = badgeForPost.getHeight() > 0 ? badgeForPost.getHeight() : dp(42f);
            float badgeX = editingView.getX() + (editingView.getWidth() / 2f) - (badgeWidth / 2f);
            float badgeY = editingView.getY() - badgeHeight - dp(12f);

            if (badgeY < dp(8f)) {
                badgeY = editingView.getY() + editingView.getHeight() + dp(12f);
            }

            badgeForPost.setX(clamp(badgeX, dp(8f), Math.max(dp(8f), getWidth() - badgeWidth - dp(8f))));
            badgeForPost.setY(clamp(badgeY, dp(8f), Math.max(dp(8f), getHeight() - badgeHeight - dp(8f))));
        });
    }

    private void hideSizePreviewPercentBadge() {
        if (sizePreviewPercentBadge == null) return;
        removeView(sizePreviewPercentBadge);
        sizePreviewPercentBadge = null;
    }

    @NonNull
    private GradientDrawable makeSizePreviewBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xDD000000);
        drawable.setStroke(Math.max(1, Math.round(dp(1.5f))), 0xCCFFFFFF);
        drawable.setCornerRadius(dp(14f));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setEditDialogPreviewAlpha(@Nullable AlertDialog dialog, boolean previewing) {
        if (dialog == null || dialog.getWindow() == null) return;

        // While dragging the size slider, make the dialog barely visible so the user
        // can see the live-resized touch button/joystick underneath. Restore it as
        // soon as the user lifts their finger or the dialog closes.
        dialog.getWindow().setDimAmount(previewing ? 0.02f : 0.32f);
        dialog.getWindow().getDecorView().setAlpha(previewing ? 0.12f : 1.0f);
    }

    private void applyControlPreview(@NonNull TouchControlButtonView view, @NonNull TouchControlData data) {
        float density = getResources().getDisplayMetrics().density;
        int width = Math.max(32, Math.round(data.width * density));
        int height = Math.max(32, Math.round(data.height * density));
        width = Math.min(width, Math.max(1, getWidth()));
        height = Math.min(height, Math.max(1, getHeight()));

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = width;
            params.height = height;
            view.setLayoutParams(params);
        }

        // X/Y are saved in layout units, just like width/height. Convert to
        // pixels only when positioning the Android view.
        view.setX(clamp(data.x * density, 0f, Math.max(0f, getWidth() - width)));
        view.setY(clamp(data.y * density, 0f, Math.max(0f, getHeight() - height)));
        view.setText(data.label);
        view.setAlpha(Math.max(0.15f, Math.min(1f, data.opacity)) * ControlsPreferences.getGlobalOpacity(getContext()));
        view.requestLayout();
        view.invalidate();
    }

    private void restoreEditPreview(
            @NonNull TouchControlButtonView view,
            @NonNull TouchControlData data,
            @NonNull String label,
            @NonNull String action,
            int keyCode,
            @NonNull int[] keyCodes,
            int mouseButton,
            int scrollY,
            float x,
            float y,
            float viewX,
            float viewY,
            float width,
            float height,
            float opacity,
            float sizePercent,
            boolean toggle,
            boolean visibleInGame,
            boolean visibleInMenu,
            @Nullable String rawX,
            @Nullable String rawY
    ) {
        data.label = label;
        data.action = action;
        data.keyCode = keyCode;
        data.keyCodes = keyCodes;
        data.mouseButton = mouseButton;
        data.scrollY = scrollY;
        data.x = x;
        data.y = y;
        data.width = width;
        data.height = height;
        data.opacity = opacity;
        data.sizePercent = sizePercent;
        data.toggle = toggle;
        data.visibleInGame = visibleInGame;
        data.visibleInMenu = visibleInMenu;
        data.rawX = rawX;
        data.rawY = rawY;

        float density = getResources().getDisplayMetrics().density;
        int pxWidth = Math.max(32, Math.round(width * density));
        int pxHeight = Math.max(32, Math.round(height * density));
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = pxWidth;
            params.height = pxHeight;
            view.setLayoutParams(params);
        }
        view.setX(viewX);
        view.setY(viewY);
        view.setText(label);
        view.setAlpha(Math.max(0.15f, Math.min(1f, opacity)) * ControlsPreferences.getGlobalOpacity(getContext()));
        view.requestLayout();
        view.invalidate();
    }
    @NonNull
    private static TextView labelView(@NonNull Context context, @NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(13f);
        view.setPadding(0, 12, 0, 0);
        return view;
    }

    @NonNull
    private static EditText textField(@NonNull Context context, @NonNull String hint, @NonNull String value, boolean number) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setText(value);
        if (number) {
            field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        return field;
    }

    private static float parseFloat(@NonNull EditText field, float fallback) {
        try {
            return Float.parseFloat(field.getText() == null ? "" : field.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean routePointerDown(@NonNull MotionEvent event, int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return false;

        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        if (isMouseGrabbed()) {
            // In grabbed/gameplay mode the real Minecraft hotbar must win over visible
            // launcher controls. Otherwise a visible control, joystick, or imported
            // Zalith button whose rectangle overlaps the bottom of the screen can steal
            // the touch before the hotbar hitbox ever sees it. This was the regression
            // where the hotbar only worked after hiding touch controls.
            int hotbarSlot = hotbarSlotForTouch(x, y);
            if (hotbarSlot >= 0) {
                startHotbarPointer(pointerId, hotbarSlot);
                return true;
            }

            TouchControlButtonView control = controlsVisible ? findControlUnder(x, y) : null;
            if (control != null) {
                controlPointerTargets.put(pointerId, control);
                dispatchSinglePointerToControl(event, pointerIndex, MotionEvent.ACTION_DOWN, control);
                return true;
            }

            // Do not forward empty gameplay touches through the raw SurfaceView/TextureView
            // path. That path only has one touch tracker and breaks when a virtual button
            // finger and a camera finger are down at the same time.
            if (cameraPointerId == NO_POINTER_ID) {
                startCameraPointer(event, pointerIndex, pointerId);
            }
            return true;
        }

        // Menu/inventory mode is not grabbed. In this state launcher controls should still
        // be tappable first, then empty screen touches pass through as normal absolute GUI
        // mouse clicks.
        TouchControlButtonView control = controlsVisible ? findControlUnder(x, y) : null;
        if (control != null) {
            controlPointerTargets.put(pointerId, control);
            dispatchSinglePointerToControl(event, pointerIndex, MotionEvent.ACTION_DOWN, control);
            return true;
        }

        // Menus/inventory are not grabbed, so they need real absolute mouse/touch events.
        // Keep this single-pointer so button fingers never get mixed into the GUI stream.
        if (passthroughTarget != null && passthroughPointerId == NO_POINTER_ID) {
            passthroughPointerId = pointerId;
            passthroughDownTime = event.getEventTime();
            boolean handled = dispatchSinglePointerToPassthrough(event, pointerIndex, MotionEvent.ACTION_DOWN);
            if (!handled) {
                passthroughPointerId = NO_POINTER_ID;
                passthroughDownTime = 0L;
            }
            return handled;
        }

        return false;
    }

    private void routePointerUp(@NonNull MotionEvent event, int pointerIndex, boolean finalUp) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) {
            if (finalUp) clearRuntimeTouchRouting();
            return;
        }

        int pointerId = event.getPointerId(pointerIndex);

        if (pointerId == cameraPointerId) {
            finishCameraPointer(event, pointerIndex, false);
        }

        if (pointerId == hotbarPointerId) {
            finishHotbarPointer();
        }

        if (pointerId == passthroughPointerId) {
            dispatchSinglePointerToPassthrough(event, pointerIndex, MotionEvent.ACTION_UP);
            passthroughPointerId = NO_POINTER_ID;
            passthroughDownTime = 0L;
        }

        TouchControlButtonView control = controlPointerTargets.get(pointerId);
        if (control != null) {
            dispatchSinglePointerToControl(event, pointerIndex, MotionEvent.ACTION_UP, control);
            controlPointerTargets.remove(pointerId);
        }

        if (finalUp) {
            clearRuntimeTouchRouting();
        }
    }

    private void dispatchCancelToControlPointers(@NonNull MotionEvent event) {
        for (int i = 0; i < controlPointerTargets.size(); i++) {
            int pointerId = controlPointerTargets.keyAt(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            TouchControlButtonView control = controlPointerTargets.valueAt(i);
            if (pointerIndex >= 0 && control != null) {
                dispatchSinglePointerToControl(event, pointerIndex, MotionEvent.ACTION_CANCEL, control);
            }
        }
    }

    private void dispatchActiveControlPointers(@NonNull MotionEvent event, int action) {
        for (int i = 0; i < controlPointerTargets.size(); i++) {
            int pointerId = controlPointerTargets.keyAt(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            TouchControlButtonView control = controlPointerTargets.valueAt(i);
            if (pointerIndex >= 0 && control != null) {
                dispatchSinglePointerToControl(event, pointerIndex, action, control);
            }
        }
    }

    private void startCameraPointer(@NonNull MotionEvent event, int pointerIndex, int pointerId) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return;

        cameraPointerId = pointerId;
        cameraDownX = event.getX(pointerIndex);
        cameraDownY = event.getY(pointerIndex);
        cameraLastX = cameraDownX;
        cameraLastY = cameraDownY;
        cameraMovedPastSlop = false;
        cameraLongPressAttackActive = false;
        scheduleCameraLongPressAttack();
    }

    private void dispatchActiveCameraPointer(@NonNull MotionEvent event) {
        if (cameraPointerId == NO_POINTER_ID) return;

        int pointerIndex = event.findPointerIndex(cameraPointerId);
        if (pointerIndex < 0) return;

        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);
        float totalDx = x - cameraDownX;
        float totalDy = y - cameraDownY;

        if (!cameraMovedPastSlop) {
            if ((totalDx * totalDx) + (totalDy * totalDy) <= (cameraTouchSlop * cameraTouchSlop)) {
                cameraLastX = x;
                cameraLastY = y;
                return;
            }
            cameraMovedPastSlop = true;
            cancelCameraLongPressAttack(false);
        }

        float dx = x - cameraLastX;
        float dy = y - cameraLastY;
        cameraLastX = x;
        cameraLastY = y;

        if (dx == 0f && dy == 0f) return;
        sendRelativeCameraDelta(dx, dy);
    }

    private void finishCameraPointer(@NonNull MotionEvent event, int pointerIndex, boolean cancelled) {
        if (pointerIndex >= 0 && pointerIndex < event.getPointerCount() && !cancelled) {
            dispatchActiveCameraPointer(event);
        }

        cancelCameraLongPressAttack(cancelled);

        if (!cancelled && cameraLongPressAttackActive) {
            sendLeftMouse(false);
        } else if (!cancelled && !cameraMovedPastSlop) {
            // Quick tap on the look area acts like the attack button.
            sendLeftMouse(true);
            sendLeftMouse(false);
        }

        cameraLongPressAttackActive = false;
        cameraPointerId = NO_POINTER_ID;
        cameraDownX = cameraDownY = cameraLastX = cameraLastY = 0f;
        cameraMovedPastSlop = false;
    }

    private void cancelCameraPointer(boolean sendRelease) {
        if (sendRelease && cameraLongPressAttackActive) {
            sendLeftMouse(false);
        }
        cancelCameraLongPressAttack(true);
        cameraLongPressAttackActive = false;
        cameraPointerId = NO_POINTER_ID;
        cameraMovedPastSlop = false;
    }

    private void scheduleCameraLongPressAttack() {
        cancelCameraLongPressAttack(false);
        cameraLongPressRunnable = () -> {
            if (cameraPointerId == NO_POINTER_ID || cameraMovedPastSlop || cameraLongPressAttackActive) return;
            cameraLongPressAttackActive = true;
            sendLeftMouse(true);
        };
        gestureHandler.postDelayed(cameraLongPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelCameraLongPressAttack(boolean cancelActivePress) {
        if (cameraLongPressRunnable != null) {
            gestureHandler.removeCallbacks(cameraLongPressRunnable);
            cameraLongPressRunnable = null;
        }
        if (cancelActivePress && cameraLongPressAttackActive) {
            sendLeftMouse(false);
            cameraLongPressAttackActive = false;
        }
    }

    private void sendRelativeCameraDelta(float dx, float dy) {
        try {
            CallbackBridge.setInputReady(true);
            CallbackBridge.mouseX += dx;
            CallbackBridge.mouseY += dy;
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to send camera touch delta", throwable);
        }
    }

    private void sendLeftMouse(boolean down) {
        try {
            CallbackBridge.setInputReady(true);
            CallbackBridge.sendMouseButton(MOUSE_BUTTON_LEFT, down);
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to send touch attack", throwable);
        }
    }

    private void startHotbarPointer(int pointerId, int slot) {
        hotbarPointerId = pointerId;
        hotbarLastSlot = slot;
        sendKeyTap(49 + slot); // GLFW_KEY_1 through GLFW_KEY_9
    }

    private void dispatchActiveHotbarPointer(@NonNull MotionEvent event) {
        if (hotbarPointerId == NO_POINTER_ID) return;

        int pointerIndex = event.findPointerIndex(hotbarPointerId);
        if (pointerIndex < 0) return;

        int slot = hotbarSlotForTouch(event.getX(pointerIndex), event.getY(pointerIndex));
        if (slot < 0 || slot == hotbarLastSlot) return;

        hotbarLastSlot = slot;
        sendKeyTap(49 + slot);
    }

    private void finishHotbarPointer() {
        hotbarPointerId = NO_POINTER_ID;
        hotbarLastSlot = -1;
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

    private void sendKeyTap(int keyCode) {
        try {
            CallbackBridge.setInputReady(true);
            CallbackBridge.sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
            CallbackBridge.setModifiers(keyCode, true);
            CallbackBridge.sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
            CallbackBridge.setModifiers(keyCode, false);
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to send hotbar key tap", throwable);
        }
    }

    private static boolean isMouseGrabbed() {
        try {
            return CallbackBridge.isGrabbing();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void applyControlsVisualState() {
        boolean childVisible = editMode || controlsVisible;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TouchControlButtonView) {
                child.setVisibility(childVisible ? VISIBLE : INVISIBLE);
            }
        }
    }

    private void releaseRuntimeControlInputs() {
        cancelCameraPointer(true);
        finishHotbarPointer();
        passthroughPointerId = NO_POINTER_ID;
        passthroughDownTime = 0L;
        controlPointerTargets.clear();

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TouchControlButtonView) {
                ((TouchControlButtonView) child).releaseInputState();
            }
        }
    }

    @Nullable
    private MinecraftGLSurface findMinecraftSurfaceTarget() {
        View current = passthroughTarget;
        while (current != null) {
            if (current instanceof MinecraftGLSurface) {
                return (MinecraftGLSurface) current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private boolean dispatchActivePassthroughPointer(@NonNull MotionEvent event, int action) {
        if (passthroughPointerId == NO_POINTER_ID) {
            return false;
        }

        int pointerIndex = event.findPointerIndex(passthroughPointerId);
        if (pointerIndex < 0) {
            return false;
        }

        return dispatchSinglePointerToPassthrough(event, pointerIndex, action);
    }

    private boolean dispatchSinglePointerToPassthrough(
            @NonNull MotionEvent source,
            int pointerIndex,
            int action
    ) {
        if (passthroughTarget == null || pointerIndex < 0 || pointerIndex >= source.getPointerCount()) {
            return false;
        }

        long downTime = passthroughDownTime > 0L ? passthroughDownTime : source.getDownTime();
        MotionEvent single = MotionEvent.obtain(
                downTime,
                source.getEventTime(),
                action,
                source.getX(pointerIndex),
                source.getY(pointerIndex),
                source.getMetaState()
        );
        int sourceClass = source.getSource();
        single.setSource(sourceClass != 0 ? sourceClass : InputDevice.SOURCE_TOUCHSCREEN);
        try {
            MinecraftGLSurface minecraftSurface = findMinecraftSurfaceTarget();
            if (minecraftSurface != null) {
                return minecraftSurface.handleTouchFromOverlay(single);
            }

            // Fallback for editor/tests or if a different target is supplied.
            return passthroughTarget.dispatchTouchEvent(single);
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to dispatch passthrough touch event", throwable);
            return false;
        } finally {
            single.recycle();
        }
    }

    private boolean dispatchWholeEventToPassthrough(@NonNull MotionEvent event) {
        if (passthroughTarget == null) {
            return false;
        }

        try {
            MotionEvent copy = MotionEvent.obtain(event);
            if (copy.getSource() == 0) copy.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            MinecraftGLSurface minecraftSurface = findMinecraftSurfaceTarget();
            boolean handled = minecraftSurface != null
                    ? minecraftSurface.handleTouchFromOverlay(copy)
                    : passthroughTarget.dispatchTouchEvent(copy);
            copy.recycle();
            return handled;
        } catch (Throwable throwable) {
            Logging.e(TAG, "Unable to dispatch whole passthrough touch event", throwable);
            return false;
        }
    }

    private void dispatchSinglePointerToControl(
            @NonNull MotionEvent source,
            int pointerIndex,
            int action,
            @NonNull TouchControlButtonView control
    ) {
        if (pointerIndex < 0 || pointerIndex >= source.getPointerCount()) return;

        float localX = source.getX(pointerIndex) - control.getX();
        float localY = source.getY(pointerIndex) - control.getY();
        MotionEvent single = MotionEvent.obtain(
                source.getDownTime(),
                source.getEventTime(),
                action,
                localX,
                localY,
                source.getMetaState()
        );
        try {
            control.dispatchTouchEvent(single);
        } finally {
            single.recycle();
        }
    }

    @Nullable
    private TouchControlButtonView findControlUnder(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (!(child instanceof TouchControlButtonView)) continue;
            if (child.getVisibility() != VISIBLE) continue;
            if (x >= child.getX()
                    && x <= child.getX() + child.getWidth()
                    && y >= child.getY()
                    && y <= child.getY() + child.getHeight()) {
                return (TouchControlButtonView) child;
            }
        }
        return null;
    }

    private boolean hasRuntimeTouchRouting() {
        return cameraPointerId != NO_POINTER_ID
                || hotbarPointerId != NO_POINTER_ID
                || passthroughPointerId != NO_POINTER_ID
                || controlPointerTargets.size() > 0;
    }

    private void clearRuntimeTouchRouting() {
        cancelCameraPointer(true);
        finishHotbarPointer();
        passthroughPointerId = NO_POINTER_ID;
        passthroughDownTime = 0L;
        controlPointerTargets.clear();
    }
    @Override
    protected void onDetachedFromWindow() {
        clearRuntimeTouchRouting();
        super.onDetachedFromWindow();
    }
}
