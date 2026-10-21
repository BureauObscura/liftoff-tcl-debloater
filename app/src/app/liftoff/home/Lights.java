package app.liftoff.home;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;

/**
 * Smart lights over the local network, one class per brand. Picked by "lights.provider" in liftoff.json:
 *   govee          Govee LAN API (UDP 4003). devices = light IPs. Turn on "LAN Control" in the Govee app.
 *   hue            Philips Hue bridge. host = bridge IP, token = bridge username, devices = light ids.
 *   wled           WLED controllers. devices = IPs.
 *   homeassistant  Home Assistant. host = URL, token = long-lived access token, devices = light entity ids.
 *                  Covers anything Home Assistant can control (LIFX, Nanoleaf, Kasa, Zigbee, Matter…).
 * Every call blocks, so call from a background thread. Device i is 0-based.
 */
abstract class Lights {
    final List<String> devices;
    final String host, token;

    Lights(Config.LightsConf c) { devices = c.devices; host = c.host; token = c.token; }

    static Lights from(Config.LightsConf c) {
        if (c == null || c.devices.isEmpty()) return null;
        switch (c.provider) {
            case "hue": return new Hue(c);
            case "wled": return new Wled(c);
            case "homeassistant": return new HomeAssistant(c);
            default: return new Govee(c);
        }
    }

    int count() { return devices.size(); }
    /** Fastest a scene may update, so cloud-free bridges aren't flooded. */
    long minStepMs() { return 0; }

    abstract void power(int i, boolean on) throws Exception;
    abstract void color(int i, int r, int g, int b) throws Exception;
    abstract void temp(int i, int kelvin) throws Exception;
    abstract void bright(int i, int percent) throws Exception;

    // ---- shared plumbing ----
    static void http(String method, String url, String auth, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000); c.setReadTimeout(3000);
        c.setRequestMethod(method); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (auth != null) c.setRequestProperty("Authorization", "Bearer " + auth);
        try (OutputStream o = c.getOutputStream()) { o.write(json.getBytes("UTF-8")); }
        int code = c.getResponseCode();
        c.getInputStream().close();
        if (code >= 400) throw new Exception("HTTP " + code);
    }

    /** Approximate RGB for a white color temperature (Tanner Helland's fit), for lights with no white mode. */
    static int[] kelvinRgb(int k) {
        double t = k / 100.0, r, g, b;
        r = t <= 66 ? 255 : 329.698727446 * Math.pow(t - 60, -0.1332047592);
        g = t <= 66 ? 99.4708025861 * Math.log(t) - 161.1195681661 : 288.1221695283 * Math.pow(t - 60, -0.0755148492);
        b = t >= 66 ? 255 : t <= 19 ? 0 : 138.5177312231 * Math.log(t - 10) - 305.0447927307;
        return new int[]{clamp(r), clamp(g), clamp(b)};
    }
    static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

    // ---- Govee (UDP, no account) ----
    static final class Govee extends Lights {
        Govee(Config.LightsConf c) { super(c); }
        void send(int i, String json) throws Exception {
            byte[] d = json.getBytes("UTF-8");
            try (DatagramSocket s = new DatagramSocket()) {
                s.send(new DatagramPacket(d, d.length, InetAddress.getByName(devices.get(i)), 4003));
            }
        }
        void power(int i, boolean on) throws Exception { send(i, "{\"msg\":{\"cmd\":\"turn\",\"data\":{\"value\":" + (on ? 1 : 0) + "}}}"); }
        void bright(int i, int p) throws Exception { send(i, "{\"msg\":{\"cmd\":\"brightness\",\"data\":{\"value\":" + Math.max(1, p) + "}}}"); }
        void color(int i, int r, int g, int b) throws Exception {
            send(i, "{\"msg\":{\"cmd\":\"colorwc\",\"data\":{\"color\":{\"r\":" + r + ",\"g\":" + g + ",\"b\":" + b + "},\"colorTemInKelvin\":0}}}");
        }
        void temp(int i, int k) throws Exception {
            send(i, "{\"msg\":{\"cmd\":\"colorwc\",\"data\":{\"color\":{\"r\":0,\"g\":0,\"b\":0},\"colorTemInKelvin\":" + k + "}}}");
        }
    }

    // ---- Philips Hue bridge (local REST API) ----
    static final class Hue extends Lights {
        Hue(Config.LightsConf c) { super(c); }
        @Override long minStepMs() { return 400; } // the bridge handles about 10 commands a second
        void state(int i, String json) throws Exception {
            http("PUT", "http://" + host + "/api/" + token + "/lights/" + devices.get(i) + "/state", null, json);
        }
        void power(int i, boolean on) throws Exception { state(i, "{\"on\":" + on + "}"); }
        void bright(int i, int p) throws Exception { state(i, "{\"on\":true,\"bri\":" + Math.max(1, p * 254 / 100) + "}"); }
        void temp(int i, int k) throws Exception {
            state(i, "{\"on\":true,\"ct\":" + Math.max(153, Math.min(500, 1_000_000 / k)) + "}");
        }
        void color(int i, int r, int g, int b) throws Exception {
            // sRGB → CIE xy, the color space the bridge takes.
            double R = lin(r), G = lin(g), B = lin(b);
            double X = R * 0.664511 + G * 0.154324 + B * 0.162028, Y = R * 0.283881 + G * 0.668433 + B * 0.047685,
                    Z = R * 0.000088 + G * 0.072310 + B * 0.986039, s = X + Y + Z;
            double x = s == 0 ? 0.3227 : X / s, y = s == 0 ? 0.329 : Y / s;
            state(i, String.format(java.util.Locale.US, "{\"on\":true,\"xy\":[%.4f,%.4f],\"transitiontime\":3}", x, y));
        }
        static double lin(int v) { double c = v / 255.0; return c > 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92; }
    }

    // ---- WLED (local JSON API) ----
    static final class Wled extends Lights {
        Wled(Config.LightsConf c) { super(c); }
        void state(int i, String json) throws Exception { http("POST", "http://" + devices.get(i) + "/json/state", null, json); }
        void power(int i, boolean on) throws Exception { state(i, "{\"on\":" + on + "}"); }
        void bright(int i, int p) throws Exception { state(i, "{\"on\":true,\"bri\":" + Math.max(1, p * 255 / 100) + "}"); }
        void color(int i, int r, int g, int b) throws Exception { state(i, "{\"on\":true,\"seg\":[{\"fx\":0,\"col\":[[" + r + "," + g + "," + b + "]]}]}"); }
        void temp(int i, int k) throws Exception { int[] c = kelvinRgb(k); color(i, c[0], c[1], c[2]); }
    }

    // ---- Home Assistant (REST API with a long-lived token) ----
    static final class HomeAssistant extends Lights {
        HomeAssistant(Config.LightsConf c) { super(c); }
        @Override long minStepMs() { return 300; }
        void call(String service, int i, String extra) throws Exception {
            http("POST", host.replaceAll("/+$", "") + "/api/services/light/" + service, token,
                    "{\"entity_id\":\"" + devices.get(i) + "\"" + extra + "}");
        }
        void power(int i, boolean on) throws Exception { call(on ? "turn_on" : "turn_off", i, ""); }
        void bright(int i, int p) throws Exception { call("turn_on", i, ",\"brightness_pct\":" + Math.max(1, p)); }
        void color(int i, int r, int g, int b) throws Exception { call("turn_on", i, ",\"rgb_color\":[" + r + "," + g + "," + b + "]"); }
        void temp(int i, int k) throws Exception { call("turn_on", i, ",\"color_temp_kelvin\":" + k); }
    }
}
