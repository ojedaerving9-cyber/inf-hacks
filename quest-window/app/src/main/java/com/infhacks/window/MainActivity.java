package com.infhacks.window;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final List<String> pkgs = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("mod", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        TextView head = new TextView(this);
        head.setText("Mod Manager - single-player only. Any multiplayer sign blocks an app.");
        head.setPadding(24, 24, 24, 24);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> open(pkgs.get(i)));
        root.addView(head);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        refresh();
    }

    private Set<String> allow() { return new HashSet<>(prefs.getStringSet("allow", new HashSet<>())); }

    private void refresh() {
        PackageManager pm = getPackageManager();
        pkgs.clear(); rows.clear();
        Set<String> allow = allow();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            boolean blocked = MultiplayerGuard.check(pm, ai.packageName).blocked;
            String tag = blocked ? "BLOCKED" : allow.contains(ai.packageName) ? "ENABLED" : "not enabled";
            pkgs.add(ai.packageName);
            rows.add(pm.getApplicationLabel(ai) + "  [" + tag + "]\n" + ai.packageName);
        }
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
            d.setNegativeButton("Cancel", null);
        }
        d.show();
    }

    /** Re-checks the guard at launch time; mod actions must go through this gate. */
    private void launchIfSafe(String pkg) {
        if (MultiplayerGuard.check(getPackageManager(), pkg).blocked) { refresh(); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
    }
}
