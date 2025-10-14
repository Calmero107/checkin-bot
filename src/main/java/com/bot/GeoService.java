package com.bot;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeoService {

    public static String getAddressFromCoordinates(double latitude, double longitude) {
        try {
            String url = String.format(
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f",
                    latitude, longitude
            );
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0"); // Nominatim yêu cầu User-Agent

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JSONObject json = new JSONObject(response.toString());
            return json.optString("display_name", "Không xác định");
        } catch (Exception e) {
            e.printStackTrace();
            return "Không xác định";
        }
    }
}
