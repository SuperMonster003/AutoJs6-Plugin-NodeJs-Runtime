package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Private settings owner in the dispatcher process; UI and tests use the same IPC path. */
public final class NodeRuntimeSettings extends ContentProvider {
    static final int DEFAULT_SLOTS = 2;
    static final int MAX_SLOTS = 8;
    static final int UNLIMITED = -1;
    static final String SLOTS = "parallelSlots";
    static final String QUEUE = "queueLimit";
    private static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".runtime-settings");

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("node-runtime-settings", Context.MODE_PRIVATE);
    }

    static Bundle read(Context context) {
        return context.getContentResolver().call(URI, "get", null, null);
    }

    static void save(Context context, int slots, int queue) {
        Bundle values = new Bundle();
        values.putInt(SLOTS, slots);
        values.putInt(QUEUE, queue);
        context.getContentResolver().call(URI, "set", null, values);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle values) {
        SharedPreferences preferences = preferences(getContext());
        if ("set".equals(method)) {
            if (values == null) throw new IllegalArgumentException("Missing runtime settings");
            int slots = values.getInt(SLOTS, DEFAULT_SLOTS), queue = values.getInt(QUEUE, UNLIMITED);
            if (slots < 1 || slots > MAX_SLOTS || queue < UNLIMITED) {
                throw new IllegalArgumentException("Slots must be 1..8 and queue limit must be -1 or nonnegative");
            }
            if (!preferences.edit().putInt(SLOTS, slots).putInt(QUEUE, queue).commit()) {
                throw new IllegalStateException("Could not save runtime settings");
            }
        } else if (!"get".equals(method)) {
            throw new IllegalArgumentException("Unknown settings operation: " + method);
        }
        Bundle result = new Bundle();
        result.putInt(SLOTS, preferences.getInt(SLOTS, DEFAULT_SLOTS));
        result.putInt(QUEUE, preferences.getInt(QUEUE, UNLIMITED));
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
