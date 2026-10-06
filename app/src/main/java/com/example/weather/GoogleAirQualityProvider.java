package com.example.weather;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Google Air Quality API (currentConditions:lookup).
 * Docs: https://developers.google.com/maps/documentation/air-quality
 * Uses the Universal AQI (0-100, where 100 = best air).
 */
public class GoogleAirQualityProvider implements AirQualityProvider {

    private final String apiKey;

    public GoogleAirQualityProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public AirQuality fetch(double lat, double lon) throws Exception {
        String url = "https://airquality.googleapis.com/v1/currentConditions:lookup?key=" + apiKey;
        JSONObject body = new JSONObject()
                .put("location", new JSONObject().put("latitude", lat).put("longitude", lon))
                .put("extraComputations", new JSONArray()
                        .put("HEALTH_RECOMMENDATIONS")
                        .put("DOMINANT_POLLUTANT_CONCENTRATION"))
                .put("languageCode", "en");
        JSONObject json = new JSONObject(WeatherUtils.httpPost(url, body.toString()));

        JSONObject index = json.getJSONArray("indexes").getJSONObject(0);
        AirQuality aq = new AirQuality();
        aq.aqi = index.optInt("aqi");
        aq.scaleName = index.optString("displayName", "Universal AQI");
        aq.category = index.optString("category", "");
        aq.dominant = AirQuality.pollutantName(index.optString("dominantPollutant", "-"));
        aq.source = "Google Air Quality API";
        aq.badness = 1f - Math.min(aq.aqi, 100) / 100f; // UAQI: higher is better

        JSONObject c = index.optJSONObject("color");
        aq.color = c == null ? Color.WHITE : Color.rgb(
                (int) (c.optDouble("red", 0) * 255),
                (int) (c.optDouble("green", 0) * 255),
                (int) (c.optDouble("blue", 0) * 255));

        JSONObject health = json.optJSONObject("healthRecommendations");
        aq.advice = health == null ? "" : health.optString("generalPopulation", "");
        return aq;
    }
}
