package net.binzume.android.whereissaifu;

import java.util.Set;

public class LocationHistory {
	
	public final double lat;
	public final double lon;
	public final long timestamp;
	public final Set<String> activeDevices;
	
	public LocationHistory(double lat, double lon, long time, Set<String> devices) {
		this.lat = lat;
		this.lon = lon;
		this.timestamp = time;
		this.activeDevices = devices;
	}

}
