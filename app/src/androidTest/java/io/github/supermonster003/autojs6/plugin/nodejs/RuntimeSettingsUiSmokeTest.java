package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public final class RuntimeSettingsUiSmokeTest {
    @Test public void pluginPageSavesBothSettingsAndRejectsOverflow() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (PoolSettingsFixture settings = new PoolSettingsFixture(2, -1)) {
            Intent intent = instrumentation.getTargetContext().getPackageManager()
                    .getLaunchIntentForPackage(instrumentation.getTargetContext().getPackageName());
            assertNotNull(intent);
            Activity activity = instrumentation.startActivitySync(intent);
            try {
                instrumentation.runOnMainSync(() -> {
                    View root = activity.getWindow().getDecorView();
                    Spinner slots = find(root, Spinner.class);
                    EditText queue = find(root, EditText.class);
                    Button save = find(root, Button.class);
                    assertEquals(1, slots.getSelectedItemPosition());
                    assertEquals("", queue.getText().toString());
                    slots.setSelection(3);
                    queue.setText("6");
                    assertTrue(save.performClick());
                    Bundle saved = PoolSettingsFixture.read(activity);
                    assertEquals(4, saved.getInt(NodeRuntimeSettings.SLOTS));
                    assertEquals(6, saved.getInt(NodeRuntimeSettings.QUEUE));
                    queue.setText("2147483648");
                    save.performClick();
                    assertNotNull(queue.getError());
                    assertEquals(6, PoolSettingsFixture.read(activity).getInt(NodeRuntimeSettings.QUEUE));
                    queue.setText("");
                    save.performClick();
                    assertEquals(-1, PoolSettingsFixture.read(activity).getInt(NodeRuntimeSettings.QUEUE));
                });
            } finally { instrumentation.runOnMainSync(activity::finish); }
        }
    }

    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T match = find(group.getChildAt(i), type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
