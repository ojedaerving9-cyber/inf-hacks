package com.infhacks.window;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final List<String> pkgs = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private boolean showSystem = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mod", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView head = new TextView(this);
        head.setText("Mod Manager - single-player only. Any multiplayer sign blocks an app.");
        head.setPadding(24, 24, 24, 24);
        Button toggle = new Button(this);
        toggle.setText("Show system apps: OFF");
        toggle.setOnClickListener(v -> {
            showSystem = !showSystem;
            toggle.setText("Show system apps: " + (showSystem ? "ON" : "OFF"));
            refresh();
        });
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> open(pkgs.get(i)));
        root.addView(head);
        root.addView(toggle);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        refresh();
    }

    private Set<String> allow() { return new HashSet<>(prefs.getStringSet("allow", new HashSet<>())); }

    private void refresh() {
        PackageManager pm = getPackageManager();
        Set<String> allow = allow();
        List<String[]> items = new ArrayList<>(); // {sortKey, pkg, row}
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system && !showSystem) continue;
            boolean blocked = MultiplayerGuard.check(pm, ai.packageName).blocked;
            int rank = blocked ? 2 : allow.contains(ai.packageName) ? 0 : 1;
            String tag = blocked ? "BLOCKED" : rank == 0 ? "ENABLED" : "not enabled";
            String label = String.valueOf(pm.getApplicationLabel(ai));
            items.add(new String[]{rank + label.toLowerCase(), ai.packageName,
                    label + "  [" + tag + "]\n" + ai.packageName});
        }
        Collections.sort(items, (a, b) -> a[0].compareTo(b[0]));
        pkgs.clear(); rows.clear();
        for (String[] it : items) { pkgs.add(it[1]); rows.add(it[2]); }
        adapter.notifyDataSetChanged();
    }

    private void open(String pkg) {
        MultiplayerGuard.Verdict v = MultiplayerGuard.check(getPackageManager(), pkg);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(pkg);
        if (v.blocked) {
            d.setMessage("Blocked - mods stop here.\n\n" + String.join("\n", v.reasons))
             .setPositiveButton("OK", null);
        } else {
            boolean on = allow().contains(pkg);
            d.setMessage("No multiplayer signs found (heuristic, not proof). Only enable if you know this app is single-player.")
             .setPositiveButton(on ? "Disable" : "Enable as single-player", (x, y) -> {
                 Set<String> a = allow();
                 if (on) a.remove(pkg); else a.add(pkg);
                 prefs.edit().putStringSet("allow", a).apply();
                 refresh();
             });
            if (on) d.setNeutralButton("Launch", (x, y) -> launchIfSafe(pkg));
            d.setNegativeButton("Download APK", (x, y) -> exportApk(pkg));
        }
        d.show();
    }

    /** Re-checks the guard at launch time; mod actions must go through this gate. */
    private void launchIfSafe(String pkg) {
        if (MultiplayerGuard.check(getPackageManager(), pkg).blocked) { refresh(); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
    }

    /** Copies the app's APK file(s) to Downloads/QuestModManager. Guard is re-checked first. */
    private void exportApk(String pkg) {
        if (MultiplayerGuard.check(getPackageManager(), pkg).blocked) {
            Toast.makeText(this, "Blocked - not exported", Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        new Thread(() -> {
            String msg;
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                List<String> files = new ArrayList<>();
                files.add(ai.sourceDir);
                if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) files.add(s);
                int n = 0;
                for (String f : files) {
                    String name = n == 0 ? pkg + ".apk" : pkg + "-split" + n + ".apk";
                    copy(new File(f), name);
                    n++;
                }
                msg = "Saved " + n + " file(s) to Downloads/QuestModManager";
            } catch (Exception e) {
                msg = "Export failed: " + e.getMessage();
            }
            final String out = msg;
            runOnUiThread(() -> Toast.makeText(this, out, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void copy(File src, String name) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
        cv.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
        cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/QuestModManager");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new Exception("could not create file");
        try (InputStream in = new FileInputStream(src);
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }
}
