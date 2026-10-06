package com.example.weather;

/**
 * Air quality "architecture": the dashboard only talks to this interface.
 * Two implementations exist — Open-Meteo (free, default) and the Google Air Quality API
 * (used when an API key is set in Settings). New providers can be added the same way.
 */
public interface AirQualityProvider {

    AirQuality fetch(double lat, double lon) throws Exception;

    /** Picks the provider from the user's settings. */
    static AirQualityProvider create(Prefs prefs) {
        if (Prefs.AQ_GOOGLE.equals(prefs.aqProvider()) && !prefs.googleKey().isEmpty()) {
            return new GoogleAirQualityProvider(prefs.googleKey());
        }
        return new OpenMeteoAirQualityProvider();
    }
}
