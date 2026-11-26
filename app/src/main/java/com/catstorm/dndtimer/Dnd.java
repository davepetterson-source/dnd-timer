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
 *  DND    - Do Not Disturb. From a preset the app sets the rules itself: alarms always, media only with
 *           music, calls only from starred contacts when favourites is on. With no call choice (the big
 *           Start button) it uses your own "priority" settings (selected contacts, alarms...)
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
    static final String KEY_SEL_IDX_V2 = "selIdx4";
    static final String KEY_SEL_MUSIC = "selMusic";
    private static final String KEY_POLICY_CHANGED = "ppc";
    private static final String KEY_MUSIC = "music";
    static final String KEY_REP_MASK = "repMask";   // days of the week, bit 0 = Monday ... bit 6 = Sunday
    static final String KEY_REP_HELD = "repHeld";   // a one-off is using the slot; the repeat comes back when it ends
    private static final String KEY_REP_IDX = "repIdx";
    private static final String KEY_REP_MODE = "repMode";
    private static final String KEY_REP_MIN = "repMin";
    private static final String KEY_REP_MUSIC = "repMusic";
    private static final String KEY_REP_CALLS = "repCalls";
    static final String KEY_SCHED_CALLS = "schedCalls";
    private static final String KEY_REP_ALARMS = "repAlarms";
    static final String KEY_SCHED_ALARMS = "schedAlarms";
    private static final String KEY_REP_LABEL = "repLabel";
    static final String KEY_SCHED_LABEL = "schedLabel";
    static final String KEY_LABEL = "runLabel";
    static final String KEY_RUN_CALLS = "runCalls";
    static final String KEY_RUN_ALARMS = "runAlarms";
    static final String KEY_HISTORY = "history";   // JSON array of the last runs, newest first
    static final String KEY_SCHED = "sched";
    static final String KEY_SCHED_START = "schedStart";
    static final String KEY_SCHED_MODE = "schedMode";
    static final String KEY_SCHED_MIN = "schedMin";
    static final String KEY_SCHED_MUSIC = "schedMusic";
    static final String KEY_SCHEDS = "schedList";      // JSON array of the repeating schedules from the page
    static final String KEY_SCHED_AUTO = "schedAuto";  // the waiting start came from that list
    private static final String KEY_SKIP = "schedSkip";
    static final String KEY_SCHED_ONCE = "schedOnce";   // key of a one-off schedule waiting in the slot
    static final String KEY_DONE = "schedDone";         // one-off schedules that have run; the page switches them off   // start time the user cancelled; the list picks the next one
    private static final String CHANNEL = "kipquiet_running";
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
    static boolean start(Context c, int mode, int minutes, boolean music, int calls, int alarms, String label, long startAt, int stIdx, int repMask) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (!nm.isNotificationPolicyAccessGranted()) {
            return false;
        }
        expireIfDue(c);
        boolean repeatWaiting = prefs(c).getInt(KEY_REP_MASK, 0) != 0;
        if (startAt > System.currentTimeMillis() + 1000) {
            SharedPreferences.Editor re = prefs(c).edit();
            if (repMask != 0) {
                re.putInt(KEY_REP_MASK, repMask).putInt(KEY_REP_IDX, stIdx).putInt(KEY_REP_MODE, mode)
                        .putInt(KEY_REP_MIN, minutes).putBoolean(KEY_REP_MUSIC, music).putInt(KEY_REP_CALLS, calls).putInt(KEY_REP_ALARMS, alarms).putString(KEY_REP_LABEL, label)
                        .remove(KEY_REP_HELD);
            } else if (repeatWaiting) {
                re.putBoolean(KEY_REP_HELD, true);   // a one-off pauses the repeat; it resumes afterwards
            } else {
                re.remove(KEY_REP_MASK);
            }
            re.apply();
            prefs(c).edit().putBoolean(KEY_SCHED, true).putLong(KEY_SCHED_START, startAt)
                    .putInt(KEY_SCHED_MODE, mode).putInt(KEY_SCHED_MIN, minutes)
                    .putBoolean(KEY_SCHED_MUSIC, music).putInt(KEY_SCHED_CALLS, calls).putInt(KEY_SCHED_ALARMS, alarms).putString(KEY_SCHED_LABEL, label).apply();
            schedule(c, startAt, pendingBegin(c));
            showNotification(c);
            return true;
        }
        SharedPreferences.Editor ie = prefs(c).edit();
        if (repMask != 0) {
            ie.putInt(KEY_REP_MASK, repMask).putInt(KEY_REP_IDX, stIdx).putInt(KEY_REP_MODE, mode)
                    .putInt(KEY_REP_MIN, minutes).putBoolean(KEY_REP_MUSIC, music).putInt(KEY_REP_CALLS, calls).putInt(KEY_REP_ALARMS, alarms).putString(KEY_REP_LABEL, label)
                    .remove(KEY_REP_HELD);
        } else if (repeatWaiting) {
            ie.putBoolean(KEY_REP_HELD, true);
        }
        ie.apply();
        clearScheduled(c);   // a waiting repeat must not start in the middle of this run
        return activate(c, mode, minutes, music, calls, alarms, label);
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
        int calls = p.getInt(KEY_SCHED_CALLS, -1);
        int alarms = p.getInt(KEY_SCHED_ALARMS, -1);
        String label = p.getString(KEY_SCHED_LABEL, "Timer");
        markOnceDone(c);
        clearScheduled(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.isNotificationPolicyAccessGranted()) {
            activate(c, mode, minutes, music, calls, alarms, label);
        } else {
            nm.cancel(NOTE_ID);
        }
    }

    private static void clearScheduled(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pendingBegin(c));
        prefs(c).edit().remove(KEY_SCHED).remove(KEY_SCHED_START).remove(KEY_SCHED_MODE)
                .remove(KEY_SCHED_MIN).remove(KEY_SCHED_MUSIC).remove(KEY_SCHED_CALLS).remove(KEY_SCHED_ALARMS).remove(KEY_SCHED_LABEL).remove(KEY_SCHED_AUTO).remove(KEY_SCHED_ONCE).apply();
    }

    /** calls: -1 = use my own Android settings, 0 = block all calls, 1 = starred contacts only.
     *  alarms: 0 = alarms silenced too, 1 = alarms allowed, -1 = default (allowed in Do Not Disturb, silenced in Silent). */
    private static boolean activate(Context c, int mode, int minutes, boolean music, int calls, int alarms, String label) {
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

        boolean explicit = mode == DND && calls >= 0;
        boolean silentAlarms = mode == SILENT && alarms == 1;
        boolean changePolicy = music || explicit || silentAlarms;
        if (changePolicy) {
            int cats = NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
            int callS = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
            int msgs = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
            int conv = 2; /* CONVERSATION_SENDERS_NONE */
            if (mode == SILENT) {
                cats = (music ? NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA : 0)
                        | (alarms == 1 ? NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS : 0);
            } else if (explicit) {
                cats = alarms == 0 ? 0 : NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
                if (music) {
                    cats |= NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
                }
                if (calls == 1) {
                    cats |= NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
                }
                callS = NotificationManager.Policy.PRIORITY_SENDERS_STARRED;
                msgs = pol.priorityMessageSenders;
                conv = 2; /* CONVERSATION_SENDERS_NONE */
            } else if (mode == DND) {
                cats = pol.priorityCategories | NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
                callS = pol.priorityCallSenders;
                msgs = pol.priorityMessageSenders;
                conv = Build.VERSION.SDK_INT >= 30 ? pol.priorityConversationSenders : 0;
            }
            nm.setNotificationPolicy(policy(cats, callS, msgs, pol.suppressedVisualEffects, conv));
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
        e.putBoolean(KEY_ACTIVE, true).putBoolean(KEY_MUSIC, music).putLong(KEY_START, now).putLong(KEY_END, end).putInt(KEY_MODE, mode).putString(KEY_LABEL, label).putInt(KEY_RUN_CALLS, calls).putInt(KEY_RUN_ALARMS, alarms);
        e.apply();

        if (end != 0) {
            schedule(c, end, pending(c));
        }
        showNotification(c);
        return true;
    }

    /** The user ended or cancelled it: put everything back and stop any repeating too. */
    static void stop(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_ACTIVE, false) && p.getBoolean(KEY_SCHED, false) && p.getBoolean(KEY_SCHED_AUTO, false)) {
            p.edit().putLong(KEY_SKIP, p.getLong(KEY_SCHED_START, 0)).apply();   // cancelled one night only
            markOnceDone(c);
        }
        stopInner(c);
        rearmList(c, true);
    }

    private static void stopInner(Context c) {
        SharedPreferences p = prefs(c);
        boolean held = p.getBoolean(KEY_REP_HELD, false) && p.getInt(KEY_REP_MASK, 0) != 0;
        if (held) {
            // it was only a one-off pausing a repeat: put the repeat back
            stopCore(c);
            resumeRepeat(c);
        } else {
            p.edit().remove(KEY_REP_MASK).remove(KEY_REP_HELD).apply();
            stopCore(c);
        }
    }

    /** The timer ran out by itself: put everything back, and line up the next repeat if there is one. */
    static void finish(Context c) {
        stopCore(c);
        resumeRepeat(c);
        rearmList(c, true);
    }

    /** Line up the next start of the stored repeat, if there is one. */
    private static void resumeRepeat(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().remove(KEY_REP_HELD).apply();
        int mask = p.getInt(KEY_REP_MASK, 0);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (mask != 0 && nm.isNotificationPolicyAccessGranted()) {
            long at = nextStart(p.getInt(KEY_REP_IDX, 1), mask, System.currentTimeMillis());
            p.edit().putBoolean(KEY_SCHED, true).putLong(KEY_SCHED_START, at)
                    .putInt(KEY_SCHED_MODE, p.getInt(KEY_REP_MODE, DND))
                    .putInt(KEY_SCHED_MIN, p.getInt(KEY_REP_MIN, 60))
                    .putBoolean(KEY_SCHED_MUSIC, p.getBoolean(KEY_REP_MUSIC, false))
                    .putInt(KEY_SCHED_CALLS, p.getInt(KEY_REP_CALLS, -1))
                    .putInt(KEY_SCHED_ALARMS, p.getInt(KEY_REP_ALARMS, -1))
                    .putString(KEY_SCHED_LABEL, p.getString(KEY_REP_LABEL, "Timer")).apply();
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
        if (p.getBoolean(KEY_ACTIVE, false)) {
            recordRun(p);
        }
        c.getSystemService(AlarmManager.class).cancel(pending(c));
        nm.cancel(NOTE_ID);
        p.edit().remove(KEY_LABEL).remove(KEY_RUN_CALLS).remove(KEY_RUN_ALARMS).remove(KEY_ACTIVE).remove(KEY_START).remove(KEY_END).remove(KEY_MODE).remove(KEY_MUSIC).remove(KEY_POLICY_CHANGED).apply();
    }

    /** Remember this run (name, start, real finish) in the list of the last five. */
    private static void recordRun(SharedPreferences p) {
        try {
            long start = p.getLong(KEY_START, 0);
            long planned = p.getLong(KEY_END, 0);
            long now = System.currentTimeMillis();
            long end = planned > 0 ? Math.min(now, planned) : now;
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("name", p.getString(KEY_LABEL, "Timer"));
            o.put("start", start);
            o.put("end", end);
            org.json.JSONArray old = new org.json.JSONArray(p.getString(KEY_HISTORY, "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < 5; i++) {
                out.put(old.get(i));
            }
            p.edit().putString(KEY_HISTORY, out.toString()).apply();
        } catch (Exception ignored) {
            // history is a nicety; never let it break stopping a timer
        }
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
        rearmList(c, false);
    }

    /** The page hands over its list of repeating schedules; the phone then starts them by itself. */
    static void setSchedules(Context c, String json) {
        prefs(c).edit().putString(KEY_SCHEDS, json).remove(KEY_SKIP).apply();
        rearmList(c, true);
    }

    /** Put the soonest enabled schedule in the waiting slot (only when nothing else is running or waiting). */
    static void rearmList(Context c, boolean force) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        boolean waiting = p.getBoolean(KEY_SCHED, false);
        boolean auto = p.getBoolean(KEY_SCHED_AUTO, false);
        if (waiting && (!auto || !force)) {
            return;
        }
        if (!force && !p.contains(KEY_SCHEDS)) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        org.json.JSONObject best = null;
        long bestAt = 0;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(p.getString(KEY_SCHEDS, "[]"));
            long base = Math.max(System.currentTimeMillis(), p.getLong(KEY_SKIP, 0));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("enabled", true)) {
                    continue;
                }
                long at = nextOccurrence(o, base);
                if (at > 0 && (best == null || at < bestAt)) {
                    best = o;
                    bestAt = at;
                }
            }
        } catch (Exception ignored) {
            best = null;
        }
        if (best == null || !nm.isNotificationPolicyAccessGranted()) {
            if (auto) {
                clearScheduled(c);
                nm.cancel(NOTE_ID);
            }
            return;
        }
        org.json.JSONArray fl = best.optJSONArray("flags");
        boolean faves = fl == null || fl.optBoolean(0, true);
        boolean alarms = fl == null || fl.optBoolean(1, true);
        boolean music = fl == null || fl.optBoolean(2, true);
        p.edit().putBoolean(KEY_SCHED, true).putBoolean(KEY_SCHED_AUTO, true).putLong(KEY_SCHED_START, bestAt)
                .putInt(KEY_SCHED_MODE, DND).putInt(KEY_SCHED_MIN, Math.max(1, best.optInt("duration", 60)))
                .putBoolean(KEY_SCHED_MUSIC, music).putInt(KEY_SCHED_CALLS, faves ? 1 : 0).putInt(KEY_SCHED_ALARMS, alarms ? 1 : 0)
                .putString(KEY_SCHED_LABEL, best.optString("name", "Quiet Time"))
                .putString(KEY_SCHED_ONCE, "once".equals(best.optString("repeat")) ? scheduleKey(best) : "").apply();
        schedule(c, bestAt, pendingBegin(c));
        showNotification(c);
    }

    static String scheduleKey(org.json.JSONObject o) {
        return o.optString("name") + "|" + o.optInt("hour") + "|" + o.optInt("minute") + "|" + o.optInt("duration");
    }

    /** A one-off schedule has started (or was cancelled): switch it off here and tell the page to do the same. */
    private static void markOnceDone(Context c) {
        SharedPreferences p = prefs(c);
        String key = p.getString(KEY_SCHED_ONCE, "");
        if (key.isEmpty()) {
            return;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(p.getString(KEY_SCHEDS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if ("once".equals(o.optString("repeat")) && o.optBoolean("enabled", true) && key.equals(scheduleKey(o))) {
                    o.put("enabled", false);
                }
            }
            org.json.JSONArray done = new org.json.JSONArray(p.getString(KEY_DONE, "[]"));
            done.put(key);
            p.edit().putString(KEY_SCHEDS, arr.toString()).putString(KEY_DONE, done.toString()).apply();
        } catch (Exception ignored) {
            // worst case the one-off stays on; never break starting
        }
    }

    /** Next time after base that this schedule starts (page days: 0 = Sunday ... 6 = Saturday), or 0. */
    static long nextOccurrence(org.json.JSONObject o, long base) {
        boolean daily = "daily".equals(o.optString("repeat"));
        org.json.JSONArray days = o.optJSONArray("days");
        java.util.Calendar c = java.util.Calendar.getInstance();
        for (int k = 0; k < 15; k++) {
            c.setTimeInMillis(base);
            c.add(java.util.Calendar.DAY_OF_YEAR, k);
            c.set(java.util.Calendar.HOUR_OF_DAY, o.optInt("hour", 22));
            c.set(java.util.Calendar.MINUTE, o.optInt("minute", 0));
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            int wd = c.get(java.util.Calendar.DAY_OF_WEEK) - 1;
            boolean ok = daily;
            for (int i = 0; !ok && days != null && i < days.length(); i++) {
                ok = days.optInt(i, -1) == wd;
            }
            if (ok && c.getTimeInMillis() > base) {
                return c.getTimeInMillis();
            }
        }
        return 0;
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
            rearmList(c, true);
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

    /** The "KipQuiet active" entry in the pull-down bar: tap to open the app, or press End. */
    private static void showNotification(Context c) {
        SharedPreferences p = prefs(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "KipQuiet running",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.deleteNotificationChannel("running");
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
        String what = (mode == SILENT ? "Silent" : "Do Not Disturb") + (music ? " + Music" : "");
        String endText = end == 0 ? "until you end it" : "Ends " + f.format(new Date(end));
        String title = scheduled ? "KipQuiet Scheduled" : "KipQuiet Active";
        String body = scheduled
                ? what + "\nStarts " + f.format(new Date(start)) + " · " + endText
                : what + "\n" + f.format(new Date(start)) + (end == 0 ? " · until you end it" : " to " + f.format(new Date(end)));

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
