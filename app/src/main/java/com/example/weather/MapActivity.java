package com.example.weather;

import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Map screen (osmdroid + free Esri satellite tiles, no API key needed).
 * Tap anywhere on the map to see the current temperature at that point.
 */
public class MapActivity extends AppCompatActivity {

    // Free map tiles from Esri (no API key needed)
    private static final OnlineTileSourceBase SATELLITE = esri("World_Imagery");
    private static final OnlineTileSourceBase STREET = esri("World_Street_Map");
    // Transparent layers drawn on top of satellite (like Google Maps "hybrid")
    private static final OnlineTileSourceBase ROADS = esri("Reference/World_Transportation");
    private static final OnlineTileSourceBase PLACES = esri("Reference/World_Boundaries_and_Places");

    /** Builds an Esri tile source. Esri tile URLs use the order z/y/x. */
    private static OnlineTileSourceBase esri(String service) {
        return new OnlineTileSourceBase(service.replace('/', '_'), 0, 19, 256, ".png",
                new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/" + service + "/MapServer/tile/"},
                "© Esri") {
            @Override
            public String getTileURLString(long index) {
                return getBaseUrl() + MapTileIndex.getZoom(index) + "/"
                        + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index);
            }
        };
    }

    private boolean satellite = true;
    private MapView map;
    private Marker marker;
    private TextView tvIcon, tvPlace, tvTemp, tvDetails;
    private ProgressBar progress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OpenStreetMap requires an identifying user agent
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        tvIcon = findViewById(R.id.tvMapIcon);
        tvPlace = findViewById(R.id.tvMapPlace);
        tvTemp = findViewById(R.id.tvMapTemp);
        tvDetails = findViewById(R.id.tvMapDetails);
        progress = findViewById(R.id.mapProgress);

        map = findViewById(R.id.map);
        map.setTileSource(SATELLITE);
        TilesOverlay roads = labelLayer(ROADS);
        TilesOverlay places = labelLayer(PLACES);
        map.getOverlays().add(new CopyrightOverlay(this));

        // Toggle satellite (with road + place labels) <-> street map
        Button btnLayer = findViewById(R.id.btnLayer);
        btnLayer.setOnClickListener(v -> {
            satellite = !satellite;
            map.setTileSource(satellite ? SATELLITE : STREET);
            roads.setEnabled(satellite);
            places.setEnabled(satellite);
            map.invalidate();
            btnLayer.setText(satellite ? R.string.street_map : R.string.satellite);
        });
        map.setMultiTouchControls(true);

        // Start at the city searched on the main screen (default: Pune)
        double lat = getIntent().getDoubleExtra("lat", 18.52);
        double lon = getIntent().getDoubleExtra("lon", 73.86);
        map.getController().setZoom(12.0);
        map.getController().setCenter(new GeoPoint(lat, lon));

        marker = new Marker(map);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Listen for taps on the map
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                onMapTapped(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));
    }

    /** Adds a transparent tile layer (roads or place names) over the base map. */
    private TilesOverlay labelLayer(OnlineTileSourceBase source) {
        TilesOverlay layer = new TilesOverlay(new MapTileProviderBasic(this, source), this);
        layer.setLoadingBackgroundColor(Color.TRANSPARENT);
        layer.setLoadingLineColor(Color.TRANSPARENT);
        map.getOverlays().add(layer);
        return layer;
    }

    private void onMapTapped(GeoPoint p) {
        // Move the pin to the tapped point
        marker.setPosition(p);
        if (!map.getOverlays().contains(marker)) map.getOverlays().add(marker);
        map.invalidate();

        progress.setVisibility(View.VISIBLE);
        tvPlace.setText(String.format(Locale.US, "%.3f, %.3f", p.getLatitude(), p.getLongitude()));
        tvTemp.setText("");
        tvDetails.setText("Loading...");

        executor.execute(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + p.getLatitude() + "&longitude=" + p.getLongitude()
                        + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code";
                JSONObject current = new JSONObject(WeatherUtils.httpGet(url)).getJSONObject("current");
                String place = placeName(p);

                mainHandler.post(() -> {
                    int code = current.optInt("weather_code");
                    double temp = current.optDouble("temperature_2m");
                    progress.setVisibility(View.GONE);
                    if (place != null) tvPlace.setText(place);
                    tvIcon.setText(WeatherUtils.icon(code));
                    tvTemp.setText(String.format(Locale.US, "%.1f°C  %s", temp, WeatherUtils.description(code)));
                    tvDetails.setText("Humidity: " + current.optInt("relative_humidity_2m")
                            + "%   |   Wind: " + current.optDouble("wind_speed_10m") + " km/h");

                    marker.setTitle(String.format(Locale.US, "%.1f°C", temp));
                    marker.showInfoWindow();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    tvDetails.setText("Error: check your internet connection");
                });
            }
        });
    }

    /** Turns coordinates into a readable place name, or null if not available. */
    private String placeName(GeoPoint p) {
        try {
            List<Address> list = new Geocoder(this, Locale.getDefault())
                    .getFromLocation(p.getLatitude(), p.getLongitude(), 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                String city = a.getLocality() != null ? a.getLocality() : a.getAdminArea();
                if (city != null) return city + ", " + a.getCountryName();
                return a.getCountryName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
