package app.liftoff.home;

import android.content.Context;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads assets/liftoff.json, which scripts/build.sh copies from the user's liftoff.json. */
final class Config {
    static final class App { final String label, pkg; App(String l, String p) { label = l; pkg = p; } }
    static final class LightsConf {
        String provider = "govee", host = "", token = "", label = "Lights";
        final List<String> devices = new ArrayList<>();
    }

    final List<App> apps = new ArrayList<>();
    LightsConf lights; // null when no lights are set up: the Lights entry is hidden
    final List<String> nowPlayingPackages = new ArrayList<>();
    final boolean weather, celsius, clock24h;
    final double lat, lon;
    final String background; // "apod" or "image"

    private static Config cached;

    static Config get(Context c) {
        if (cached == null) cached = new Config(c);
        return cached;
    }

    private Config(Context c) {
        JSONObject j;
        try (InputStream in = c.getAssets().open("liftoff.json"); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            j = new JSONObject(sc.hasNext() ? sc.next() : "{}");
        } catch (Exception e) { j = new JSONObject(); }

        JSONArray a = j.optJSONArray("apps");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) apps.add(new App(o.optString("label"), o.optString("package")));
        }
        JSONObject l = j.optJSONObject("lights");
        if (l != null) {
            LightsConf lc = new LightsConf();
            lc.provider = l.optString("provider", "govee").toLowerCase().replaceAll("[^a-z]", "");
            lc.host = l.optString("host"); lc.token = l.optString("token"); lc.label = l.optString("label", "Lights");
            JSONArray d = l.optJSONArray("devices");
            if (d == null) d = l.optJSONArray("govee"); // older configs
            if (d != null) for (int i = 0; i < d.length(); i++) lc.devices.add(d.optString(i));
            if (!lc.devices.isEmpty()) { lights = lc; apps.add(new App(lc.label, c.getPackageName())); }
        }
        Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));

        JSONObject w = j.optJSONObject("weather");
        weather = w != null && w.has("latitude") && w.has("longitude");
        lat = w == null ? 0 : w.optDouble("latitude");
        lon = w == null ? 0 : w.optDouble("longitude");
        celsius = w != null && "celsius".equalsIgnoreCase(w.optString("units"));

        JSONObject np = j.optJSONObject("nowPlaying");
        JSONArray p = np == null ? null : np.optJSONArray("packages");
        if (p != null) for (int i = 0; i < p.length(); i++) nowPlayingPackages.add(p.optString(i));

        JSONObject bg = j.optJSONObject("background");
        background = bg == null ? "apod" : bg.optString("mode", "apod");
        JSONObject clock = j.optJSONObject("clock");
        clock24h = clock != null && clock.optBoolean("24h", false);
    }
}
