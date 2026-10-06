package com.example.weather;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** App settings, last location and recent searches (stored in SharedPreferences). */
public class Prefs {

    public static final String AQ_OPEN_METEO = "open-meteo";
    public static final String AQ_GOOGLE = "google";

    private static final int MAX_RECENTS = 6;
    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);
    }

    // ---- Settings ----
    public boolean fahrenheit() { return sp.getBoolean("fahrenheit", false); }
    public void setFahrenheit(boolean v) { put("fahrenheit", v); }

    /** "kmh", "ms" or "mph" (Open-Meteo wind_speed_unit values). */
    public String windUnit() { return sp.getString("wind_unit", "kmh"); }
    public void setWindUnit(String v) { sp.edit().putString("wind_unit", v).apply(); bump(); }

    public boolean use24h() { return sp.getBoolean("use_24h", true); }
    public void setUse24h(boolean v) { put("use_24h", v); }

    public boolean animatedBg() { return sp.getBoolean("animated_bg", true); }
    public void setAnimatedBg(boolean v) { put("animated_bg", v); }

    public boolean useGps() { return sp.getBoolean("use_gps", true); }
    public void setUseGps(boolean v) { put("use_gps", v); }

    public String aqProvider() { return sp.getString("aq_provider", AQ_OPEN_METEO); }
    public void setAqProvider(String v) { sp.edit().putString("aq_provider", v).apply(); bump(); }

    public String googleKey() { return sp.getString("google_key", ""); }
    public void setGoogleKey(String v) {
        if (!v.equals(googleKey())) { sp.edit().putString("google_key", v).apply(); bump(); }
    }

    /** Asked for location permission at least once (so we don't nag every launch). */
    public boolean askedLocation() { return sp.getBoolean("asked_location", false); }
    public void setAskedLocation() { sp.edit().putBoolean("asked_location", true).apply(); }

    /** Changes whenever a setting changes, so screens know to reload. */
    public long version() { return sp.getLong("version", 0); }

    private void put(String key, boolean v) { sp.edit().putBoolean(key, v).apply(); bump(); }
    private void bump() { sp.edit().putLong("version", version() + 1).apply(); }

    // ---- Last shown place ----
    public Place lastPlace() {
        if (!sp.contains("place_lat")) return new Place("Pune", "Maharashtra, India", 18.52, 73.86);
        return new Place(sp.getString("place_name", ""), sp.getString("place_region", ""),
                Double.longBitsToDouble(sp.getLong("place_lat", 0)),
                Double.longBitsToDouble(sp.getLong("place_lon", 0)));
    }

    public void saveLastPlace(Place p) {
        sp.edit().putString("place_name", p.name).putString("place_region", p.region)
                .putLong("place_lat", Double.doubleToLongBits(p.lat))
                .putLong("place_lon", Double.doubleToLongBits(p.lon)).apply();
    }

    // ---- Recent searches ----
    public List<Place> recents() {
        List<Place> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("recents", "[]"));
            for (int i = 0; i < arr.length(); i++) list.add(Place.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return list;
    }

    public void addRecent(Place p) {
        List<Place> list = recents();
        // Remove duplicates of the same city, then put it first
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equals(p.name) && list.get(i).region.equals(p.region)) list.remove(i);
        }
        list.add(0, p);
        JSONArray arr = new JSONArray();
        try {
            for (int i = 0; i < list.size() && i < MAX_RECENTS; i++) arr.put(list.get(i).toJson());
        } catch (Exception ignored) {
        }
        sp.edit().putString("recents", arr.toString()).apply();
    }
}
