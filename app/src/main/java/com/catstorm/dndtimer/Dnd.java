package com.catstorm.dndtimer;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * All the Do Not Disturb logic. Three modes:
 *  SILENT - total silence (calls, notifications, sounds, alarms and media all blocked)
 *  DND    - normal Do Not Disturb using your own "priority" settings (selected contacts, alarms...)
 *  MEDIA  - Meditation: only media (music/video) is allowed; calls, alarms and notifications blocked
 * Whatever DND was set to before is saved at the start and put back when the timer ends.
 */
final class Dnd {

    static final int SILENT = 0;
    static final int DND = 1;
    static final int MEDIA = 2;

    static final String ACTION_END = "com.catstorm.dndtimer.END";

    static final String KEY_ACTIVE = "active";
    static final String KEY_START = "start";
    static final String KEY_END = "end";          // 0 = no automatic end
    static final String KEY_MODE = "mode";
    static final String KEY_SEL_MODE = "selMode";
    static final String KEY_SEL_IDX = "selIdx";

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

    /** Starts a session. minutes < 0 means "until I turn it off". False if DND access is not granted. */
    static boolean start(Context c, int mode, int minutes) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            return false;
        }
        expireIfDue(c);

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

        if (mode == SILENT) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        } else if (mode == MEDIA) {
            nm.setNotificationPolicy(policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    pol.suppressedVisualEffects,
                    2 /* CONVERSATION_SENDERS_NONE */));
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        }

        long now = System.currentTimeMillis();
        long end = minutes < 0 ? 0 : now + minutes * 60_000L;
        e.putBoolean(KEY_ACTIVE, true).putLong(KEY_START, now).putLong(KEY_END, end).putInt(KEY_MODE, mode);
        e.apply();

        if (end != 0) {
            schedule(c, end);
        }
        return true;
    }

    /** Puts DND back exactly how it was and clears the timer. */
    static void stop(Context c) {
        SharedPreferences p = prefs(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (p.getBoolean(KEY_ACTIVE, false) && nm.isNotificationPolicyAccessGranted()) {
            if (p.getInt(KEY_MODE, DND) == MEDIA) {
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
        p.edit().remove(KEY_ACTIVE).remove(KEY_START).remove(KEY_END).remove(KEY_MODE).apply();
    }

    /** If a timer ran out while nothing was listening, finish it now. */
    static void expireIfDue(Context c) {
        SharedPreferences p = prefs(c);
        long end = p.getLong(KEY_END, 0);
        if (p.getBoolean(KEY_ACTIVE, false) && end != 0 && end <= System.currentTimeMillis()) {
            stop(c);
        }
    }

    /** After a reboot the alarm is gone: re-arm it, or finish the timer if it already ended. */
    static void onBoot(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        long end = p.getLong(KEY_END, 0);
        if (end == 0) {
            return;
        }
        if (end <= System.currentTimeMillis()) {
            stop(c);
        } else {
            schedule(c, end);
        }
    }

    private static NotificationManager.Policy policy(int cats, int calls, int msgs, int effects, int conv) {
        if (Build.VERSION.SDK_INT >= 30) {
            return new NotificationManager.Policy(cats, calls, msgs, effects, conv);
        }
        return new NotificationManager.Policy(cats, calls, msgs, effects);
    }

    private static void schedule(Context c, long endMillis) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(c);
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
}
