package com.infhacks.window;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Fail-closed check: any sign of online/multiplayer blocks the app. Heuristic, not proof. */
public final class MultiplayerGuard {
    private static final String[] SIGNALS = {
        "photonengine", "exitgames", "photon.realtime", "libphoton", "unity.netcode", "libnetcode",
        "mirror.networking", "fishnet", "playfab.multiplayer", "gamelift", "nakama", "heroiclabs",
        "oculus.platform", "oculus.multiplayer", "vivox", "agora.rtc", "io.agora", "steamworks",
        "epiconline", "matchmaking", "matchmaker", "multiplayer", "colyseus", "normcore",
        "dissonance", "braincloud", "unityengine.networking", "roblox"
    };
    private static final String[] BLOCKED_PACKAGES = {
        "com.roblox.client", "com.oculus.vrshell", "com.facebook.horizon", "com.oculus.horizon"
    };

    public static final class Verdict {
        public final boolean blocked;
        public final List<String> reasons;
        Verdict(List<String> r) { reasons = r; blocked = !r.isEmpty(); }
    }

    public static Verdict check(PackageManager pm, String pkg) {
        List<String> why = new ArrayList<>();
        for (String b : BLOCKED_PACKAGES) if (b.equals(pkg)) why.add("known multiplayer app");
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS);
            if (pi.activities != null) for (ActivityInfo a : pi.activities) scan(a.name, why);
            if (pi.services != null) for (ServiceInfo s : pi.services) scan(s.name, why);
            if (pi.requestedPermissions != null) for (String p : pi.requestedPermissions) scan(p, why);
            try (ZipFile z = new ZipFile(pi.applicationInfo.sourceDir)) {
                Enumeration<? extends ZipEntry> e = z.entries();
                while (e.hasMoreElements()) scan(e.nextElement().getName(), why);
            }
        } catch (Exception ex) {
            why.add("could not inspect app (" + ex.getClass().getSimpleName() + ")"); // fail closed
        }
        // de-duplicate
        List<String> out = new ArrayList<>();
        for (String w : why) if (!out.contains(w)) out.add(w);
        return new Verdict(out);
    }

    private static void scan(String s, List<String> why) {
        if (s == null) return;
        String l = s.toLowerCase(Locale.ROOT);
        for (String sig : SIGNALS) if (l.contains(sig)) { why.add("multiplayer signal: " + sig); return; }
    }
}
