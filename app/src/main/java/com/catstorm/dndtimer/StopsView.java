package com.catstorm.dndtimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/** The duration slider: a row of stops (15m ... 6h, infinity). Tap or drag to choose. */
class StopsView extends View {

    interface Listener {
        void onChanged(int index);
    }

    private final String[] labels;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int selected;
    private boolean locked;
    private Listener listener;

    StopsView(Context context, String[] labels, int selected) {
        super(context);
        this.labels = labels;
        this.selected = selected;
        density = context.getResources().getDisplayMetrics().density;

        line.setStrokeWidth(4f * density);
        line.setStrokeCap(Paint.Cap.ROUND);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f,
                context.getResources().getDisplayMetrics()));
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setSelected(int index) {
        if (index != selected) {
            selected = index;
            invalidate();
        }
    }

    void setLocked(boolean value) {
        locked = value;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(0, widthSpec), Math.round(56f * density));
    }

    private float centerX(int i) {
        return (i + 0.5f) * getWidth() / labels.length;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = 17f * density;
        int n = labels.length;

        line.setColor(0xFF2D3A68);
        canvas.drawLine(centerX(0), cy, centerX(n - 1), cy, line);
        line.setColor(0xFF7B70FF);
        canvas.drawLine(centerX(0), cy, centerX(selected), cy, line);

        for (int i = 0; i < n; i++) {
            float cx = centerX(i);
            if (i == selected) {
                dot.setColor(0xFFFFFFFF);
                canvas.drawCircle(cx, cy, 12f * density, dot);
                dot.setColor(0xFF6E62FF);
                canvas.drawCircle(cx, cy, 9f * density, dot);
            } else {
                dot.setColor(i < selected ? 0xFF8F84FF : 0xFF3B4878);
                canvas.drawCircle(cx, cy, 5.5f * density, dot);
            }
            boolean sel = i == selected;
            text.setColor(sel ? 0xFFFFFFFF : 0xFFA9B6E0);
            text.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            canvas.drawText(labels[i], cx, 34f * density + 14f * density, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) {
            return false;
        }
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int idx = (int) (e.getX() / getWidth() * labels.length);
            idx = Math.max(0, Math.min(labels.length - 1, idx));
            if (idx != selected) {
                selected = idx;
                invalidate();
                if (listener != null) {
                    listener.onChanged(idx);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return action == MotionEvent.ACTION_CANCEL;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
