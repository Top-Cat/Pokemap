package uk.co.thomasc.pgo.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.JSONObject;

public class Util {
	
	public static JSONObject JSONfromStream(InputStream is) throws IOException {
		BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, "UTF-8")); 
		StringBuilder responseStrBuilder = new StringBuilder();

		String inputStr;
		while ((inputStr = streamReader.readLine()) != null)
			responseStrBuilder.append(inputStr);
		return new JSONObject(responseStrBuilder.toString());
	}
}