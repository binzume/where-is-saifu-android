package net.binzume.android.whereissaifu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Intent intent1 = new Intent(getApplicationContext(), SaifuUpdateService.class);
		intent1.setAction("start");
		startService(intent1);

		findViewById(R.id.AlertButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("alert");
				startService(intent);
			}
		});

		findViewById(R.id.StopButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("stop");
				startService(intent);
			}
		});

		findViewById(R.id.CloseButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("close");
				startService(intent);
			}
		});

		findViewById(R.id.PingButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("poll");
				startService(intent);
			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
