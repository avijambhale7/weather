package com.example.weather;

import org.json.JSONObject;

/** Free air quality data (US AQI) from Open-Meteo. No API key required. */
public class OpenMeteoAirQualityProvider implements AirQualityProvider {

    // Sub-index fields; the highest one is the dominant pollutant
    private static final String[] SUB_INDEXES = {
            "us_aqi_pm2_5", "us_aqi_pm10", "us_aqi_ozone",
            "us_aqi_nitrogen_dioxide", "us_aqi_sulphur_dioxide", "us_aqi_carbon_monoxide"};

    @Override
    public AirQuality fetch(double lat, double lon) throws Exception {
        String url = "https://air-quality-api.open-meteo.com/v1/air-quality"
                + "?latitude=" + lat + "&longitude=" + lon
                + "&current=us_aqi,us_aqi_pm2_5,us_aqi_pm10,us_aqi_ozone,"
                + "us_aqi_nitrogen_dioxide,us_aqi_sulphur_dioxide,us_aqi_carbon_monoxide";
        JSONObject current = new JSONObject(WeatherUtils.httpGet(url)).getJSONObject("current");

        AirQuality aq = new AirQuality();
        aq.aqi = current.optInt("us_aqi");
        aq.scaleName = "US AQI";
        aq.source = "Open-Meteo";

        String best = SUB_INDEXES[0];
        for (String key : SUB_INDEXES) {
            if (current.optDouble(key, -1) > current.optDouble(best, -1)) best = key;
        }
        aq.dominant = AirQuality.pollutantName(best.replace("us_aqi_", ""));
        aq.applyUsBand();
        return aq;
    }
}
