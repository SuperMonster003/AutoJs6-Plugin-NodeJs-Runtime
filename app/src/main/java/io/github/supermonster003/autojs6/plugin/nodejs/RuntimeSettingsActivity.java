package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Plugin Center opens CATEGORY_INFO without creating a launcher icon. */
public final class RuntimeSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setTheme(night ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
        super.onCreate(state);
        setTitle(R.string.runtime_settings_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(form);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0,
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        Bundle settings = NodeRuntimeSettings.read(this);
        TextView slotsLabel = label(form, R.string.runtime_settings_slots);
        Spinner slots = new Spinner(this);
        slots.setId(View.generateViewId());
        slotsLabel.setLabelFor(slots.getId());
        String[] choices = new String[NodeRuntimeSettings.MAX_SLOTS];
        for (int i = 0; i < choices.length; i++) choices[i] = Integer.toString(i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        slots.setAdapter(adapter);
        slots.setSelection(state == null ? settings.getInt(NodeRuntimeSettings.SLOTS) - 1 : state.getInt("slots"));
        form.addView(slots);

        TextView queueLabel = label(form, R.string.runtime_settings_queue);
        EditText queue = new EditText(this);
        queue.setId(View.generateViewId());
        queueLabel.setLabelFor(queue.getId());
        queue.setSingleLine(true);
        queue.setInputType(InputType.TYPE_CLASS_NUMBER);
        queue.setHint(R.string.runtime_settings_unlimited);
        int limit = settings.getInt(NodeRuntimeSettings.QUEUE);
        queue.setText(state == null ? (limit < 0 ? "" : Integer.toString(limit)) : state.getString("queue"));
        form.addView(queue);
        label(form, R.string.runtime_settings_explanation);
        Button save = new Button(this);
        save.setText(R.string.runtime_settings_save);
        form.addView(save);
        save.setOnClickListener(view -> {
            int capacity;
            try {
                String value = queue.getText().toString().trim();
                capacity = value.isEmpty() ? NodeRuntimeSettings.UNLIMITED : Integer.parseInt(value);
                if (!value.isEmpty() && capacity < 0) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                queue.setError(getString(R.string.runtime_settings_invalid_queue));
                return;
            }
            NodeRuntimeSettings.save(this, slots.getSelectedItemPosition() + 1, capacity);
            Toast.makeText(this, R.string.runtime_settings_saved, Toast.LENGTH_SHORT).show();
        });
        slotInput = slots;
        queueInput = queue;
    }

    private Spinner slotInput;
    private EditText queueInput;

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("slots", slotInput.getSelectedItemPosition());
        state.putString("queue", queueInput.getText().toString());
        super.onSaveInstanceState(state);
    }

    private TextView label(LinearLayout form, int text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0);
        form.addView(label);
        return label;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
