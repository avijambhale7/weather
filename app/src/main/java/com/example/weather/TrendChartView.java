package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * 24-hour temperature trend line chart.
 * Single series → no legend; only Now / High / Low are labelled.
 * Touch and drag to see the temperature at any hour.
 */
public class TrendChartView extends View {

    private static final int LINE = 0xFF00E5FF;

    private float[] temps = new float[0];
    private String[] labels = new String[0];
    private int selected = -1;

    private final float dp;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final RectF rect = new RectF();

    public TrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * dp);
        line.setColor(LINE);
        line.setStrokeCap(Paint.Cap.ROUND);

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp);
        grid.setColor(0x1FFFFFFF);
        grid.setPathEffect(new DashPathEffect(new float[]{4 * dp, 4 * dp}, 0));

        text.setColor(Color.WHITE);
        text.setTextSize(12 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);

        muted.setColor(0x99FFFFFF);
        muted.setTextSize(10 * dp);
        muted.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] temps, String[] labels) {
        this.temps = temps;
        this.labels = labels;
        selected = -1;
        if (temps.length > 0) {
            int lo = 0, hi = 0;
            for (int i = 0; i < temps.length; i++) {
                if (temps[i] < temps[lo]) lo = i;
                if (temps[i] > temps[hi]) hi = i;
            }
            setContentDescription(String.format(Locale.US,
                    "Temperature trend: now %s, high %s at %s, low %s at %s",
                    WeatherUtils.deg(temps[0]), WeatherUtils.deg(temps[hi]), labels[hi],
                    WeatherUtils.deg(temps[lo]), labels[lo]));
        }
        invalidate();
    }

    private float left() { return 16 * dp; }
    private float right() { return getWidth() - 16 * dp; }
    private float top() { return 30 * dp; }
    private float bottom() { return getHeight() - 46 * dp; } // room for "Low" label + hour labels

    private float x(int i) {
        return left() + i * (right() - left()) / Math.max(temps.length - 1, 1);
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = temps.length;
        if (n < 2) return;

        float min = temps[0], max = temps[0];
        int lo = 0, hi = 0;
        for (int i = 0; i < n; i++) {
            if (temps[i] < min) { min = temps[i]; lo = i; }
            if (temps[i] > max) { max = temps[i]; hi = i; }
        }
        float range = Math.max(max - min, 1f);
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) ys[i] = bottom() - (temps[i] - min) / range * (bottom() - top());

        // Recessive gridlines at high, middle, low
        for (int g = 0; g < 3; g++) {
            float gy = top() + g * (bottom() - top()) / 2f;
            c.drawLine(left(), gy, right(), gy, grid);
        }

        // Smooth line
        path.reset();
        path.moveTo(x(0), ys[0]);
        for (int i = 1; i < n; i++) {
            float cx = (x(i - 1) + x(i)) / 2f;
            path.cubicTo(cx, ys[i - 1], cx, ys[i], x(i), ys[i]);
        }

        // Soft area under the line
        fillPath.set(path);
        fillPath.lineTo(x(n - 1), bottom());
        fillPath.lineTo(x(0), bottom());
        fillPath.close();
        fill.setShader(new LinearGradient(0, top(), 0, bottom(), 0x5500E5FF, 0x0000E5FF, Shader.TileMode.CLAMP));
        c.drawPath(fillPath, fill);
        c.drawPath(path, line);

        // Hour labels every 3 hours
        for (int i = 0; i < n; i += 3) {
            c.drawText(i == 0 ? "Now" : labels[i], x(i), getHeight() - 8 * dp, muted);
        }

        // Direct labels: Now, High, Low (skip one if it would overlap)
        marker(c, 0, ys[0], "Now " + WeatherUtils.deg(temps[0]), true);
        if (hi != 0 && Math.abs(x(hi) - x(0)) > 48 * dp) marker(c, hi, ys[hi], "High " + WeatherUtils.deg(max), true);
        if (lo != 0 && Math.abs(x(lo) - x(0)) > 24 * dp) marker(c, lo, ys[lo], "Low " + WeatherUtils.deg(min), false);

        // Touch crosshair + tooltip
        if (selected >= 0) {
            float sx = x(selected), sy = ys[selected];
            grid.setPathEffect(null);
            grid.setColor(0x66FFFFFF);
            c.drawLine(sx, top() - 6 * dp, sx, bottom(), grid);
            grid.setColor(0x1FFFFFFF);
            grid.setPathEffect(new DashPathEffect(new float[]{4 * dp, 4 * dp}, 0));
            drawDot(c, sx, sy);

            String s = (selected == 0 ? "Now" : labels[selected]) + "  ·  " + WeatherUtils.deg(temps[selected]);
            float tw = text.measureText(s) + 20 * dp;
            float tx = Math.max(tw / 2, Math.min(getWidth() - tw / 2, sx));
            rect.set(tx - tw / 2, 0, tx + tw / 2, 24 * dp);
            tip.setStyle(Paint.Style.FILL);
            tip.setColor(0xF01A1446);
            c.drawRoundRect(rect, 12 * dp, 12 * dp, tip);
            tip.setStyle(Paint.Style.STROKE);
            tip.setStrokeWidth(dp);
            tip.setColor(0x66FFFFFF);
            c.drawRoundRect(rect, 12 * dp, 12 * dp, tip);
            c.drawText(s, tx, 16.5f * dp, text);
        }
    }

    private void marker(Canvas c, int i, float y, String label, boolean above) {
        if (selected >= 0) return; // tooltip replaces labels while touching
        drawDot(c, x(i), y);
        float lx = Math.max(left() + 24 * dp, Math.min(right() - 24 * dp, x(i)));
        c.drawText(label, lx, above ? y - 10 * dp : y + 20 * dp, text);
    }

    /** 8dp marker with a dark ring so it separates from the line. */
    private void drawDot(Canvas c, float cx, float cy) {
        dot.setColor(0xFF1A1446);
        c.drawCircle(cx, cy, 6 * dp, dot);
        dot.setColor(Color.WHITE);
        c.drawCircle(cx, cy, 4 * dp, dot);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (temps.length < 2) return false;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                float step = (right() - left()) / (temps.length - 1);
                selected = Math.max(0, Math.min(temps.length - 1, Math.round((e.getX() - left()) / step)));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selected = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}
