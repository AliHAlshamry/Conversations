package eu.siacs.auva.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.auva.R;
import eu.siacs.auva.ui.util.SettingsUtils;
import eu.siacs.auva.utils.ThemeHelper;

import static eu.siacs.auva.ui.XmppActivity.configureActionBar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onResume(){
        super.onResume();
        SettingsUtils.applyScreenshotPreventionSetting(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(ThemeHelper.find(this));

        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        setTitle(getString(R.string.title_activity_about_x, getString(R.string.app_name)));
    }
}
