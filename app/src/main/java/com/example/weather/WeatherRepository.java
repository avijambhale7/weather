package com.example.weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Downloads weather and place data from the free Open-Meteo APIs (no API key). */
public class WeatherRepository {

    /** Current conditions + 24-hour + 10-day forecast. Units follow the user's settings. */
    public static WeatherData fetch(double lat, double lon, Prefs prefs) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,"
                + "wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,uv_index,visibility,dew_point_2m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,"
                + "precipitation_probability_max"
                + "&forecast_days=10&timezone=auto"
                + "&wind_speed_unit=" + prefs.windUnit()
                + (prefs.fahrenheit() ? "&temperature_unit=fahrenheit" : "");
        JSONObject json = new JSONObject(WeatherUtils.httpGet(url));

        WeatherData w = new WeatherData();
        w.utcOffsetSeconds = json.optInt("utc_offset_seconds");

        // Current conditions
        JSONObject c = json.getJSONObject("current");
        WeatherData.Current cur = w.current;
        cur.time = c.getString("time");
        cur.temp = c.optDouble("temperature_2m");
        cur.feelsLike = c.optDouble("apparent_temperature");
        cur.humidity = c.optDouble("relative_humidity_2m");
        cur.isDay = c.optInt("is_day", 1) == 1;
        cur.code = c.optInt("weather_code");
        cur.windSpeed = c.optDouble("wind_speed_10m");
        cur.windDir = c.optDouble("wind_direction_10m");
        cur.windGusts = c.optDouble("wind_gusts_10m");
        cur.pressure = c.optDouble("pressure_msl");

        // Hourly: find the current hour, then take the next 24
        JSONObject h = json.getJSONObject("hourly");
        JSONArray times = h.getJSONArray("time");
        int start = 0;
        String nowHour = cur.time.substring(0, 13); // "yyyy-MM-ddTHH"
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).startsWith(nowHour)) { start = i; break; }
        }
        cur.uv = h.getJSONArray("uv_index").optDouble(start, 0);
        cur.visibility = h.getJSONArray("visibility").optDouble(start, Double.NaN);
        cur.dewPoint = h.getJSONArray("dew_point_2m").optDouble(start, Double.NaN);

        for (int i = start; i < start + 24 && i < times.length(); i++) {
            WeatherData.Hour hr = new WeatherData.Hour();
            hr.time = times.getString(i);
            hr.temp = h.getJSONArray("temperature_2m").optDouble(i);
            hr.code = h.getJSONArray("weather_code").optInt(i);
            hr.precipProb = h.getJSONArray("precipitation_probability").optInt(i, 0);
            hr.isDay = h.getJSONArray("is_day").optInt(i, 1) == 1;
            w.hours.add(hr);
        }

        // Daily
        JSONObject d = json.getJSONObject("daily");
        JSONArray dates = d.getJSONArray("time");
        for (int i = 0; i < dates.length(); i++) {
            WeatherData.Day day = new WeatherData.Day();
            day.date = dates.getString(i);
            day.code = d.getJSONArray("weather_code").optInt(i);
            day.max = d.getJSONArray("temperature_2m_max").optDouble(i);
            day.min = d.getJSONArray("temperature_2m_min").optDouble(i);
            day.sunrise = d.getJSONArray("sunrise").optString(i);
            day.sunset = d.getJSONArray("sunset").optString(i);
            day.uvMax = d.getJSONArray("uv_index_max").optDouble(i, 0);
            day.precipProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            w.days.add(day);
        }
        return w;
    }

    /** City search for the search screen. */
    public static List<Place> searchPlaces(String query) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&name="
                + java.net.URLEncoder.encode(query, "UTF-8");
        JSONObject json = new JSONObject(WeatherUtils.httpGet(url));
        List<Place> list = new ArrayList<>();
        JSONArray results = json.optJSONArray("results");
        if (results == null) return list;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String admin = r.optString("admin1", "");
            String country = r.optString("country", "");
            String region = admin.isEmpty() ? country : admin + ", " + country;
            list.add(new Place(r.getString("name"), region, r.getDouble("latitude"), r.getDouble("longitude")));
        }
        return list;
    }

    /** Short label for a GPS point when no place name is available. */
    public static String coordsLabel(double lat, double lon) {
        return String.format(Locale.US, "%.2f, %.2f", lat, lon);
    }
}
