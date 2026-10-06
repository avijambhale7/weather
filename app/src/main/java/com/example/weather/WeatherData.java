package com.example.weather;

import java.util.ArrayList;
import java.util.List;

/** Everything the dashboard shows, parsed from the Open-Meteo forecast API. */
public class WeatherData {

    public int utcOffsetSeconds;
    public Current current = new Current();
    public List<Hour> hours = new ArrayList<>();   // next 24 hours, starting now
    public List<Day> days = new ArrayList<>();     // 10 days, starting today

    public static class Current {
        public String time;
        public double temp, feelsLike, humidity, dewPoint;
        public double windSpeed, windGusts, windDir, pressure;
        public double uv, visibility;              // visibility in metres
        public int code;
        public boolean isDay;
    }

    public static class Hour {
        public String time;
        public double temp;
        public int code, precipProb;
        public boolean isDay;
    }

    public static class Day {
        public String date, sunrise, sunset;
        public double max, min, uvMax;
        public int code, precipProb;
    }
}
