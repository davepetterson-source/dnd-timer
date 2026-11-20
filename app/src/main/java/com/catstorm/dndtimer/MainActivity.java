package com.catstorm.dndtimer;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
            "15m", "30m", "45m", "1h", "1h15", "1h30", "2h", "3h", "4h", "5h", "6h", "∞"
    };
    private static final int[] MINUTES = {15, 30, 45, 60, 75, 90, 120, 180, 240, 300, 360, -1};

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

    private LinearLayout banner;
    private ImageView bannerIcon;
    private TextView bannerTitle;
    private RingView ring;
    private TextView clockView;
    private TextView ringLabel;
    private LinearLayout cardSilent;
    private LinearLayout cardDnd;
    private LinearLayout cardMedia;
    private StopsView stops;
    private LinearLayout bar;
    private TextView durText;
    private LinearLayout chip;
    private ImageView chipIcon;
    private TextView chipText;
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
        selMode = p.getInt(Dnd.KEY_SEL_MODE, Dnd.SILENT);
        selIdx = Math.max(0, Math.min(LABELS.length - 1, p.getInt(Dnd.KEY_SEL_IDX, 5)));

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

        // Moonlit lake picture, fading to the background colour
        FrameLayout bgWrap = new FrameLayout(this);
        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.bg_moon);
        bg.setAdjustViewBounds(true);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        bgWrap.addView(bg, new FrameLayout.LayoutParams(MATCH, WRAP));
        View overlay = new View(this);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26090F2C, 0x4D090F2C, 0x73090F2C, 0xCC090F2C, 0xFF090F2C}));
        bgWrap.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(bgWrap, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(icon(R.drawable.ic_moon, 48, 0xFF8DB4FF), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Sleeping Timer", 28, WHITE, true));
        titles.addView(text("Quieter nights. A brighter you.", 15, MUTED, false));
        header.addView(titles, lin(0, WRAP, 1f, 10, 0, 0, 0));
        col.addView(header, lin(MATCH, WRAP, 0f, 0, 0, 0, 0));

        // Status banner
        banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(8), dp(12), dp(8));
        bannerIcon = icon(R.drawable.ic_moon, 40, 0xFF9EC3FF);
        bannerTitle = text("", 17, WHITE, true);
        bannerTitle.setSingleLine(true);
        banner.addView(bannerIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        banner.addView(bannerTitle, lin(WRAP, WRAP, 0f, 10, 0, 0, 0));
        col.addView(banner, lin(dp(268), dp(56), 0f, 0, 12, 0, 0));

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
        zl.topMargin = dp(24);
        ringBox.addView(zz, zl);
        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.VERTICAL);
        centre.setGravity(Gravity.CENTER);
        centre.setPadding(0, dp(14), 0, 0);
        clockView = text("", 22, WHITE, true);
        clockView.setGravity(Gravity.CENTER);
        ringLabel = text("Selected duration", 14, MUTED, false);
        centre.addView(clockView, new LinearLayout.LayoutParams(WRAP, WRAP));
        centre.addView(ringLabel, lin(WRAP, WRAP, 0f, 0, 2, 0, 0));
        ringBox.addView(centre, new FrameLayout.LayoutParams(MATCH, MATCH));
        ringCard.addView(ringBox, new FrameLayout.LayoutParams(dp(200), dp(200), Gravity.CENTER));

        TextView hand = text("Better Sleep Brighter Days", 21, 0xFFAAB6DF, false);
        hand.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        hand.setGravity(Gravity.CENTER);
        hand.setRotation(-8f);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(dp(74), WRAP, Gravity.END | Gravity.TOP);
        hl.topMargin = dp(78);
        hl.rightMargin = dp(14);
        ringCard.addView(hand, hl);
        col.addView(ringCard, lin(MATCH, dp(232), 0f, 0, 14, 0, 0));

        // Choose mode
        LinearLayout modeHead = new LinearLayout(this);
        modeHead.setGravity(Gravity.CENTER_VERTICAL);
        modeHead.addView(text("Choose mode", 18, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout whatsThis = new LinearLayout(this);
        whatsThis.setGravity(Gravity.CENTER_VERTICAL);
        whatsThis.addView(text("What’s the difference?", 13, 0xFF7FB0FF, false));
        whatsThis.addView(icon(R.drawable.ic_info, 18, 0xFF7FB0FF), lin(dp(18), dp(18), 0f, 6, 0, 0, 0));
        whatsThis.setOnClickListener(v -> showInfo());
        modeHead.addView(whatsThis, new LinearLayout.LayoutParams(WRAP, dp(30)));
        col.addView(modeHead, lin(MATCH, dp(30), 0f, 0, 16, 0, 0));

        cardSilent = modeCard(R.drawable.ic_speaker_off, 0xFF7A3FD0, "Silent",
                "No calls, no notifications, no sounds. Full silence.");
        cardDnd = modeCard(R.drawable.ic_bell, 0xFF2F6BFF, "Do Not Disturb",
                "Calls from selected contacts can ring through.");
        cardMedia = modeCard(R.drawable.ic_music, 0xFF17A596, "Meditation",
                "Music and media keep playing. Calls, alarms and notifications are blocked.");
        cardSilent.setOnClickListener(v -> chooseMode(Dnd.SILENT));
        cardDnd.setOnClickListener(v -> chooseMode(Dnd.DND));
        cardMedia.setOnClickListener(v -> chooseMode(Dnd.MEDIA));

        LinearLayout row = new LinearLayout(this);
        row.setBaselineAligned(false);
        row.addView(cardSilent, lin(0, MATCH, 1f, 0, 0, 5, 0));
        row.addView(cardDnd, lin(0, MATCH, 1f, 5, 0, 0, 0));
        col.addView(row, lin(MATCH, WRAP, 0f, 0, 8, 0, 0));
        col.addView(cardMedia, lin(MATCH, WRAP, 0f, 0, 10, 0, 0));

        // Duration
        LinearLayout durHead = new LinearLayout(this);
        durHead.setGravity(Gravity.CENTER_VERTICAL);
        durHead.addView(text("Duration", 18, WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        durHead.addView(icon(R.drawable.ic_moon, 16, 0xFF7FB0FF), new LinearLayout.LayoutParams(dp(16), dp(16)));
        durHead.addView(text("Slide to choose how long", 12.5f, 0xFFDBE3FF, false), lin(WRAP, WRAP, 0f, 6, 0, 0, 0));
        col.addView(durHead, lin(MATCH, dp(30), 0f, 0, 16, 0, 0));

        stops = new StopsView(this, LABELS, selIdx);
        stops.setListener(index -> {
            selIdx = index;
            Dnd.prefs(this).edit().putInt(Dnd.KEY_SEL_IDX, index).apply();
            refresh();
        });
        col.addView(stops, lin(MATCH, dp(56), 0f, 0, 6, 0, 0));

        // Start / end bar
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), 0, dp(8), 0);
        durText = text("", 20, WHITE, true);
        durText.setGravity(Gravity.CENTER);
        durText.setPadding(dp(14), 0, 0, 0);
        chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), 0, dp(18), 0);
        chip.setBackground(rect(0x4D000000, 0, 0, 21));
        chipIcon = icon(R.drawable.ic_play, 16, WHITE);
        chipText = text("", 15, WHITE, true);
        chipText.setLetterSpacing(0.05f);
        chip.addView(chipIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        chip.addView(chipText, lin(WRAP, WRAP, 0f, 8, 0, 0, 0));
        bar.addView(durText, new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(chip, new LinearLayout.LayoutParams(WRAP, dp(42)));
        bar.setOnClickListener(v -> toggle());
        col.addView(bar, lin(MATCH, dp(58), 0f, 0, 10, 0, 0));

        // Start and end time
        col.addView(text("Start and end time", 18, WHITE, true), lin(WRAP, dp(30), 0f, 0, 16, 0, 0));
        LinearLayout box = new LinearLayout(this);
        box.setBackground(rect(PANEL_FILL, PANEL_STROKE, 1, 16));
        box.addView(timeColumn(R.drawable.ic_clock, true), new LinearLayout.LayoutParams(0, MATCH, 1f));
        View divider = new View(this);
        divider.setBackgroundColor(PANEL_STROKE);
        box.addView(divider, new LinearLayout.LayoutParams(dp(1), MATCH));
        box.addView(timeColumn(R.drawable.ic_flag, false), new LinearLayout.LayoutParams(0, MATCH, 1f));
        col.addView(box, lin(MATCH, dp(84), 0f, 0, 6, 0, 0));

        return root;
    }

    private LinearLayout timeColumn(int iconRes, boolean isStart) {
        LinearLayout c = new LinearLayout(this);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.addView(icon(iconRes, 26, 0xFFCFD8FF), new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("", 13, MUTED, false);
        TextView val = text("", 27, WHITE, true);
        TextView day = text("", 13, MUTED, false);
        t.addView(label);
        t.addView(val);
        t.addView(day);
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
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setClickable(true);

        FrameLayout circle = new FrameLayout(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(circleColor);
        circle.setBackground(oval);
        ImageView ic = icon(iconRes, 22, WHITE);
        circle.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        card.addView(circle, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 17, WHITE, true));
        texts.addView(text(sub, 11.5f, 0xFFD3DAFA, false), lin(WRAP, WRAP, 0f, 0, 2, 0, 0));
        card.addView(texts, lin(0, WRAP, 1f, 10, 0, 0, 0));
        return card;
    }

    private void styleCards(int mode) {
        cardSilent.setBackground(rect(mode == Dnd.SILENT ? 0x807440C8 : 0x40582CA0,
                mode == Dnd.SILENT ? 0xFFD9A0FF : 0x59C48CFF, 2, 18));
        cardDnd.setBackground(rect(mode == Dnd.DND ? 0x802854D2 : 0x401E3CA0,
                mode == Dnd.DND ? 0xFF6F9CFF : 0x596496FF, 2, 18));
        cardMedia.setBackground(rect(mode == Dnd.MEDIA ? 0x80198C87 : 0x40146E6E,
                mode == Dnd.MEDIA ? 0xFF4FE0C8 : 0x5946D2BE, 2, 18));
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
        } else if (!Dnd.start(this, selMode, MINUTES[selIdx])) {
            Toast.makeText(this,
                    "Switch on “Sleeping Timer” under Do Not Disturb access, then come back and press Start",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
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

        stops.setSelected(selIdx);
        stops.setLocked(active);
        styleCards(selMode);

        // Banner
        if (active) {
            banner.setBackground(rect(0x66203CA0, 0xBF6E96FF, 2, 18));
            bannerIcon.setColorFilter(0xFF9EC3FF);
            bannerTitle.setTextColor(WHITE);
            bannerTitle.setText("Sleep Timer Active");
        } else {
            banner.setBackground(rect(0x59126440, 0xBF3DDC84, 2, 18));
            bannerIcon.setColorFilter(0xFF6DF0A4);
            bannerTitle.setTextColor(0xFF8AF3B4);
            bannerTitle.setText("Sleep Timer Inactive");
        }

        // Ring
        String clock;
        float clockSp = 30f;
        float frac = 0f;
        String label;
        if (active) {
            if (end == 0) {
                clock = "∞";
                frac = 1f;
                label = "No automatic end";
            } else {
                long left = Math.max(0, end - now);
                clock = clockStr(left);
                frac = (float) left / (float) Math.max(1, end - start);
                label = "Time remaining";
            }
        } else {
            label = "Selected duration";
            if (minutes < 0) {
                clock = "∞";
            } else {
                clock = durWords(minutes);
                clockSp = 22f;
            }
        }
        clockView.setText(clock);
        clockView.setTextSize(clockSp);
        ringLabel.setText(label);
        ring.setFraction(frac);

        // Start / end bar
        GradientDrawable g;
        if (active) {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1D3380, 0xFF101C54});
            g.setStroke(dp(1), 0x99829AFF);
            durText.setVisibility(View.VISIBLE);
            durText.setText(minutes < 0 ? "Until I turn it off" : durTextOf(minutes));
            chipIcon.setImageResource(R.drawable.ic_stop);
            chipText.setText("END");
        } else {
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1FA75C, 0xFF0B7A3C});
            g.setStroke(dp(1), 0x40FFFFFF);
            durText.setVisibility(View.GONE);
            chipIcon.setImageResource(R.drawable.ic_play);
            chipText.setText("START SLEEP TIMER");
        }
        g.setCornerRadius(dp(29));
        bar.setBackground(g);

        // Start and end times
        long shownEnd = active ? end : (minutes < 0 ? 0 : now + minutes * 60_000L);
        startLabel.setText(active ? "Started at" : "Will start");
        startVal.setText(active ? hhmm(start) : "Now");
        String sd = active ? dayStr(start, now) : "";
        startDay.setText(sd);
        startDay.setVisibility(sd.isEmpty() ? View.GONE : View.VISIBLE);
        endLabel.setText(active ? "Ends at" : "Will end at");
        endVal.setText(shownEnd == 0 ? "--:--" : hhmm(shownEnd));
        endDay.setText(shownEnd == 0 ? "No end time" : dayStr(shownEnd, now));
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
                "Best for sleep, meditation or when you need total quiet.", 0x59962838),
                lin(0, MATCH, 1f, 0, 0, 5, 0));
        cols.addView(featureCard(R.drawable.ic_bell, 0xFF3F55FF, "Do Not Disturb",
                "Reduces interruptions but allows important calls.", 0x8C648CFF, 0x401E3CA0,
                new String[]{"Calls from selected contacts can ring through", "Notifications are silenced (or limited)",
                        "Alarms and emergency calls still work", "Ideal for rest while staying available to important people"},
                "Best when you still want to hear from key contacts.", 0x662846BE),
                lin(0, MATCH, 1f, 5, 0, 0, 0));
        sheet.addView(cols, lin(MATCH, WRAP, 0f, 0, 16, 0, 0));

        // Meditation
        LinearLayout med = new LinearLayout(this);
        med.setGravity(Gravity.CENTER_VERTICAL);
        med.setPadding(dp(12), dp(12), dp(12), dp(12));
        med.setBackground(rect(0x40146E6E, 0x8C46D2BE, 1, 16));
        med.addView(iconCircle(R.drawable.ic_music, 0xFF17A596, 38, 20), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout medText = new LinearLayout(this);
        medText.setOrientation(LinearLayout.VERTICAL);
        medText.addView(text("Meditation", 15, WHITE, true));
        medText.addView(text("Music and media keep playing. Calls, alarms and notifications are blocked. "
                + "Best for guided meditation or sleep music.", 11.5f, 0xFFDBE3FF, false));
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
            sb.append(r).append(" Minutes");
        }
        return sb.toString();
    }

    private static String durTextOf(int m) {
        return durWords(m).replace('\n', ' ');
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
