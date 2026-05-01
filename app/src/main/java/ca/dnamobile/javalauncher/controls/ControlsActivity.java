package ca.dnamobile.javalauncher.controls;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.FullscreenUtils;

public final class ControlsActivity extends AppCompatActivity {
    private static final int REQUEST_IMPORT_CONTROLS = 9011;
    private static final int REQUEST_EXPORT_CONTROLS = 9012;

    private final ArrayList<File> layoutFiles = new ArrayList<>();
    @Nullable private File pendingExportFile;
    private ArrayAdapter<String> adapter;
    private ListView listView;
    private TextView summary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("Touch Controls");
        title.setTextSize(24f);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        summary = new TextView(this);
        summary.setTextSize(14f);
        root.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button importButton = new Button(this);
        importButton.setText("Import");
        importButton.setOnClickListener(view -> openImportPicker());
        actions.addView(importButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button editButton = new Button(this);
        editButton.setText("Edit Current");
        editButton.setOnClickListener(view -> startActivity(new Intent(this, ControlsEditorActivity.class)));
        actions.addView(editButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setOnClickListener(view -> finish());
        actions.addView(closeButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        listView = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> selectLayout(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showLayoutOptions(position);
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        refreshList();
        root.post(this::enableImmersiveSafely);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listView != null) {
            listView.post(this::enableImmersiveSafely);
        } else {
            enableImmersiveSafely();
        }
        refreshList();
    }

    private void enableImmersiveSafely() {
        try {
            FullscreenUtils.enableImmersive(this);
        } catch (Throwable throwable) {
            Logging.e("ControlsActivity", "Unable to enable immersive mode", throwable);
        }
    }

    private void refreshList() {
        layoutFiles.clear();
        layoutFiles.addAll(TouchControlsStore.listLayouts(this));

        ArrayList<String> names = new ArrayList<>();
        String selected = ControlsPreferences.getSelectedLayoutPath(this);
        for (File file : layoutFiles) {
            TouchControlsLayoutData data = TouchControlsStore.loadLayout(file);
            String prefix = file.getAbsolutePath().equals(selected) ? "✓ " : "   ";
            names.add(prefix + data.name + "\n" + file.getName());
        }
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
        summary.setText("Select a layout, long-press for options. Import/export uses JSON layouts compatible with JavaLauncher, Zalith/Pojav-style, Mojo, and Amethyst-derived controls.");
    }

    private void selectLayout(int position) {
        if (position < 0 || position >= layoutFiles.size()) return;
        File file = layoutFiles.get(position);
        ControlsPreferences.setSelectedLayoutPath(this, file.getAbsolutePath());
        Toast.makeText(this, "Selected " + file.getName(), Toast.LENGTH_SHORT).show();
        refreshList();
    }

    private void showLayoutOptions(int position) {
        if (position < 0 || position >= layoutFiles.size()) return;
        File file = layoutFiles.get(position);
        String[] options = new String[]{"Use this layout", "Edit", "Export JSON", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) selectLayout(position);
                    if (which == 1) {
                        ControlsPreferences.setSelectedLayoutPath(this, file.getAbsolutePath());
                        startActivity(new Intent(this, ControlsEditorActivity.class));
                    }
                    if (which == 2) openExportPicker(file);
                    if (which == 3) confirmDelete(file);
                })
                .show();
    }

    private void confirmDelete(@NonNull File file) {
        if (file.equals(TouchControlsStore.getDefaultLayoutFile(this))) {
            Toast.makeText(this, "Default layout cannot be deleted.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete layout?")
                .setMessage(file.getName())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    if (file.getAbsolutePath().equals(ControlsPreferences.getSelectedLayoutPath(this))) {
                        ControlsPreferences.setSelectedLayoutPath(this, TouchControlsStore.getDefaultLayoutFile(this).getAbsolutePath());
                    }
                    refreshList();
                })
                .show();
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT_CONTROLS);
        } catch (ActivityNotFoundException throwable) {
            Toast.makeText(this, "No file picker found.", Toast.LENGTH_LONG).show();
        }
    }

    private void openExportPicker(@NonNull File file) {
        pendingExportFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, safeExportName(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_CONTROLS);
        } catch (ActivityNotFoundException throwable) {
            pendingExportFile = null;
            Toast.makeText(this, "No file picker found.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMPORT_CONTROLS) {
            handleImportResult(resultCode, data);
            return;
        }

        if (requestCode == REQUEST_EXPORT_CONTROLS) {
            handleExportResult(resultCode, data);
        }
    }

    private void handleImportResult(int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        try {
            File imported = TouchControlsStore.saveImportedLayout(this, uri);
            Toast.makeText(this, "Imported " + imported.getName(), Toast.LENGTH_SHORT).show();
            refreshList();
        } catch (Throwable throwable) {
            Logging.e("ControlsActivity", "Unable to import controls", throwable);
            Toast.makeText(this, "Import failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleExportResult(int resultCode, @Nullable Intent data) {
        File source = pendingExportFile;
        pendingExportFile = null;

        if (resultCode != RESULT_OK || data == null || data.getData() == null || source == null) {
            return;
        }

        Uri uri = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("Unable to open export destination.");
            String json = TouchControlsStore.readText(source);
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, "Exported " + source.getName(), Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            Logging.e("ControlsActivity", "Unable to export controls", throwable);
            Toast.makeText(this, "Export failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private static String safeExportName(@NonNull File file) {
        String name = file.getName();
        if (!name.toLowerCase().endsWith(".json")) {
            name += ".json";
        }
        return name.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
