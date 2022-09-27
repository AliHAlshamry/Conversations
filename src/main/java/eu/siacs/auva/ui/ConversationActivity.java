package eu.siacs.auva.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.auva.ui.util.SettingsUtils;

public class ConversationActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivity(new Intent(this, ConversationsActivity.class));
		finish();
	}

	@Override
	protected void onResume(){
		super.onResume();
		SettingsUtils.applyScreenshotPreventionSetting(this);
	}
}
