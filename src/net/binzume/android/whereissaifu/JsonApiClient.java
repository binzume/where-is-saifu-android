package net.binzume.android.whereissaifu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class JsonApiClient {

	protected static final String TAG = "JsonApiClient";

	private final String apiUrl;
		
	public JsonApiClient(String url) {
		apiUrl = url;
	}
	

	@SuppressWarnings("serial")
	public static class Params extends ArrayList<NameValuePair> {

		public void put(String key, String value) {
			add(new BasicNameValuePair(key, value));
		}

		public UrlEncodedFormEntity encode() {
			try {
				return new UrlEncodedFormEntity(this, HTTP.UTF_8);
			} catch (UnsupportedEncodingException e) {
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	public static class ApiException extends Exception {
		public final int statusCode;

		public ApiException(int statusCode) {
			this.statusCode = statusCode;
		}

	}

	@SuppressWarnings("serial")
	public static class AuthRequiredException extends ApiException {

		public AuthRequiredException(int statusCode) {
			super(statusCode);
		}

	}

	HttpClient httpClient = new DefaultHttpClient();

	protected JSONObject post(String path, Params params) throws IOException, JSONException, ApiException {
		Log.d(TAG, "post " + path);
		HttpPost request = new HttpPost(apiUrl + path);
		request.setEntity(params.encode());
		HttpResponse response = httpClient.execute(request);
		int status = response.getStatusLine().getStatusCode();
		if (status != 200) {
			Log.d(TAG, "status: " + response.getStatusLine().getStatusCode());
			if (status == 401) {
				throw new AuthRequiredException(status);
			}
			throw new ApiException(status); // FIXME
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		String jsonString = reader.readLine();
		reader.close();

		int p = jsonString.indexOf("//");
		if (p >= 0) {
			jsonString = jsonString.substring(p + 2);
		}

		return new JSONObject(jsonString);
	}

	public JSONObject get(String path, Params params) throws IOException, JSONException, ApiException {
		String q = "";
		if (params != null) {
			for (NameValuePair param : params) {
				q += param.getName() + "=" + URLEncoder.encode(param.getValue(), "UTF-8") + "&";
			}
			if (q.length() > 0) {
				q = "?" + q.substring(1);
			}
		}
		HttpGet request = new HttpGet(apiUrl + path + q);
		HttpResponse response = httpClient.execute(request);
		if (response.getStatusLine().getStatusCode() != 200) {
			Log.d(TAG, "status: " + response.getStatusLine().getStatusCode());
			throw new IOException(); // FIXME
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
		String jsonString = reader.readLine();
		reader.close();
		return new JSONObject(jsonString);
	}
}
