package com.catstorm.dndtimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** The glowing countdown ring. fraction = how much of the ring is lit (0..1). */
class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;
    private final float stroke;
    private float fraction = 0f;

    RingView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        stroke = 12f * density;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(0xFF2B3C80);

        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(stroke + 8f * density);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setColor(0x405B9BFF);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
    }

    void setFraction(float f) {
        fraction = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float pad = stroke / 2f + 6f * density;
        oval.set(pad, pad, w - pad, h - pad);
        arc.setShader(new LinearGradient(0, 0, w, h, 0xFF5B9BFF, 0xFF7D6BFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawOval(oval, track);
        if (fraction > 0f) {
            canvas.drawArc(oval, -90f, 360f * fraction, false, glow);
            canvas.drawArc(oval, -90f, 360f * fraction, false, arc);
        }
    }
}
