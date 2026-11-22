package com.catstorm.dndtimer;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A glassy, tapered corner tile whose inner edge curves around the timer circle. */
class TileView extends FrameLayout {

    static final float PAD = 8f;   // room around the shape for the glow, in dp

    private final Path path = new Path();
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final int[] fill;
    private final int[] edge;
    private final boolean up;     // gradient runs bottom to top
    private final float w;
    private final float h;

    TileView(Context c, float w, float h, boolean flipX, boolean flipY, float ya, float xb,
             float ccx, float ccy, float minR, int[] fill, int[] edge, int glow) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        this.w = w;
        this.h = h;
        this.fill = fill;
        this.edge = edge;
        this.up = flipY;
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClipChildren(false);
        setClipToPadding(false);

        float cx = flipX ? w - ccx : ccx;
        float cy = flipY ? h - ccy : ccy;
        List<float[]> pts = new ArrayList<>();
        Map<Integer, Float> corners = new HashMap<>();
        build(w, h, ya, xb, cx, cy, minR, pts, corners);
        for (float[] p : pts) {
            if (flipX) {
                p[0] = w - p[0];
            }
            if (flipY) {
                p[1] = h - p[1];
            }
        }
        rounded(pts, corners);

        fillP.setStyle(Paint.Style.FILL);
        edgeP.setStyle(Paint.Style.STROKE);
        edgeP.setStrokeWidth(1.5f * d);
        glowP.setStyle(Paint.Style.STROKE);
        glowP.setStrokeWidth(3f * d);
        glowP.setColor(glow);
        glowP.setMaskFilter(new BlurMaskFilter(6f * d, BlurMaskFilter.Blur.NORMAL));
    }

    /** Positions a child in tile coordinates (dp). */
    void place(View v, float x, float y, float cw, float ch) {
        LayoutParams lp = new LayoutParams(Math.round(cw * d), Math.round(ch * d));
        lp.leftMargin = Math.round((x + PAD) * d);
        lp.topMargin = Math.round((y + PAD) * d);
        addView(v, lp);
    }

    private static void build(float w, float h, float ya, float xb, float ccx, float ccy, float minR,
                              List<float[]> pts, Map<Integer, Float> corners) {
        float p1x = w, p1y = ya, p2x = xb, p2y = h;
        float mx = (p1x + p2x) / 2f, my = (p1y + p2y) / 2f;
        float nx = -(p2y - p1y), ny = p2x - p1x;
        float ln = (float) Math.hypot(nx, ny);
        nx /= ln;
        ny /= ln;
        if (nx * (0 - mx) + ny * (0 - my) < 0) {
            nx = -nx;
            ny = -ny;
        }
        float k = 4;
        List<float[]> curve;
        while (true) {
            float qx = mx + nx * k, qy = my + ny * k;
            curve = new ArrayList<>();
            float best = Float.MAX_VALUE;
            for (int i = 0; i <= 24; i++) {
                float t = i / 24f;
                float x = (1 - t) * (1 - t) * p1x + 2 * (1 - t) * t * qx + t * t * p2x;
                float y = (1 - t) * (1 - t) * p1y + 2 * (1 - t) * t * qy + t * t * p2y;
                curve.add(new float[]{x, y});
                best = Math.min(best, (float) Math.hypot(x - ccx, y - ccy));
            }
            if (best >= minR || k > 60) {
                break;
            }
            k += 1;
        }
        pts.add(new float[]{w * 0.5f, 0});
        pts.add(new float[]{w, 0});
        pts.addAll(curve);
        pts.add(new float[]{0, h});
        pts.add(new float[]{0, 0});
        int n = curve.size();
        corners.put(1, 22f);
        corners.put(2, 16f);
        corners.put(n + 1, 18f);
        corners.put(n + 2, 20f);
        corners.put(n + 3, 22f);
    }

    private void rounded(List<float[]> pts, Map<Integer, Float> corners) {
        int m = pts.size();
        float[] seg = new float[m];
        float[] cum = new float[m + 1];
        for (int i = 0; i < m; i++) {
            float[] a = pts.get(i), b = pts.get((i + 1) % m);
            seg[i] = (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
            cum[i + 1] = cum[i] + seg[i];
        }
        float total = cum[m];
        Map<Integer, float[]> ex = new HashMap<>();
        for (Map.Entry<Integer, Float> e : corners.entrySet()) {
            ex.put(e.getKey(), new float[]{cum[e.getKey()] - e.getValue(), cum[e.getKey()] + e.getValue()});
        }
        float o = PAD * d;
        path.reset();
        path.moveTo(pts.get(0)[0] * d + o, pts.get(0)[1] * d + o);
        for (int i = 1; i < m; i++) {
            if (corners.containsKey(i)) {
                float[] lo = at(pts, seg, cum, total, ex.get(i)[0]);
                float[] hi = at(pts, seg, cum, total, ex.get(i)[1]);
                path.lineTo(lo[0] * d + o, lo[1] * d + o);
                path.quadTo(pts.get(i)[0] * d + o, pts.get(i)[1] * d + o, hi[0] * d + o, hi[1] * d + o);
            } else {
                boolean inside = false;
                for (Map.Entry<Integer, float[]> e : ex.entrySet()) {
                    if (e.getKey() != i && cum[i] > e.getValue()[0] && cum[i] < e.getValue()[1]) {
                        inside = true;
                    }
                }
                if (!inside) {
                    path.lineTo(pts.get(i)[0] * d + o, pts.get(i)[1] * d + o);
                }
            }
        }
        path.close();
    }

    private static float[] at(List<float[]> pts, float[] seg, float[] cum, float total, float s) {
        s = ((s % total) + total) % total;
        int m = pts.size();
        for (int i = 0; i < m; i++) {
            if (cum[i + 1] >= s) {
                float t = seg[i] == 0 ? 0 : (s - cum[i]) / seg[i];
                float[] a = pts.get(i), b = pts.get((i + 1) % m);
                return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
            }
        }
        return pts.get(0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float o = PAD * d;
        float y0 = up ? o + h * d : o;
        float y1 = up ? o : o + h * d;
        fillP.setShader(new LinearGradient(0, y0, 0, y1, fill, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        edgeP.setShader(new LinearGradient(0, y0, 0, y1, edge, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, glowP);
        canvas.drawPath(path, fillP);
        canvas.drawPath(path, edgeP);
    }
}
