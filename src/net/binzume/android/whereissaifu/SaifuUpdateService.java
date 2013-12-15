package net.binzume.android.whereissaifu;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class SaifuUpdateService extends Service {

	private final String addr = Constants.BT_ADDR;
	private BluetoothGatt gatt = null;
	private Handler handler = new Handler();
	private BluetoothGattCharacteristic characteristic;
	private long lastRssi = 0;
	private boolean found = true;

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (gatt == null) {
			find();
			if (found) {
				found = false;
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						if (!found) {
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
			}
		} else {
			if (intent != null) {
				Log.d("saifu", "action:" + intent.getAction());
				if (characteristic != null && "alert".equals(intent.getAction())) {
					Log.d("saifu", "GATT write Characteristic!");
					characteristic.setValue(new byte[] { 0x02 });
					gatt.writeCharacteristic(characteristic);
				}
				if ("stop".equals(intent.getAction())) {
					Log.d("saifu", "GATT write Characteristic!");
					characteristic.setValue(new byte[] { 0x00 });
					gatt.writeCharacteristic(characteristic);
				}
				if ("close".equals(intent.getAction())) {
					gatt.disconnect();
					gatt = null;
				}
				if ("poll".equals(intent.getAction())) {
					readRssi();
				}
			} else {
				readRssi();
			}
		}
		return START_STICKY;
	}

	private void readRssi() {
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Whereis");
		wl.acquire(2000);
		lastRssi = 0;
		if (found) {
			found = false;
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!found) {
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
		}
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (lastRssi != 0) {
					wl.release();
				}
			}
		}, 500);
		if (gatt != null) {
			gatt.readRemoteRssi();
		}
	}

	private void find() {
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

		final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null) { //  || blueToothAdapter.isEnabled()
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
		device.connectGatt(this, true, new BluetoothGattCallback() {

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
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					handler.postDelayed(new Runnable() {

						@Override
						public void run() {
							gatt.discoverServices();
						}
					}, 500);
					Log.d("saifu", "Connected");
					SaifuUpdateService.this.gatt = gatt;
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (SaifuUpdateService.this.gatt == gatt) {
						gatt.close();
						SaifuUpdateService.this.gatt = null;
					}
					Log.d("saifu", "Disconnected");
				}
			}

			@Override
			public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
				LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
				final Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				found = true;
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
				super.onReadRemoteRssi(gatt, rssi, status);
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				super.onServicesDiscovered(gatt, status);
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
	}

}
