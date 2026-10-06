# Weather & Forecast 2.0 – Mobile Application Development Microproject

An Android weather app (Java + XML) with a futuristic glassmorphism design. It shows live weather, forecasts, air quality, and sun and moon times for your GPS location or any city.

## Features
| Feature | How it works |
|---|---|
| 🌌 Futuristic animated background | `WeatherBackgroundView` is a custom Canvas view with drifting aurora glows, a neon perspective grid, and particles that change with the weather (stars, rain, snow, clouds, lightning) |
| ✨ Glassmorphism UI | Translucent gradient cards with a light border (`bg_glass.xml`) on a dark theme |
| 📍 GPS / location permission flow | Explains first, asks for permission, and handles "denied", "blocked" (opens app settings) and "location off" (opens location settings) |
| 🌡️ Current weather dashboard | Big temperature, condition, feels-like, high/low |
| ⏱ Hourly forecast | Horizontal `RecyclerView` (`HourlyAdapter`) covering the next 24 hours, with rain chance |
| 📅 10-day forecast | Rows with min/max and a colour range bar (`RangeBarView`) |
| 📈 Temperature trend | `TrendChartView` is a smooth 24-hour line chart. Drag a finger to see each hour |
| 💧 Humidity, 💨 Wind, ☀️ UV, 👁️ Visibility | Glass detail tiles |
| 🌅 Sunrise / sunset | From the Open-Meteo daily data, plus total daylight hours |
| 🌙 Moonrise / moonset | Calculated on the device (`MoonCalc`, SunCalc algorithm) together with the moon phase |
| 🫁 Air quality architecture | `AirQualityProvider` interface with two implementations: `OpenMeteoAirQualityProvider` (free, default) and `GoogleAirQualityProvider` (Google Air Quality API, used when a key is entered in Settings) |
| AQI, dominant pollutant, health advice | AQI card with a colour scale bar and a text category |
| 🔎 Location search | `SearchActivity` shows live city results while you type, recent searches, and a "use current location" option |
| 🗺️ Map | Satellite map with road and place labels. Tap anywhere to see its temperature |
| ⚙️ Settings | °C/°F, wind unit, 24-hour time, animation on/off, GPS on launch, air-quality provider |

## Android concepts used
Activities and Intents, `ActivityResultLauncher`, runtime permissions, `LocationManager`, `Geocoder`, `RecyclerView`, `ListView`, `SwipeRefreshLayout`, custom Views (Canvas, Paint, Shader, touch events), `SharedPreferences`, background threads (`ExecutorService` + `Handler`), `HttpURLConnection` GET/POST, JSON parsing, styles/themes, drawables, edge-to-edge window insets.

## Project structure
```
app/src/main/java/com/example/weather/
├── MainActivity.java              # Dashboard + GPS permission flow
├── SearchActivity.java            # City search + recents
├── SettingsActivity.java          # Settings screen
├── MapActivity.java               # Satellite map, tap for temperature
├── WeatherRepository.java         # Open-Meteo forecast + geocoding
├── WeatherData.java / Place.java  # Data models
├── AirQualityProvider.java        # Air quality interface
├── OpenMeteoAirQualityProvider.java
├── GoogleAirQualityProvider.java
├── AirQuality.java                # AQI model, categories, health advice
├── MoonCalc.java                  # Moonrise/moonset/phase
├── Prefs.java                     # Settings, last place, recents
├── WeatherUtils.java              # HTTP, icons, formatting
├── HourlyAdapter.java
└── WeatherBackgroundView / TrendChartView / RangeBarView / AqiScaleView  # custom views
```

## Data sources (free, no key needed)
- Weather: https://open-meteo.com (forecast, geocoding, air quality)
- Maps: Esri World Imagery (via osmdroid)
- Optional: Google Air Quality API. Turn on the "Air Quality API" in Google Cloud Console and paste the key in Settings. If the key fails, the app falls back to Open-Meteo.

## How to run
1. Connect an Android phone with **USB debugging** turned on.
2. Run `.\gradlew installDebug` in the `Weather` folder, or open the folder in Android Studio and press **Run ▶**.
# weather
