package com.catstorm.dndtimer;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** The timer circle: a glowing disc with fine tick marks, or thick segments that switch off as time runs out. */
class RingView extends View {

    private static final int SEGMENTS = 60;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private float fraction = 0f;
    private boolean active = false;

    RingView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * d);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(3f * d);
        glow.setMaskFilter(new BlurMaskFilter(8f * d, BlurMaskFilter.Blur.NORMAL));
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(1.2f * d);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(0x9996AFFF);
        segGlow.setMaskFilter(new BlurMaskFilter(3f * d, BlurMaskFilter.Blur.NORMAL));
    }

    void setFraction(float f, boolean running) {
        fraction = Math.max(0f, Math.min(1f, f));
        active = running;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = getWidth() / 264f * 1f;          // drawn on a 264 unit square
        float c = getWidth() / 2f;
        float u = s;                                 // one design unit in px

        // outer disc
        disc.setShader(new RadialGradient(c, c, 132 * u, new int[]{0xFF0A1032, 0xFF0C1440, 0xFF18236A, 0xFF0F1850},
                new float[]{0f, 0.78f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        glow.setColor(0x995082FF);
        canvas.drawCircle(c, c, 131 * u, glow);
        canvas.drawCircle(c, c, 132 * u, disc);
        rim.setColor(0xE678A0FF);
        canvas.drawCircle(c, c, 131.25f * u, rim);

        // inner disc
        disc.setShader(new RadialGradient(c, c * 0.98f, 108 * u * 1.35f, new int[]{0xFF121C56, 0xFF080D2C},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(c, c, 108 * u, disc);
        rim.setColor(0x8C6E91FF);
        canvas.drawCircle(c, c, 107.25f * u, rim);

        if (!active) {
            for (int i = 0; i < 120; i++) {
                boolean longTick = i % 5 == 0;
                float r1 = (longTick ? 111 : 114) * u;
                float r2 = (longTick ? 127 : 124) * u;
                canvas.save();
                canvas.rotate(i * 3f, c, c);
                canvas.drawLine(c, c - r2, c, c - r1, tick);
                canvas.restore();
            }
        } else {
            int lit = fraction <= 0f ? 0 : (int) Math.ceil(fraction * SEGMENTS - 0.001f);
            for (int i = 0; i < SEGMENTS; i++) {
                canvas.save();
                canvas.rotate(i * 6f, c, c);
                r.set(c - 3 * u, 3 * u, c + 3 * u, 22 * u);
                if (i < lit) {
                    float t = i / 59f;
                    int red = (int) (60 + 130 * t);
                    int green = (int) (200 - 120 * t);
                    int col = 0xFF000000 | (red << 16) | (green << 8) | 255;
                    segGlow.setColor((col & 0x00FFFFFF) | 0xCC000000);
                    canvas.drawRoundRect(r, 2.5f * u, 2.5f * u, segGlow);
                    seg.setColor(col);
                    canvas.drawRoundRect(r, 2.5f * u, 2.5f * u, seg);
                } else {
                    seg.setColor(0xFF25305F);
                    canvas.drawRoundRect(r, 2.5f * u, 2.5f * u, seg);
                }
                canvas.restore();
            }
        }
    }
}
