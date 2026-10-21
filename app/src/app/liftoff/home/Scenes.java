package app.liftoff.home;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

/**
 * Animated and two-light scenes for Govee lights (LAN API, UDP 4003). With more than two lights,
 * odd and even lights alternate between the scene's two colors.
 * Runs on its own thread inside the app process, which OverlayService keeps alive,
 * so a scene keeps going after you leave the Lights screen. Any plain button stops it.
 */
final class Scenes {
    interface Frame { /** @return {r,g,b, r,g,b, brightness} for lamp 0 and lamp 1 at time t seconds */ int[] at(double t); }

    static String[] LAMPS = new String[0]; // set by LightsActivity before a scene starts
    private static Thread runner;
    private static volatile int generation;
    private static final Random rnd = new Random();

    static void stop() { generation++; }

    static void play(Frame f, long stepMs) {
        final int gen = ++generation;
        runner = new Thread(() -> {
            int[] last = null;
            long t0 = System.currentTimeMillis();
            try (DatagramSocket s = new DatagramSocket()) {
                send(s, -1, "{\"msg\":{\"cmd\":\"turn\",\"data\":{\"value\":1}}}");
                while (gen == generation) {
                    int[] c = f.at((System.currentTimeMillis() - t0) / 1000.0);
                    for (int i = 0; i < 2; i++) {
                        if (last == null || last[i * 3] != c[i * 3] || last[i * 3 + 1] != c[i * 3 + 1] || last[i * 3 + 2] != c[i * 3 + 2])
                            send(s, i, "{\"msg\":{\"cmd\":\"colorwc\",\"data\":{\"color\":{\"r\":" + c[i * 3] + ",\"g\":" + c[i * 3 + 1]
                                    + ",\"b\":" + c[i * 3 + 2] + "},\"colorTemInKelvin\":0}}}");
                    }
                    if (last == null || last[6] != c[6])
                        send(s, -1, "{\"msg\":{\"cmd\":\"brightness\",\"data\":{\"value\":" + c[6] + "}}}");
                    last = c;
                    if (stepMs == 0) break; // static scene: set once
                    Thread.sleep(stepMs);
                }
            } catch (Exception ignored) { }
        });
        runner.setDaemon(true);
        runner.start();
    }

    private static void send(DatagramSocket s, int lamp, String json) throws Exception {
        byte[] d = json.getBytes("UTF-8");
        for (int i = 0; i < LAMPS.length; i++)
            if (lamp < 0 || lamp == i % 2) s.send(new DatagramPacket(d, d.length, InetAddress.getByName(LAMPS[i]), 4003));
    }

    // ---- helpers ----
    static int[] hsv(double h, double s, double v) {
        h = ((h % 360) + 360) % 360;
        double c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return new int[]{(int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255)};
    }
    static int[] mix(int[] a, int[] b, double k) {
        return new int[]{(int) (a[0] + (b[0] - a[0]) * k), (int) (a[1] + (b[1] - a[1]) * k), (int) (a[2] + (b[2] - a[2]) * k)};
    }
    static int[] two(int[] a, int[] b, int bright) { return new int[]{a[0], a[1], a[2], b[0], b[1], b[2], bright}; }
    static double wave(double t, double period) { return (Math.sin(2 * Math.PI * t / period) + 1) / 2; }

    // ---- scenes ----
    /** Tesla red and Earth blue. */
    static final Frame STARMAN = t -> two(new int[]{255, 25, 0}, new int[]{0, 90, 255}, 70);
    /** Hot pink and cyan, like an 80s synthwave poster. */
    static final Frame SYNTHWAVE = t -> two(new int[]{255, 0, 140}, new int[]{0, 220, 255}, 70);
    /** Slow green, teal and violet drift, lamps out of phase. */
    static final Frame AURORA = t -> {
        double a = wave(t, 24), b = wave(t + 9, 24);
        return two(mix(new int[]{0, 255, 120}, new int[]{120, 0, 255}, a), mix(new int[]{0, 200, 200}, new int[]{60, 255, 80}, b), 65);
    };
    /** Orange melting into magenta and back. */
    static final Frame SUNSET = t -> {
        double a = wave(t, 30), b = wave(t + 12, 30);
        return two(mix(new int[]{255, 90, 0}, new int[]{255, 0, 90}, a), mix(new int[]{255, 140, 20}, new int[]{200, 0, 160}, b), 60);
    };
    /** Deep blue to teal swells, like light under water. */
    static final Frame OCEAN = t -> {
        double a = wave(t, 10), b = wave(t + 4, 10);
        return two(mix(new int[]{0, 40, 255}, new int[]{0, 200, 180}, a), mix(new int[]{0, 120, 255}, new int[]{0, 60, 200}, b), (int) (40 + 30 * wave(t, 10)));
    };
    /** Warm amber flicker. */
    static final Frame FIREPLACE = t -> {
        int[] a = mix(new int[]{255, 60, 0}, new int[]{255, 140, 10}, rnd.nextDouble());
        int[] b = mix(new int[]{255, 50, 0}, new int[]{255, 120, 0}, rnd.nextDouble());
        return two(a, b, 25 + rnd.nextInt(30));
    };
    /** Terminal-green pulse. */
    static final Frame MATRIX = t -> {
        double a = wave(t, 3), b = wave(t + 1.5, 3);
        return two(mix(new int[]{0, 60, 10}, new int[]{50, 255, 100}, a), mix(new int[]{0, 60, 10}, new int[]{50, 255, 100}, b), 70);
    };
    /** Full rainbow chase, lamps half a wheel apart. */
    static final Frame PARTY = t -> {
        double h = t * 60;
        int[] a = hsv(h, 1, 1), b = hsv(h + 180, 1, 1);
        return two(a, b, 100);
    };
}
