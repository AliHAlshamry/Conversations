package eu.siacs.chatx.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.chatx.R;
import eu.siacs.chatx.ui.util.SettingsUtils;
import eu.siacs.chatx.utils.ThemeHelper;

import static eu.siacs.chatx.ui.XmppActivity.configureActionBar;

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
