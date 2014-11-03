package net.binzume.android.whereissaifu;

import java.io.Serializable;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

@SuppressWarnings("serial")
public class BLETagDevice implements Serializable {
	private static final String TAG = "BLETagDevice";

	private final static int RECONNECT_WAIT = 5000;
	private final static int CONNECT_TIMEOUT = 10000;

	public static final int CONNECT_STATE_CONNECTING = 1;
	public static final int CONNECT_STATE_CONNECTED = 2;
	public static final int CONNECT_STATE_DISCONNECTING = 3;
	public static final int CONNECT_STATE_DISCONNECTED = 0;

	public static final UUID ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
	public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
	public static final UUID BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
	public static final UUID BATTERY_POWER_STATE_UUID = UUID.fromString("00002a1b-0000-1000-8000-00805f9b34fb");
	public static final UUID ALERT_LEVEL_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

	public interface TagDeviceEventListener {
		void onStatusUpdated(BLETagDevice d, int st);

		void onPressButton(BLETagDevice d, int st);

		void onLost(BLETagDevice d);
	}

	public final String addr;
	public String name;
	public int lastRssi;
	public int battery = -1;
	public long lastRespondTime;
	private volatile int connectState = 0;

	private transient volatile TagDeviceEventListener listener;
	private transient volatile BluetoothGatt gatt = null;

	public BLETagDevice(String addr) {
		this.addr = addr;
		this.name = "UNKNOWN";
	}

	public boolean isConnected() {
		return connectState == CONNECT_STATE_CONNECTED;
	}

	public int getConnectState() {
		return connectState;
	}

	public void setListener(TagDeviceEventListener listener) {
		this.listener = listener;
	}

	public void setAlarm(int level) {
		BluetoothGattCharacteristic c = characteristic(ALERT_SERVICE_UUID, ALERT_LEVEL_UUID);
		if (c == null) {
			Log.w(TAG, "ALERT_LEVEL_UUID NOT found");
			return;
		}
		c.setValue(new byte[] { (byte) level });
		gatt.writeCharacteristic(c);
	}

	public void checkBattery() {
		BluetoothGattCharacteristic c = characteristic(BATTERY_SERVICE_UUID, BATTERY_UUID);
		if (c == null) {
			Log.w(TAG, "BATTERY_STATE_UUID NOT found");
			if (isConnected()) {
				gatt.discoverServices();
			}
			return;
		}
		gatt.readCharacteristic(c);
	}

	private BluetoothGattCharacteristic characteristic(UUID sid, UUID cid) {
		if (!isConnected()) {
			return null;
		}
		BluetoothGattService s = gatt.getService(sid);
		if (s == null) {
			Log.w(TAG, "Service NOT found :" + sid.toString());
			return null;
		}
		BluetoothGattCharacteristic c = s.getCharacteristic(cid);
		if (c == null) {
			Log.w(TAG, "Characteristic NOT found :" + cid.toString());
			return null;
		}
		return c;
	}

	public void connect(final BluetoothDevice device, final Handler handler, final Context context) {
		Log.d(TAG, "connect: " + addr);
		name = device.getName();
		//addr = device.getAddress();
		if (gatt != null) {
			return;
		}
		connectState = CONNECT_STATE_CONNECTING;

		gatt = device.connectGatt(context, false, new BluetoothGattCallback() {

			@Override
			public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
				// Log.d(TAG, "onDescriptorRead " + descriptor.getValue().length  + " : "+ descriptor.getValue()[0] + ","+ descriptor.getValue()[1]);
				super.onDescriptorRead(gatt, descriptor, status);
			}

			private int state = 0;
			private static final int STATE_DISCOVER = 1;
			private static final int STATE_OK = 0;

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				if (characteristic.getValue() != null && characteristic.getValue().length > 0) {
					Log.d(TAG, "onCharacteristicRead " + characteristic.getValue()[0]);
					if (characteristic.getUuid().equals(BATTERY_UUID)) {
						battery = characteristic.getValue()[0];
						statusUpdated(0);
					}
				}
				lastRespondTime = System.currentTimeMillis();
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				if (characteristic.getValue() != null && characteristic.getValue().length > 0) {
					Log.d(TAG, "onCharacteristicChanged : " + characteristic.getValue()[0] + "," + characteristic.getValue()[1]);
					if (listener != null) {
						listener.onPressButton(BLETagDevice.this, characteristic.getValue()[0]);
					}
				}
				lastRespondTime = System.currentTimeMillis();
			}

			@Override
			public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
				state = STATE_OK;
				Log.d(TAG, "GATT " + name + " rssi: " + rssi + " t" + +System.currentTimeMillis());
				lastRssi = rssi;
				statusUpdated(1);
				lastRespondTime = System.currentTimeMillis();
			}

			@Override
			public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
				super.onConnectionStateChange(gatt, status, newState);
				Log.d(TAG, "onConnectionStateChange " + addr + " " + status + " : " + newState);
				if (status != 0)
					return;
				if (newState == BluetoothProfile.STATE_CONNECTED) {
					name = device.getName();
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
					BLETagDevice.this.gatt = gatt;
					connectState = CONNECT_STATE_CONNECTED;
					statusUpdated(0);
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (connectState == CONNECT_STATE_CONNECTED) { // auto reconnect
						gatt.close();
						if (BLETagDevice.this.gatt == gatt) {
							BLETagDevice.this.gatt = null;
						}
						// reconnect
						handler.postDelayed(new Runnable() {
							public void run() {
								if (BLETagDevice.this.gatt == gatt) {
									Log.d(TAG, "Reconnecting...");
									connect(device, handler, context);
									// connect(handler);
								}
							}
						}, RECONNECT_WAIT);
					} else {
						gatt.close();
						if (BLETagDevice.this.gatt == gatt) {
							BLETagDevice.this.gatt = null;
						}
					}
					connectState = CONNECT_STATE_DISCONNECTED;
					statusUpdated(0);
				}
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				super.onServicesDiscovered(gatt, status);
				state = STATE_OK;
				// TODO Auto-generated method stub
				for (BluetoothGattService s : gatt.getServices()) {
					Log.d(TAG, "GATT s:" + s.getUuid().toString());
					for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
						Log.d(TAG, "GATT   c:" + c.getUuid());
						if (BATTERY_UUID.equals(c.getUuid())) {
							gatt.readCharacteristic(c);
						}
						if (BATTERY_POWER_STATE_UUID.equals(c.getUuid())) {
							Log.d(TAG, "BATTERY_POWER_STATE_UUID   c:" + c.getUuid());
							//BluetoothGattDescriptor descriptor = c.getDescriptor(
							//      UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
							//gatt.readDescriptor(descriptor);
							//descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
							//gatt.writeDescriptor(descriptor);
							gatt.setCharacteristicNotification(c, true);
						}
					}
				}
				handler.postDelayed(new Runnable() {
					public void run() {
						readRemoteRssi();
					}
				}, 300);
			}
		});
		if (gatt != null) {
			connect(handler);
		} else {
			connectState = CONNECT_STATE_DISCONNECTED;
		}
	}

	public void disconnect() {
		final BluetoothGatt g = gatt;
		gatt = null;
		//characteristic = null;
		if (g != null) {
			connectState = CONNECT_STATE_DISCONNECTING;
			g.disconnect();
		} else {
			connectState = CONNECT_STATE_DISCONNECTED;
		}
	}

	public void readRemoteRssi() {
		if (!isConnected())
			return;
		gatt.readRemoteRssi();
	}

	private void statusUpdated(int st) {
		Log.d(TAG, "connectState: " + connectState);
		if (listener != null) {
			listener.onStatusUpdated(this, st);
		}
	}

	private void connect(final Handler handler) {
		Log.d(TAG, "Connecting...");
		connectState = CONNECT_STATE_CONNECTING;
		gatt.connect();

		handler.postDelayed(new Runnable() {
			public void run() {
				if (connectState == CONNECT_STATE_CONNECTING) {
					Log.d(TAG, "Coonnect timeout");
					if (gatt != null) {
						gatt.close();
						gatt = null;
						connectState = CONNECT_STATE_DISCONNECTED;
					}

					if (lastRssi != 0) {
						lastRssi = 0;
						Log.d(TAG, "status update: LOST");
						if (listener != null) {
							listener.onLost(BLETagDevice.this);
						}
						statusUpdated(2);
					}
				}
			}
		}, CONNECT_TIMEOUT);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof BLETagDevice) {
			return ((BLETagDevice) o).addr.equals(addr);
		}
		return false;
	}
}
