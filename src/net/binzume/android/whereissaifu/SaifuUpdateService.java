package net.binzume.android.whereissaifu;

import java.util.LinkedList;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class SaifuUpdateService extends Service implements BLETagDevice.TagDeviceEventListener {

	private Handler handler = new Handler();
	private boolean resetAdapter = false;
	private boolean disabled = false;
	private BLETagDevice currentDevice = new BLETagDevice(Constants.BT_ADDR);
	
	final LinkedList<LocationHistory> histories = new LinkedList<LocationHistory>();
	
	public class SaifuStatusBinder extends Binder {
		public boolean connected() {
			return currentDevice != null && currentDevice.isConnected();
		}
	}

	@Override
	public void onCreate() {
		setPollingInterval(600);
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		terminate();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new SaifuStatusBinder();
	}
	
	private void sendStatus(BLETagDevice d) {
		Intent intent = new Intent("bletag_status");
		intent.putExtra("device", d);
		intent.putExtra("addr", d.addr);
		sendBroadcast(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && "close".equals(intent.getAction())) {
			disabled = false;
			terminate();
			stopSelf();
			return START_NOT_STICKY;
		}
		if (intent != null && "tellStatus".equals(intent.getAction())) {
			sendStatus(currentDevice);
			return START_STICKY;
		}
		
		if (intent != null &&  "scan".equals(intent.getAction())) {
			handler.postDelayed(new Runnable() {
				private int count = 0;
				@Override
				public void run() {
					if (!(currentDevice == null || !currentDevice.isConnected())) {
						Log.d("saifu", "requested scanning....");
						terminate();
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								startService(new Intent(SaifuUpdateService.this, SaifuUpdateService.class));
							}
						}, 1000);
					}
					// Reset BT Adapter
					if (count++ < 10 && !(currentDevice != null && currentDevice.isConnected()) && resetAdapter) {
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								if (!(currentDevice != null && currentDevice.isConnected())) {
									final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
									final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
									bluetoothAdapter.disable();
								}
							}
						}, 33000);
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
								final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
								if (!bluetoothAdapter.isEnabled()) {
									bluetoothAdapter.enable();
								}
							}
						}, 35000);
						handler.postDelayed(this, 40000);
					}
					
				}
			},30000);
		}
		if (intent != null &&  "found".equals(intent.getAction())) {
			notifyMessage("Saifu OK");
			return START_STICKY;
		}

		if (currentDevice == null || currentDevice.getConnectState() == BLETagDevice.CONNECT_STATE_DISCONNECTED) {
			if (intent != null && "poll".equals(intent.getAction())) {
				disabled = false;
			}
			if (!disabled) {
				find();
			}
		} else if (currentDevice != null && currentDevice.isConnected()) {
			Log.d("saifu", "action:" + intent.getAction());
			if ("alert".equals(intent.getAction())) {
				currentDevice.setAlarm(intent.getIntExtra("value", 0));
			} else if ("poll".equals(intent.getAction())) {
				readRssi();
			} else {
				// readRssi();
			}
		}
		return START_STICKY;
	}

	private void readRssi() {
		if (currentDevice == null || !currentDevice.isConnected())
			return;
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Whereis");
		wl.acquire(2000);
		currentDevice.lastRssi = 0;
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (currentDevice != null && currentDevice.lastRssi == 0) {
					notifyMessage("Saifu status update: LOST rssi");
					new AsyncTask<Void, Void, Void>() {
						@Override
						protected Void doInBackground(Void... params) {
							new LocationApiClient().updateStatus("lost");
							return null;
						}
					}.execute();
				}
			}
		}, 1500);

		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (currentDevice != null && currentDevice.lastRssi != 0) {
					wl.release();
				}
			}
		}, 500);
		currentDevice.readRemoteRssi();
	}
	
	private void terminate() {
		Log.d("saifu", "terminate....");
		if (currentDevice != null) {
			currentDevice.disconnect();
		}
		setPollingInterval(0);
	}

	private void setPollingInterval(int interval) {
		AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), SaifuUpdateService.class);
		intent.setAction("poll");
		PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Log.d("ChatActivity", "interval: " + interval);
		if (interval > 0) {
			long t = interval * 1000;
			alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + t, t, pendingIntent);
		} else {
			alarmManager.cancel(pendingIntent);
		}
	}

	@SuppressWarnings("deprecation")
	public void notifyMessage(String message) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Notification notification = new Notification();
		notification.defaults = Notification.DEFAULT_ALL;
		notification.icon = R.drawable.ic_launcher;
		// notification.when = notificationTime;
		notification.tickerText = message;
		
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		notification.setLatestEventInfo(this, "Choco Chats", notification.tickerText, contentIntent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.vibrate = new long[]{0, 200, 200, 200, 200, 100};
		notificationManager.notify(100, notification);
	}

	private void find() {
		// connecting = true;

		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

		final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { //  
			Log.e("saifu", "Available Bluetooth Adapter not found.");
			return;
		}

		// bonded device
		boolean ok = false;
		for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
			Log.d("saifu", "Bonded " + device.getName());
			//if ("LBT-VRU01".equals(device.getName())) {
			if (currentDevice.addr.equalsIgnoreCase(device.getAddress()) && !ok) {
				connect(device);
				ok = true;
			}
		}

		if (!ok) {

			Log.d("saifu", "startLeScan");
			final BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
				private boolean found = false;

				@Override
				public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
					Log.d("saifu", "Scan " + device.getName() + " addr:" + device.getAddress() + " rssi: " + rssi);
					if (currentDevice.addr.equalsIgnoreCase(device.getAddress()) && !found) {
						found = true;
						bluetoothAdapter.stopLeScan(this);
						connect(device);
					}
				}
			};
			bluetoothAdapter.startLeScan(callback);

			handler.postDelayed(new Runnable() {

				@Override
				public void run() {
					Log.d("saifu", "stopLeScan");
					bluetoothAdapter.stopLeScan(callback);
					if (currentDevice.getConnectState() == BLETagDevice.CONNECT_STATE_DISCONNECTED) {
						Log.d("saifu", "not respond. try direct connect");
						BluetoothDevice device = bluetoothAdapter.getRemoteDevice(currentDevice.addr);
						if (device != null) {
							connect(device);
						}
					}
				}
			}, 1000);
		}
	}

	private void connect(BluetoothDevice device) {
		currentDevice.setListener(this);
		currentDevice.connect(device, handler, this);
	}


	@Override
	public void onStatusUpdated(final BLETagDevice d, int st) {
		sendStatus(d);
		if (st == 1) {
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			final Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
			if (location != null) {
				Log.d("saifu", "Location latlon:" + location.getLatitude() + "," + location.getLongitude() + " acc:" + location.getAccuracy());
				
				// histories.add(new LocationHistory(location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), null));

				new AsyncTask<Void, Void, Void>() {

					@Override
					protected Void doInBackground(Void... params) {
						new LocationApiClient().updateLocaton(location.getLatitude(), location.getLongitude(), location.getAccuracy(), d.lastRssi);
						return null;
					}
				}.execute();
			}
			
		}
	}

	@Override
	public void onPressButton(final BLETagDevice d, int st) {
		Intent intent = new Intent("button_pressed");
		intent.putExtra("device", d);
		intent.putExtra("addr", d.addr);
		intent.putExtra("st", st);
		sendBroadcast(intent);
		
		if (st == 1) {

			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			@SuppressWarnings("deprecation")
			final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Whereis");
			wl.acquire(2000);

			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			final Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					new LocationApiClient().updateLocaton(location.getLatitude(), location.getLongitude(), location.getAccuracy(), d.lastRssi);
					return null;
				}
			}.execute();
		}
	}

	@Override
	public void onLost(BLETagDevice d) {
		notifyMessage("Saifu status update: LOST");
		Log.d("saifu", "Saifu status update: LOST");
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				new LocationApiClient().updateStatus("lost");
				return null;
			}
		}.execute();
	}
}
