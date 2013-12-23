package net.binzume.android.whereissaifu;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class SaifuUpdateService extends Service {

	private final String addr = Constants.BT_ADDR;
	private BluetoothGatt gatt = null;
	private Handler handler = new Handler();
	private BluetoothGattCharacteristic characteristic;
	private long lastRssi = 0;
	private boolean connected = false;
	private boolean connecting = false;

	private final static int RECONNECT_WAIT = 5000;
	private final static int CONNECT_TIMEOUT = 10000;

	@Override
	public void onCreate() {
		setPollingInterval(600);
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && "close".equals(intent.getAction())) {
			BluetoothGatt g = gatt;
			gatt = null;
			connected = false;
			connecting = false;
			if (g != null) {
				g.disconnect();
				g.close();
			}
			setPollingInterval(0);
			stopSelf();
			return START_NOT_STICKY;
		}
		
		if (intent != null &&  "scan".equals(intent.getAction())) {
			handler.postDelayed(new Runnable() {
				private int count = 0;
				@Override
				public void run() {
					Log.d("saifu", "requested scanning....");
		            startService(new Intent(SaifuUpdateService.this, SaifuUpdateService.class));
					if (count++ < 10 && !connected) {
						handler.postDelayed(this, 30000);
					}
					
				}
			},45000);
		}
		if (intent != null &&  "found".equals(intent.getAction())) {
			notifyMessage("Saifu OK");
			return START_STICKY;
		}

		if (!connected && !connecting) {
			find();
		} else if (gatt != null && intent != null) {
			Log.d("saifu", "action:" + intent.getAction());
			if (characteristic != null && "alert".equals(intent.getAction())) {
				Log.d("saifu", "GATT write Characteristic!");
				characteristic.setValue(new byte[] { 0x02 });
				gatt.writeCharacteristic(characteristic);
			} else if ("stop".equals(intent.getAction())) {
				Log.d("saifu", "GATT write Characteristic!");
				characteristic.setValue(new byte[] { 0x00 });
				gatt.writeCharacteristic(characteristic);
			} else if ("poll".equals(intent.getAction())) {
				readRssi();
			} else {
				// readRssi();
			}
		}
		return START_STICKY;
	}

	private void readRssi() {
		if (!connected || gatt == null)
			return;
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Whereis");
		wl.acquire(2000);
		lastRssi = 0;
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (lastRssi == 0) {
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
				if (lastRssi != 0) {
					wl.release();
				}
			}
		}, 500);
		gatt.readRemoteRssi();
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
		connecting = true;

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
			if ("LBT-VRU01".equals(device.getName())) {
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
					if (addr.equalsIgnoreCase(device.getAddress()) && !found) {
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
					if (gatt == null) {
						Log.d("saifu", "not respond. try direct connect");
						BluetoothDevice device = bluetoothAdapter.getRemoteDevice(addr);
						if (device != null) {
							connect(device);
						}
					}
				}
			}, 1000);
		}
	}

	private void connect(BluetoothDevice device) {
		gatt = device.connectGatt(this, false, new BluetoothGattCallback() {
			
			private int state = 0;
			private static final int STATE_DISCOVER = 1;
			private static final int STATE_OK = 0;
			

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				if (characteristic.getValue().length > 0) {
					Log.d("saifu", "onCharacteristicRead " + characteristic.getValue()[0]);
				}
				super.onCharacteristicRead(gatt, characteristic, status);
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				if (characteristic.getValue().length > 0) {
					Log.d("saifu", "onCharacteristicChanged " + characteristic.getValue());
				}
				super.onCharacteristicChanged(gatt, characteristic);
			}

			@Override
			public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
				super.onConnectionStateChange(gatt, status, newState);
				if (status != 0)
					return;
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					state = STATE_DISCOVER;
					handler.postDelayed(new Runnable() {
						private int c = 0;
						public void run() {
							if (c++ < 5 && state == STATE_DISCOVER) {
								gatt.discoverServices();
								handler.postDelayed(this, 1000); // retry after.
							}
						}
					}, 300);
					SaifuUpdateService.this.gatt = gatt;
					connecting = false;
					connected = true;
					Log.d("saifu", "Connected");
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (SaifuUpdateService.this.gatt == gatt) {
						SaifuUpdateService.this.gatt = null;
					}
					if (connected) {
						// reconnect
						handler.postDelayed(new Runnable() {
							public void run() {
								Log.d("saifu", "Reconnecting...");
								connect(gatt);
							}
						}, RECONNECT_WAIT);
					}
					connecting = false;
					connected = false;
					Log.d("saifu", "Disconnected");
				}
			}

			@Override
			public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
				super.onReadRemoteRssi(gatt, rssi, status);
				state = STATE_OK;
				LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
				final Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
				if (location != null) {
					Log.d("saifu", "Location latlon:" + location.getLatitude() + "," + location.getLongitude() + " acc:" + location.getAccuracy());

					new AsyncTask<Void, Void, Void>() {

						@Override
						protected Void doInBackground(Void... params) {
							new LocationApiClient().updateLocaton(location.getLatitude(), location.getLongitude(), location.getAccuracy(), rssi);
							return null;
						}
					}.execute();
				}

				Log.d("saifu", "GATT " + gatt.getDevice().getName() + " rssi: " + rssi + " t" + +System.currentTimeMillis());
				lastRssi = rssi;
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				super.onServicesDiscovered(gatt, status);
				state = STATE_OK;
				// TODO Auto-generated method stub
				for (BluetoothGattService s : gatt.getServices()) {
					Log.d("saifu", "GATT s:" + s.getUuid().toString());
					for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
						Log.d("saifu", "GATT   c:" + c.getUuid());
						if ("00001802-0000-1000-8000-00805f9b34fb".equals(s.getUuid().toString())) {
							if ("00002a06-0000-1000-8000-00805f9b34fb".equals(c.getUuid().toString())) {
								characteristic = c;
							}
						}
						if ("00002a19-0000-1000-8000-00805f9b34fb".equals(c.getUuid().toString())) {
							gatt.setCharacteristicNotification(c, true);
							gatt.readCharacteristic(c);
						}
					}
				}
				gatt.readRemoteRssi();
			}
		});
		if (gatt != null && connected == false) {
			connect(gatt);
		} else {
			connecting = false;
		}
	}

	private void connect(final BluetoothGatt gatt) {
		Log.d("saifu", "Connecting...");
		connected = false;
		connecting = true;
		gatt.connect();

		handler.postDelayed(new Runnable() {
			public void run() {
				if (connecting) {
					Log.d("saifu", "Coonnect timeout");
					gatt.disconnect();
					SaifuUpdateService.this.gatt = null;
					connected = false;
					connecting = false;

					if (lastRssi != 0) {
						lastRssi = 0;
						notifyMessage("Saifu status update: LOST");
						new AsyncTask<Void, Void, Void>() {
							@Override
							protected Void doInBackground(Void... params) {
								new LocationApiClient().updateStatus("lost");
								return null;
							}
						}.execute();
					}
				}
			}
		}, CONNECT_TIMEOUT);
	}
}
