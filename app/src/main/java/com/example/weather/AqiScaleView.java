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

/** AQI scale bar (clean → hazardous) with a marker. Always shown next to a text category. */
public class AqiScaleView extends View {

    private static final int[] COLORS = {0xFF00E400, 0xFFFFDE33, 0xFFFF9933, 0xFFFF4D4D, 0xFFB266FF, 0xFFC2185B};

    private float badness = 0f;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public AqiScaleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
    }

    /** 0 = clean air, 1 = hazardous. */
    public void setBadness(float b) {
        badness = Math.max(0f, Math.min(1f, b));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), cy = getHeight() / 2f, half = 4 * dp, r = 8 * dp;

        paint.setColor(Color.WHITE);
        paint.setShader(new LinearGradient(r, 0, w - r, 0, COLORS, null, Shader.TileMode.CLAMP));
        rect.set(r, cy - half, w - r, cy + half);
        c.drawRoundRect(rect, half, half, paint);

        float x = r + badness * (w - 2 * r);
        paint.setShader(null);
        paint.setColor(0xFF1A1446);
        c.drawCircle(x, cy, r, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(x, cy, r - 2.5f * dp, paint);
    }
}
