package com.example.weather;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Networking, weather-code text/icons and formatting helpers. */
public class WeatherUtils {

    // ---------------- Networking ----------------

    /** Simple HTTP GET that returns the response body as a String. */
    public static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return readResponse(conn);
    }

    /** HTTP POST with a JSON body (used for the Google Air Quality API). */
    public static String httpPost(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        try {
            int code = conn.getResponseCode();
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }
            }
            if (code >= 400) throw new Exception("HTTP " + code + ": " + sb);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // ---------------- Weather codes ----------------

    public static String description(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Rain showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    public static String icon(int code) {
        return icon(code, true);
    }

    public static String icon(int code, boolean isDay) {
        if (code <= 1) return isDay ? "☀️" : "🌙";
        if (code == 2) return isDay ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code <= 48) return "🌫️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "❄️";
        if (code <= 82) return "🌦️";
        if (code <= 86) return "🌨️";
        return "⛈️";
    }

    // ---------------- Formatting ----------------

    /** Rounded temperature with a degree sign, e.g. "25°". */
    public static String deg(double t) {
        return Math.round(t) + "°";
    }

    /** "06:21" (24h) or "6:21 AM" from an Open-Meteo local time "yyyy-MM-ddTHH:mm". */
    public static String time(String iso, boolean h24) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso);
            return new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.US).format(d);
        } catch (Exception e) {
            return "--";
        }
    }

    /** Hour label for the hourly strip: "14" / "2 PM". */
    public static String hour(String iso, boolean h24) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso);
            return new SimpleDateFormat(h24 ? "HH:00" : "h a", Locale.US).format(d);
        } catch (Exception e) {
            return "--";
        }
    }

    /** Formats an epoch time in the location's own time zone. */
    public static String epochTime(long ms, int utcOffsetSeconds, boolean h24) {
        if (ms < 0) return "--";
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms + utcOffsetSeconds * 1000L));
    }

    /** Epoch millis of local midnight for a date "yyyy-MM-dd" at a UTC offset. */
    public static long localMidnight(String date, int utcOffsetSeconds) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(date).getTime() - utcOffsetSeconds * 1000L;
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /** "Today" for the first day, otherwise "Mon", "Tue"… */
    public static String dayName(String date, int index) {
        if (index == 0) return "Today";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            return new SimpleDateFormat("EEE", Locale.US).format(d);
        } catch (Exception e) {
            return date;
        }
    }

    public static String windUnitLabel(String unit) {
        switch (unit) {
            case "ms": return "m/s";
            case "mph": return "mph";
            default: return "km/h";
        }
    }

    public static String compass(double degrees) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(((degrees % 360) + 360) % 360 / 45) % 8];
    }

    public static String uvLevel(double uv) {
        if (uv < 3) return "Low";
        if (uv < 6) return "Moderate";
        if (uv < 8) return "High";
        if (uv < 11) return "Very high";
        return "Extreme";
    }

    public static String uvAdvice(double uv) {
        if (uv < 3) return "No protection needed";
        if (uv < 6) return "Wear sunglasses & sunscreen";
        if (uv < 8) return "Use sun protection 11am–4pm";
        return "Avoid the midday sun";
    }

    public static String visibilityText(double metres) {
        if (Double.isNaN(metres)) return "Unknown";
        if (metres >= 10000) return "Perfectly clear view";
        if (metres >= 5000) return "Good visibility";
        if (metres >= 2000) return "Light haze";
        if (metres >= 1000) return "Haze";
        return "Fog — drive carefully";
    }
}
