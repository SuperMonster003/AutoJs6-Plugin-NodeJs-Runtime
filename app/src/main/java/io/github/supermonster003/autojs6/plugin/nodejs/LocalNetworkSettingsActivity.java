package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A user-operated permission page. Opening it never starts a runtime or requests a grant. */
public final class LocalNetworkSettingsActivity extends Activity {
    private TextView status;
    private Button allow;

    @Override public void onCreate(Bundle state) {
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(night ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        setTitle(getApplicationInfo().loadLabel(getPackageManager()));
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.local_network_title);
        title.setTextSize(22);
        content.addView(title);
        TextView explanation = new TextView(this);
        explanation.setText(R.string.local_network_explanation);
        explanation.setTextSize(16);
        content.addView(explanation);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, padding, 0, padding);
        content.addView(status);
        allow = new Button(this);
        allow.setText(R.string.local_network_allow);
        allow.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT < 37 || LocalNetworkAccess.isGranted(this)) return;
            boolean asked = getPreferences(MODE_PRIVATE).getBoolean("requested", false);
            if (asked && !shouldShowRequestPermissionRationale(LocalNetworkAccess.PERMISSION)) {
                openSettings();
            } else {
                getPreferences(MODE_PRIVATE).edit().putBoolean("requested", true).apply();
                requestPermissions(new String[]{LocalNetworkAccess.PERMISSION}, 37);
            }
        });
        content.addView(allow);
        Button settings = new Button(this);
        settings.setText(R.string.local_network_settings);
        settings.setOnClickListener(view -> openSettings());
        content.addView(settings);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        refresh();
    }

    private void refresh() {
        boolean granted = LocalNetworkAccess.isGranted(this);
        status.setText(Build.VERSION.SDK_INT < 37 ? R.string.local_network_not_required
                : granted ? R.string.local_network_granted : R.string.local_network_denied);
        allow.setEnabled(!granted);
    }

    private void openSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }
}
