package app.liftoff.home;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The home screen: full-bleed background, clock top right, an alphabetical text list of apps bottom left.
 * Weather (top left) and Now Playing (bottom right) are drawn on top by OverlayService.
 */
public class HomeActivity extends Activity {
    /** True while the home screen is the screen you're looking at; OverlayService only draws then. */
    static volatile boolean inFront;
    static final int DIM = Color.argb(150, 255, 255, 255), SHADOW = Color.argb(200, 0, 0, 0);

    Typeface face;
    ImageView bg;
    SharedPreferences prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Config cfg = Config.get(this);
        face = Fonts.michroma(this);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        applyBgExtra(getIntent());

        FrameLayout root = new FrameLayout(this);
        bg = new ImageView(this);
        bg.setImageResource(R.drawable.home_bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        // Top right: settings gear, then date stacked beside a big time (mirrors the weather module).
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.gear);
        gear.setId(View.generateViewId());
        gear.setFocusable(true);
        gear.setPadding(10, 10, 10, 10);
        gear.setAlpha(0.6f);
        gear.setOnFocusChangeListener((v, f) -> { v.setAlpha(f ? 1f : 0.6f); v.setScaleX(f ? 1.15f : 1f); v.setScaleY(f ? 1.15f : 1f); });
        gear.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(68, 68);
        gp.rightMargin = 36;
        clock.addView(gear, gp);
        TextClock date = clockText("EEE,\nMMM d", 12);
        date.setGravity(Gravity.END);
        date.setLetterSpacing(0.03f);
        date.setLineSpacing(8, 1f);
        date.setShadowLayer(6, 0, 2, Color.argb(160, 0, 0, 0));
        date.setPadding(0, 0, 20, 0);
        clock.addView(date);
        TextClock time = clockText(cfg.clock24h ? "H:mm" : "h:mm", 32);
        time.setLetterSpacing(0f);
        time.setTranslationY(-10); // centre the big digits on the two small lines
        clock.addView(time);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.setMargins(0, 22, 64, 0);
        root.addView(clock, cp);

        // Bottom left: the apps, alphabetical. Apps that aren't installed are skipped.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        PackageManager pm = getPackageManager();
        for (Config.App app : cfg.apps) {
            Intent launch = app.pkg.equals(getPackageName())
                    ? new Intent(this, LightsActivity.class)
                    : pm.getLeanbackLaunchIntentForPackage(app.pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(app.pkg);
            if (launch == null) continue;
            list.addView(item(app.label, launch));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(64, 0, 0, 56);
        root.addView(list, lp);

        setContentView(root);
        int n = list.getChildCount();
        if (n > 0) {
            View first = list.getChildAt(0);
            first.setId(View.generateViewId());
            first.setNextFocusUpId(gear.getId());
            gear.setNextFocusDownId(first.getId());
            gear.setNextFocusLeftId(first.getId());
            for (int i = 0; i < n; i++) list.getChildAt(i).setNextFocusRightId(gear.getId());
            first.requestFocus();
        } else gear.requestFocus();
    }

    // ---- background: NASA Astronomy Picture of the Day, or the bundled image ----
    // Switch at runtime over adb:  am start -n app.liftoff.home/.HomeActivity --es bg image   (or --es bg apod)
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); applyBgExtra(i); showBackground(); }
    @Override protected void onResume() { super.onResume(); inFront = true; showBackground(); }
    @Override protected void onPause() { inFront = false; super.onPause(); }

    void applyBgExtra(Intent i) {
        String m = i == null ? null : i.getStringExtra("bg");
        if (m != null) prefs.edit().putString("bg", m).apply();
    }

    boolean apodMode() { return "apod".equals(prefs.getString("bg", Config.get(this).background)); }

    void showBackground() {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File f = new File(getFilesDir(), "apod.img");
        if (apodMode() && today.equals(prefs.getString("apodDate", "")) && f.exists() && showApod(f)) return;
        bg.setImageResource(R.drawable.home_bg);
        if (apodMode()) new Thread(() -> fetchApod(today, f)).start();
    }

    boolean showApod(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = Math.max(1, Math.min(o.outWidth / 1920, o.outHeight / 1080));
        o.inJustDecodeBounds = false;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return false;
        bg.setImageBitmap(b);
        return true;
    }

    /** Pulls today's picture from NASA's APOD page. Video days or any failure keep the bundled image. */
    void fetchApod(String today, File f) {
        try {
            String page = get("https://science.nasa.gov/apod/");
            int i = page.indexOf("media-detail-hero__media");
            if (i < 0) return;
            Matcher img = Pattern.compile("<img[^>]+src=\"([^\"]+/apod/apod/[^\"?]+)").matcher(page);
            if (!img.find(i)) return;
            HttpURLConnection c = (HttpURLConnection) new URL(img.group(1) + "?w=2400&fit=clip").openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(30000);
            File tmp = new File(getFilesDir(), "apod.tmp");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536]; for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(f)) return;
            prefs.edit().putString("apodDate", today).apply();
            runOnUiThread(() -> { if (apodMode()) showApod(f); });
        } catch (Exception ignored) { }
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Liftoff/1.0");
        try (InputStream in = c.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) { return sc.hasNext() ? sc.next() : ""; }
    }

    TextClock clockText(String fmt, int sp) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(fmt); c.setFormat24Hour(fmt);
        c.setTextSize(sp);
        c.setTextColor(Color.WHITE);
        c.setTypeface(face);
        c.setShadowLayer(8, 0, 2, Color.argb(160, 0, 0, 0));
        return c;
    }

    TextView item(String label, Intent launch) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13.6f);
        t.setTypeface(face);
        t.setLetterSpacing(0.04f);
        t.setTextColor(DIM);
        t.setShadowLayer(6, 0, 2, SHADOW);
        t.setPadding(0, 8, 0, 8);
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, f) -> {
            TextView tv = (TextView) v;
            tv.setText(f ? "› " + label : label);
            tv.setTextColor(f ? Color.WHITE : DIM);
        });
        t.setOnClickListener(v -> {
            try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        });
        return t;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        return keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, e); // home has nowhere to go back to
    }
}
