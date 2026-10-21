package app.liftoff.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The Lights panel: scenes and basics for whatever lights liftoff.json sets up (see Lights). */
public class LightsActivity extends Activity {
    // Terminal look: black screen, phosphor-green text.
    static final int INK = Color.rgb(51, 255, 102), PAPER = Color.BLACK, DIM = Color.argb(150, 51, 255, 102);

    final ExecutorService net = Executors.newSingleThreadExecutor();
    int brightness = 70;
    TextView status;
    Lights lights;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        lights = Lights.from(Config.get(this).lights);
        if (lights == null) { finish(); return; }
        Scenes.lights = lights;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(96, 64, 96, 48);

        TextView title = text("> LIGHTS", 34, true);
        root.addView(title);
        status = text(lights.count() + (lights.count() == 1 ? " light" : " lights") + " ready_", 18, false);
        status.setTextColor(DIM);
        status.setPadding(0, 4, 0, 24);
        root.addView(status);

        root.addView(section("// scenes"));
        GridLayout scenes = new GridLayout(this);
        scenes.setColumnCount(4);
        root.addView(scenes);
        scene(scenes, "Starman", Scenes.STARMAN, 0);
        scene(scenes, "Synthwave", Scenes.SYNTHWAVE, 0);
        scene(scenes, "Aurora", Scenes.AURORA, 400);
        scene(scenes, "Sunset", Scenes.SUNSET, 400);
        scene(scenes, "Ocean", Scenes.OCEAN, 300);
        scene(scenes, "Fireplace", Scenes.FIREPLACE, 180);
        scene(scenes, "Matrix", Scenes.MATRIX, 200);
        scene(scenes, "Party", Scenes.PARTY, 250);

        root.addView(section("// basics"));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        root.addView(grid);

        add(grid, "Movie", () -> { temp(2200); bright(15); say("Movie"); });
        add(grid, "Warm", () -> { temp(2700); bright(70); say("Warm"); });
        add(grid, "Bright", () -> { temp(5000); bright(100); say("Bright"); });
        add(grid, "Night", () -> { color(255, 60, 0); bright(5); say("Night"); });
        add(grid, "Purple", () -> { color(140, 0, 255); bright(60); say("Purple"); });
        add(grid, "Blue", () -> { color(0, 70, 255); bright(60); say("Blue"); });
        add(grid, "Red", () -> { color(255, 0, 0); bright(60); say("Red"); });
        add(grid, "Teal", () -> { color(0, 200, 160); bright(60); say("Teal"); });
        add(grid, "Dimmer", () -> { brightness = Math.max(5, brightness - 15); bright(brightness); say("Brightness " + brightness + "%"); });
        add(grid, "Brighter", () -> { brightness = Math.min(100, brightness + 15); bright(brightness); say("Brightness " + brightness + "%"); });
        add(grid, "All on", () -> { turn(1); say("On"); });
        add(grid, "All off", () -> { turn(0); say("Off"); });

        setContentView(root);
        scenes.getChildAt(0).requestFocus();
    }

    void scene(GridLayout grid, String label, Scenes.Frame f, long stepMs) {
        add(grid, label, () -> { Scenes.play(f, stepMs); say(label + (stepMs > 0 ? " running" : "")); }, true);
    }

    TextView section(String s) {
        TextView t = text(s, 15, false);
        t.setTextColor(DIM);
        t.setPadding(0, 6, 0, 12);
        return t;
    }

    TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(INK);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setLetterSpacing(bold ? 0.15f : 0.1f);
        t.setShadowLayer(12, 0, 0, Color.argb(120, 51, 255, 102));
        return t;
    }

    void add(GridLayout grid, String label, Runnable action) { add(grid, label, action, false); }

    void add(GridLayout grid, String label, Runnable action, boolean scene) {
        Button btn = new Button(this);
        btn.setText(label.toUpperCase());
        btn.setAllCaps(false);
        btn.setTextSize(17);
        btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btn.setLetterSpacing(0.12f);
        btn.setGravity(Gravity.CENTER);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, box(INK, INK));
        bg.addState(new int[]{}, box(PAPER, INK));
        btn.setBackground(bg);
        btn.setOnFocusChangeListener((v, f) -> ((Button) v).setTextColor(f ? PAPER : INK));
        btn.setTextColor(INK);
        btn.setOnClickListener(v -> { if (!scene) Scenes.stop(); action.run(); });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 380; lp.height = 96; lp.setMargins(0, 0, 32, 22);
        grid.addView(btn, lp);
    }

    GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(3, stroke);
        g.setCornerRadius(6);
        return g;
    }

    void say(String s) { status.setText("> " + s.toLowerCase() + "_"); }

    interface Cmd { void run(int i) throws Exception; }

    void each(Cmd cmd) {
        net.execute(() -> {
            try { for (int i = 0; i < lights.count(); i++) cmd.run(i); }
            catch (Exception e) { runOnUiThread(() -> status.setText("> error: lights unreachable_")); }
        });
    }

    void turn(int on) { each(i -> lights.power(i, on == 1)); }
    void bright(int v) { each(i -> lights.bright(i, v)); }
    void temp(int k) { each(i -> { lights.power(i, true); lights.temp(i, k); }); }
    void color(int r, int g, int b) { each(i -> { lights.power(i, true); lights.color(i, r, g, b); }); }
}
