package com.example.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main weather dashboard.
 * Flow: GPS (with permission flow) or saved city → Open-Meteo forecast + air quality → render cards.
 */
public class MainActivity extends AppCompatActivity {

    private Prefs prefs;
    private WeatherBackgroundView bgView;
    private SwipeRefreshLayout swipe;
    private TextView tvLocation, tvGreeting, tvDate, tvHeroIcon, tvTemp, tvCondition, tvHiLo, tvUpdated;
    private TempRingView ring;
    private LinearLayout chipsRow;
    private TextView tvAqi, tvAqiCategory, tvAqiScale, tvAqiPollutant, tvAqiAdvice, tvAqiSource;
    private AqiScaleView aqiBar;
    private TrendChartView chart;
    private LinearLayout dailyContainer, detailsGrid;
    private final HourlyAdapter hourlyAdapter = new HourlyAdapter();

    private Place place;            // place currently shown
    private boolean hasData;
    private long loadedVersion;     // settings version used for the current data
    private long loadToken;         // ignores results from older requests
    private CancellationSignal locationCancel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Search screen result: a city, or "use GPS"
    private final ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent d = result.getData();
                if (result.getResultCode() != RESULT_OK || d == null) return;
                if (d.getBooleanExtra("gps", false)) {
                    requestLocation();
                } else {
                    load(new Place(d.getStringExtra("name"), d.getStringExtra("region"),
                            d.getDoubleExtra("lat", 0), d.getDoubleExtra("lon", 0)));
                }
            });

    // Location permission result
    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    fetchGpsLocation();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                        && prefs.askedLocation()) {
                    showPermissionBlockedDialog();
                } else {
                    Toast.makeText(this, "Location denied. Tap 📍 to search for a city.", Toast.LENGTH_LONG).show();
                    if (!hasData) load(prefs.lastPlace());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        // Draw behind the status bar so the animated background fills the screen
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        bgView = findViewById(R.id.bgView);
        swipe = findViewById(R.id.swipe);
        tvLocation = findViewById(R.id.tvLocation);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDate = findViewById(R.id.tvDate);
        ring = findViewById(R.id.ring);
        chipsRow = findViewById(R.id.chipsRow);
        tvHeroIcon = findViewById(R.id.tvHeroIcon);
        tvTemp = findViewById(R.id.tvTemp);
        tvCondition = findViewById(R.id.tvCondition);
        tvHiLo = findViewById(R.id.tvHiLo);
        tvUpdated = findViewById(R.id.tvUpdated);
        tvAqi = findViewById(R.id.tvAqi);
        tvAqiCategory = findViewById(R.id.tvAqiCategory);
        tvAqiScale = findViewById(R.id.tvAqiScale);
        tvAqiPollutant = findViewById(R.id.tvAqiPollutant);
        tvAqiAdvice = findViewById(R.id.tvAqiAdvice);
        tvAqiSource = findViewById(R.id.tvAqiSource);
        aqiBar = findViewById(R.id.aqiBar);
        chart = findViewById(R.id.chart);
        dailyContainer = findViewById(R.id.dailyContainer);
        detailsGrid = findViewById(R.id.detailsGrid);

        // Keep content clear of the status and navigation bars
        View content = findViewById(R.id.content);
        View scrim = findViewById(R.id.statusScrim);
        int pad = dp(16);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pad, bars.top + pad / 2, pad, bars.bottom + pad);
            scrim.getLayoutParams().height = bars.top + dp(24);
            scrim.requestLayout();
            swipe.setProgressViewOffset(false, bars.top, bars.top + dp(72));
            return insets;
        });

        // Parallax: background drifts a little as the page scrolls
        NestedScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> bgView.setScrollOffset(y));

        RecyclerView rv = findViewById(R.id.rvHourly);
        rv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rv.setAdapter(hourlyAdapter);

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent));
        swipe.setProgressBackgroundColorSchemeColor(0xFF1A1446);
        swipe.setOnRefreshListener(() -> load(place != null ? place : prefs.lastPlace()));

        findViewById(R.id.btnLocation).setOnClickListener(v -> openSearch());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnMap).setOnClickListener(v -> {
            Place p = place != null ? place : prefs.lastPlace();
            startActivity(new Intent(this, MapActivity.class).putExtra("lat", p.lat).putExtra("lon", p.lon));
        });

        loadedVersion = prefs.version();
        tvLocation.setText(prefs.lastPlace().name);
        if (prefs.useGps()) startLocationFlow();
        else load(prefs.lastPlace());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgView.setAnimated(prefs.animatedBg());
        ring.setAnimated(prefs.animatedBg());
        // Settings changed (units etc.) → reload
        if (loadedVersion != prefs.version()) {
            loadedVersion = prefs.version();
            load(place != null ? place : prefs.lastPlace());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        bgView.setAnimated(false); // save battery while not visible
        ring.setAnimated(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationCancel != null) locationCancel.cancel();
        executor.shutdown();
    }

    private void openSearch() {
        searchLauncher.launch(new Intent(this, SearchActivity.class));
    }

    // =================== GPS / location permission flow ===================

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** On launch: use GPS if allowed, otherwise explain and ask once. */
    private void startLocationFlow() {
        if (hasLocationPermission()) {
            fetchGpsLocation();
            return;
        }
        load(prefs.lastPlace()); // show something right away
        if (prefs.askedLocation()) return; // don't nag on every launch
        prefs.setAskedLocation();
        new AlertDialog.Builder(this)
                .setTitle(R.string.loc_title)
                .setMessage(R.string.loc_message)
                .setPositiveButton(R.string.allow, (d, w) -> requestLocation())
                .setNegativeButton(R.string.search_city, (d, w) -> openSearch())
                .show();
    }

    /** "Use current location" pressed: ask for permission if needed. */
    private void requestLocation() {
        if (hasLocationPermission()) {
            fetchGpsLocation();
        } else {
            prefs.setAskedLocation();
            permLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void showPermissionBlockedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Location permission is blocked")
                .setMessage("To use your current location, allow Location for this app in Settings.")
                .setPositiveButton(R.string.open_settings, (d, w) -> startActivity(
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton(R.string.cancel, null)
                .show();
        if (!hasData) load(prefs.lastPlace());
    }

    @SuppressLint("MissingPermission") // checked by hasLocationPermission()
    private void fetchGpsLocation() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gps_off_title)
                    .setMessage(R.string.gps_off_message)
                    .setPositiveButton(R.string.open_settings, (d, w) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            if (!hasData) load(prefs.lastPlace());
            return;
        }

        tvLocation.setText("Locating…");
        swipe.setRefreshing(true);

        // A recent cached fix is good enough and instant
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(provider);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 15 * 60 * 1000) {
            onLocation(best);
            return;
        }

        // Otherwise ask for a fresh fix
        final Location fallback = best;
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
        if (locationCancel != null) locationCancel.cancel();
        locationCancel = new CancellationSignal();
        LocationManagerCompat.getCurrentLocation(lm, provider, locationCancel,
                ContextCompat.getMainExecutor(this), loc -> {
                    if (loc != null) onLocation(loc);
                    else if (fallback != null) onLocation(fallback);
                    else {
                        Toast.makeText(this, "Couldn't get your location", Toast.LENGTH_SHORT).show();
                        load(place != null ? place : prefs.lastPlace());
                    }
                });
    }

    private void onLocation(Location loc) {
        double lat = loc.getLatitude(), lon = loc.getLongitude();
        executor.execute(() -> {
            String name = cityName(lat, lon);
            main.post(() -> load(new Place(name != null ? name : WeatherRepository.coordsLabel(lat, lon),
                    "Current location", lat, lon)));
        });
    }

    /** Reverse geocoding: coordinates → city name (null if unavailable). */
    private String cityName(double lat, double lon) {
        try {
            List<Address> list = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                if (a.getLocality() != null) return a.getLocality();
                if (a.getSubAdminArea() != null) return a.getSubAdminArea();
                return a.getAdminArea();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // =================== Loading ===================

    private void load(Place p) {
        place = p;
        prefs.saveLastPlace(p);
        tvLocation.setText(p.name);
        swipe.setRefreshing(true);
        final long token = ++loadToken;

        executor.execute(() -> {
            try {
                WeatherData w = WeatherRepository.fetch(p.lat, p.lon, prefs);
                AirQuality aq = fetchAirQuality(p);
                main.post(() -> {
                    if (token != loadToken) return;
                    swipe.setRefreshing(false);
                    hasData = true;
                    render(w);
                    renderAirQuality(aq);
                    renderChips(w, aq);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (token != loadToken) return;
                    swipe.setRefreshing(false);
                    Toast.makeText(this, "Couldn't load weather. Check internet and pull down to retry.",
                            Toast.LENGTH_LONG).show();
                    if (!hasData) tvCondition.setText("No connection — pull down to retry");
                });
            }
        });
    }

    /** Uses the provider chosen in Settings; falls back to Open-Meteo if Google fails. */
    private AirQuality fetchAirQuality(Place p) {
        try {
            return AirQualityProvider.create(prefs).fetch(p.lat, p.lon);
        } catch (Exception e) {
            if (!Prefs.AQ_GOOGLE.equals(prefs.aqProvider())) return null;
            try {
                AirQuality aq = new OpenMeteoAirQualityProvider().fetch(p.lat, p.lon);
                aq.source += " (Google API key failed)";
                return aq;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    // =================== Rendering ===================

    private void render(WeatherData w) {
        boolean h24 = prefs.use24h();
        boolean f = prefs.fahrenheit();
        WeatherData.Current c = w.current;
        WeatherData.Day today = w.days.get(0);

        bgView.setMode(WeatherBackgroundView.modeFor(c.code, c.isDay));

        // Header: greeting + local date/time of the place
        long localMs = System.currentTimeMillis() + w.utcOffsetSeconds * 1000L;
        int hour = (int) ((localMs / 3600000L) % 24);
        tvGreeting.setText(hour < 5 ? "Good night" : hour < 12 ? "Good morning"
                : hour < 17 ? "Good afternoon" : hour < 21 ? "Good evening" : "Good night");
        SimpleDateFormat df = new SimpleDateFormat(h24 ? "EEEE, d MMM  ·  HH:mm" : "EEEE, d MMM  ·  h:mm a", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        tvDate.setText(df.format(new Date(localMs)));

        // HUD ring
        tvHeroIcon.setText(WeatherUtils.icon(c.code, c.isDay));
        tvTemp.setText(WeatherUtils.deg(c.temp));
        tvCondition.setText(WeatherUtils.description(c.code));
        ring.setValues(today.min, today.max, c.temp);
        tvHiLo.setText("Feels like " + WeatherUtils.deg(c.feelsLike));
        tvUpdated.setText("Updated " + new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.US).format(new Date())
                + "  ·  " + (f ? "°F" : "°C") + "  ·  pull down to refresh");

        // Hourly + trend chart
        hourlyAdapter.setData(w.hours, h24);
        float[] temps = new float[w.hours.size()];
        String[] labels = new String[w.hours.size()];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = (float) w.hours.get(i).temp;
            labels[i] = WeatherUtils.hour(w.hours.get(i).time, h24);
        }
        chart.setData(temps, labels);

        renderDaily(w, f);
        renderDetails(w, h24);
    }

    private void renderDaily(WeatherData w, boolean f) {
        dailyContainer.removeAllViews();
        float weekMin = Float.MAX_VALUE, weekMax = -Float.MAX_VALUE;
        for (WeatherData.Day d : w.days) {
            weekMin = Math.min(weekMin, (float) d.min);
            weekMax = Math.max(weekMax, (float) d.max);
        }
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < w.days.size(); i++) {
            WeatherData.Day d = w.days.get(i);
            View row = inf.inflate(R.layout.item_daily, dailyContainer, false);
            ((TextView) row.findViewById(R.id.tvDay)).setText(WeatherUtils.dayName(d.date, i));
            ((TextView) row.findViewById(R.id.tvDayIcon)).setText(WeatherUtils.icon(d.code));
            ((TextView) row.findViewById(R.id.tvDayPrecip)).setText(d.precipProb >= 20 ? d.precipProb + "%" : "");
            ((TextView) row.findViewById(R.id.tvDayMin)).setText(WeatherUtils.deg(d.min));
            ((TextView) row.findViewById(R.id.tvDayMax)).setText(WeatherUtils.deg(d.max));
            ((RangeBarView) row.findViewById(R.id.rangeBar)).setRange(weekMin, weekMax,
                    (float) d.min, (float) d.max, i == 0 ? (float) w.current.temp : Float.NaN, f);
            dailyContainer.addView(row);

            if (i < w.days.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
                dailyContainer.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }
    }

    private void renderDetails(WeatherData w, boolean h24) {
        detailsGrid.removeAllViews();
        WeatherData.Current c = w.current;
        WeatherData.Day today = w.days.get(0);
        String wu = WeatherUtils.windUnitLabel(prefs.windUnit());

        // Humidity + Wind
        LinearLayout row = newRow();
        addTile(row, "HUMIDITY", Math.round(c.humidity) + "%",
                Double.isNaN(c.dewPoint) ? "" : "Dew point is " + WeatherUtils.deg(c.dewPoint) + " right now")
                .setRing((float) c.humidity / 100f, 0xFF40C4FF);
        addTile(row, "WIND", Math.round(c.windSpeed) + " " + wu,
                "From " + WeatherUtils.compass(c.windDir) + "\nGusts up to " + Math.round(c.windGusts) + " " + wu)
                .setCompass((float) c.windDir);

        // UV + Visibility
        row = newRow();
        double uvShown = c.isDay ? c.uv : today.uvMax;
        addTile(row, "UV INDEX", String.valueOf(Math.round(c.uv)),
                c.isDay ? WeatherUtils.uvLevel(c.uv) + "\n" + WeatherUtils.uvAdvice(c.uv)
                        : "Night time\nToday's max " + Math.round(today.uvMax) + " (" + WeatherUtils.uvLevel(today.uvMax) + ")")
                .setArc((float) (uvShown / 11.0), MiniGaugeView.uvColor(uvShown));
        String vis = Double.isNaN(c.visibility) ? "--"
                : c.visibility >= 1000 ? Math.round(c.visibility / 1000) + " km"
                : String.format(Locale.US, "%.1f km", c.visibility / 1000);
        addTile(row, "VISIBILITY", vis, WeatherUtils.visibilityText(c.visibility))
                .setRing(Double.isNaN(c.visibility) ? 0 : (float) Math.min(c.visibility / 20000.0, 1), 0xFFB388FF);

        // Sun + Moon
        row = newRow();
        addTile(row, "SUNRISE", WeatherUtils.time(today.sunrise, h24),
                "Sunset " + WeatherUtils.time(today.sunset, h24) + "\nDaylight " + daylight(today))
                .setSun(sunProgress(c.time, today));
        long midnight = WeatherUtils.localMidnight(today.date, w.utcOffsetSeconds);
        long[] moon = MoonCalc.riseSet(midnight, place.lat, place.lon);
        double phase = MoonCalc.phase(System.currentTimeMillis());
        addTile(row, "MOONRISE", WeatherUtils.epochTime(moon[0], w.utcOffsetSeconds, h24),
                "Moonset " + WeatherUtils.epochTime(moon[1], w.utcOffsetSeconds, h24)
                        + "\n" + MoonCalc.phaseName(phase))
                .setMoon((float) phase);

        // Feels like + Pressure
        row = newRow();
        String feel = c.feelsLike > c.temp + 1 ? "Humidity makes it feel warmer"
                : c.feelsLike < c.temp - 1 ? "Wind makes it feel cooler" : "Similar to the actual temperature";
        float feelFrac = (float) ((c.feelsLike - today.min) / Math.max(today.max - today.min, 1));
        addTile(row, "FEELS LIKE", WeatherUtils.deg(c.feelsLike), feel)
                .setArc(feelFrac, 0xFFFF8A65);
        addTile(row, "PRESSURE", Math.round(c.pressure) + " hPa",
                c.pressure < 1000 ? "Low pressure — unsettled weather"
                        : c.pressure > 1022 ? "High pressure — settled weather" : "Normal sea-level pressure")
                .setArc((float) ((c.pressure - 960) / 100.0), 0xFF69F0AE);
    }

    /** Where the sun is between sunrise (0) and sunset (1); outside 0..1 means night. */
    private float sunProgress(String now, WeatherData.Day d) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            long rise = f.parse(d.sunrise).getTime(), set = f.parse(d.sunset).getTime();
            return (f.parse(now).getTime() - rise) / (float) (set - rise);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Quick-stat chips under the ring: humidity, wind, UV, AQI. */
    private void renderChips(WeatherData w, AirQuality aq) {
        chipsRow.removeAllViews();
        WeatherData.Current c = w.current;
        addChip("💧", Math.round(c.humidity) + "%", "Humidity");
        addChip("💨", String.valueOf(Math.round(c.windSpeed)), WeatherUtils.windUnitLabel(prefs.windUnit()));
        addChip("☀️", String.valueOf(Math.round(c.isDay ? c.uv : w.days.get(0).uvMax)), c.isDay ? "UV now" : "UV max");
        addChip("🫁", aq == null ? "--" : String.valueOf(aq.aqi), "AQI");
    }

    private void addChip(String icon, String value, String label) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.bg_glass);
        chip.setPadding(0, dp(10), 0, dp(10));

        TextView top = new TextView(this);
        top.setText(icon + " " + value);
        top.setTextColor(Color.WHITE);
        top.setTextSize(15);
        top.setTypeface(null, android.graphics.Typeface.BOLD);
        top.setGravity(android.view.Gravity.CENTER);
        TextView bottom = new TextView(this);
        bottom.setText(label);
        bottom.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        bottom.setTextSize(11);
        bottom.setGravity(android.view.Gravity.CENTER);
        chip.addView(top);
        chip.addView(bottom);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (chipsRow.getChildCount() > 0) lp.setMarginStart(dp(8));
        chipsRow.addView(chip, lp);
    }

    private String daylight(WeatherData.Day d) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            long mins = (f.parse(d.sunset).getTime() - f.parse(d.sunrise).getTime()) / 60000;
            return (mins / 60) + "h " + (mins % 60) + "m";
        } catch (Exception e) {
            return "--";
        }
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        detailsGrid.addView(row, lp);
        return row;
    }

    /** Adds a detail tile and returns its mini gauge so the caller can set it up. */
    private MiniGaugeView addTile(LinearLayout row, String title, String value, String sub) {
        if (row.getChildCount() > 0) row.addView(new View(this), new LinearLayout.LayoutParams(dp(12), 1));
        View tile = LayoutInflater.from(this).inflate(R.layout.item_tile, row, false);
        ((TextView) tile.findViewById(R.id.tvTileTitle)).setText(title);
        ((TextView) tile.findViewById(R.id.tvTileValue)).setText(value);
        ((TextView) tile.findViewById(R.id.tvTileSub)).setText(sub);
        row.addView(tile);
        return tile.findViewById(R.id.gauge);
    }

    private void renderAirQuality(AirQuality aq) {
        if (aq == null) {
            tvAqi.setText("--");
            tvAqiCategory.setText("Unavailable");
            tvAqiCategory.setCompoundDrawablesRelative(null, null, null, null);
            tvAqiScale.setText("");
            tvAqiPollutant.setText("");
            tvAqiAdvice.setText("Air quality data couldn't be loaded. Pull down to retry.");
            tvAqiSource.setText("");
            aqiBar.setBadness(0);
            return;
        }
        boolean google = aq.source.startsWith("Google");
        tvAqi.setText(String.valueOf(aq.aqi));
        tvAqiCategory.setText(aq.category);
        // Colored dot + text label (never color alone)
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(aq.color);
        dot.setSize(dp(12), dp(12));
        dot.setBounds(0, 0, dp(12), dp(12));
        tvAqiCategory.setCompoundDrawablesRelative(dot, null, null, null);
        tvAqiCategory.setCompoundDrawablePadding(dp(8));
        tvAqiScale.setText(aq.scaleName + (google ? "  ·  100 = cleanest air" : "  ·  0–500, lower is better"));
        aqiBar.setBadness(aq.badness);
        tvAqiPollutant.setText("Dominant pollutant: " + aq.dominant);
        tvAqiAdvice.setText("🩺  " + aq.advice);
        tvAqiSource.setText("Source: " + aq.source);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
