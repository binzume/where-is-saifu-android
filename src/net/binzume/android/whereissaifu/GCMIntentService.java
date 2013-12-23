package net.binzume.android.whereissaifu;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService {
	
	private static final String MESSAGE_SCAN = "scan_request";
	private static final String MESSAGE_FOUND = "found";
	
	public GCMIntentService() {
		super(Constants.GCM_SENDER_ID);
	}

	@Override
	protected void onError(Context arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d("saifu", "GCM onMessage" + intent.getStringExtra("message"));
		if (MESSAGE_SCAN.equals(intent.getStringExtra("message"))) {
			Log.d("saifu", "GCM MESSAGE_SCAN");
			Intent serviceIntent = new Intent(context, SaifuUpdateService.class);
			serviceIntent.setAction("scan");
            context.startService(serviceIntent);
		}
		if (MESSAGE_FOUND.equals(intent.getStringExtra("message"))) {
			Log.d("saifu", "GCM MESSAGE_FOUND");
			Intent serviceIntent = new Intent(context, SaifuUpdateService.class);
			serviceIntent.setAction("found");
            context.startService(serviceIntent);
		}
	}

	@Override
	protected void onRegistered(Context context, String registrationId) {
		Log.d("saifu", "GCM registration id:" +  registrationId);
	}

	@Override
	protected void onUnregistered(Context arg0, String arg1) {

	}

}
