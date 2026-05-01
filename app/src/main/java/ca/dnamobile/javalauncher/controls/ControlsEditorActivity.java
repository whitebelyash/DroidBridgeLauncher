package ca.dnamobile.javalauncher.controls;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import ca.dnamobile.javalauncher.utils.FullscreenUtils;

/** Drag buttons to move; long-press a button to edit/delete it. */
public final class ControlsEditorActivity extends AppCompatActivity {
    private static final String UI_PREFS = "touch_controls_editor_ui";
    private static final String KEY_MENU_X = "floating_menu_x";
    private static final String KEY_MENU_Y = "floating_menu_y";

    private TouchControlsOverlay overlay;
    private FrameLayout root;
    private LinearLayout editorPanel;
    private Button menuButton;

    private int menuTouchSlop;
    private float menuDownRawX;
    private float menuDownRawY;
    private float menuStartX;
    private float menuStartY;
    private boolean menuDragging;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        menuTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        root = new FrameLayout(this);
        setContentView(root);

        overlay = new TouchControlsOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        editorPanel = new LinearLayout(this);
        editorPanel.setOrientation(LinearLayout.VERTICAL);
        editorPanel.setGravity(Gravity.CENTER_VERTICAL);
        editorPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        editorPanel.setBackground(makePanelBackground());
        editorPanel.setVisibility(View.GONE);
        root.addView(editorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        ));

        buildEditorPanel();

        menuButton = new Button(this);
        menuButton.setText("⚙");
        menuButton.setTextSize(22f);
        menuButton.setAllCaps(false);
        menuButton.setAlpha(0.76f);
        menuButton.setOnTouchListener(this::handleMenuButtonTouch);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                dp(52),
                dp(52),
                Gravity.TOP | Gravity.START
        );
        root.addView(menuButton, menuParams);

        editorPanel.bringToFront();
        menuButton.bringToFront();

        // Wait until the overlay is attached and measured before loading/rebuilding.
        // Loading before addView() left the first editor screen empty until Add Key/Add Mouse was pressed.
        root.post(() -> {
            restoreMenuButtonPosition();
            if (overlay != null) {
                overlay.setEditMode(true);
                overlay.loadSelectedLayout();
            }
            enableImmersiveSafely();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveSafely();
    }

    private void buildEditorPanel() {
        LinearLayout rowOne = panelRow();
        LinearLayout rowTwo = panelRow();
        LinearLayout rowThree = panelRow();

        Button addKey = panelButton("Add Key");
        addKey.setOnClickListener(view -> {
            overlay.addControl(TouchControlData.key("Key", 32, dp(120), dp(120), 72, 52));
            Toast.makeText(this, "Long-press the new button to edit key code.", Toast.LENGTH_SHORT).show();
        });
        rowOne.addView(addKey, panelButtonParams());

        Button addMouse = panelButton("Add Mouse");
        addMouse.setOnClickListener(view -> overlay.addControl(TouchControlData.mouse("Mouse", 0, dp(220), dp(120))));
        rowOne.addView(addMouse, panelButtonParams());

        Button addJoystick = panelButton("Add Stick");
        addJoystick.setOnClickListener(view -> {
            overlay.addControl(TouchControlData.joystick("Joystick", dp(48), dp(330), 128, 128));
            Toast.makeText(this, "Added joystick. Long-press it to resize or move it.", Toast.LENGTH_SHORT).show();
        });
        rowOne.addView(addJoystick, panelButtonParams());

        Button snap = panelButton("");
        updateSnapButtonText(snap);
        snap.setOnClickListener(view -> {
            boolean enabled = !ControlsPreferences.isSnapControlsEnabled(this);
            ControlsPreferences.setSnapControlsEnabled(this, enabled);
            updateSnapButtonText(snap);
            Toast.makeText(this, enabled ? "Snap enabled." : "Snap disabled.", Toast.LENGTH_SHORT).show();
        });
        rowTwo.addView(snap, panelButtonParams());

        Button save = panelButton("Save");
        save.setOnClickListener(view -> {
            overlay.saveLayout();
            Toast.makeText(this, "Touch controls saved.", Toast.LENGTH_SHORT).show();
        });
        rowTwo.addView(save, panelButtonParams());

        Button hide = panelButton("Hide panel");
        hide.setOnClickListener(view -> setPanelVisible(false));
        rowTwo.addView(hide, panelButtonParams());

        Button close = panelButton("Close editor");
        close.setOnClickListener(view -> finish());
        rowThree.addView(close, panelButtonParamsWide());

        editorPanel.addView(rowOne);
        editorPanel.addView(rowTwo);
        editorPanel.addView(rowThree);
    }

    private boolean handleMenuButtonTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                menuDownRawX = event.getRawX();
                menuDownRawY = event.getRawY();
                menuStartX = menuButton.getX();
                menuStartY = menuButton.getY();
                menuDragging = false;
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - menuDownRawX;
                float dy = event.getRawY() - menuDownRawY;
                if (!menuDragging && ((dx * dx) + (dy * dy)) > (menuTouchSlop * menuTouchSlop)) {
                    menuDragging = true;
                    setPanelVisible(false);
                }
                if (menuDragging) {
                    moveMenuButton(menuStartX + dx, menuStartY + dy);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.getParent().requestDisallowInterceptTouchEvent(false);
                if (menuDragging) {
                    saveMenuButtonPosition();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    view.performClick();
                    setPanelVisible(editorPanel.getVisibility() != View.VISIBLE);
                }
                menuDragging = false;
                return true;

            default:
                return true;
        }
    }

    private void setPanelVisible(boolean visible) {
        editorPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        menuButton.setAlpha(visible ? 1.0f : 0.76f);
        if (visible) {
            editorPanel.post(this::positionPanelNearMenuButton);
        }
    }

    private void restoreMenuButtonPosition() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        float defaultX = dp(10);
        float defaultY = Math.max(dp(10), root.getHeight() - dp(62));
        float x = prefs.getFloat(KEY_MENU_X, defaultX);
        float y = prefs.getFloat(KEY_MENU_Y, defaultY);
        moveMenuButton(x, y);
    }

    private void saveMenuButtonPosition() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .edit()
                .putFloat(KEY_MENU_X, menuButton.getX())
                .putFloat(KEY_MENU_Y, menuButton.getY())
                .apply();
    }

    private void moveMenuButton(float x, float y) {
        float maxX = Math.max(0f, root.getWidth() - menuButton.getWidth() - dp(4));
        float maxY = Math.max(0f, root.getHeight() - menuButton.getHeight() - dp(4));
        menuButton.setX(clamp(x, dp(4), maxX));
        menuButton.setY(clamp(y, dp(4), maxY));
        if (editorPanel.getVisibility() == View.VISIBLE) {
            positionPanelNearMenuButton();
        }
    }

    private void positionPanelNearMenuButton() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;

        editorPanel.measure(
                View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST)
        );

        int panelWidth = Math.max(1, editorPanel.getMeasuredWidth());
        int panelHeight = Math.max(1, editorPanel.getMeasuredHeight());
        float spacing = dp(8);

        boolean openRight = menuButton.getX() + (menuButton.getWidth() / 2f) < (root.getWidth() / 2f);
        float x = openRight
                ? menuButton.getX() + menuButton.getWidth() + spacing
                : menuButton.getX() - panelWidth - spacing;
        float y = menuButton.getY();

        // If there is not enough room on either side, park the panel above/below the gear.
        if (x < dp(4) || x + panelWidth > root.getWidth() - dp(4)) {
            x = clamp(menuButton.getX(), dp(4), Math.max(dp(4), root.getWidth() - panelWidth - dp(4)));
            if (menuButton.getY() > root.getHeight() / 2f) {
                y = menuButton.getY() - panelHeight - spacing;
            } else {
                y = menuButton.getY() + menuButton.getHeight() + spacing;
            }
        }

        editorPanel.setX(clamp(x, dp(4), Math.max(dp(4), root.getWidth() - panelWidth - dp(4))));
        editorPanel.setY(clamp(y, dp(4), Math.max(dp(4), root.getHeight() - panelHeight - dp(4))));
    }

    private LinearLayout panelRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private Button panelButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private LinearLayout.LayoutParams panelButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(42));
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams panelButtonParamsWide() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(230), dp(42));
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private GradientDrawable makePanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xDD111111);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), 0x55FFFFFF);
        return drawable;
    }

    private void enableImmersiveSafely() {
        try {
            FullscreenUtils.enableImmersive(this);
        } catch (Throwable ignored) {
        }
    }

    private void updateSnapButtonText(Button button) {
        button.setText(ControlsPreferences.isSnapControlsEnabled(this) ? "Snap: ON" : "Snap: OFF");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
