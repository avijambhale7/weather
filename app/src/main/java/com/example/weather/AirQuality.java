package com.example.weather;

import android.graphics.Color;

/** Air quality result shown on the AQI card (from any provider). */
public class AirQuality {
    public int aqi;
    public String scaleName;     // e.g. "US AQI" or "Universal AQI"
    public String category;      // e.g. "Moderate"
    public String dominant;      // e.g. "PM2.5"
    public String advice;        // health advice text
    public String source;        // "Open-Meteo" / "Google Air Quality API"
    public int color;            // category color
    public float badness;        // 0 = clean air, 1 = hazardous (for the scale bar)

    /** Fills category, color, advice and badness from a US EPA AQI value. */
    public void applyUsBand() {
        badness = Math.min(aqi, 300) / 300f;
        if (aqi <= 50) {
            set("Good", "#00E400", "Air quality is great. Enjoy outdoor activities.");
        } else if (aqi <= 100) {
            set("Moderate", "#FFDE33", "Acceptable for most people. Unusually sensitive people should limit long outdoor exertion.");
        } else if (aqi <= 150) {
            set("Unhealthy for Sensitive Groups", "#FF9933", "Children, older adults and people with asthma or heart disease should reduce outdoor activity.");
        } else if (aqi <= 200) {
            set("Unhealthy", "#FF4D4D", "Everyone may feel effects. Limit time outdoors and consider wearing a mask.");
        } else if (aqi <= 300) {
            set("Very Unhealthy", "#B266FF", "Health alert. Avoid outdoor activity and keep windows closed.");
        } else {
            set("Hazardous", "#C2185B", "Emergency conditions. Stay indoors and use an air purifier if possible.");
        }
    }

    private void set(String cat, String hex, String adv) {
        category = cat;
        color = Color.parseColor(hex);
        if (advice == null || advice.isEmpty()) advice = adv;
    }

    /** Turns API pollutant codes like "pm25" or "no2" into readable names. */
    public static String pollutantName(String code) {
        switch (code.toLowerCase()) {
            case "pm25": case "pm2_5": return "PM2.5 (fine particles)";
            case "pm10": return "PM10 (coarse particles)";
            case "o3": case "ozone": return "O₃ (ozone)";
            case "no2": case "nitrogen_dioxide": return "NO₂ (nitrogen dioxide)";
            case "so2": case "sulphur_dioxide": return "SO₂ (sulphur dioxide)";
            case "co": case "carbon_monoxide": return "CO (carbon monoxide)";
            default: return code.toUpperCase();
        }
    }
}
