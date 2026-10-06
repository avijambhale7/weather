package com.example.weather;

import org.json.JSONException;
import org.json.JSONObject;

/** A named location (city or GPS point). */
public class Place {
    public final String name;
    public final String region;
    public final double lat;
    public final double lon;

    public Place(String name, String region, double lat, double lon) {
        this.name = name;
        this.region = region == null ? "" : region;
        this.lat = lat;
        this.lon = lon;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("name", name).put("region", region).put("lat", lat).put("lon", lon);
    }

    public static Place fromJson(JSONObject o) {
        return new Place(o.optString("name"), o.optString("region"), o.optDouble("lat"), o.optDouble("lon"));
    }
}
