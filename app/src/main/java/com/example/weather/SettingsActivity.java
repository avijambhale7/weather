package com.example.weather;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/** Settings: units, time format, background animation, GPS on launch, air quality provider. */
public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;
    private EditText etGoogleKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Temperature unit
        RadioGroup rgTemp = findViewById(R.id.rgTemp);
        rgTemp.check(prefs.fahrenheit() ? R.id.rbFahrenheit : R.id.rbCelsius);
        rgTemp.setOnCheckedChangeListener((g, id) -> prefs.setFahrenheit(id == R.id.rbFahrenheit));

        // Wind unit
        RadioGroup rgWind = findViewById(R.id.rgWind);
        String unit = prefs.windUnit();
        rgWind.check(unit.equals("ms") ? R.id.rbMs : unit.equals("mph") ? R.id.rbMph : R.id.rbKmh);
        rgWind.setOnCheckedChangeListener((g, id) ->
                prefs.setWindUnit(id == R.id.rbMs ? "ms" : id == R.id.rbMph ? "mph" : "kmh"));

        // Switches
        SwitchCompat sw24h = findViewById(R.id.sw24h);
        sw24h.setChecked(prefs.use24h());
        sw24h.setOnCheckedChangeListener((b, on) -> prefs.setUse24h(on));

        SwitchCompat swAnim = findViewById(R.id.swAnim);
        swAnim.setChecked(prefs.animatedBg());
        swAnim.setOnCheckedChangeListener((b, on) -> prefs.setAnimatedBg(on));

        SwitchCompat swGps = findViewById(R.id.swGps);
        swGps.setChecked(prefs.useGps());
        swGps.setOnCheckedChangeListener((b, on) -> prefs.setUseGps(on));

        // Air quality provider
        View googleBox = findViewById(R.id.googleBox);
        etGoogleKey = findViewById(R.id.etGoogleKey);
        etGoogleKey.setText(prefs.googleKey());
        RadioGroup rgAq = findViewById(R.id.rgAq);
        boolean google = Prefs.AQ_GOOGLE.equals(prefs.aqProvider());
        rgAq.check(google ? R.id.rbAqGoogle : R.id.rbAqOpenMeteo);
        googleBox.setVisibility(google ? View.VISIBLE : View.GONE);
        rgAq.setOnCheckedChangeListener((g, id) -> {
            boolean g2 = id == R.id.rbAqGoogle;
            prefs.setAqProvider(g2 ? Prefs.AQ_GOOGLE : Prefs.AQ_OPEN_METEO);
            googleBox.setVisibility(g2 ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.setGoogleKey(etGoogleKey.getText().toString().trim());
    }
}
