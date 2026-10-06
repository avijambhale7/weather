package com.example.weather;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Futuristic HUD ring for the current temperature.
 * The neon arc shows where "now" sits between today's low and high.
 * An outer dashed ring rotates slowly. Temperature text sits on top (in the layout).
 */
public class TempRingView extends View {

    private static final float START = 135f, SWEEP = 270f;

    private float fraction = 0f;   // animated 0..1
    private String lowLabel = "", highLabel = "";
    private boolean animated = true;
    private final long startTime = SystemClock.uptimeMillis();
    private final float dp;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();

    public TempRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(10 * dp);
        track.setColor(0x26FFFFFF);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(10 * dp);

        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeWidth(26 * dp);
        glow.setAlpha(45);

        hud.setStyle(Paint.Style.STROKE);
        hud.setStrokeWidth(1.2f * dp);
        hud.setColor(0x66FFFFFF);
        hud.setPathEffect(new DashPathEffect(new float[]{2 * dp, 8 * dp}, 0));

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(1.5f * dp);
        tick.setColor(0x4DFFFFFF);

        label.setColor(0xB3FFFFFF);
        label.setTextSize(12 * dp);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
    }

    /** Sets today's low/high and the current temperature; the arc animates to its new position. */
    public void setValues(double low, double high, double current) {
        lowLabel = "L " + WeatherUtils.deg(low);
        highLabel = "H " + WeatherUtils.deg(high);
        float target = (float) ((current - low) / Math.max(high - low, 1));
        target = Math.max(0.02f, Math.min(1f, target));
        ValueAnimator va = ValueAnimator.ofFloat(0f, target);
        va.setDuration(1200);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        va.start();
        setContentDescription("Now " + WeatherUtils.deg(current) + ", today's low " + WeatherUtils.deg(low)
                + ", high " + WeatherUtils.deg(high));
    }

    public void setAnimated(boolean a) {
        animated = a;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float cx = w / 2f, cy = h / 2f;
        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{0xFF00E5FF, 0xFF7C4DFF, 0xFFFF4FD8, 0xFF00E5FF},
                new float[]{0f, 0.375f, 0.75f, 1f});
        matrix.setRotate(START, cx, cy);
        g.setLocalMatrix(matrix);
        arc.setShader(g);
        glow.setShader(g);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - 28 * dp;
        float t = animated ? (SystemClock.uptimeMillis() - startTime) / 1000f : 0f;

        // Outer rotating HUD ring
        c.save();
        c.rotate(t * 8, cx, cy);
        c.drawCircle(cx, cy, r + 20 * dp, hud);
        c.restore();

        // Tick marks around the arc
        for (int i = 0; i <= 27; i++) {
            double a = Math.toRadians(START + i * SWEEP / 27);
            float in = r - 16 * dp, out = r - (i % 9 == 0 ? 24 : 20) * dp;
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, tick);
        }

        oval.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, START, SWEEP, false, track);
        c.drawArc(oval, START, SWEEP * fraction, false, glow);
        c.drawArc(oval, START, SWEEP * fraction, false, arc);

        // Knob at the current position
        double a = Math.toRadians(START + SWEEP * fraction);
        float kx = cx + (float) Math.cos(a) * r, ky = cy + (float) Math.sin(a) * r;
        dot.setColor(0x6600E5FF);
        c.drawCircle(kx, ky, 13 * dp, dot);
        dot.setColor(Color.WHITE);
        c.drawCircle(kx, ky, 7 * dp, dot);

        // Low / High labels at the ends of the arc
        double sa = Math.toRadians(START), ea = Math.toRadians(START + SWEEP);
        float lr = r + 4 * dp;
        c.drawText(lowLabel, cx + (float) Math.cos(sa) * lr, cy + (float) Math.sin(sa) * lr + 22 * dp, label);
        c.drawText(highLabel, cx + (float) Math.cos(ea) * lr, cy + (float) Math.sin(ea) * lr + 22 * dp, label);

        if (animated) postInvalidateOnAnimation();
    }
}
