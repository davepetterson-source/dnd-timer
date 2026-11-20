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
import android.widget.NumberPicker;
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
            "1 min", "15 min", "30 min", "45 min", "1 hour", "1 hour 15 min", "1 hour 30 min",
            "2 hours", "3 hours", "4 hours", "5 hours", "6 hours", "Until I stop"
    };
    private static final int[] MINUTES = {1, 15, 30, 45, 60, 75, 90, 120, 180, 240, 300, 360, -1};
    private static final int DEFAULT_IDX = 6;

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
    private NumberPicker wheel;
    private LinearLayout pill;
    private ImageView pillIcon;
    private TextView pillText;
    private TextView startLabel;
    private TextView startVal;
    private TextView startDay;
    private TextView endLabel;
    private TextView endVal;
    private TextView endDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1233);
        getWindow().setNavigationBarColor(0xFF070B21);

        nm = getSystemService(NotificationManager.class);
        SharedPreferences p = Dnd.prefs(this);
        selMode = p.getInt(Dnd.KEY_SEL_MODE, Dnd.SILENT) == Dnd.DND ? Dnd.DND : Dnd.SILENT;
        selIdx = Math.max(0, Math.min(LABELS.length - 1, p.getInt(Dnd.KEY_SEL_IDX_V2, DEFAULT_IDX)));
        selMusic = p.getBoolean(Dnd.KEY_SEL_MUSIC, false);

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
        col.setPadding(dp(16), dp(8), dp(16), dp(24));
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
        ImageView zz = new ImageView(this);
        zz.setImageResource(R.drawable.ic_moon_zz);
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(dp(54), dp(54),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        zl.topMargin = dp(20);
        ringBox.addView(zz, zl);

        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.VERTICAL);
        centre.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        centre.setPadding(0, dp(74), 0, 0);
        clockView = text("", 21, WHITE, true);
        clockView.setGravity(Gravity.CENTER);
        ringLabel = text("", 13, MUTED, false);
        ringLabel.setGravity(Gravity.CENTER);

        pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), 0, dp(16), 0);
        pillIcon = icon(R.drawable.ic_play, 14, WHITE);
        pillText = text("", 15, WHITE, true);
        pillText.setLetterSpacing(0.05f);
        pill.addView(pillIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        pill.addView(pillText, lin(WRAP, WRAP, 0f, 7, 0, 0, 0));
        pill.setOnClickListener(v -> toggle());

        centre.addView(clockView, new LinearLayout.LayoutParams(MATCH, dp(48)));
        centre.addView(ringLabel, new LinearLayout.LayoutParams(MATCH, dp(16)));
        centre.addView(pill, lin(WRAP, dp(34), 0f, 0, 4, 0, 0));
        ringBox.addView(centre, new FrameLayout.LayoutParams(MATCH, MATCH));
        ringCard.addView(ringBox, new FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER));

        TextView hand = text("Better Sleep Brighter Days", 20, 0xFFAAB6DF, false);
        hand.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        hand.setGravity(Gravity.CENTER);
        hand.setRotation(-8f);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(dp(66), WRAP, Gravity.END | Gravity.TOP);
        hl.topMargin = dp(84);
        hl.rightMargin = dp(8);
        ringCard.addView(hand, hl);
        col.addView(ringCard, lin(MATCH, dp(222), 0f, 0, 8, 0, 0));

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
        col.addView(modeHead, lin(MATCH, dp(26), 0f, 0, 8, 0, 0));

        cardSilent = modeCard(R.drawable.ic_speaker_off, 0xFF7A3FD0, "Silent",
                "No calls, no notifications, no sounds. Full silence.");
        cardDnd = modeCard(R.drawable.ic_bell, 0xFF2F6BFF, "Do Not Disturb",
                "Calls from selected contacts can ring through.");
        cardSilent.setOnClickListener(v -> chooseMode(Dnd.SILENT));
        cardDnd.setOnClickListener(v -> chooseMode(Dnd.DND));

        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.addView(cardSilent, lin(0, MATCH, 1f, 0, 0, 5, 0));
        row.addView(cardDnd, lin(0, MATCH, 1f, 5, 0, 0, 0));
        col.addView(row, lin(MATCH, WRAP, 0f, 0, 6, 0, 0));

        // Keep music playing
        LinearLayout musicRow = new LinearLayout(this);
        musicRow.setGravity(Gravity.CENTER_VERTICAL);
        musicRow.setPadding(dp(12), 0, dp(12), 0);
        musicRow.setBackground(rect(0x38146E6E, 0x7346D2BE, 1, 16));
        musicRow.addView(iconCircle(R.drawable.ic_music, 0xFF17A596, 30, 17), new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.addView(text("Keep music playing", 15, WHITE, true));
        mt.addView(text("Works in both modes", 11, 0xFFC4CDF0, false));
        musicRow.addView(mt, lin(0, WRAP, 1f, 10, 0, 0, 0));
        musicSwitch = new Switch(this);
        musicSwitch.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{WHITE, 0xFFB8C6F2}));
        musicSwitch.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF17A596, 0xFF3B4878}));
        musicSwitch.setOnCheckedChangeListener((b, on) -> {
            selMusic = on;
            Dnd.prefs(this).edit().putBoolean(Dnd.KEY_SEL_MUSIC, on).apply();
        });
        musicRow.addView(musicSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(musicRow, lin(MATCH, dp(44), 0f, 0, 6, 0, 0));

        // Duration wheel (left) + start / end times (right)
        LinearLayout bottom = new LinearLayout(this);
        bottom.setBaselineAligned(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Duration", 17, WHITE, true), new LinearLayout.LayoutParams(MATCH, dp(22)));

        wheel = new NumberPicker(this);
        wheel.setMinValue(0);
        wheel.setMaxValue(LABELS.length - 1);
        wheel.setDisplayedValues(LABELS);
        wheel.setWrapSelectorWheel(false);
        wheel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        wheel.setValue(selIdx);
        if (Build.VERSION.SDK_INT >= 29) {
            wheel.setTextColor(WHITE);
            wheel.setTextSize(dp(16));
        }
        wheel.setOnValueChangedListener((np, oldVal, newVal) -> {
            selIdx = newVal;
            Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_IDX_V2, newVal).apply();
            refresh();
        });
        FrameLayout wheelBox = new FrameLayout(this);
        wheelBox.setBackground(rect(0xC7101A3C, 0x47829AE6, 1, 16));
        wheelBox.setClipToOutline(true);
        wheelBox.addView(wheel, new FrameLayout.LayoutParams(MATCH, MATCH));
        left.addView(wheelBox, lin(MATCH, dp(96), 0f, 0, 4, 0, 0));
        bottom.addView(left, lin(0, WRAP, 0.9f, 0, 0, 5, 0));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(text("Start and end", 17, WHITE, true), new LinearLayout.LayoutParams(MATCH, dp(22)));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), 0);
        box.setBackground(rect(PANEL_FILL, PANEL_STROKE, 1, 16));
        box.addView(timeRow(R.drawable.ic_clock, true), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        View divider = new View(this);
        divider.setBackgroundColor(PANEL_STROKE);
        box.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
        box.addView(timeRow(R.drawable.ic_flag, false), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        right.addView(box, lin(MATCH, dp(96), 0f, 0, 4, 0, 0));
        bottom.addView(right, lin(0, WRAP, 1.1f, 5, 0, 0, 0));

        col.addView(bottom, lin(MATCH, WRAP, 0f, 0, 6, 0, 0));
        return root;
    }

    private LinearLayout timeRow(int iconRes, boolean isStart) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(icon(iconRes, 22, 0xFFCFD8FF), new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("", 12, MUTED, false);
        LinearLayout valRow = new LinearLayout(this);
        valRow.setGravity(Gravity.BOTTOM);
        TextView val = text("", 19, WHITE, true);
        TextView day = text("", 12, MUTED, false);
        valRow.addView(val);
        valRow.addView(day, lin(WRAP, WRAP, 0f, 4, 0, 0, 2));
        t.addView(label);
        t.addView(valRow);
        c.addView(t, lin(WRAP, WRAP, 0f, 10, 0, 0, 0));
        if (isStart) {
            startLabel = label;
            startVal = val;
            startDay = day;
        } else {
            endLabel = label;
            endVal = val;
            endDay = day;
        }
        return c;
    }

    private LinearLayout modeCard(int iconRes, int circleColor, String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setClickable(true);

        FrameLayout circle = new FrameLayout(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(circleColor);
        circle.setBackground(oval);
        ImageView ic = icon(iconRes, 20, WHITE);
        circle.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        card.addView(circle, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, WHITE, true));
        texts.addView(text(sub, 11, 0xFFD3DAFA, false), lin(WRAP, WRAP, 0f, 0, 1, 0, 0));
        card.addView(texts, lin(0, WRAP, 1f, 8, 0, 0, 0));
        return card;
    }

    private void styleCards(int mode) {
        cardSilent.setBackground(rect(mode == Dnd.SILENT ? 0x807440C8 : 0x40582CA0,
                mode == Dnd.SILENT ? 0xFFD9A0FF : 0x59C48CFF, 2, 18));
        cardDnd.setBackground(rect(mode == Dnd.DND ? 0x802854D2 : 0x401E3CA0,
                mode == Dnd.DND ? 0xFF6F9CFF : 0x596496FF, 2, 18));
    }

    // ------------------------------------------------------------------ behaviour

    private void chooseMode(int mode) {
        if (Dnd.prefs(this).getBoolean(Dnd.KEY_ACTIVE, false)) {
            return;
        }
        selMode = mode;
        Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_MODE, mode).apply();
        refresh();
    }

    private void toggle() {
        if (Dnd.prefs(this).getBoolean(Dnd.KEY_ACTIVE, false)) {
            Dnd.stop(this);
        } else {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            if (!Dnd.start(this, selMode, MINUTES[selIdx], selMusic)) {
                Toast.makeText(this,
                        "Switch on “Sleeping Timer” under Do Not Disturb access, then come back and press Start",
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
        long start = p.getLong(Dnd.KEY_START, 0);
        long end = p.getLong(Dnd.KEY_END, 0);
        long now = System.currentTimeMillis();
        int minutes = MINUTES[selIdx];
        if (active) {
            selMode = p.getInt(Dnd.KEY_MODE, selMode);
        }

        wheel.setEnabled(!active);
        if (wheel.getValue() != selIdx) {
            wheel.setValue(selIdx);
        }
        musicSwitch.setEnabled(!active);
        if (musicSwitch.isChecked() != selMusic) {
            musicSwitch.setChecked(selMusic);
        }
        styleCards(selMode);

        // Banner
        if (active) {
            banner.setBackground(rect(0x66203CA0, 0xBF6E96FF, 2, 16));
            bannerIcon.setColorFilter(0xFF9EC3FF);
            bannerTitle.setTextColor(WHITE);
            bannerTitle.setText("Sleep Timer Active");
        } else {
            banner.setBackground(rect(0x59126440, 0xBF3DDC84, 2, 16));
            bannerIcon.setColorFilter(0xFF6DF0A4);
            bannerTitle.setTextColor(0xFF8AF3B4);
            bannerTitle.setText("Sleep Timer Inactive");
        }

        // Ring
        String clock;
        float clockSp = 30f;
        float frac = 0f;
        String caption = "";
        if (active) {
            if (end == 0) {
                clock = "∞";
                frac = 1f;
                caption = "no automatic end";
            } else {
                long left = Math.max(0, end - now);
                clock = clockStr(left);
                frac = (float) left / (float) Math.max(1, end - start);
                caption = "of " + shortDur((int) Math.round((end - start) / 60000.0));
            }
        } else {
            if (minutes < 0) {
                clock = "∞";
            } else {
                clock = durWords(minutes);
                clockSp = 21f;
            }
        }
        clockView.setText(clock);
        clockView.setTextSize(clockSp);
        clockView.setGravity(Gravity.CENTER);
        ringLabel.setText(caption);
        ring.setFraction(frac);

        // Start / End pill
        GradientDrawable g;
        if (active) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2A4BC0, 0xFF1A2F8C});
            g.setStroke(dp(1), 0x66FFFFFF);
            pillIcon.setImageResource(R.drawable.ic_stop);
            pillText.setText("END");
        } else {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14894A, 0xFF0B6E39});
            g.setStroke(dp(1), 0x4DFFFFFF);
            pillIcon.setImageResource(R.drawable.ic_play);
            pillText.setText("START");
        }
        g.setCornerRadius(dp(17));
        pill.setBackground(g);

        // Start and end times
        long shownEnd = active ? end : (minutes < 0 ? 0 : now + minutes * 60_000L);
        startLabel.setText(active ? "Started at" : "Will start");
        startVal.setText(active ? hhmm(start) : "Now");
        String sd = active ? dayStr(start, now) : "";
        startDay.setText(sd);
        startDay.setVisibility(sd.isEmpty() ? View.GONE : View.VISIBLE);
        endLabel.setText(active ? "Ends at" : "Will end at");
        endVal.setText(shownEnd == 0 ? "--:--" : hhmm(shownEnd));
        endDay.setText(shownEnd == 0 ? "no end time" : dayStr(shownEnd, now));
    }

    // ------------------------------------------------------------------ pop-up

    private void showInfo() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView sv = new ScrollView(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(12), dp(16), dp(28));
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
        sheet.addView(titleRow, lin(MATCH, WRAP, 0f, 0, 12, 0, 0));
        sheet.addView(text("Both modes help you get a more peaceful, distraction-free time.", 13, MUTED, false),
                lin(WRAP, WRAP, 0f, 0, 2, 0, 0));

        LinearLayout cols = new LinearLayout(this);
        cols.setBaselineAligned(false);
        cols.addView(featureCard(R.drawable.ic_speaker_off, 0xFFD64557, "Silent", "Complete silence for total peace.",
                0x8CF06478, 0x38962838,
                new String[]{"No calls", "No notifications", "No sounds or vibrations", "Alarms are muted too",
                        "Your phone stays completely silent"},
                "Best for sleep or when you need total quiet.", 0x59962838),
                lin(0, MATCH, 1f, 0, 0, 5, 0));
        cols.addView(featureCard(R.drawable.ic_bell, 0xFF3F55FF, "Do Not Disturb",
                "Reduces interruptions but allows important calls.", 0x8C648CFF, 0x401E3CA0,
                new String[]{"Calls from selected contacts can ring through", "Notifications are silenced (or limited)",
                        "Alarms and emergency calls still work", "Ideal for rest while staying available to important people"},
                "Best when you still want to hear from key contacts.", 0x662846BE),
                lin(0, MATCH, 1f, 5, 0, 0, 0));
        sheet.addView(cols, lin(MATCH, WRAP, 0f, 0, 16, 0, 0));

        // Keep music playing
        LinearLayout med = new LinearLayout(this);
        med.setGravity(Gravity.CENTER_VERTICAL);
        med.setPadding(dp(12), dp(12), dp(12), dp(12));
        med.setBackground(rect(0x40146E6E, 0x8C46D2BE, 1, 16));
        med.addView(iconCircle(R.drawable.ic_music, 0xFF17A596, 38, 20), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout medText = new LinearLayout(this);
        medText.setOrientation(LinearLayout.VERTICAL);
        medText.addView(text("Keep music playing", 15, WHITE, true));
        medText.addView(text("Switch this on and music or video keeps playing in either mode. "
                + "Calls, alarms and notifications stay blocked, so you can drift off to meditation music.",
                11.5f, 0xFFDBE3FF, false));
        med.addView(medText, lin(0, WRAP, 1f, 10, 0, 0, 0));
        sheet.addView(med, lin(MATCH, WRAP, 0f, 0, 10, 0, 0));

        TextView gotIt = text("Got it", 18, WHITE, true);
        gotIt.setGravity(Gravity.CENTER);
        gotIt.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF6A5CF5, 0xFF4F40DF}));
        ((GradientDrawable) gotIt.getBackground()).setCornerRadius(dp(27));
        gotIt.setOnClickListener(v -> d.dismiss());
        sheet.addView(gotIt, lin(MATCH, dp(54), 0f, 0, 16, 0, 0));

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
            card.addView(row, lin(MATCH, WRAP, 0f, 0, 0, 0, 8));
        }

        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        TextView pill = text(best, 11, 0xFFEEF2FF, false);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(8), dp(9), dp(8), dp(9));
        pill.setBackground(rect(bestFill, 0, 0, 14));
        card.addView(pill, lin(MATCH, WRAP, 0f, 0, 12, 0, 0));
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

    private static String shortDur(int m) {
        int h = m / 60;
        int r = m % 60;
        if (h > 0 && r > 0) {
            return h + " hr " + r + " min";
        }
        return h > 0 ? h + " hr" : r + " min";
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
