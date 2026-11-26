package com.catstorm.dndtimer;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Shows the approved KipQuiet V27 screens (bundled in assets, works offline) and connects them to the
 * real Do Not Disturb / Silent engine in {@link Dnd}. The screens only display state; alarms, the
 * notification, restoring settings and reboot recovery all stay native.
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF090F2C;
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setTextZoom(100);
        ws.setBuiltInZoomControls(false);
        ws.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return true;   // the screens never navigate anywhere
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "KipNative");
        web.loadUrl("file:///android_asset/kipquiet.html");
        setContentView(web);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Dnd.expireIfDue(this);
        if (web != null) {
            web.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__kqBack ? window.__kqBack() : false", v -> {
            if (!"true".equals(v)) {
                MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    /** Called from the screens' JavaScript. Methods run on a background thread. */
    private class Bridge {

        @JavascriptInterface
        public String arm(String json) {
            try {
                JSONObject o = new JSONObject(json);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (!nm.isNotificationPolicyAccessGranted()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Switch on “KipQuiet” under Do Not Disturb access, then come back and press Start",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    });
                    return "{\"ok\":false,\"reason\":\"access\"}";
                }
                int mode = "silent".equals(o.optString("mode")) ? Dnd.SILENT : Dnd.DND;
                int minutes = Math.max(1, o.optInt("minutes", 60));
                boolean music = o.optBoolean("music", false);
                // faves: true = starred contacts may call, false = no calls; missing = use my own Android settings
                String label = o.optString("label", "Timer");
                int alarms = o.has("alarms") ? (o.optBoolean("alarms") ? 1 : 0) : -1;
                int calls = o.has("faves") ? (o.optBoolean("faves") ? 1 : 0) : -1;
                long startAt = o.optLong("startMs", 0);
                int startMin = o.optInt("startMin", -1);
                int stIdx = startMin < 0 ? 0 : startMin / 15 + 1;
                int mask = 0;
                JSONArray rep = o.optJSONArray("repeat");
                if (startMin >= 0 && rep != null) {
                    for (int i = 0; i < 7 && i < rep.length(); i++) {
                        if (rep.optBoolean(i)) {
                            mask |= 1 << i;
                        }
                    }
                }
                boolean ok = Dnd.start(MainActivity.this, mode, minutes, music, calls, alarms, label, startAt, stIdx, mask);
                if (ok) {
                    Dnd.prefs(MainActivity.this).edit().putInt("runMin", minutes).apply();
                }
                return ok ? "{\"ok\":true}" : "{\"ok\":false}";
            } catch (Exception e) {
                return "{\"ok\":false}";
            }
        }

        @JavascriptInterface
        public String setSchedules(String json) {
            try {
                Dnd.setSchedules(MainActivity.this, json);
                NotificationManager nm = getSystemService(NotificationManager.class);
                return nm.isNotificationPolicyAccessGranted() ? "{\"ok\":true}" : "{\"ok\":false,\"reason\":\"access\"}";
            } catch (Exception e) {
                return "{\"ok\":false}";
            }
        }

        @JavascriptInterface
        public void requestAccess() {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "Switch on “KipQuiet” under Do Not Disturb access so schedules can run",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            });
        }

        @JavascriptInterface
        public void disarm() {
            Dnd.stop(MainActivity.this);
        }

        @JavascriptInterface
        public String state() {
            try {
                Dnd.expireIfDue(MainActivity.this);
                SharedPreferences p = Dnd.prefs(MainActivity.this);
                JSONObject o = new JSONObject();
                if (p.getBoolean(Dnd.KEY_ACTIVE, false)) {
                    long start = p.getLong(Dnd.KEY_START, 0);
                    long end = p.getLong(Dnd.KEY_END, 0);
                    o.put("phase", "running");
                    o.put("startedAt", start);
                    o.put("scheduledFor", JSONObject.NULL);
                    o.put("endsAt", end == 0 ? JSONObject.NULL : end);
                    o.put("runDurationMin", end == 0 ? p.getInt("runMin", 60) : Math.round((end - start) / 60000.0));
                    o.put("mode", p.getInt(Dnd.KEY_MODE, Dnd.DND) == Dnd.SILENT ? "silent" : "dnd");
                    o.put("music", p.getBoolean("music", false));
                    o.put("label", p.getString(Dnd.KEY_LABEL, "Timer"));
                    o.put("faves", p.getInt(Dnd.KEY_RUN_CALLS, -1));
                    o.put("alarms", p.getInt(Dnd.KEY_RUN_ALARMS, -1));
                } else if (p.getBoolean(Dnd.KEY_SCHED, false)) {
                    long at = p.getLong(Dnd.KEY_SCHED_START, 0);
                    int min = p.getInt(Dnd.KEY_SCHED_MIN, 60);
                    o.put("phase", "scheduled");
                    o.put("auto", p.getBoolean(Dnd.KEY_SCHED_AUTO, false));
                    o.put("startedAt", JSONObject.NULL);
                    o.put("scheduledFor", at);
                    o.put("endsAt", at + min * 60_000L);
                    o.put("runDurationMin", min);
                    o.put("mode", p.getInt(Dnd.KEY_SCHED_MODE, Dnd.DND) == Dnd.SILENT ? "silent" : "dnd");
                    o.put("music", p.getBoolean(Dnd.KEY_SCHED_MUSIC, false));
                    o.put("faves", p.getInt(Dnd.KEY_SCHED_CALLS, -1));
                    o.put("alarms", p.getInt(Dnd.KEY_SCHED_ALARMS, -1));
                    int repMask = p.getInt(Dnd.KEY_REP_MASK, 0);
                    o.put("repWait", repMask != 0 && !p.getBoolean(Dnd.KEY_REP_HELD, false));
                    o.put("repMask", repMask);
                    o.put("repLabel", p.getString(Dnd.KEY_SCHED_LABEL, "Timer"));
                } else {
                    o.put("phase", "idle");
                    o.put("startedAt", JSONObject.NULL);
                    o.put("scheduledFor", JSONObject.NULL);
                    o.put("endsAt", JSONObject.NULL);
                }
                o.put("history", new org.json.JSONArray(p.getString(Dnd.KEY_HISTORY, "[]")));
                return o.toString();
            } catch (Exception e) {
                return "{\"phase\":\"idle\",\"startedAt\":null,\"scheduledFor\":null,\"endsAt\":null}";
            }
        }
    }
}
