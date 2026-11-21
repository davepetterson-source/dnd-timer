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
            "2hrs", "3hrs", "4hrs", "5hrs", "6hrs", "Until I stop"
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
    private static final int[] MINUTES = {1, 5, 10, 15, 30, 45, 60, 75, 90, 120, 180, 240, 300, 360, -1};
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

    private LinearLayout banner;
    private ImageView bannerIcon;
    private TextView bannerTitle;
    private RingView ring;
    private TextView clockView;
    private TextView ringLabel;
    private LinearLayout cardSilent;
    private LinearLayout cardDnd;
    private Switch musicSwitch;
    private android.widget.CompoundButton.OnCheckedChangeListener musicListener;
    private OdometerView durWheel;
    private OdometerView stWheel;
    private TextView durCaption;
    private LinearLayout clockBox;
    private LinearLayout modeRowView;
    private LinearLayout repPill;
    private TextView repPillText;
    private ImageView leftTitleIcon;
    private TextView leftTitle;
    private TextView rightTitle;
    private LinearLayout startedBox;
    private LinearLayout endBox;
    private FrameLayout wheelBox;
    private TextView[] zeds;
    private android.animation.ObjectAnimator[] zedAnims;
    private boolean zedsRunning = false;
    private int[] zedBaseX;
    private int selDays = 0;
    private int selSt = 0;
    private TextView musicState;
    private LinearLayout musicRow;
    private LinearLayout goBar;
    private ImageView goIcon;
    private TextView goText;
    private TextView startVal;
    private TextView startDay;
    private TextView endVal;
    private TextView endDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1233);
        getWindow().setNavigationBarColor(0xFF070B21);

        nm = getSystemService(NotificationManager.class);
        SharedPreferences p = Dnd.prefs(this);
        selMode = p.getInt(Dnd.KEY_SEL_MODE, Dnd.DND) == Dnd.SILENT ? Dnd.SILENT : Dnd.DND;
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

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF090F2C);

        // Moonlit lake picture, scaled to the screen width, fading to the background colour
        float k = getResources().getDisplayMetrics().widthPixels
                / (390f * getResources().getDisplayMetrics().density);
        FrameLayout bgWrap = new FrameLayout(this);
        bgWrap.setClipChildren(true);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg_moon);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(567 * k), dp(850 * k));
        bp.leftMargin = -dp(127 * k);
        bp.topMargin = -dp(348 * k);
        bgWrap.addView(bg, bp);
        View overlay = new View(this);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1A090F2C, 0x66090F2C, 0xD9090F2C, 0xFF090F2C}));
        bgWrap.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(bgWrap, new FrameLayout.LayoutParams(MATCH, dp(520 * k), Gravity.TOP));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(12));
        scroll.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Status banner
        banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(6), dp(12), dp(6));
        bannerIcon = icon(R.drawable.ic_moon, 34, 0xFF9EC3FF);
        bannerTitle = text("", 17, WHITE, true);
        bannerTitle.setSingleLine(true);
        banner.addView(bannerIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        banner.addView(bannerTitle, lin(WRAP, WRAP, 0f, 10, 0, 0, 0));
        col.addView(banner, lin(dp(268), dp(48), 0f, 0, 0, 0, 0));

        // Ring card
        FrameLayout ringCard = new FrameLayout(this);
        ringCard.setBackground(rect(PANEL_FILL, PANEL_STROKE, 1, 22));
        FrameLayout ringBox = new FrameLayout(this);
        ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(MATCH, MATCH));
        // Duration drum inside the circle (shown while idle)
        durCaption = text("DURATION", 10, 0xFF8F9BC9, true);
        durCaption.setLetterSpacing(0.12f);
        durCaption.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dcl = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP);
        dcl.topMargin = dp(28);
        ringBox.addView(durCaption, dcl);

        durWheel = new OdometerView(this, LABELS, selIdx);
        durWheel.setCornerDp(60);
        durWheel.setBaseSp(31);
        durWheel.setPitchDp(38);
        durWheel.setFitDp(156);
        durWheel.setWindowHalfDp(20);
        durWheel.setListener(index -> {
            selIdx = index;
            Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_IDX_V2, index).apply();
            refresh();
        });
        FrameLayout.LayoutParams dwl = new FrameLayout.LayoutParams(dp(176), dp(122),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dwl.topMargin = dp(48);
        ringBox.addView(durWheel, dwl);

        // Countdown (shown while scheduled or running)
        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.VERTICAL);
        centre.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        centre.setPadding(0, dp(80), 0, 0);
        clockView = text("", 30, WHITE, true);
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);
        ringLabel = text("", 13, MUTED, false);
        ringLabel.setGravity(Gravity.CENTER);
        ringLabel.setIncludeFontPadding(false);
        centre.addView(clockView, new LinearLayout.LayoutParams(MATCH, dp(40)));
        centre.addView(ringLabel, new LinearLayout.LayoutParams(MATCH, dp(22)));
        ringBox.addView(centre, new FrameLayout.LayoutParams(MATCH, MATCH));
        clockBox = centre;
        ringCard.addView(ringBox, new FrameLayout.LayoutParams(dp(212), dp(212), Gravity.CENTER));
        zeds = new TextView[3];
        zedAnims = new android.animation.ObjectAnimator[3];
        int[] zx = {104, 118, 132};
        int[] zy = {64, 46, 24};
        float[] zs = {16, 22, 28};
        for (int i = 0; i < 3; i++) {
            TextView z = text(i == 0 ? "z" : "Z", zs[i], 0xFFCFD8FF, true);
            z.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.ITALIC));
            z.setShadowLayer(dp(5), 0, 0, 0xCC8CA0FF);
            z.setAlpha(0f);
            z.setVisibility(View.GONE);
            FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(WRAP, WRAP,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            zp.topMargin = dp(zy[i] + 8);
            ringCard.addView(z, zp);
            z.setTranslationX(dp(zx[i]));
            zeds[i] = z;
        }
        zedBaseX = zx;
        col.addView(ringCard, lin(MATCH, dp(218), 0f, 0, 10, 0, 0));

        // Start at / Ends at cards
        LinearLayout bottom = new LinearLayout(this);
        bottom.setBaselineAligned(false);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout leftCard = new LinearLayout(this);
        leftCard.setOrientation(LinearLayout.VERTICAL);
        leftCard.setPadding(dp(6), dp(4), dp(6), dp(6));
        leftCard.setBackground(rect(PANEL_FILL, PANEL_STROKE, 1, 16));
        LinearLayout lt = new LinearLayout(this);
        lt.setGravity(Gravity.CENTER_VERTICAL);
        lt.setPadding(dp(6), 0, 0, 0);
        leftTitleIcon = icon(R.drawable.ic_play_circle, 22, 0xFF3FD97F);
        leftTitle = text("Start at", 16, WHITE, true);
        leftTitle.setIncludeFontPadding(false);
        lt.addView(leftTitleIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        lt.addView(leftTitle, lin(WRAP, WRAP, 0f, 7, 0, 0, 0));
        leftCard.addView(lt, new LinearLayout.LayoutParams(MATCH, dp(28)));

        stWheel = new OdometerView(this, START_LABELS, selSt);
        stWheel.setListener(index -> {
            selSt = index;
            refresh();
        });
        wheelBox = new FrameLayout(this);
        wheelBox.addView(stWheel, new FrameLayout.LayoutParams(MATCH, MATCH));
        startedBox = new LinearLayout(this);
        startedBox.setOrientation(LinearLayout.VERTICAL);
        startedBox.setGravity(Gravity.CENTER);
        startVal = text("", 24, WHITE, true);
        startVal.setGravity(Gravity.CENTER);
        startDay = text("", 15, MUTED, false);
        startDay.setGravity(Gravity.CENTER);
        startedBox.addView(startVal, new LinearLayout.LayoutParams(WRAP, WRAP));
        startedBox.addView(startDay, new LinearLayout.LayoutParams(WRAP, WRAP));
        wheelBox.addView(startedBox, new FrameLayout.LayoutParams(MATCH, MATCH));
        leftCard.addView(wheelBox, lin(MATCH, dp(120), 0f, 0, 2, 0, 0));
        leftCol.addView(leftCard, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Repeat pill under the Start at card
        repPill = new LinearLayout(this);
        repPill.setGravity(Gravity.CENTER);
        repPill.setPadding(dp(10), 0, dp(10), 0);
        repPill.addView(icon(R.drawable.ic_repeat, 16, WHITE), new LinearLayout.LayoutParams(dp(16), dp(16)));
        repPillText = text("Repeat", 13, WHITE, true);
        repPillText.setSingleLine(true);
        repPillText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        repPill.addView(repPillText, lin(0, WRAP, 0f, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) repPillText.getLayoutParams()).width = WRAP;
        repPill.setOnClickListener(v -> showRepeat());
        leftCol.addView(repPill, lin(MATCH, dp(34), 0f, 0, 8, 0, 0));
        bottom.addView(leftCol, lin(0, WRAP, 0.9f, 0, 0, 5, 0));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(6), dp(4), dp(6), dp(6));
        right.setBackground(rect(PANEL_FILL, PANEL_STROKE, 1, 16));
        LinearLayout rt = new LinearLayout(this);
        rt.setGravity(Gravity.CENTER_VERTICAL);
        rt.setPadding(dp(6), 0, 0, 0);
        rt.addView(icon(R.drawable.ic_flag_solid, 22, 0xFFFFC233), new LinearLayout.LayoutParams(dp(22), dp(22)));
        rightTitle = text("Ends at", 16, WHITE, true);
        rightTitle.setIncludeFontPadding(false);
        rt.addView(rightTitle, lin(WRAP, WRAP, 0f, 7, 0, 0, 0));
        right.addView(rt, new LinearLayout.LayoutParams(MATCH, dp(28)));
        endBox = new LinearLayout(this);
        endBox.setOrientation(LinearLayout.VERTICAL);
        endBox.setGravity(Gravity.CENTER);
        endVal = text("", 22, WHITE, true);
        endDay = text("", 14, MUTED, false);
        endBox.addView(endVal, new LinearLayout.LayoutParams(WRAP, WRAP));
        endBox.addView(endDay, new LinearLayout.LayoutParams(WRAP, WRAP));
        right.addView(endBox, lin(MATCH, dp(120), 0f, 0, 2, 0, 0));
        bottom.addView(right, lin(0, WRAP, 1.1f, 5, 0, 0, 0));
        col.addView(bottom, lin(MATCH, WRAP, 0f, 0, 10, 0, 0));

        // Choose mode
        LinearLayout modeHead = new LinearLayout(this);
        modeHead.setGravity(Gravity.CENTER_VERTICAL);
        modeHead.addView(text("Choose mode", 17, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout whatsThis = new LinearLayout(this);
        whatsThis.setGravity(Gravity.CENTER_VERTICAL);
        whatsThis.addView(text("What’s the difference?", 13, 0xFF7FB0FF, false));
        whatsThis.addView(icon(R.drawable.ic_info, 17, 0xFF7FB0FF), lin(dp(17), dp(17), 0f, 6, 0, 0, 0));
        whatsThis.setOnClickListener(v -> showInfo());
        modeHead.addView(whatsThis, new LinearLayout.LayoutParams(WRAP, dp(26)));
        col.addView(modeHead, lin(MATCH, dp(28), 0f, 0, 8, 0, 0));

        cardSilent = modeCard(R.drawable.ic_speaker_off, 0xFF7A3FD0, "Silent");
        cardDnd = modeCard(R.drawable.ic_bell, 0xFF2F6BFF, "Do Not Disturb");
        cardSilent.setOnClickListener(v -> chooseMode(Dnd.SILENT));
        cardDnd.setOnClickListener(v -> chooseMode(Dnd.DND));

        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.addView(cardDnd, lin(0, MATCH, 1f, 0, 0, 5, 0));
        row.addView(cardSilent, lin(0, MATCH, 1f, 5, 0, 0, 0));
        modeRowView = row;
        col.addView(row, lin(MATCH, dp(44), 0f, 0, 4, 0, 0));

        // Keep music playing
        musicRow = new LinearLayout(this);
        musicRow.setGravity(Gravity.CENTER_VERTICAL);
        musicRow.setPadding(dp(12), 0, dp(12), 0);
        musicRow.addView(iconCircle(R.drawable.ic_music, 0xFF17A596, 30, 17), new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.addView(text("Keep music playing", 15, WHITE, true));
        mt.addView(text("Works in both modes", 11, 0xFFC4CDF0, false));
        musicRow.addView(mt, lin(0, WRAP, 1f, 10, 0, 0, 0));
        musicSwitch = new Switch(this);
        musicSwitch.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{WHITE, 0xFF8F9BC9}));
        musicSwitch.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF25D366, 0xFF27315F}));
        musicListener = (b, on) -> {
            selMusic = on;
            Dnd.prefs(this).edit().putBoolean(Dnd.KEY_SEL_MUSIC, on).apply();
            refresh();
        };
        musicSwitch.setOnCheckedChangeListener(musicListener);
        musicState = text("OFF", 13, 0xFF8F9BC9, true);
        musicState.setGravity(Gravity.END);
        musicRow.addView(musicState, lin(dp(32), WRAP, 0f, 0, 0, 8, 0));
        musicRow.addView(musicSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(musicRow, lin(MATCH, dp(54), 0f, 0, 8, 0, 0));

        goBar = new LinearLayout(this);
        goBar.setGravity(Gravity.CENTER);
        goIcon = icon(R.drawable.ic_play, 18, WHITE);
        goText = text("", 18, WHITE, true);
        goText.setLetterSpacing(0.04f);
        goBar.addView(goIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        goBar.addView(goText, lin(WRAP, WRAP, 0f, 9, 0, 0, 0));
        goBar.setOnClickListener(v -> toggle());
        col.addView(goBar, lin(MATCH, dp(54), 0f, 0, 12, 0, 0));
        return root;
    }

    private LinearLayout modeCard(int iconRes, int circleColor, String title) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(6), dp(6), dp(6));
        card.setClickable(true);

        FrameLayout circle = new FrameLayout(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(circleColor);
        circle.setBackground(oval);
        circle.addView(icon(iconRes, 16, WHITE), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        card.addView(circle, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView t = text(title, 13.5f, WHITE, true);
        t.setSingleLine(true);
        card.addView(t, lin(0, WRAP, 1f, 6, 0, 0, 0));
        return card;
    }

    private void styleCards(int mode) {
        cardSilent.setBackground(rect(mode == Dnd.SILENT ? 0x807440C8 : 0x40582CA0,
                mode == Dnd.SILENT ? 0xFFD9A0FF : 0x59C48CFF, 2, 14));
        cardDnd.setBackground(rect(mode == Dnd.DND ? 0x802854D2 : 0x401E3CA0,
                mode == Dnd.DND ? 0xFF6F9CFF : 0x596496FF, 2, 14));
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
        boolean hasDays = selDays != 0;
        repPill.setEnabled(idle);
        repPill.setAlpha(idle || hasDays ? 1f : 0.5f);
        repPill.setBackground(hasDays ? rect(0xFF6A5CF5, 0xFFB3A8FF, 1, 17) : rect(0x99101A46, 0x80829AE6, 1, 17));
        repPillText.setText(hasDays ? daysSummary(selDays) : "Repeat");

        // Controls: greyed out and locked once scheduled or running
        durWheel.setEnabled(idle);
        durWheel.setSelected(selIdx);
        durWheel.setVisibility(idle ? View.VISIBLE : View.GONE);
        durCaption.setVisibility(idle ? View.VISIBLE : View.GONE);
        clockBox.setVisibility(idle ? View.GONE : View.VISIBLE);
        stWheel.setEnabled(idle);
        stWheel.setSelected(selSt);
        boolean startedNow = active && p.getBoolean("startedNow", false);
        stWheel.setAlpha(idle ? 1f : 0.4f);
        stWheel.setVisibility(startedNow ? View.GONE : View.VISIBLE);
        startedBox.setVisibility(startedNow ? View.VISIBLE : View.GONE);
        leftTitleIcon.setImageResource(startedNow ? R.drawable.ic_clock : R.drawable.ic_play_circle);
        leftTitle.setText(startedNow ? "Started" : "Start at");
        rightTitle.setText(active ? "Ends" : "Ends at");
        endBox.setBackground(active ? null : rect(0x99080E2C, PANEL_STROKE, 1, 14));
        modeRowView.setAlpha(idle ? 1f : 0.4f);
        musicRow.setAlpha(idle ? 1f : 0.4f);
        musicSwitch.setEnabled(idle);
        cardSilent.setEnabled(idle);
        cardDnd.setEnabled(idle);
        if (sched) {
            selMusic = p.getBoolean(Dnd.KEY_SCHED_MUSIC, selMusic);
        } else if (active) {
            selMusic = p.getBoolean("music", selMusic);
        }
        musicState.setText(selMusic ? "ON" : "OFF");
        musicState.setTextColor(selMusic ? 0xFF5DFF9A : 0xFF8F9BC9);
        musicRow.setBackground(selMusic ? rect(0x2925D366, 0xBF25D366, 1, 16)
                : rect(0x80101A46, 0x4D829AE6, 1, 16));
        if (musicSwitch.isChecked() != selMusic) {
            musicSwitch.setOnCheckedChangeListener(null);
            musicSwitch.setChecked(selMusic);
            musicSwitch.setOnCheckedChangeListener(musicListener);
        }
        styleCards(selMode);

        // Banner
        if (active) {
            banner.setBackground(rect(0x66203CA0, 0xBF6E96FF, 2, 16));
            bannerIcon.setColorFilter(0xFF9EC3FF);
            bannerTitle.setTextColor(WHITE);
            bannerTitle.setText("Sleep Timer Active");
        } else if (sched) {
            banner.setBackground(rect(0x66785000, 0xCCFFBE50, 2, 16));
            bannerIcon.setColorFilter(0xFFFFD27A);
            bannerTitle.setTextColor(0xFFFFE0A0);
            bannerTitle.setText("Sleep Timer Scheduled");
        } else {
            banner.setBackground(rect(0x59126440, 0xBF3DDC84, 2, 16));
            bannerIcon.setColorFilter(0xFF6DF0A4);
            bannerTitle.setTextColor(0xFF8AF3B4);
            bannerTitle.setText("Sleep Timer Inactive");
        }

        // Ring
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
        ring.setFraction(frac);

        // Start / End bar
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
        g.setCornerRadius(dp(27));
        goBar.setBackground(g);

        // Start / end times
        startVal.setText(hhmm(start));
        startDay.setText(dayStr(start, now));
        endVal.setText(end == 0 ? "--:--" : hhmm(end));
        endDay.setText(end == 0 ? "no end time" : dayStr(end, now));

        // Sleeping zeds while the timer runs
        if (active && !zedsRunning) {
            startZeds();
        } else if (!active && zedsRunning) {
            stopZeds();
        }
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

    private void showRepeat() {
        if (locked()) {
            return;
        }
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
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
        sheet.addView(text("Repeat on", 24, WHITE, true), lin(MATCH, WRAP, 0f, 0, 14, 0, 0));
        sheet.addView(text("Starts at the Start at time on the days you choose", 13, 0xFFB8C6F2, false),
                lin(MATCH, WRAP, 0f, 0, 2, 0, 0));

        String[] dl = {"M", "T", "W", "T", "F", "S", "S"};
        final TextView[] chips = new TextView[7];
        LinearLayout rowChips = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView chip = text(dl[i], 15, WHITE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                selDays ^= (1 << idx);
                if ((selDays & (1 << idx)) != 0 && selSt == 0) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    int mod = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                    selSt = ((mod / 15 + 1) % 96) + 1;
                    stWheel.setSelected(selSt);
                }
                styleDayChips(chips);
                refresh();
            });
            chips[i] = chip;
            rowChips.addView(chip, lin(0, dp(44), 1f, 3, 0, 3, 0));
        }
        styleDayChips(chips);
        sheet.addView(rowChips, lin(MATCH, WRAP, 0f, 0, 18, 0, 0));

        LinearLayout btns = new LinearLayout(this);
        TextView clear = text("Clear", 15, 0xFFDBE3FF, true);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(rect(0, 0x80829AE6, 1, 25));
        clear.setOnClickListener(v -> {
            selDays = 0;
            refresh();
            d.dismiss();
        });
        TextView done = text("Done", 18, WHITE, true);
        done.setGravity(Gravity.CENTER);
        GradientDrawable dbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF6A5CF5, 0xFF4F40DF});
        dbg.setCornerRadius(dp(25));
        done.setBackground(dbg);
        done.setOnClickListener(v -> d.dismiss());
        btns.addView(clear, lin(dp(110), dp(50), 0f, 0, 0, 10, 0));
        btns.addView(done, lin(0, dp(50), 1f, 0, 0, 0, 0));
        sheet.addView(btns, lin(MATCH, WRAP, 0f, 0, 22, 0, 0));

        d.setContentView(sheet);
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
