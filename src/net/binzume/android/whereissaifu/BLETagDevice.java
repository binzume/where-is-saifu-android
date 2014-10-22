package net.binzume.android.whereissaifu;

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

public class BLETagDevice {
	private static final String tag = "BLETagDevice";

	private final static int RECONNECT_WAIT = 5000;
	private final static int CONNECT_TIMEOUT = 10000;

	public static final int CONNECT_STATE_CONNECTING = 1;
	public static final int CONNECT_STATE_CONNECTED = 2;
	public static final int CONNECT_STATE_DISCONNECTING = 3;
	public static final int CONNECT_STATE_DISCONNECTED = 0;

	public interface TagDeviceEventListener {
		void onStatusUpdated(BLETagDevice d, int st);

		void onPressButton(BLETagDevice d, int st);

		void onLost(BLETagDevice d);
	}

	private TagDeviceEventListener listener;
	private BluetoothGattCharacteristic characteristic;
	private BluetoothGatt gatt = null;
	private volatile int connectState = 0;

	public final String addr;
	public String name;
	public int lastRssi;
	public long lastRespondTime;

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

	public void connect(BluetoothDevice device, final Handler handler, Context context) {
		name = device.getName();
		//addr = device.getAddress();
		if (gatt != null) {
			return;
		}
		connectState = CONNECT_STATE_CONNECTING;

		gatt = device.connectGatt(context, false, new BluetoothGattCallback() {

			@Override
			public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
				// Log.d(tag, "onDescriptorRead " + descriptor.getValue().length  + " : "+ descriptor.getValue()[0] + ","+ descriptor.getValue()[1]);
				super.onDescriptorRead(gatt, descriptor, status);
			}

			private int state = 0;
			private static final int STATE_DISCOVER = 1;
			private static final int STATE_OK = 0;

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				if (characteristic.getValue() != null && characteristic.getValue().length > 0) {
					Log.d(tag, "onCharacteristicRead " + characteristic.getValue()[0]);
				}
				super.onCharacteristicRead(gatt, characteristic, status);
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
				if (characteristic.getValue() != null && characteristic.getValue().length > 0) {
					Log.d(tag, "onCharacteristicChanged : " + characteristic.getValue()[0] + "," + characteristic.getValue()[1]);
					if (listener != null) {
						listener.onPressButton(BLETagDevice.this, characteristic.getValue()[0]);
					}
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
					BLETagDevice.this.gatt = gatt;
					connectState = CONNECT_STATE_CONNECTED;
					statusUpdated(0);
				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (BLETagDevice.this.gatt == gatt) {
						BLETagDevice.this.gatt = null;
					}
					if (connectState == CONNECT_STATE_CONNECTED) { // auto reconnect
						// reconnect
						handler.postDelayed(new Runnable() {
							public void run() {
								Log.d(tag, "Reconnecting...");
								connect(handler);
							}
						}, RECONNECT_WAIT);
					} else {
						gatt.close();
					}
					connectState = CONNECT_STATE_DISCONNECTED;
					statusUpdated(0);
				}
			}

			@Override
			public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
				super.onReadRemoteRssi(gatt, rssi, status);
				state = STATE_OK;
				Log.d(tag, "GATT " + name + " rssi: " + rssi + " t" + +System.currentTimeMillis());
				lastRssi = rssi;
				statusUpdated(1);
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				super.onServicesDiscovered(gatt, status);
				state = STATE_OK;
				// TODO Auto-generated method stub
				for (BluetoothGattService s : gatt.getServices()) {
					Log.d(tag, "GATT s:" + s.getUuid().toString());
					for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
						Log.d(tag, "GATT   c:" + c.getUuid());
						if ("00001802-0000-1000-8000-00805f9b34fb".equals(s.getUuid().toString())) {
							if ("00002a06-0000-1000-8000-00805f9b34fb".equals(c.getUuid().toString())) {
								characteristic = c;
							}
						}
						if ("00002a19-0000-1000-8000-00805f9b34fb".equals(c.getUuid().toString())) {
							gatt.readCharacteristic(c);
						}
						if ("00002a1b-0000-1000-8000-00805f9b34fb".equals(c.getUuid().toString())) {
							//BluetoothGattDescriptor descriptor = c.getDescriptor(
							//       UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
							//gatt.readDescriptor(descriptor);
							gatt.setCharacteristicNotification(c, true);
							//descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
							//gatt.writeDescriptor(descriptor);
							// gatt.readCharacteristic(c);
						}
					}
				}
				gatt.readRemoteRssi();
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
		characteristic = null;
		if (g != null) {
			connectState = CONNECT_STATE_DISCONNECTING;
			g.disconnect();
		}
	}

	public void readRemoteRssi() {
		if (!isConnected())
			return;
		gatt.readRemoteRssi();
	}

	public void setAlarm(int level) {
		if (isConnected() && characteristic != null) {
			Log.d("saifu", "GATT write Characteristic!");
			characteristic.setValue(new byte[] { (byte) level });
			gatt.writeCharacteristic(characteristic);
		}
	}

	private void statusUpdated(int st) {
		Log.d(tag, "connectState: " + connectState);
		if (listener != null) {
			listener.onStatusUpdated(this, st);
		}
	}

	private void connect(final Handler handler) {
		Log.d(tag, "Connecting...");
		connectState = CONNECT_STATE_CONNECTING;
		gatt.connect();

		handler.postDelayed(new Runnable() {
			public void run() {
				if (connectState == CONNECT_STATE_CONNECTING) {
					Log.d(tag, "Coonnect timeout");
					disconnect();

					if (lastRssi != 0) {
						lastRssi = 0;
						Log.d("BLETagDevice", "status update: LOST");
						if (listener != null) {
							listener.onLost(BLETagDevice.this);
						}
						statusUpdated(2);
					}
				}
			}
		}, CONNECT_TIMEOUT);
	}
}
