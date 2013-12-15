package net.binzume.android.whereissaifu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SaifuStartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startService(new Intent(context, SaifuUpdateService.class));
        }
	}

}
