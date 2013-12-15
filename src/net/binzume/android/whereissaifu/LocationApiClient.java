package net.binzume.android.whereissaifu;

import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

public class LocationApiClient extends JsonApiClient {

	private static final String TAG = "LocationApiClient";

	public LocationApiClient() {
		super(Constants.API_URL);
	}
	
	public void updateLocaton(double lat, double lon, float acc, int rssi) {
		Params params = new Params();
		params.put("key", Constants.API_KEY);
		params.put("lat", String.valueOf(lat));
		params.put("lon", String.valueOf(lon));
		params.put("acc", String.valueOf(acc));
		params.put("rssi", String.valueOf(rssi));
		params.put("timestamp", String.valueOf(System.currentTimeMillis()));

		try {
			JSONObject json = post("location", params);
			Log.d(TAG, "res:" + json.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void updateStatus(String status) {
		Params params = new Params();
		params.put("key", Constants.API_KEY);
		params.put("status", status);
		params.put("timestamp", String.valueOf(System.currentTimeMillis()));

		try {
			JSONObject json = post("status", params);
			Log.d(TAG, "res:" + json.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
