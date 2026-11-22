package com.catstorm.dndtimer;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB8C6F2;
    private static final int PANEL_FILL = 0x99101A46;
    private static final int PANEL_STROKE = 0x47829AE6;

    private static final String[] LABELS = {
            "1min", "5min", "10min", "15min", "30min", "45min", "1hr", "1hr 15min", "1hr 30min",
            "2hrs", "3hrs", "4hrs", "5hrs", "6hrs", "7hrs", "8hrs", "Until I stop"
    };
    private static final String[] START_LABELS = buildStartLabels();

    private static String[] buildStartLabels() {
        String[] a = new String[97];
        a[0] = "Now";
        for (int i = 0; i < 96; i++) {
            a[i + 1] = pad2(i / 4) + ":" + pad2((i % 4) * 15);
        }
        return a;
    }
    private static final int[] MINUTES = {1, 5, 10, 15, 30, 45, 60, 75, 90, 120, 180, 240, 300, 360, 420, 480, -1};
    private static final int DEFAULT_IDX = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private NotificationManager nm;
    private int selMode;
    private int selIdx;
    private boolean selMusic;

    private static final float CX = 195f;
    private static final float CY = 174f;

    private FrameLayout rootView;
    private FrameLayout stage;
    private RingView ring;
    private TextView clockView;
    private TextView ringLabel;
    private TextView durCaption;
    private OdometerView durWheel;
    private OdometerView stWheel;
    private FrameLayout stWheelBox;
    private TextView leftTitle;
    private TextView rightTitle;
    private TextView startVal;
    private TextView startDay;
    private TextView endVal;
    private TextView endDay;
    private TextView clockCaptionBox;
    private LinearLayout clockBox;
    private LinearLayout cardDnd;
    private LinearLayout cardSilent;
    private LinearLayout goBar;
    private ImageView goIcon;
    private TextView goText;
    private TileView[] tiles = new TileView[4];
    private TextView[] zeds;
    private android.animation.ObjectAnimator[] zedAnims;
    private boolean zedsRunning = false;
    private int[] zedBaseX = {0, 0, 0};
    private int selDays = 0;
    private int selSt = 0;

    // Settings page
    private ScrollView settingsView;
    private LinearLayout presetList;
    private LinearLayout chipRow;
    private TextView repHint;
    private TextView[] setChips = new TextView[7];
    private Switch setMusic;
    private android.widget.CompoundButton.OnCheckedChangeListener setMusicListener;
    private TextView defDnd;
    private TextView defSilent;
    private TextView aboutSub;

    private static final String[] PRESET_NAMES = {"Meditation", "Kip", "Sleep"};
    private static final int[] PRESET_DEF_MIN = {15, 60, 480};
    private static final int[] PRESET_DEF_MODE = {Dnd.SILENT, Dnd.DND, Dnd.SILENT};
    private static final boolean[] PRESET_DEF_MUSIC = {true, false, false};
    private static final String[] PRESET_EMOJI = {"🧘", "🛏️", "😴"};
    private static final int[] PRESET_COL = {0x887B3FE0, 0x883A8DFF, 0x884A6DE0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1233);
        getWindow().setNavigationBarColor(0xFF070B21);

        nm = getSystemService(NotificationManager.class);
        SharedPreferences p = Dnd.prefs(this);
        selMode = p.getInt(Dnd.KEY_SEL_MODE, p.getInt("defMode", Dnd.DND)) == Dnd.SILENT ? Dnd.SILENT : Dnd.DND;
        selIdx = Math.max(0, Math.min(LABELS.length - 1, p.getInt(Dnd.KEY_SEL_IDX_V2, DEFAULT_IDX)));
        selMusic = p.getBoolean(Dnd.KEY_SEL_MUSIC, false);
        selDays = p.getInt(Dnd.KEY_REP_MASK, 0);

        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (zedsRunning) {
            stopZeds();
        }
    }

    // ------------------------------------------------------------------ UI

    private static int argb(float a, int r, int g, int b) {
        return (Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private TextView lab(String s, float dip, int color, boolean bold, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, dip);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setGravity(gravity);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private void put(FrameLayout p, View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? WRAP : dp(w), h < 0 ? WRAP : dp(h));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        p.addView(v, lp);
    }

    private ImageView ico(int res, float size, int tint) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        if (tint != 0) {
            v.setColorFilter(tint);
        }
        return v;
    }

    private TextView[] tileDur = new TextView[3];
    private ImageView[][] tileStatus = new ImageView[3][3];

    private View buildUi() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(0xFF090F2C);
        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF090F2C);

        // Moonlit lake, fading into the background colour
        FrameLayout bgWrap = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg_moon);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        put(bgWrap, bg, -127, -348, 567, 850);
        View overlay = new View(this);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1A090F2C, 0x66090F2C, 0xD9090F2C, 0xFF090F2C}));
        put(bgWrap, overlay, 0, 0, 390, 520);
        put(stage, bgWrap, 0, 0, 390, 520);

        // ---- corner tiles
        int[][][] tf = {
                {{argb(.92f, 178, 110, 255), argb(.72f, 128, 62, 228), argb(.55f, 84, 36, 180)},
                        {0xFFE6CAFF, 0xFFA874FF, 0xFF7A46E8}},
                {{argb(.85f, 110, 190, 255), argb(.65f, 58, 128, 240), argb(.5f, 34, 78, 200)},
                        {0xFFD4ECFF, 0xFF78B8FF, 0xFF3F7FF0}},
                {{argb(.62f, 56, 70, 170), argb(.5f, 40, 52, 140), argb(.42f, 30, 38, 110)},
                        {0xFFA9B8FF, 0xFF6C84F0, 0xFF4B62D8}},
                {{argb(.65f, 60, 84, 200), argb(.52f, 44, 60, 160), argb(.42f, 32, 40, 120)},
                        {0xFFB4C4FF, 0xFF7590FF, 0xFF4D66E0}}};
        int[] glows = {argb(.75f, 170, 100, 255), argb(.7f, 80, 160, 255), argb(.5f, 90, 120, 255), argb(.55f, 90, 130, 255)};
        float[][] geo = {   // x0, y0, w, h, ccx(content centre x)
                {9, 10, 89, 127, 38}, {292, 10, 89, 127, 45}, {9, 200, 98, 132, 33}, {283, 200, 98, 132, 52}};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean flipX = i == 1 || i == 3;
            boolean flipY = i >= 2;
            float x0 = geo[i][0], y0 = geo[i][1], w = geo[i][2], h = geo[i][3], ccx = geo[i][4];
            float ya = 28f, xb = i < 2 ? 43f : 40f;
            TileView t = new TileView(this, w, h, flipX, flipY, ya, xb, CX - x0, CY - y0, 137f,
                    tf[i][0], tf[i][1], glows[i]);
            tiles[i] = t;
            float top0 = i < 2 ? 30 : 45;
            float[] rows = i < 2 ? new float[]{30, 60, 79, 98} : new float[]{45, 72, 90, 111};
            int iconRes = i == 0 ? R.drawable.ic_med : i == 1 ? R.drawable.ic_bed : i == 2 ? R.drawable.ic_gear : R.drawable.ic_face;
            int tint = i == 0 ? 0xFFF3DCFF : i == 1 ? 0xFF8FD3FF : i == 2 ? 0xFFFFFFFF : 0;
            float isz = i == 0 ? 34 : i == 1 ? 38 : i == 2 ? 32 : 34;
            ImageView ic = ico(iconRes, isz, tint);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.place(ic, ccx - isz / 2f, rows[0] - isz / 2f, isz, isz);
            String nm = i == 2 ? "Setup" : PRESET_NAMES[i < 2 ? i : 2];
            TextView name = lab(nm, 13, WHITE, true, Gravity.CENTER);
            t.place(name, ccx - 45, rows[1] - 8, 90, 16);
            if (i == 2) {
                String[] sub = {"Presets", "Repeat", "& more"};
                for (int j = 0; j < 3; j++) {
                    t.place(lab(sub[j], 11, 0xFFD6DEFA, false, Gravity.CENTER), ccx - 40, 84 + 13 * j + 0f, 80, 13);
                }
            } else {
                int p = i < 2 ? i : 2;
                tileDur[p] = lab("", 11, 0xFFE2E8FF, false, Gravity.CENTER);
                t.place(tileDur[p], ccx - 40, rows[2] - 6, 80, 13);
                LinearLayout st = new LinearLayout(this);
                st.setGravity(Gravity.CENTER);
                int[] res = {R.drawable.ic_speaker_off, R.drawable.ic_bell, R.drawable.ic_music};
                for (int j = 0; j < 3; j++) {
                    ImageView s = ico(res[j], 14, 0xFFEEF0FF);
                    tileStatus[p][j] = s;
                    st.addView(s, lin(dp(14), dp(14), 0f, j == 0 ? 0 : 5, 0, 0, 0));
                }
                t.place(st, ccx - 40, rows[3] - 8, 80, 16);
                t.setOnClickListener(v -> applyPreset(p));
            }
            if (i == 2) {
                t.setOnClickListener(v -> showSettings());
            }
            put(stage, t, x0 - TileView.PAD, y0 - TileView.PAD, w + 2 * TileView.PAD, h + 2 * TileView.PAD);
        }
        // the sleep tile is the fourth one
        tiles[3].setOnClickListener(v -> applyPreset(2));

        // ---- timer circle
        ring = new RingView(this);
        put(stage, ring, CX - 132, CY - 132, 264, 264);

        durCaption = lab("DURATION", 12, 0xFFCDD5F5, true, Gravity.CENTER);
        durCaption.setLetterSpacing(0.1f);
        put(stage, durCaption, CX - 80, 80, 160, 14);

        durWheel = new OdometerView(this, LABELS, selIdx);
        durWheel.setPlain(11f, 14f, new int[]{0xFF4A3FD0, 0xFF2F289A}, new float[]{0f, 1f}, false, 0x8C968CFF, 0xFF9FABD8);
        durWheel.setBaseSp(27);
        durWheel.setPitchDp(44);
        durWheel.setFitDp(170);
        durWheel.setWindowHalfDp(23);
        durWheel.setListener(index -> {
            selIdx = index;
            Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_IDX_V2, index).apply();
            refresh();
        });
        put(stage, durWheel, CX - 100, CY - 72, 200, 148);

        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.CENTER_HORIZONTAL);
        clockView = lab("", 40, WHITE, true, Gravity.CENTER);
        clockView.setLetterSpacing(0.012f);
        ringLabel = lab("", 15, 0xFFC4CDF0, false, Gravity.CENTER);
        clockBox.addView(clockView, new LinearLayout.LayoutParams(MATCH, dp(40)));
        clockBox.addView(ringLabel, lin(MATCH, dp(20), 0f, 0, 2, 0, 0));
        put(stage, clockBox, CX - 100, CY - 30, 200, 64);

        // sleeping zeds
        zeds = new TextView[3];
        zedAnims = new android.animation.ObjectAnimator[3];
        float[][] zp = {{0, 40, 14}, {12, 24, 19}, {26, 4, 24}};
        for (int i = 0; i < 3; i++) {
            TextView z = lab(i == 0 ? "z" : "Z", zp[i][2], 0xFFCFD8FF, true, Gravity.CENTER);
            z.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.ITALIC));
            z.setShadowLayer(dp(5), 0, 0, 0xCC8CA0FF);
            z.setAlpha(0f);
            z.setVisibility(View.GONE);
            put(stage, z, CX + 40 + zp[i][0], CY - 104 + zp[i][1], 30, 30);
            zeds[i] = z;
        }

        // ---- Start at / Finishes cards
        int[] cardBg = {argb(.72f, 22, 32, 84), argb(.72f, 12, 18, 56)};
        int cardEdge = argb(.32f, 120, 145, 235);
        FrameLayout leftCard = new FrameLayout(this);
        leftCard.setBackground(gradRect(cardBg, cardEdge, 1, 16));
        put(stage, leftCard, 10, 347, 180, 138);
        ImageView lic = ico(R.drawable.ic_clock, 22, 0xFFE6EBFF);
        put(leftCard, lic, 12, 13, 22, 22);
        leftTitle = lab("Start at", 15, WHITE, false, Gravity.CENTER_VERTICAL);
        put(leftCard, leftTitle, 43, 13, 120, 22);
        stWheelBox = new FrameLayout(this);
        stWheelBox.setBackground(rect(0x8C060A22, 0, 0, 12));
        stWheel = new OdometerView(this, START_LABELS, selSt);
        stWheel.setPlain(0f, 10f, new int[]{argb(.15f, 50, 70, 190), argb(.7f, 60, 84, 210), argb(.7f, 60, 84, 210), argb(.15f, 50, 70, 190)},
                new float[]{0f, 0.3f, 0.7f, 1f}, true, 0, 0xFF9FABD8);
        stWheel.setBaseSp(19);
        stWheel.setPitchDp(30);
        stWheel.setWindowHalfDp(15);
        stWheel.setListener(index -> {
            selSt = index;
            refresh();
        });
        stWheelBox.addView(stWheel, new FrameLayout.LayoutParams(MATCH, MATCH));
        put(leftCard, stWheelBox, 8, 48, 164, 88);
        startVal = lab("", 26, WHITE, true, Gravity.CENTER);
        startDay = lab("", 15, 0xFFC4CDF0, false, Gravity.CENTER);
        put(leftCard, startVal, 0, 56, 180, 32);
        put(leftCard, startDay, 0, 92, 180, 20);

        FrameLayout rightCard = new FrameLayout(this);
        rightCard.setBackground(gradRect(cardBg, cardEdge, 1, 16));
        put(stage, rightCard, 200, 347, 180, 138);
        put(rightCard, ico(R.drawable.ic_flag_o, 22, 0xFFE6EBFF), 12, 13, 22, 22);
        rightTitle = lab("Finishes", 15, WHITE, false, Gravity.CENTER_VERTICAL);
        put(rightCard, rightTitle, 43, 13, 120, 22);
        endVal = lab("", 27, WHITE, true, Gravity.CENTER);
        endDay = lab("", 15, 0xFFC4CDF0, false, Gravity.CENTER);
        put(rightCard, endVal, 0, 56, 180, 32);
        put(rightCard, endDay, 0, 92, 180, 20);

        // ---- Start / End button
        goBar = new LinearLayout(this);
        goBar.setGravity(Gravity.CENTER);
        goIcon = ico(R.drawable.ic_play, 22, WHITE);
        goText = lab("", 17, WHITE, true, Gravity.CENTER);
        goText.setLetterSpacing(0.02f);
        goBar.addView(goIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        goBar.addView(goText, lin(WRAP, WRAP, 0f, 12, 0, 0, 0));
        goBar.setOnClickListener(v -> toggle());
        put(stage, goBar, 10, 500, 370, 58);

        // ---- mode buttons
        cardDnd = modeBtn(R.drawable.ic_bell, 0, "Do Not Disturb", 0xFFFFFFFF);
        cardSilent = modeBtn(R.drawable.ic_speaker_off, 1, "Silent", 0xFFD9B8FF);
        cardDnd.setOnClickListener(v -> chooseMode(Dnd.DND));
        cardSilent.setOnClickListener(v -> chooseMode(Dnd.SILENT));
        put(stage, cardDnd, 10, 584, 182, 64);
        put(stage, cardSilent, 198, 584, 182, 64);

        rootView.addView(stage, new FrameLayout.LayoutParams(dp(390), dp(690), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        rootView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float wpx = r - l;
            float hpx = b - t;
            float k = Math.min(wpx / dp(390), hpx / dp(690));
            stage.setPivotX(dp(195));
            stage.setPivotY(0);
            stage.setScaleX(k);
            stage.setScaleY(k);
            stage.setTranslationY(Math.max(0f, (hpx - dp(690) * k) / 2f));
        });

        settingsView = buildSettings();
        settingsView.setVisibility(View.GONE);
        rootView.addView(settingsView, new FrameLayout.LayoutParams(MATCH, MATCH));
        return rootView;
    }

    private GradientDrawable gradRect(int[] colors, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), stroke);
        }
        return g;
    }

    private LinearLayout modeBtn(int iconRes, int kind, String title, int tint) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        FrameLayout circle = new FrameLayout(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(dp(20));
        glow.setColors(kind == 0 ? new int[]{argb(.55f, 140, 190, 255), argb(0f, 80, 130, 255)}
                : new int[]{argb(.6f, 170, 110, 255), argb(0f, 120, 60, 220)});
        circle.setBackground(glow);
        circle.addView(ico(iconRes, 30, tint), new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        b.addView(circle, new LinearLayout.LayoutParams(dp(40), dp(40)));
        b.addView(lab(title, 15, WHITE, false, Gravity.CENTER_VERTICAL), lin(WRAP, WRAP, 0f, 6, 0, 0, 0));
        return b;
    }

    private void styleCards(int mode) {
        styleMode(cardDnd, mode == Dnd.DND, 0);
        styleMode(cardSilent, mode == Dnd.SILENT, 1);
    }

    private void styleMode(View b, boolean sel, int kind) {
        int[] c;
        int stroke;
        int glow;
        if (kind == 0) {
            c = sel ? new int[]{0xFF3F7DFF, 0xFF2350D8} : new int[]{argb(.45f, 40, 80, 200), argb(.45f, 24, 48, 140)};
            stroke = sel ? 0xFF8FB6FF : argb(.45f, 110, 150, 255);
            glow = 0xFF3C78FF;
        } else {
            c = sel ? new int[]{0xFF8A4FF0, 0xFF5E2CC0} : new int[]{argb(.5f, 96, 52, 180), argb(.5f, 60, 30, 130)};
            stroke = sel ? 0xFFD6A8FF : argb(.5f, 170, 120, 255);
            glow = 0xFFA05AFF;
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1.5f), stroke);
        b.setBackground(g);
        b.setElevation(sel ? dp(10) : 0);
        b.setOutlineSpotShadowColor(glow);
        b.setOutlineAmbientShadowColor(glow);
    }



    // ------------------------------------------------------------------ behaviour

    private boolean locked() {
        SharedPreferences p = Dnd.prefs(this);
        return p.getBoolean(Dnd.KEY_ACTIVE, false) || p.getBoolean(Dnd.KEY_SCHED, false);
    }

    private void chooseMode(int mode) {
        if (locked()) {
            return;
        }
        selMode = mode;
        Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_MODE, mode).apply();
        refresh();
    }

    private void toggle() {
        if (locked()) {
            Dnd.stop(this);
        } else {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            int mask = selSt > 0 ? selDays : 0;
            long startAt = selSt > 0 ? Dnd.nextStart(selSt, mask, System.currentTimeMillis()) : 0;
            if (Dnd.start(this, selMode, MINUTES[selIdx], selMusic, startAt, selSt, mask)) {
                Dnd.prefs(this).edit().putBoolean("startedNow", selSt == 0).apply();
            } else {
                Toast.makeText(this,
                        "Switch on “KipQuiet” under Do Not Disturb access, then come back and press Start",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            }
        }
        refresh();
    }

    private int presetMin(int p) {
        return Dnd.prefs(this).getInt("pMin" + p, PRESET_DEF_MIN[p]);
    }

    private int presetMode(int p) {
        return Dnd.prefs(this).getInt("pMode" + p, PRESET_DEF_MODE[p]);
    }

    private boolean presetMusic(int p) {
        return Dnd.prefs(this).getBoolean("pMusic" + p, PRESET_DEF_MUSIC[p]);
    }

    private int idxForMinutes(int m) {
        for (int i = 0; i < MINUTES.length; i++) {
            if (MINUTES[i] == m) {
                return i;
            }
        }
        return DEFAULT_IDX;
    }

    private void applyPreset(int p) {
        if (locked()) {
            return;
        }
        selIdx = idxForMinutes(presetMin(p));
        selMode = presetMode(p);
        selMusic = presetMusic(p);
        Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_IDX_V2, selIdx).putInt(Dnd.KEY_SEL_MODE, selMode)
                .putBoolean(Dnd.KEY_SEL_MUSIC, selMusic).apply();
        refresh();
    }

    private void refresh() {
        Dnd.expireIfDue(this);
        SharedPreferences p = Dnd.prefs(this);
        boolean active = p.getBoolean(Dnd.KEY_ACTIVE, false);
        boolean sched = !active && p.getBoolean(Dnd.KEY_SCHED, false);
        boolean idle = !active && !sched;
        long now = System.currentTimeMillis();
        boolean planned = selSt > 0;
        long start;
        long end;
        int minutes;
        if (active) {
            start = p.getLong(Dnd.KEY_START, 0);
            end = p.getLong(Dnd.KEY_END, 0);
            minutes = end == 0 ? -1 : (int) Math.round((end - start) / 60000.0);
            selMode = p.getInt(Dnd.KEY_MODE, selMode);
        } else if (sched) {
            start = p.getLong(Dnd.KEY_SCHED_START, 0);
            minutes = p.getInt(Dnd.KEY_SCHED_MIN, 60);
            end = minutes < 0 ? 0 : start + minutes * 60_000L;
            selMode = p.getInt(Dnd.KEY_SCHED_MODE, selMode);
        } else {
            minutes = MINUTES[selIdx];
            start = planned ? Dnd.nextStart(selSt, selDays, now) : now;
            end = minutes < 0 ? 0 : start + minutes * 60_000L;
        }
        if (!idle) {
            selDays = p.getInt(Dnd.KEY_REP_MASK, 0);
        }
        if (sched) {
            selMusic = p.getBoolean(Dnd.KEY_SCHED_MUSIC, selMusic);
        } else if (active) {
            selMusic = p.getBoolean("music", selMusic);
        }

        // locked while scheduled or running
        durWheel.setEnabled(idle);
        durWheel.setSelected(selIdx);
        durWheel.setVisibility(idle ? View.VISIBLE : View.GONE);
        durCaption.setVisibility(idle ? View.VISIBLE : View.GONE);
        clockBox.setVisibility(idle ? View.GONE : View.VISIBLE);
        stWheel.setEnabled(idle);
        stWheel.setSelected(selSt);
        boolean startedNow = active && p.getBoolean("startedNow", false);
        stWheelBox.setAlpha(idle ? 1f : 0.45f);
        stWheelBox.setVisibility(startedNow ? View.GONE : View.VISIBLE);
        startVal.setVisibility(startedNow ? View.VISIBLE : View.GONE);
        startDay.setVisibility(startedNow ? View.VISIBLE : View.GONE);
        leftTitle.setText(startedNow ? "Started" : "Start at");
        float a = idle ? 1f : 0.45f;
        cardDnd.setAlpha(a);
        cardSilent.setAlpha(a);
        cardDnd.setEnabled(idle);
        cardSilent.setEnabled(idle);
        styleCards(selMode);
        for (int i = 0; i < 3; i++) {
            tiles[i < 2 ? i : 3].setAlpha(idle ? 1f : 0.55f);
        }

        // preset tiles
        for (int i = 0; i < 3; i++) {
            tileDur[i].setText(shortDur(presetMin(i)));
            tileStatus[i][0].setVisibility(presetMode(i) == Dnd.SILENT ? View.VISIBLE : View.GONE);
            tileStatus[i][1].setVisibility(presetMode(i) == Dnd.DND ? View.VISIBLE : View.GONE);
            tileStatus[i][2].setVisibility(presetMusic(i) ? View.VISIBLE : View.GONE);
        }

        // ring
        float frac = 0f;
        if (active) {
            clockView.setTextSize(30);
            if (end == 0) {
                clockView.setText("∞");
                frac = 1f;
                ringLabel.setText("no automatic end");
            } else {
                long left = Math.max(0, end - now);
                clockView.setText(clockStr(left));
                frac = (float) left / (float) Math.max(1, end - start);
                ringLabel.setText("of " + shortDur(minutes));
            }
        } else if (sched) {
            long wait = Math.max(0, start - now);
            if (wait >= 86_400_000L) {
                clockView.setText(longCount(wait));
                clockView.setTextSize(26);
            } else {
                clockView.setText(clockStr(wait));
                clockView.setTextSize(30);
            }
            ringLabel.setText("until it starts");
        }
        ring.setFraction(frac, active);

        // Start / End button
        GradientDrawable g;
        if (idle) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF26BD65, 0xFF0E7D3D});
            goIcon.setImageResource(R.drawable.ic_play);
            goText.setText(planned ? "SCHEDULE SLEEP TIMER" : "START SLEEP TIMER");
        } else {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFE32D40, 0xFFA30F22});
            goIcon.setImageResource(R.drawable.ic_stop);
            goText.setText(active ? "END SLEEP TIMER" : "CANCEL SCHEDULED START");
        }
        g.setStroke(dp(1), 0x40FFFFFF);
        g.setCornerRadius(dp(29));
        goBar.setBackground(g);

        startVal.setText(hhmm(start));
        startDay.setText(dayStr(start, now));
        endVal.setText(end == 0 ? "--:--" : hhmm(end));
        endDay.setText(end == 0 ? "no end time" : dayStr(end, now));

        if (active && !zedsRunning) {
            startZeds();
        } else if (!active && zedsRunning) {
            stopZeds();
        }
        if (settingsView != null && settingsView.getVisibility() == View.VISIBLE) {
            refreshSettings();
        }
    }

    // ------------------------------------------------------------------ settings

    @Override
    public void onBackPressed() {
        if (settingsView != null && settingsView.getVisibility() == View.VISIBLE) {
            settingsView.setVisibility(View.GONE);
        } else {
            super.onBackPressed();
        }
    }

    private void showSettings() {
        refreshSettings();
        settingsView.scrollTo(0, 0);
        settingsView.setVisibility(View.VISIBLE);
    }

    private LinearLayout section(LinearLayout parent, String title) {
        parent.addView(text(title, 13, 0xFF9FB0F0, true), lin(WRAP, WRAP, 0f, 4, 18, 0, 6));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rect(0x99101A46, 0x47829AE6, 1, 16));
        parent.addView(box, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));
        return box;
    }

    private ScrollView buildSettings() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF090F2C);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(30));
        sv.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));
        sv.setFitsSystemWindows(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = icon(R.drawable.ic_back, 22, WHITE);
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setOnClickListener(v -> settingsView.setVisibility(View.GONE));
        head.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        head.addView(text("Setup", 24, WHITE, true), lin(WRAP, WRAP, 0f, 6, 0, 0, 0));
        col.addView(head, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));

        LinearLayout pr = section(col, "PRESETS");
        presetList = pr;

        LinearLayout rp = section(col, "REPEAT (for scheduled starts)");
        chipRow = new LinearLayout(this);
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setPadding(dp(8), dp(12), dp(8), dp(4));
        String[] dn = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView c = text(dn[i], 15, WHITE, true);
            c.setGravity(Gravity.CENTER);
            c.setOnClickListener(v -> {
                if (locked()) {
                    return;
                }
                selDays ^= (1 << bit);
                Dnd.prefs(this).edit().putInt(Dnd.KEY_REP_MASK, selDays).apply();
                refresh();
            });
            setChips[i] = c;
            chipRow.addView(c, lin(0, dp(38), 1f, 3, 0, 3, 0));
        }
        rp.addView(chipRow, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));
        repHint = text("", 13, 0xFFAAB6E0, false);
        rp.addView(repHint, lin(MATCH, WRAP, 0f, 16, 4, 16, 12));

        LinearLayout mu = section(col, "MUSIC");
        LinearLayout mrow = new LinearLayout(this);
        mrow.setGravity(Gravity.CENTER_VERTICAL);
        mrow.setPadding(dp(14), dp(10), dp(14), dp(10));
        mrow.addView(icon(R.drawable.ic_music, 22, 0xFF6DF0C4), new LinearLayout.LayoutParams(dp(26), dp(26)));
        mrow.addView(text("Keep music playing", 16, WHITE, false), lin(0, WRAP, 1f, 12, 0, 0, 0));
        setMusic = new Switch(this);
        setMusicListener = (b, on) -> {
            if (locked()) {
                b.setChecked(selMusic);
                return;
            }
            selMusic = on;
            Dnd.prefs(this).edit().putBoolean(Dnd.KEY_SEL_MUSIC, on).apply();
        };
        setMusic.setOnCheckedChangeListener(setMusicListener);
        mrow.addView(setMusic, new LinearLayout.LayoutParams(WRAP, WRAP));
        mu.addView(mrow, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));

        LinearLayout dm = section(col, "DEFAULT MODE");
        LinearLayout drow = new LinearLayout(this);
        drow.setPadding(dp(10), dp(10), dp(10), dp(10));
        defDnd = text("Do Not Disturb", 15, WHITE, true);
        defSilent = text("Silent", 15, WHITE, true);
        defDnd.setGravity(Gravity.CENTER);
        defSilent.setGravity(Gravity.CENTER);
        defDnd.setOnClickListener(v -> setDefault(Dnd.DND));
        defSilent.setOnClickListener(v -> setDefault(Dnd.SILENT));
        drow.addView(defDnd, lin(0, dp(44), 1f, 0, 0, 5, 0));
        drow.addView(defSilent, lin(0, dp(44), 1f, 5, 0, 0, 0));
        dm.addView(drow, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));

        LinearLayout hi = section(col, "HELP");
        hi.addView(linkRow(R.drawable.ic_info, "Help & Information", "What’s the difference between the modes?", v -> showInfo()),
                lin(MATCH, WRAP, 0f, 0, 0, 0, 0));
        LinearLayout ab = section(col, "ABOUT");
        aboutSub = text("KipQuiet · sets Silent or Do Not Disturb for a time you choose, then puts your phone back how it was.", 14, 0xFFC4CDF0, false);
        ab.addView(aboutSub, lin(MATCH, WRAP, 0f, 14, 12, 14, 12));
        return sv;
    }

    private View linkRow(int iconRes, String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.addView(icon(iconRes, 22, 0xFF9EC3FF), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 16, WHITE, false), new LinearLayout.LayoutParams(WRAP, WRAP));
        t.addView(text(sub, 13, 0xFFAAB6E0, false), new LinearLayout.LayoutParams(WRAP, WRAP));
        r.addView(t, lin(0, WRAP, 1f, 12, 0, 0, 0));
        r.addView(icon(R.drawable.ic_chev, 18, 0xFF9FB0F0), new LinearLayout.LayoutParams(dp(20), dp(20)));
        r.setOnClickListener(l);
        return r;
    }

    private void setDefault(int mode) {
        Dnd.prefs(this).edit().putInt("defMode", mode).apply();
        if (!locked()) {
            selMode = mode;
            Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_MODE, mode).apply();
        }
        refresh();
    }

    private void refreshSettings() {
        // presets
        presetList.removeAllViews();
        for (int i = 0; i < 3; i++) {
            final int pi = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(6), dp(10));
            row.addView(text(PRESET_EMOJI[i], 22, WHITE, false), new LinearLayout.LayoutParams(dp(34), WRAP));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text(PRESET_NAMES[i], 16, WHITE, true), new LinearLayout.LayoutParams(WRAP, WRAP));
            t.addView(text(shortDur(presetMin(i)) + " · " + (presetMode(i) == Dnd.SILENT ? "Silent" : "Do Not Disturb")
                    + (presetMusic(i) ? " · music on" : ""), 13, 0xFFAAB6E0, false), new LinearLayout.LayoutParams(WRAP, WRAP));
            row.addView(t, lin(0, WRAP, 1f, 8, 0, 0, 0));
            TextView dots = text("⋮", 24, 0xFFC4CDF0, true);
            dots.setGravity(Gravity.CENTER);
            dots.setOnClickListener(v -> editPreset(pi));
            row.addView(dots, new LinearLayout.LayoutParams(dp(48), dp(44)));
            presetList.addView(row, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));
        }
        // repeat
        for (int i = 0; i < 7; i++) {
            boolean on = (selDays & (1 << i)) != 0;
            setChips[i].setBackground(on ? ovalBg(0xFF6A5CF5, 0xFFB3A8FF) : ovalBg(0x99101A46, 0x66829AE6));
            setChips[i].setTextColor(on ? WHITE : 0xFFAAB6E0);
            setChips[i].setAlpha(locked() ? 0.5f : 1f);
        }
        repHint.setText(selDays == 0 ? "No repeat: a scheduled start runs once."
                : "Repeats: " + daysSummary(selDays));
        if (setMusic.isChecked() != selMusic) {
            setMusic.setOnCheckedChangeListener(null);
            setMusic.setChecked(selMusic);
            setMusic.setOnCheckedChangeListener(setMusicListener);
        }
        int dm = Dnd.prefs(this).getInt("defMode", Dnd.DND);
        defDnd.setBackground(dm == Dnd.DND ? rect(0xFF2F63F0, 0xFF8FB6FF, 1, 14) : rect(0x66203080, 0x66829AE6, 1, 14));
        defSilent.setBackground(dm == Dnd.SILENT ? rect(0xFF7A44D8, 0xFFD6A8FF, 1, 14) : rect(0x66402080, 0x66829AE6, 1, 14));
    }

    private void editPreset(final int pi) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int[] idx = {idxForMinutes(presetMin(pi))};
        final int[] mode = {presetMode(pi)};
        final boolean[] mus = {presetMusic(pi)};

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(16), dp(18), dp(18));
        sheet.setBackground(rect(0xFF141F52, 0xFF4A62C0, 1, 22));
        sheet.addView(text("Edit " + PRESET_NAMES[pi], 22, WHITE, true), lin(WRAP, WRAP, 0f, 0, 0, 0, 8));
        sheet.addView(text("Duration", 13, 0xFF9FB0F0, true), lin(WRAP, WRAP, 0f, 0, 4, 0, 4));
        OdometerView w = new OdometerView(this, LABELS, idx[0]);
        w.setPlain(0f, 12f, new int[]{0xFF4A3FD0, 0xFF2F289A}, new float[]{0f, 1f}, false, 0x8C968CFF, 0xFF9FABD8);
        w.setBaseSp(22);
        w.setPitchDp(38);
        w.setFitDp(150);
        w.setWindowHalfDp(19);
        w.setListener(i -> idx[0] = i);
        sheet.addView(w, lin(MATCH, dp(114), 0f, 0, 0, 0, 0));

        sheet.addView(text("Mode", 13, 0xFF9FB0F0, true), lin(WRAP, WRAP, 0f, 0, 12, 0, 4));
        LinearLayout mrow = new LinearLayout(this);
        final TextView bd = text("Do Not Disturb", 15, WHITE, true);
        final TextView bs = text("Silent", 15, WHITE, true);
        bd.setGravity(Gravity.CENTER);
        bs.setGravity(Gravity.CENTER);
        final Runnable paint = () -> {
            bd.setBackground(mode[0] == Dnd.DND ? rect(0xFF2F63F0, 0xFF8FB6FF, 1, 14) : rect(0x66203080, 0x66829AE6, 1, 14));
            bs.setBackground(mode[0] == Dnd.SILENT ? rect(0xFF7A44D8, 0xFFD6A8FF, 1, 14) : rect(0x66402080, 0x66829AE6, 1, 14));
        };
        paint.run();
        bd.setOnClickListener(v -> {
            mode[0] = Dnd.DND;
            paint.run();
        });
        bs.setOnClickListener(v -> {
            mode[0] = Dnd.SILENT;
            paint.run();
        });
        mrow.addView(bd, lin(0, dp(44), 1f, 0, 0, 5, 0));
        mrow.addView(bs, lin(0, dp(44), 1f, 5, 0, 0, 0));
        sheet.addView(mrow, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));

        LinearLayout krow = new LinearLayout(this);
        krow.setGravity(Gravity.CENTER_VERTICAL);
        krow.addView(text("Keep music playing", 16, WHITE, false), lin(0, WRAP, 1f, 0, 0, 0, 0));
        Switch sw = new Switch(this);
        sw.setChecked(mus[0]);
        sw.setOnCheckedChangeListener((b, on) -> mus[0] = on);
        krow.addView(sw, new LinearLayout.LayoutParams(WRAP, WRAP));
        sheet.addView(krow, lin(MATCH, WRAP, 0f, 0, 14, 0, 0));

        LinearLayout btns = new LinearLayout(this);
        TextView cancel = text("Cancel", 16, 0xFFC4CDF0, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> d.dismiss());
        TextView save = text("Save", 16, WHITE, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(rect(0xFF2F63F0, 0xFF8FB6FF, 1, 14));
        save.setOnClickListener(v -> {
            Dnd.prefs(this).edit().putInt("pMin" + pi, MINUTES[idx[0]]).putInt("pMode" + pi, mode[0])
                    .putBoolean("pMusic" + pi, mus[0]).apply();
            d.dismiss();
            refresh();
        });
        btns.addView(cancel, lin(0, dp(46), 1f, 0, 0, 6, 0));
        btns.addView(save, lin(0, dp(46), 1f, 6, 0, 0, 0));
        sheet.addView(btns, lin(MATCH, WRAP, 0f, 0, 16, 0, 0));

        d.setContentView(sheet);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), WRAP);
        d.show();
    }



    private void startZeds() {
        zedsRunning = true;
        for (int i = 0; i < 3; i++) {
            TextView z = zeds[i];
            z.setVisibility(View.VISIBLE);
            z.setScaleX(0.6f);
            z.setScaleY(0.6f);
            android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofPropertyValuesHolder(z,
                    android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.95f, 0.7f, 0f),
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dp(zedBaseX[i]), dp(zedBaseX[i] + 26)),
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dp(58)),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.25f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.25f));
            an.setDuration(3600);
            an.setStartDelay(i * 1200L);
            an.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            an.setInterpolator(new android.view.animation.LinearInterpolator());
            zedAnims[i] = an;
            an.start();
        }
    }

    private void stopZeds() {
        zedsRunning = false;
        for (int i = 0; i < 3; i++) {
            if (zedAnims[i] != null) {
                zedAnims[i].cancel();
            }
            zeds[i].setAlpha(0f);
            zeds[i].setVisibility(View.GONE);
        }
    }

    private static String daysSummary(int mask) {
        if (mask == 127) {
            return "Every day";
        }
        if (mask == 31) {
            return "Weekdays";
        }
        if (mask == 96) {
            return "Weekends";
        }
        String[] n = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(n[i]);
            }
        }
        return sb.toString();
    }

    private void styleDayChips(TextView[] chips) {
        for (int i = 0; i < 7; i++) {
            boolean on = (selDays & (1 << i)) != 0;
            chips[i].setBackground(on ? ovalBg(0xFF6A5CF5, 0xFFB3A8FF) : ovalBg(0x99101A46, 0x66829AE6));
            chips[i].setTextColor(on ? WHITE : 0xFFAAB6E0);
        }
    }

    // ------------------------------------------------------------------ pop-up

    private void showInfo() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView sv = new ScrollView(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(12), dp(16), dp(24));
        float r = dp(28);
        GradientDrawable sbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF18265A, 0xFF0C1438});
        sbg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sbg);

        View grabber = new View(this);
        grabber.setBackground(rect(0x73BEC8F0, 0, 0, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(44), dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(grabber, gp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("What’s the difference?", 24, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        ImageView close = icon(R.drawable.ic_close, 22, 0xFFDBE3FF);
        close.setPadding(dp(11), dp(11), dp(11), dp(11));
        close.setOnClickListener(v -> d.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        sheet.addView(titleRow, lin(MATCH, WRAP, 0f, 0, 6, 0, 0));
        LinearLayout cols = new LinearLayout(this);
        cols.setBaselineAligned(false);
        cols.addView(featureCard(R.drawable.ic_bell, 0xFF3F55FF, "Do Not Disturb", "Key calls only",
                0x8C648CFF, 0x401E3CA0,
                new String[]{"Chosen contacts can ring through", "Notifications silenced", "Alarms still work"},
                "Best if you may be needed", 0x662846BE),
                lin(0, MATCH, 1f, 0, 0, 5, 0));
        cols.addView(featureCard(R.drawable.ic_speaker_off, 0xFFD64557, "Silent", "Total silence",
                0x8CF06478, 0x38962838,
                new String[]{"No calls or notifications", "No sounds or vibrations", "Alarms muted too"},
                "Best for sleep", 0x59962838),
                lin(0, MATCH, 1f, 5, 0, 0, 0));
        sheet.addView(cols, lin(MATCH, WRAP, 0f, 0, 14, 0, 0));

        // Keep music playing
        LinearLayout med = new LinearLayout(this);
        med.setGravity(Gravity.CENTER_VERTICAL);
        med.setPadding(dp(12), dp(10), dp(12), dp(10));
        med.setBackground(rect(0x40146E6E, 0x8C46D2BE, 1, 16));
        med.addView(iconCircle(R.drawable.ic_music, 0xFF17A596, 34, 18), new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout medText = new LinearLayout(this);
        medText.setOrientation(LinearLayout.VERTICAL);
        medText.addView(text("Keep music playing", 15, WHITE, true));
        medText.addView(text("Music or video keeps playing in either mode. Everything else stays blocked.",
                12f, 0xFFDBE3FF, false));
        med.addView(medText, lin(0, WRAP, 1f, 10, 0, 0, 0));
        sheet.addView(med, lin(MATCH, WRAP, 0f, 0, 10, 0, 0));

        TextView gotIt = text("Got it", 18, WHITE, true);
        gotIt.setGravity(Gravity.CENTER);
        gotIt.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF6A5CF5, 0xFF4F40DF}));
        ((GradientDrawable) gotIt.getBackground()).setCornerRadius(dp(27));
        gotIt.setOnClickListener(v -> d.dismiss());
        sheet.addView(gotIt, lin(MATCH, dp(54), 0f, 0, 14, 0, 0));

        sv.addView(sheet, new FrameLayout.LayoutParams(MATCH, WRAP));
        d.setContentView(sv);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(MATCH, WRAP);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
        }
        d.show();
    }

    private LinearLayout featureCard(int iconRes, int circleColor, String title, String desc, int border, int fill,
                                     String[] bullets, String best, int bestFill) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(rect(fill, border, 1, 16));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(iconCircle(iconRes, circleColor, 38, 20), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(text(title, 15, WHITE, true));
        ht.addView(text(desc, 11, 0xFFDBE3FF, false));
        head.addView(ht, lin(0, WRAP, 1f, 8, 0, 0, 0));
        card.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        View line = new View(this);
        line.setBackgroundColor(0x33C8D2FA);
        card.addView(line, lin(MATCH, 1, 0f, 0, 10, 0, 10));

        for (String b : bullets) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(14), dp(14));
            tp.topMargin = dp(1);
            row.addView(icon(R.drawable.ic_check, 14, MUTED), tp);
            row.addView(text(b, 11.5f, 0xFFDBE3FF, false), lin(0, WRAP, 1f, 8, 0, 0, 0));
            card.addView(row, lin(MATCH, WRAP, 0f, 0, 0, 0, 6));
        }

        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        TextView pill = text(best, 11, 0xFFEEF2FF, false);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(8), dp(7), dp(8), dp(7));
        pill.setBackground(rect(bestFill, 0, 0, 14));
        card.addView(pill, lin(MATCH, WRAP, 0f, 0, 8, 0, 0));
        return card;
    }

    private FrameLayout iconCircle(int iconRes, int color, int sizeDp, int iconDp) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(color);
        f.setBackground(oval);
        f.addView(icon(iconRes, iconDp, WHITE), new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        return f;
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lin(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h, weight);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private GradientDrawable rect(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), stroke);
        }
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private ImageView icon(int res, int sizeDp, int tint) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(tint);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    private static String pad2(long n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String clockStr(long ms) {
        long s = (ms + 999) / 1000;
        return pad2(s / 3600) + ":" + pad2((s % 3600) / 60) + ":" + pad2(s % 60);
    }

    private static String durWords(int m) {
        int h = m / 60;
        int r = m % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append(h > 1 ? " Hours" : " Hour");
        }
        if (r > 0) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(r).append(r == 1 ? " Minute" : " Minutes");
        }
        return sb.toString();
    }

    private GradientDrawable ovalBg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        return g;
    }

    private static String longCount(long ms) {
        long m = ms / 60_000L;
        return (m / 1440) + "d " + ((m % 1440) / 60) + "h " + (m % 60) + "m";
    }

    private static String shortDur(int m) {
        int h = m / 60;
        int r = m % 60;
        if (h > 0 && r > 0) {
            return h + "hr " + r + "min";
        }
        return h > 0 ? h + "hr" : r + "min";
    }

    private static String hhmm(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private static String dayStr(long t, long ref) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(t);
        b.setTimeInMillis(ref);
        midnight(a);
        midnight(b);
        long diff = Math.round((a.getTimeInMillis() - b.getTimeInMillis()) / 86400000.0);
        if (diff == 0) {
            return "Today";
        }
        if (diff == 1) {
            return "Tomorrow";
        }
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(t));
    }

    private static void midnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
