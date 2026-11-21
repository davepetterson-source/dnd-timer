package com.catstorm.dndtimer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * All the Do Not Disturb logic. Three modes:
 *  SILENT - total silence (calls, notifications, sounds, alarms all blocked)
 *  DND    - normal Do Not Disturb using your own "priority" settings (selected contacts, alarms...)
 * "Keep music playing" can be added to either: music/video/games are let through, nothing else
 * changes. (Silent + music = only media allowed; DND + music = your settings plus media.)
 * Whatever DND was set to before is saved at the start and put back when the timer ends.
 */
final class Dnd {

    static final int SILENT = 0;
    static final int DND = 1;

    static final String ACTION_END = "com.catstorm.dndtimer.END";      // timer ran out (alarm)
    static final String ACTION_STOP = "com.catstorm.dndtimer.STOP";    // user pressed End / Cancel
    static final String ACTION_BEGIN = "com.catstorm.dndtimer.BEGIN";

    static final String KEY_ACTIVE = "active";
    static final String KEY_START = "start";
    static final String KEY_END = "end";          // 0 = no automatic end
    static final String KEY_MODE = "mode";
    static final String KEY_SEL_MODE = "selMode2";
    static final String KEY_SEL_IDX = "selIdx";
    static final String KEY_SEL_IDX_V2 = "selIdx2";
    static final String KEY_SEL_MUSIC = "selMusic";
    private static final String KEY_POLICY_CHANGED = "ppc";
    private static final String KEY_MUSIC = "music";
    static final String KEY_REP_MASK = "repMask";   // days of the week, bit 0 = Monday ... bit 6 = Sunday
    private static final String KEY_REP_IDX = "repIdx";
    private static final String KEY_REP_MODE = "repMode";
    private static final String KEY_REP_MIN = "repMin";
    private static final String KEY_REP_MUSIC = "repMusic";
    static final String KEY_SCHED = "sched";
    static final String KEY_SCHED_START = "schedStart";
    static final String KEY_SCHED_MODE = "schedMode";
    static final String KEY_SCHED_MIN = "schedMin";
    static final String KEY_SCHED_MUSIC = "schedMusic";
    private static final String CHANNEL = "running";
    private static final int NOTE_ID = 1;

    private static final String KEY_PREV_FILTER = "pf";
    private static final String KEY_PREV_CATS = "pc";
    private static final String KEY_PREV_CALLS = "pcs";
    private static final String KEY_PREV_MSGS = "pms";
    private static final String KEY_PREV_EFFECTS = "pe";
    private static final String KEY_PREV_CONV = "pcv";

    private Dnd() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sleeping_timer", Context.MODE_PRIVATE);
    }

    /**
     * Starts a session now, or at startAt (a clock time in the future) if that is given.
     * minutes < 0 means "until I turn it off". False if DND access is not granted.
     */
    static boolean start(Context c, int mode, int minutes, boolean music, long startAt, int stIdx, int repMask) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            return false;
        }
        expireIfDue(c);
        if (startAt > System.currentTimeMillis() + 1000) {
            SharedPreferences.Editor re = prefs(c).edit();
            if (repMask != 0) {
                re.putInt(KEY_REP_MASK, repMask).putInt(KEY_REP_IDX, stIdx).putInt(KEY_REP_MODE, mode)
                        .putInt(KEY_REP_MIN, minutes).putBoolean(KEY_REP_MUSIC, music);
            } else {
                re.remove(KEY_REP_MASK);
            }
            re.apply();
            prefs(c).edit().putBoolean(KEY_SCHED, true).putLong(KEY_SCHED_START, startAt)
                    .putInt(KEY_SCHED_MODE, mode).putInt(KEY_SCHED_MIN, minutes)
                    .putBoolean(KEY_SCHED_MUSIC, music).apply();
            schedule(c, startAt, pendingBegin(c));
            showNotification(c);
            return true;
        }
        return activate(c, mode, minutes, music);
    }

    /** The scheduled start time has arrived: switch on for real. */
    static void beginScheduled(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_SCHED, false)) {
            return;
        }
        int mode = p.getInt(KEY_SCHED_MODE, DND);
        int minutes = p.getInt(KEY_SCHED_MIN, 60);
        boolean music = p.getBoolean(KEY_SCHED_MUSIC, false);
        clearScheduled(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.isNotificationPolicyAccessGranted()) {
            activate(c, mode, minutes, music);
        } else {
            nm.cancel(NOTE_ID);
        }
    }

    private static void clearScheduled(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pendingBegin(c));
        prefs(c).edit().remove(KEY_SCHED).remove(KEY_SCHED_START).remove(KEY_SCHED_MODE)
                .remove(KEY_SCHED_MIN).remove(KEY_SCHED_MUSIC).apply();
    }

    private static boolean activate(Context c, int mode, int minutes, boolean music) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        NotificationManager.Policy pol = nm.getNotificationPolicy();

        if (!p.getBoolean(KEY_ACTIVE, false)) {
            int prev = nm.getCurrentInterruptionFilter();
            if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                prev = NotificationManager.INTERRUPTION_FILTER_ALL;
            }
            e.putInt(KEY_PREV_FILTER, prev);
            e.putInt(KEY_PREV_CATS, pol.priorityCategories);
            e.putInt(KEY_PREV_CALLS, pol.priorityCallSenders);
            e.putInt(KEY_PREV_MSGS, pol.priorityMessageSenders);
            e.putInt(KEY_PREV_EFFECTS, pol.suppressedVisualEffects);
            e.putInt(KEY_PREV_CONV, Build.VERSION.SDK_INT >= 30 ? pol.priorityConversationSenders : 0);
        }

        boolean changePolicy = music;
        if (changePolicy) {
            int cats = NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
            int calls = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
            int msgs = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
            int conv = 2; /* CONVERSATION_SENDERS_NONE */
            if (mode == DND) {
                cats = pol.priorityCategories | NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
                calls = pol.priorityCallSenders;
                msgs = pol.priorityMessageSenders;
                conv = Build.VERSION.SDK_INT >= 30 ? pol.priorityConversationSenders : 0;
            }
            nm.setNotificationPolicy(policy(cats, calls, msgs, pol.suppressedVisualEffects, conv));
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } else if (mode == SILENT) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        }
        if (!p.getBoolean(KEY_ACTIVE, false) || changePolicy) {
            e.putBoolean(KEY_POLICY_CHANGED, changePolicy || p.getBoolean(KEY_POLICY_CHANGED, false));
        }

        long now = System.currentTimeMillis();
        long end = minutes < 0 ? 0 : now + minutes * 60_000L;
        e.putBoolean(KEY_ACTIVE, true).putBoolean(KEY_MUSIC, music).putLong(KEY_START, now).putLong(KEY_END, end).putInt(KEY_MODE, mode);
        e.apply();

        if (end != 0) {
            schedule(c, end, pending(c));
        }
        showNotification(c);
        return true;
    }

    /** The user ended or cancelled it: put everything back and stop any repeating too. */
    static void stop(Context c) {
        prefs(c).edit().remove(KEY_REP_MASK).apply();
        stopCore(c);
    }

    /** The timer ran out by itself: put everything back, and line up the next repeat if there is one. */
    static void finish(Context c) {
        stopCore(c);
        SharedPreferences p = prefs(c);
        int mask = p.getInt(KEY_REP_MASK, 0);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (mask != 0 && nm.isNotificationPolicyAccessGranted()) {
            long at = nextStart(p.getInt(KEY_REP_IDX, 1), mask, System.currentTimeMillis());
            p.edit().putBoolean(KEY_SCHED, true).putLong(KEY_SCHED_START, at)
                    .putInt(KEY_SCHED_MODE, p.getInt(KEY_REP_MODE, DND))
                    .putInt(KEY_SCHED_MIN, p.getInt(KEY_REP_MIN, 60))
                    .putBoolean(KEY_SCHED_MUSIC, p.getBoolean(KEY_REP_MUSIC, false)).apply();
            schedule(c, at, pendingBegin(c));
            showNotification(c);
        }
    }

    /** Next time the clock reads the chosen time (15-minute steps from 00:00, idx 1 = 00:00) on an allowed day. */
    static long nextStart(int stIdx, int mask, long now) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        for (int k = 0; k < 9; k++) {
            c.setTimeInMillis(now);
            c.add(java.util.Calendar.DAY_OF_YEAR, k);
            c.set(java.util.Calendar.HOUR_OF_DAY, 0);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            c.add(java.util.Calendar.MINUTE, (stIdx - 1) * 15);
            int wd = (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
            if (c.getTimeInMillis() > now && (mask == 0 || (mask & (1 << wd)) != 0)) {
                return c.getTimeInMillis();
            }
        }
        return now + 86_400_000L;
    }

    private static void stopCore(Context c) {
        clearScheduled(c);
        SharedPreferences p = prefs(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (p.getBoolean(KEY_ACTIVE, false) && nm.isNotificationPolicyAccessGranted()) {
            if (p.getBoolean(KEY_POLICY_CHANGED, false)) {
                nm.setNotificationPolicy(policy(
                        p.getInt(KEY_PREV_CATS, 0),
                        p.getInt(KEY_PREV_CALLS, 0),
                        p.getInt(KEY_PREV_MSGS, 0),
                        p.getInt(KEY_PREV_EFFECTS, 0),
                        p.getInt(KEY_PREV_CONV, 0)));
            }
            nm.setInterruptionFilter(
                    p.getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL));
        }
        c.getSystemService(AlarmManager.class).cancel(pending(c));
        nm.cancel(NOTE_ID);
        p.edit().remove(KEY_ACTIVE).remove(KEY_START).remove(KEY_END).remove(KEY_MODE).remove(KEY_MUSIC).remove(KEY_POLICY_CHANGED).apply();
    }

    /** If a timer ran out while nothing was listening, finish it now. */
    static void expireIfDue(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(KEY_SCHED, false) && p.getLong(KEY_SCHED_START, 0) <= System.currentTimeMillis()) {
            beginScheduled(c);
        }
        long end = p.getLong(KEY_END, 0);
        if (p.getBoolean(KEY_ACTIVE, false) && end != 0 && end <= System.currentTimeMillis()) {
            finish(c);
        }
    }

    /** After a reboot the alarm is gone: re-arm it, or finish the timer if it already ended. */
    static void onBoot(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(KEY_SCHED, false)) {
            long st = p.getLong(KEY_SCHED_START, 0);
            if (st <= System.currentTimeMillis()) {
                beginScheduled(c);
            } else {
                schedule(c, st, pendingBegin(c));
                showNotification(c);
            }
        }
        p = prefs(c);
        if (!p.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        long end = p.getLong(KEY_END, 0);
        showNotification(c);
        if (end == 0) {
            return;
        }
        if (end <= System.currentTimeMillis()) {
            finish(c);
        } else {
            schedule(c, end, pending(c));
        }
    }

    private static NotificationManager.Policy policy(int cats, int calls, int msgs, int effects, int conv) {
        if (Build.VERSION.SDK_INT >= 30) {
            return new NotificationManager.Policy(cats, calls, msgs, effects, conv);
        }
        return new NotificationManager.Policy(cats, calls, msgs, effects);
    }

    private static void schedule(Context c, long endMillis, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, pi);
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, DndReceiver.class).setAction(ACTION_END);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent pendingBegin(Context c) {
        Intent i = new Intent(c, DndReceiver.class).setAction(ACTION_BEGIN);
        return PendingIntent.getBroadcast(c, 3, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The "Sleep Timer active" entry in the pull-down bar: tap to open the app, or press End. */
    private static void showNotification(Context c) {
        SharedPreferences p = prefs(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sleep Timer running",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        boolean scheduled = p.getBoolean(KEY_SCHED, false);
        int mode = scheduled ? p.getInt(KEY_SCHED_MODE, DND) : p.getInt(KEY_MODE, DND);
        boolean music = scheduled ? p.getBoolean(KEY_SCHED_MUSIC, false) : p.getBoolean(KEY_MUSIC, false);
        long start;
        long end;
        if (scheduled) {
            start = p.getLong(KEY_SCHED_START, 0);
            int min = p.getInt(KEY_SCHED_MIN, 60);
            end = min < 0 ? 0 : start + min * 60_000L;
        } else {
            start = p.getLong(KEY_START, 0);
            end = p.getLong(KEY_END, 0);
        }

        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String what = (mode == SILENT ? "Silent" : "Do Not Disturb") + (music ? " + music" : "");
        String endText = end == 0 ? "until you end it" : "ends " + f.format(new Date(end));
        String title = scheduled ? "Sleep Timer scheduled" : "Sleep Timer active";
        String body = scheduled
                ? "Starts " + f.format(new Date(start)) + " · " + what + " · " + endText
                : what + " · " + f.format(new Date(start)) + (end == 0 ? " · until you end it" : " to " + f.format(new Date(end)));

        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent endNow = PendingIntent.getBroadcast(c, 1,
                new Intent(c, DndReceiver.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, scheduled ? "Cancel" : "End", endNow).build());
        nm.notify(NOTE_ID, b.build());
    }
}
