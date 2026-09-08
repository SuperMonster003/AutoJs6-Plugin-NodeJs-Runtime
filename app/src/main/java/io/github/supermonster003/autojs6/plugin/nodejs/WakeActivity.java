package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Activity;
import android.os.Bundle;

/** Allows the host plugin center to activate a newly installed, stopped plugin. */
public final class WakeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
