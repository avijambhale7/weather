package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Min/max bar for one day of the 10-day forecast.
 * The track spans the lowest→highest temperature of the whole 10 days;
 * the colored part shows this day's range. A dot marks the current temperature (today only).
 */
public class RangeBarView extends View {

    private float weekMin, weekMax, dayMin, dayMax, current = Float.NaN;
    private boolean fahrenheit;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public RangeBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
    }

    public void setRange(float weekMin, float weekMax, float dayMin, float dayMax, float current, boolean fahrenheit) {
        this.weekMin = weekMin;
        this.weekMax = weekMax;
        this.dayMin = dayMin;
        this.dayMax = dayMax;
        this.current = current;
        this.fahrenheit = fahrenheit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), cy = getHeight() / 2f, half = 2.5f * dp;
        float span = Math.max(weekMax - weekMin, 1f);

        // track
        paint.setShader(null);
        paint.setColor(0x26FFFFFF);
        rect.set(0, cy - half, w, cy + half);
        c.drawRoundRect(rect, half, half, paint);

        // this day's range, colored cold → warm
        float x1 = (dayMin - weekMin) / span * w;
        float x2 = Math.max(x1 + 2 * half, (dayMax - weekMin) / span * w);
        paint.setColor(Color.WHITE); // full opacity, otherwise the track's alpha dims the gradient
        paint.setShader(new LinearGradient(x1, 0, x2, 0, tempColor(dayMin), tempColor(dayMax), Shader.TileMode.CLAMP));
        rect.set(x1, cy - half, x2, cy + half);
        c.drawRoundRect(rect, half, half, paint);

        // current temperature dot
        if (!Float.isNaN(current)) {
            float cx = Math.max(half, Math.min(w - half, (current - weekMin) / span * w));
            paint.setShader(null);
            paint.setColor(0xFF1A1446);
            c.drawCircle(cx, cy, 5 * dp, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(cx, cy, 3.5f * dp, paint);
        }
    }

    /** Blue (cold) → green → yellow → orange → red (hot). */
    private int tempColor(float t) {
        float celsius = fahrenheit ? (t - 32) * 5 / 9 : t;
        float[] stops = {0, 10, 20, 27, 33, 40};
        int[] colors = {0xFF4FC3F7, 0xFF4DD0E1, 0xFF81C784, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF7043};
        if (celsius <= stops[0]) return colors[0];
        for (int i = 1; i < stops.length; i++) {
            if (celsius <= stops[i]) {
                float f = (celsius - stops[i - 1]) / (stops[i] - stops[i - 1]);
                return blend(colors[i - 1], colors[i], f);
            }
        }
        return colors[colors.length - 1];
    }

    private static int blend(int a, int b, float f) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
