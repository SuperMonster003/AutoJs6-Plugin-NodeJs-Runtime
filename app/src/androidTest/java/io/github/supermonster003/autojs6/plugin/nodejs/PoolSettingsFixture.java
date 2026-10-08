package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;

/** Exercises the real private settings provider and restores the user's values. */
final class PoolSettingsFixture implements AutoCloseable {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Bundle original = read(context);

    PoolSettingsFixture(int slots, int queue) { set(slots, queue); }

    void set(int slots, int queue) { save(context, slots, queue); }

    // Exercise the IPC boundary without relying on app methods that R8 can inline/remove.
    static Bundle read(Context context) {
        return context.getContentResolver().call(android.net.Uri.parse("content://" + context.getPackageName()
                + ".runtime-settings"), "get", null, null);
    }

    private static void save(Context context, int slots, int queue) {
        Bundle values = new Bundle();
        values.putInt(NodeRuntimeSettings.SLOTS, slots);
        values.putInt(NodeRuntimeSettings.QUEUE, queue);
        context.getContentResolver().call(android.net.Uri.parse("content://" + context.getPackageName()
                + ".runtime-settings"), "set", null, values);
    }

    @Override public void close() {
        save(context, original.getInt(NodeRuntimeSettings.SLOTS), original.getInt(NodeRuntimeSettings.QUEUE));
    }
}
