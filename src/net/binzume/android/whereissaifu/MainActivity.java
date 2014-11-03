package net.binzume.android.whereissaifu;

import com.google.android.gcm.GCMRegistrar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
	private BroadcastReceiver receiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Intent intent1 = new Intent(getApplicationContext(), SaifuUpdateService.class);
		intent1.setAction("start");
		startService(intent1);

		findViewById(R.id.AlertButton1).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("alert");
				intent.putExtra("value", 1);
				startService(intent);
			}
		});

		findViewById(R.id.AlertButton2).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("alert");
				intent.putExtra("value", 2);
				startService(intent);
			}
		});

		findViewById(R.id.AlertStopButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("alert");
				intent.putExtra("value", 0);
				startService(intent);
			}
		});

		findViewById(R.id.DisconnectButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("close");
				startService(intent);
			}
		});

		findViewById(R.id.RefreshButton).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
				intent.setAction("poll");
				startService(intent);
			}
		});
		
		
		GCMRegistrar.checkDevice(this);
		GCMRegistrar.checkManifest(this);
		final String regId = GCMRegistrar.getRegistrationId(this);
		if ("".equals(regId)) {
		  GCMRegistrar.register(this, Constants.GCM_SENDER_ID);
		} else {
		  Log.d("saifu", "Already registered:" + regId);
		}
	}

	private void update(BLETagDevice d) {
		((TextView) findViewById(R.id.NameText)).setText(d.addr);
		((TextView) findViewById(R.id.NameText)).setText("ADDR:" + d.addr + " (" + d.name + ")");
		((TextView) findViewById(R.id.StatusText)).setText(d.isConnected() ? "Connected " + d.lastRssi : "Disconnected");
		((TextView) findViewById(R.id.StatusText)).setTextColor(d.isConnected() ? Color.GREEN : Color.RED);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (receiver != null) {
			unregisterReceiver(receiver);
			receiver = null;
		}
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if ("bletag_status".equals(intent.getAction())) {
					BLETagDevice d = (BLETagDevice) intent.getSerializableExtra("device");
					update(d);
				}
				if ("button_pressed".equals(intent.getAction())) {
					BLETagDevice d = (BLETagDevice) intent.getSerializableExtra("device");
					update(d);
					if (intent.getIntExtra("st", 0) != 0) {
						((TextView) findViewById(R.id.StatusText)).setTextColor(Color.BLUE);
					}
				}
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction("bletag_status");
		filter.addAction("button_pressed");
		registerReceiver(receiver, filter);
		
		Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
		intent.setAction("tellStatus");
		startService(intent);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (receiver != null) {
			unregisterReceiver(receiver);
			receiver = null;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
