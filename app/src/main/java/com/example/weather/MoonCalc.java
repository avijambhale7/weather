package com.example.weather;

/**
 * Moonrise / moonset and moon phase, calculated on the device.
 * Based on the well-known SunCalc algorithm (Vladimir Agafonkin, BSD licence).
 */
public class MoonCalc {

    private static final double RAD = Math.PI / 180;
    private static final double DAY_MS = 86400000.0;
    private static final double J1970 = 2440588, J2000 = 2451545;
    private static final double E = RAD * 23.4397; // obliquity of the Earth

    private static double toDays(double ms) { return ms / DAY_MS - 0.5 + J1970 - J2000; }

    /** Moon altitude (radians) above the horizon at a moment. */
    private static double moonAltitude(double ms, double lat, double lng) {
        double lw = RAD * -lng, phi = RAD * lat, d = toDays(ms);

        double L = RAD * (218.316 + 13.176396 * d);  // ecliptic longitude
        double M = RAD * (134.963 + 13.064993 * d);  // mean anomaly
        double F = RAD * (93.272 + 13.229350 * d);   // mean distance
        double l = L + RAD * 6.289 * Math.sin(M);
        double b = RAD * 5.128 * Math.sin(F);

        double ra = Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l));
        double dec = Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l));
        double H = RAD * (280.16 + 360.9856235 * d) - lw - ra;

        double h = Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H));
        // atmospheric refraction
        double hr = Math.max(h, 0);
        return h + 0.0002967 / Math.tan(hr + 0.00312536 / (hr + 0.08901179));
    }

    /**
     * Moonrise and moonset during the 24 hours after {@code dayStartMs}.
     * @return {rise, set} as epoch millis, or -1 when it doesn't happen that day.
     */
    public static long[] riseSet(long dayStartMs, double lat, double lng) {
        double hc = 0.133 * RAD;
        double h0 = moonAltitude(dayStartMs, lat, lng) - hc;
        double rise = Double.NaN, set = Double.NaN;

        for (int i = 1; i <= 24; i += 2) {
            double h1 = moonAltitude(dayStartMs + i * 3600000.0, lat, lng) - hc;
            double h2 = moonAltitude(dayStartMs + (i + 1) * 3600000.0, lat, lng) - hc;

            double a = (h0 + h2) / 2 - h1, b = (h2 - h0) / 2;
            double xe = -b / (2 * a), ye = (a * xe + b) * xe + h1;
            double d = b * b - 4 * a * h1, x1 = 0, x2 = 0;
            int roots = 0;
            if (d >= 0) {
                double dx = Math.sqrt(d) / (Math.abs(a) * 2);
                x1 = xe - dx;
                x2 = xe + dx;
                if (Math.abs(x1) <= 1) roots++;
                if (Math.abs(x2) <= 1) roots++;
                if (x1 < -1) x1 = x2;
            }
            if (roots == 1) {
                if (h0 < 0) rise = i + x1; else set = i + x1;
            } else if (roots == 2) {
                rise = i + (ye < 0 ? x2 : x1);
                set = i + (ye < 0 ? x1 : x2);
            }
            if (!Double.isNaN(rise) && !Double.isNaN(set)) break;
            h0 = h2;
        }
        return new long[]{
                Double.isNaN(rise) ? -1 : (long) (dayStartMs + rise * 3600000),
                Double.isNaN(set) ? -1 : (long) (dayStartMs + set * 3600000)};
    }

    /** Moon phase 0..1 (0 = new, 0.5 = full). */
    public static double phase(long ms) {
        double synodic = 29.530588853;
        double knownNewMoon = 947182440000.0; // 2000-01-06 18:14 UTC
        double days = (ms - knownNewMoon) / DAY_MS;
        double p = (days % synodic) / synodic;
        return p < 0 ? p + 1 : p;
    }

    public static String phaseName(double p) {
        if (p < 0.03 || p > 0.97) return "New Moon";
        if (p < 0.22) return "Waxing Crescent";
        if (p < 0.28) return "First Quarter";
        if (p < 0.47) return "Waxing Gibbous";
        if (p < 0.53) return "Full Moon";
        if (p < 0.72) return "Waning Gibbous";
        if (p < 0.78) return "Last Quarter";
        return "Waning Crescent";
    }

    public static String phaseEmoji(double p) {
        String[] e = {"🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"};
        return e[(int) Math.round(p * 8) % 8];
    }
}
