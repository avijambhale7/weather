package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small gauge shown in each detail tile:
 * RING (percentage), ARC (meter), COMPASS (wind direction), SUN (sun position), MOON (phase).
 */
public class MiniGaugeView extends View {

    private enum Type { NONE, RING, ARC, COMPASS, SUN, MOON }

    private Type type = Type.NONE;
    private float value;
    private int color = 0xFF00E5FF;
    private final float dp;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    public MiniGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(0x99FFFFFF);
        text.setTextSize(8 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    /** Full ring filled to {@code fraction} (0..1). */
    public void setRing(float fraction, int color) { set(Type.RING, fraction, color); }

    /** 270° meter filled to {@code fraction} (0..1). */
    public void setArc(float fraction, int color) { set(Type.ARC, fraction, color); }

    /** Arrow showing where the wind blows to; {@code fromDegrees} is the API wind direction. */
    public void setCompass(float fromDegrees) { set(Type.COMPASS, fromDegrees, color); }

    /** Sun along its daily arc: 0 = sunrise, 1 = sunset, outside = night. */
    public void setSun(float progress) { set(Type.SUN, progress, 0xFFFFD54F); }

    /** Moon phase 0..1 (0 = new, 0.5 = full). */
    public void setMoon(float phase) { set(Type.MOON, phase, 0xFFFFF3C4); }

    private void set(Type t, float v, int c) {
        type = t;
        value = v;
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 4 * dp;
        stroke.setPathEffect(null);

        switch (type) {
            case RING:
            case ARC: {
                float start = type == Type.RING ? -90 : 135, sweep = type == Type.RING ? 360 : 270;
                oval.set(cx - r, cy - r, cx + r, cy + r);
                stroke.setStrokeWidth(4 * dp);
                stroke.setColor(0x26FFFFFF);
                c.drawArc(oval, start, sweep, false, stroke);
                stroke.setColor(color);
                c.drawArc(oval, start, sweep * Math.max(0.01f, Math.min(1f, value)), false, stroke);
                break;
            }
            case COMPASS: {
                stroke.setStrokeWidth(1.5f * dp);
                stroke.setColor(0x4DFFFFFF);
                c.drawCircle(cx, cy, r, stroke);
                c.drawText("N", cx, cy - r + 9 * dp, text);
                // wind blows *to* the opposite of where it comes from
                c.save();
                c.rotate(value + 180, cx, cy);
                path.reset();
                path.moveTo(cx, cy - r + 10 * dp);
                path.lineTo(cx + 6 * dp, cy + r * 0.45f);
                path.lineTo(cx, cy + r * 0.25f);
                path.lineTo(cx - 6 * dp, cy + r * 0.45f);
                path.close();
                fill.setColor(0xFF00E5FF);
                c.drawPath(path, fill);
                c.restore();
                break;
            }
            case SUN: {
                float baseY = h - 8 * dp;
                oval.set(4 * dp, baseY - (w - 8 * dp) / 2, w - 4 * dp, baseY + (w - 8 * dp) / 2);
                stroke.setStrokeWidth(1.5f * dp);
                stroke.setColor(0x66FFFFFF);
                stroke.setPathEffect(new DashPathEffect(new float[]{3 * dp, 4 * dp}, 0));
                c.drawArc(oval, 180, 180, false, stroke);
                stroke.setPathEffect(null);
                c.drawLine(0, baseY, w, baseY, stroke);
                float p = Math.max(0f, Math.min(1f, value));
                boolean up = value >= 0 && value <= 1;
                double a = Math.toRadians(180 + 180 * p);
                float sx = oval.centerX() + (float) Math.cos(a) * oval.width() / 2;
                float sy = oval.centerY() + (float) Math.sin(a) * oval.height() / 2;
                fill.setColor(up ? 0x66FFD54F : 0x33FFFFFF);
                c.drawCircle(sx, sy, 8 * dp, fill);
                fill.setColor(up ? color : 0x99FFFFFF);
                c.drawCircle(sx, sy, 4.5f * dp, fill);
                break;
            }
            case MOON: {
                c.save();
                path.reset();
                path.addCircle(cx, cy, r, Path.Direction.CW);
                c.clipPath(path);
                fill.setColor(0xFF2A2F4A);
                c.drawCircle(cx, cy, r, fill);
                fill.setColor(color);
                c.drawCircle(cx, cy, r, fill);
                // shadow disc slides across to make the phase shape
                float p = value;
                float shadowX = p <= 0.5f ? cx - 2 * r * (p * 2) : cx + 2 * r * (1 - (p - 0.5f) * 2);
                fill.setColor(0xF02A2F4A);
                c.drawCircle(shadowX, cy, r, fill);
                c.restore();
                stroke.setStrokeWidth(1 * dp);
                stroke.setColor(0x4DFFFFFF);
                c.drawCircle(cx, cy, r, stroke);
                break;
            }
            default:
                break;
        }
    }

    /** Used by tiles that need a plain color constant. */
    public static int uvColor(double uv) {
        if (uv < 3) return 0xFF69F0AE;
        if (uv < 6) return 0xFFFFD740;
        if (uv < 8) return 0xFFFFAB40;
        if (uv < 11) return 0xFFFF5252;
        return 0xFFE040FB;
    }

}
