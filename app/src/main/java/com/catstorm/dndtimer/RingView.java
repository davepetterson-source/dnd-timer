package com.catstorm.dndtimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The countdown ring, drawn as 60 little segments. fraction = how much of it is still lit (0..1). */
class RingView extends View {

    private static final int SEGMENTS = 60;

    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF seg = new RectF();
    private final float density;
    private float fraction = 0f;

    RingView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        off.setColor(0xFF2B3C80);
        glow.setColor(0x405B9BFF);
    }

    void setFraction(float f) {
        fraction = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float k = getWidth() / 200f;               // design drawn on a 200 unit circle
        float w = 5.2f * k;
        float len = 13f * k;
        float top = 6f * k;
        float rad = w / 2f;
        int lit = fraction <= 0f ? 0 : (int) Math.ceil(fraction * SEGMENTS - 0.001f);
        for (int i = 0; i < SEGMENTS; i++) {
            canvas.save();
            canvas.rotate(i * (360f / SEGMENTS), cx, cy);
            seg.set(cx - w / 2f, top, cx + w / 2f, top + len);
            if (i < lit) {
                float t = i / (SEGMENTS - 1f);
                int r = (int) (91 + (125 - 91) * t);
                int g = (int) (155 + (107 - 155) * t);
                on.setColor(0xFF000000 | (r << 16) | (g << 8) | 255);
                RectF big = new RectF(seg.left - 2f * density, seg.top - 1f * density,
                        seg.right + 2f * density, seg.bottom + 1f * density);
                canvas.drawRoundRect(big, rad + 2f * density, rad + 2f * density, glow);
                canvas.drawRoundRect(seg, rad, rad, on);
            } else {
                canvas.drawRoundRect(seg, rad, rad, off);
            }
            canvas.restore();
        }
    }
}
