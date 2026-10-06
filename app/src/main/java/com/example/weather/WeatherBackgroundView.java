package com.example.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Futuristic animated background that matches the weather:
 * aurora ribbons, glowing orbs, a glowing planet horizon, and weather particles
 * (stars, sun rays, clouds, rain, snow, lightning). Moves with a slight parallax on scroll.
 */
public class WeatherBackgroundView extends View {

    public enum Mode { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, RAIN, SNOW, STORM }

    /** Chooses a background mode from an Open-Meteo weather code. */
    public static Mode modeFor(int code, boolean isDay) {
        if (code >= 95) return Mode.STORM;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Mode.SNOW;
        if (code >= 51) return Mode.RAIN;
        if (code >= 3) return Mode.CLOUDY;
        return isDay ? Mode.CLEAR_DAY : Mode.CLEAR_NIGHT;
    }

    private static final float GLOW_R = 100f; // radial shaders are built at this radius, then scaled

    private Mode mode = Mode.CLEAR_NIGHT;
    private boolean animated = true;
    private float scroll;                     // scroll position for parallax
    private final long startTime = SystemClock.uptimeMillis();
    private final float dp;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    // per-mode palette & shaders
    private int rimColor;
    private int[] auroraColors;
    private float auroraStrength;
    private LinearGradient bgShader;
    private LinearGradient[] ribbonShaders;
    private RadialGradient orbA, orbB, softWhite, sunGlow, planetShader;

    // particles: x, y, size (0-1), speed, phase, depth layer (0-2)
    private int count;
    private float[] px, py, size, speed, phase;
    private int[] layer;
    private float flash, nextFlash = 2f, boltX;

    public WeatherBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setMode(Mode m) {
        if (m != mode) {
            mode = m;
            setup();
            invalidate();
        }
    }

    public void setAnimated(boolean a) {
        animated = a;
        invalidate();
    }

    /** Called from the scroll view so the background drifts slightly (parallax). */
    public void setScrollOffset(float y) {
        scroll = y;
        if (!animated) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        setup();
    }

    private void setup() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        int top, mid, bottom, orb1, orb2;
        switch (mode) {
            case CLEAR_DAY:
                top = 0xFF0B4FB3; mid = 0xFF2F72D6; bottom = 0xFF6A4FD8; orb1 = 0xFF7FE7FF; orb2 = 0xFFFF8AD8;
                auroraColors = new int[]{0xFF9FF3FF, 0xFFFFC1F2, 0xFFFFFFFF}; auroraStrength = 0.45f;
                rimColor = 0xFFB3F0FF; count = 30; break;
            case CLOUDY:
                top = 0xFF141B2B; mid = 0xFF253047; bottom = 0xFF3A4562; orb1 = 0xFF8C9EFF; orb2 = 0xFF90A4AE;
                auroraColors = new int[]{0xFF8C9EFF, 0xFFB0BEC5, 0xFF80DEEA}; auroraStrength = 0.35f;
                rimColor = 0xFF9FA8DA; count = 7; break;
            case RAIN:
                top = 0xFF040B17; mid = 0xFF0C1E33; bottom = 0xFF163757; orb1 = 0xFF00B8D4; orb2 = 0xFF304FFE;
                auroraColors = new int[]{0xFF00B8D4, 0xFF3D5AFE, 0xFF18FFFF}; auroraStrength = 0.3f;
                rimColor = 0xFF26C6DA; count = 140; break;
            case SNOW:
                top = 0xFF1C2945; mid = 0xFF3E5379; bottom = 0xFF7F95BD; orb1 = 0xFFE1F5FE; orb2 = 0xFFB3E5FC;
                auroraColors = new int[]{0xFFE1F5FE, 0xFFB3E5FC, 0xFFFFFFFF}; auroraStrength = 0.4f;
                rimColor = 0xFFE1F5FE; count = 110; break;
            case STORM:
                top = 0xFF040410; mid = 0xFF120B24; bottom = 0xFF281542; orb1 = 0xFF7C4DFF; orb2 = 0xFF2962FF;
                auroraColors = new int[]{0xFF7C4DFF, 0xFF651FFF, 0xFF448AFF}; auroraStrength = 0.4f;
                rimColor = 0xFFB388FF; count = 150; break;
            default: // CLEAR_NIGHT
                top = 0xFF02030C; mid = 0xFF0B0A2A; bottom = 0xFF1A0E3C; orb1 = 0xFF7C4DFF; orb2 = 0xFF00BFA5;
                auroraColors = new int[]{0xFF00FFC6, 0xFF7C4DFF, 0xFF00B3FF}; auroraStrength = 0.75f;
                rimColor = 0xFF8C6CFF; count = 90; break;
        }

        bgShader = new LinearGradient(0, 0, 0, h, new int[]{top, mid, bottom}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        orbA = radial(withAlpha(orb1, 0x55));
        orbB = radial(withAlpha(orb2, 0x44));
        softWhite = radial(0x26FFFFFF);
        sunGlow = radial(0xAAFFE082);
        planetShader = new RadialGradient(0, 0, GLOW_R, new int[]{withAlpha(bottom, 0xFF), 0xFF05060F},
                new float[]{0.7f, 1f}, Shader.TileMode.CLAMP);

        // Aurora ribbon: bright lower edge fading upward (built in a 0..1 box, then scaled)
        ribbonShaders = new LinearGradient[3];
        for (int i = 0; i < 3; i++) {
            int c = auroraColors[i];
            // transparent at the top → soft color → bright edge near the bottom → gone
            ribbonShaders[i] = new LinearGradient(0, 0, 0, 1,
                    new int[]{Color.TRANSPARENT, withAlpha(c, (int) (90 * auroraStrength)),
                            withAlpha(c, (int) (230 * auroraStrength)), Color.TRANSPARENT},
                    new float[]{0f, 0.6f, 0.9f, 1f}, Shader.TileMode.CLAMP);
        }

        Random rnd = new Random(42);
        px = new float[count]; py = new float[count]; size = new float[count];
        speed = new float[count]; phase = new float[count]; layer = new int[count];
        for (int i = 0; i < count; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h;
            size[i] = rnd.nextFloat();
            speed[i] = 0.5f + rnd.nextFloat();
            phase[i] = rnd.nextFloat() * 6.28f;
            layer[i] = rnd.nextInt(3);
        }
    }

    private static RadialGradient radial(int color) {
        return new RadialGradient(0, 0, GLOW_R, color, Color.TRANSPARENT, Shader.TileMode.CLAMP);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bgShader == null) setup();
        if (bgShader == null) return;
        int w = getWidth(), h = getHeight();
        float t = animated ? (SystemClock.uptimeMillis() - startTime) / 1000f : 12f;
        float par = Math.min(scroll, h * 2f) * 0.2f; // parallax amount

        paint.setShader(bgShader);
        c.drawRect(0, 0, w, h, paint);

        // Soft glowing orbs
        glow(c, orbA, w * (0.15f + 0.12f * (float) Math.sin(t * 0.11)), h * 0.22f - par * 0.5f, w * 0.85f);
        glow(c, orbB, w * (0.9f + 0.08f * (float) Math.cos(t * 0.09)), h * 0.55f - par * 0.3f, w * 0.75f);

        if (mode == Mode.CLEAR_NIGHT) drawStars(c, w, h, t, par);
        if (mode == Mode.CLEAR_DAY) drawSun(c, w, h, t, par);
        drawAurora(c, w, h, t, par);

        switch (mode) {
            case CLEAR_DAY: drawMotes(c, h, t); break;
            case CLOUDY: drawClouds(c, w, h, t, par); break;
            case RAIN: drawRain(c, h, t, 1f); break;
            case SNOW: drawSnow(c, h, t); break;
            case STORM: drawRain(c, h, t, 1.4f); drawLightning(c, w, h, t); break;
            default: break;
        }

        drawPlanet(c, w, h, t, par);

        if (animated) postInvalidateOnAnimation();
    }

    private void glow(Canvas c, Shader shader, float x, float y, float radius) {
        c.save();
        c.translate(x, y);
        c.scale(radius / GLOW_R, radius / GLOW_R);
        paint.setShader(shader);
        c.drawCircle(0, 0, GLOW_R, paint);
        c.restore();
    }

    /**
     * Aurora curtains: rows of thin vertical light rays along a drifting wave.
     * Each ray is bright at the bottom edge and fades upward, like real northern lights.
     */
    private void drawAurora(Canvas c, int w, int h, float t, float par) {
        float step = 3 * dp;
        stroke.setStrokeWidth(3.6f * dp); // slightly wider than the step = no gaps
        stroke.setStrokeCap(Paint.Cap.BUTT);
        for (int k = 0; k < 3; k++) {
            float baseY = h * (0.16f + 0.07f * k) - par * (0.8f - 0.2f * k);
            float amp = h * 0.035f;
            float band = h * (0.18f - 0.03f * k);

            matrix.setScale(1, band + amp * 2);
            matrix.postTranslate(0, baseY - band - amp);
            ribbonShaders[k].setLocalMatrix(matrix);
            stroke.setShader(ribbonShaders[k]);

            for (float x = 0; x <= w; x += step) {
                float yb = wave(x, w, t, k, baseY, amp);
                // ray height and brightness shimmer along the curtain
                float shimmer = Math.abs((float) Math.sin(x * 0.011f + t * 0.6f + k * 2.1f));
                float flick = 0.55f + 0.45f * (float) Math.sin(x * 0.045f - t * 1.3f + k);
                float yt = yb - band * (0.45f + 0.55f * shimmer);
                stroke.setAlpha((int) (255 * Math.max(0.15f, shimmer * flick)));
                c.drawLine(x, yb, x, yt, stroke);
            }
        }
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    private float wave(float x, int w, float t, int k, float baseY, float amp) {
        double u = x / (double) w * Math.PI * 2;
        return baseY + amp * (float) Math.sin(u * 1.2 + t * 0.35 + k * 1.7)
                + amp * 0.5f * (float) Math.sin(u * 2.7 - t * 0.22 + k);
    }

    /** A huge glowing planet curve along the bottom of the screen. */
    private void drawPlanet(Canvas c, int w, int h, float t, float par) {
        float r = w * 1.5f;
        float cx = w * 0.5f, cy = h + r * 0.86f - par * 0.15f;
        c.save();
        c.translate(cx, cy);
        c.scale(r / GLOW_R, r / GLOW_R);
        paint.setShader(planetShader);
        c.drawCircle(0, 0, GLOW_R, paint);
        c.restore();

        // Glowing rim: a few strokes from wide/faint to thin/bright, gently pulsing
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.8);
        stroke.setShader(null);
        stroke.setColor(rimColor);
        float[] widths = {22, 12, 6, 2};
        int[] alphas = {14, 30, 70, 200};
        for (int i = 0; i < widths.length; i++) {
            stroke.setStrokeWidth(widths[i] * dp);
            stroke.setAlpha((int) (alphas[i] * pulse));
            c.drawCircle(cx, cy, r, stroke);
        }
    }

    /** Tiny stars in three depth layers + a few sparkling ones + a shooting star. */
    private void drawStars(Canvas c, int w, int h, float t, float par) {
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < count; i++) {
            float depth = 0.4f + layer[i] * 0.3f;
            float y = (py[i] * 0.7f - par * depth) % h;
            if (y < 0) y += h;
            float twinkle = 0.3f + 0.7f * Math.abs((float) Math.sin(t * speed[i] * 0.8f + phase[i]));
            paint.setAlpha((int) (200 * twinkle * depth));
            float r = (0.5f + size[i] * 0.6f + layer[i] * 0.35f) * dp;
            c.drawCircle(px[i], y, r, paint);

            // Every 12th star sparkles with a 4-point glint
            if (i % 12 == 0) {
                float g = (3 + 4 * twinkle) * dp;
                stroke.setShader(null);
                stroke.setColor(Color.WHITE);
                stroke.setStrokeWidth(0.8f * dp);
                stroke.setAlpha((int) (160 * twinkle));
                c.drawLine(px[i] - g, y, px[i] + g, y, stroke);
                c.drawLine(px[i], y - g, px[i], y + g, stroke);
            }
        }
        float cycle = t % 8f;
        if (cycle < 0.9f) {
            float p = cycle / 0.9f;
            float x = w * (0.2f + 0.6f * p), y = h * (0.06f + 0.14f * p);
            stroke.setShader(new LinearGradient(x, y, x - 70 * dp, y - 20 * dp,
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            stroke.setAlpha((int) (255 * (1 - p)));
            stroke.setStrokeWidth(2 * dp);
            c.drawLine(x, y, x - 70 * dp, y - 20 * dp, stroke);
            stroke.setShader(null);
        }
    }

    /** Glowing sun with slowly rotating light rays. */
    private void drawSun(Canvas c, int w, int h, float t, float par) {
        float sx = w * 0.8f, sy = h * 0.1f - par * 0.4f;
        float pulse = 1f + 0.05f * (float) Math.sin(t * 1.1f);
        glow(c, sunGlow, sx, sy, w * 0.55f * pulse);

        paint.setShader(null);
        paint.setColor(Color.WHITE);
        float len = h * 0.9f;
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36 + t * 4);
            double spread = Math.toRadians(3.5);
            path.reset();
            path.moveTo(sx, sy);
            path.lineTo(sx + (float) Math.cos(a - spread) * len, sy + (float) Math.sin(a - spread) * len);
            path.lineTo(sx + (float) Math.cos(a + spread) * len, sy + (float) Math.sin(a + spread) * len);
            path.close();
            paint.setAlpha(12);
            c.drawPath(path, paint);
        }
        paint.setColor(0xCCFFF8E1);
        c.drawCircle(sx, sy, 24 * dp, paint);
    }

    private void drawMotes(Canvas c, int h, float t) {
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < count; i++) {
            float y = h - (py[i] + t * speed[i] * 22 * dp) % h;
            float x = px[i] + (float) Math.sin(t * 0.6f + phase[i]) * 12 * dp;
            paint.setAlpha((int) (40 + 100 * Math.abs(Math.sin(t + phase[i]))));
            c.drawCircle(x, y, (0.8f + size[i] * 1.6f) * dp, paint);
        }
    }

    private void drawClouds(Canvas c, int w, int h, float t, float par) {
        for (int i = 0; i < count; i++) {
            float r = w * (0.35f + size[i] * 0.3f);
            float x = (px[i] + t * speed[i] * 10 * dp) % (w + 2 * r) - r;
            glow(c, softWhite, x, py[i] * 0.5f - par * 0.5f, r);
        }
    }

    /** Rain in three depth layers (far = thin, slow, faint). */
    private void drawRain(Canvas c, int h, float t, float intensity) {
        stroke.setShader(null);
        stroke.setColor(0xFFAEE4FF);
        for (int i = 0; i < count; i++) {
            float depth = 0.5f + layer[i] * 0.25f;
            float len = (14 + 14 * depth) * dp;
            float y = (py[i] + t * speed[i] * h * 0.8f * intensity * depth) % (h + len) - len;
            float x = px[i] - y * 0.1f;
            stroke.setStrokeWidth((0.6f + depth) * dp);
            stroke.setAlpha((int) (50 + 110 * depth * size[i]));
            c.drawLine(x, y, x - len * 0.1f, y + len, stroke);
        }
    }

    private void drawSnow(Canvas c, int h, float t) {
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < count; i++) {
            float depth = 0.5f + layer[i] * 0.25f;
            float y = (py[i] + t * speed[i] * 50 * dp * depth) % h;
            float x = px[i] + (float) Math.sin(t * 0.8f + phase[i]) * 16 * dp * depth;
            paint.setAlpha((int) (90 + 140 * depth));
            c.drawCircle(x, y, (1f + size[i] * 2.5f) * depth * dp, paint);
        }
    }

    /** Lightning: a jagged bolt plus a screen flash every few seconds. */
    private void drawLightning(Canvas c, int w, int h, float t) {
        if (t > nextFlash) {
            flash = 1f;
            boltX = w * (0.2f + (float) Math.random() * 0.6f);
            nextFlash = t + 3f + (float) Math.random() * 5f;
        }
        if (flash < 0.02f) return;

        paint.setShader(null);
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (flash * 90));
        c.drawRect(0, 0, w, h, paint);

        Random r = new Random((long) (nextFlash * 1000));
        path.reset();
        float x = boltX, y = 0;
        path.moveTo(x, y);
        while (y < h * 0.55f) {
            y += (20 + r.nextInt(30)) * dp;
            x += (r.nextFloat() - 0.5f) * 50 * dp;
            path.lineTo(x, y);
        }
        stroke.setShader(null);
        stroke.setColor(0xFFE1D5FF);
        stroke.setStrokeWidth(6 * dp);
        stroke.setAlpha((int) (flash * 60));
        c.drawPath(path, stroke);
        stroke.setStrokeWidth(2 * dp);
        stroke.setAlpha((int) (flash * 255));
        c.drawPath(path, stroke);
        flash *= 0.9f;
    }
}
