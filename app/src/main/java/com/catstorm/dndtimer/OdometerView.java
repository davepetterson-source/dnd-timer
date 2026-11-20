package com.catstorm.dndtimer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A rolling drum, like a car odometer: drag it and the choices roll past a lit window. */
class OdometerView extends View {

    interface Listener {
        void onSelected(int index);
    }

    private static final float STEP_DEG = 24f;

    private final String[] labels;
    private final float density;
    private final float rowPx;
    private final float radiusPx;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float pos;
    private int selected;
    private Listener listener;
    private ValueAnimator anim;
    private VelocityTracker vt;
    private float downY;
    private float startPos;
    private boolean dragging;

    OdometerView(Context c, String[] labels, int selected) {
        super(c);
        this.labels = labels;
        this.selected = selected;
        this.pos = selected;
        density = c.getResources().getDisplayMetrics().density;
        rowPx = 28f * density;
        radiusPx = 66f * density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(density);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getSelected() {
        return selected;
    }

    /** Move the drum to an index (animated). */
    void setSelected(int index) {
        index = Math.max(0, Math.min(labels.length - 1, index));
        if (index != selected || (!dragging && Math.abs(pos - index) > 0.01f && anim == null)) {
            selected = index;
            rollTo(index);
        }
    }

    private void rollTo(float target) {
        if (anim != null) {
            anim.cancel();
        }
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration(Math.min(450, 120 + (long) (Math.abs(target - pos) * 90)));
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void choose(int index) {
        index = Math.max(0, Math.min(labels.length - 1, index));
        boolean changed = index != selected;
        selected = index;
        rollTo(index);
        if (changed && listener != null) {
            listener.onSelected(index);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (anim != null) {
                    anim.cancel();
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                downY = e.getY();
                startPos = pos;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                vt.addMovement(e);
                float dy = downY - e.getY();
                if (!dragging && Math.abs(dy) > 6f * density) {
                    dragging = true;
                }
                if (dragging) {
                    pos = Math.max(0, Math.min(labels.length - 1, startPos + dy / rowPx));
                    int near = Math.round(pos);
                    if (near != selected) {
                        selected = near;
                        if (listener != null) {
                            listener.onSelected(near);
                        }
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (dragging) {
                    vt.computeCurrentVelocity(1000);
                    float rowsPerSec = -vt.getYVelocity() / rowPx;
                    choose(Math.round(pos + rowsPerSec * 0.18f));
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    choose(selected + Math.round((e.getY() - getHeight() / 2f) / rowPx));
                }
                vt.recycle();
                vt = null;
                dragging = false;
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // drum body
        fill.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0xFF070C24, 0xFF111B48, 0xFF111B48, 0xFF070C24},
                new float[]{0f, 0.16f, 0.84f, 1f}, Shader.TileMode.CLAMP));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, fill);
        fill.setShader(null);

        // lit window
        fill.setColor(0x2E786EFF);
        rect.set(4 * density, cy - 16 * density, w - 4 * density, cy + 16 * density);
        canvas.drawRoundRect(rect, 4 * density, 4 * density, fill);
        line.setColor(0x66FFFFFF);
        canvas.drawLine(rect.left, rect.top, rect.right, rect.top, line);
        canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, line);

        // rolling numbers
        for (int i = 0; i < labels.length; i++) {
            float d = i - pos;
            float a = d * STEP_DEG;
            if (Math.abs(a) >= 85f) {
                continue;
            }
            double rad = Math.toRadians(a);
            float cos = (float) Math.cos(rad);
            float y = cy + radiusPx * (float) Math.sin(rad);
            float ad = Math.min(Math.abs(d), 2f);
            text.setTextSize((21f - ad * 2.5f) * density);
            text.setColor(Math.abs(d) < 0.5f ? 0xFFFFFFFF : 0xFFAAB6E0);
            text.setAlpha((int) (255 * Math.pow(Math.max(0f, cos), 1.3)));
            canvas.save();
            canvas.translate(cx, y);
            canvas.scale(1f, Math.max(0.05f, cos));
            canvas.drawText(labels[i], 0, 7.5f * density, text);
            canvas.restore();
        }

        // top and bottom shading, so it reads as a curved drum
        float sh = 46 * density;
        fill.setShader(new LinearGradient(0, 0, 0, sh, 0xF7050818, 0x00050818, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, sh, fill);
        fill.setShader(new LinearGradient(0, h - sh, 0, h, 0x00050818, 0xF7050818, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h - sh, w, h, fill);
        fill.setShader(null);

        line.setColor(0x59829AE6);
        rect.set(density / 2, density / 2, w - density / 2, h - density / 2);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, line);
    }
}
