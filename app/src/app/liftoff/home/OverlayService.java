package app.liftoff.home;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import org.json.JSONObject;

/**
 * Draws the home-screen overlays: weather (top left) and a "Now playing" card (bottom right) while music
 * plays on this TV. Only visible while the Liftoff home screen is in front. Runs as a notification
 * listener so the system keeps it alive and lets it read media sessions. scripts/install.sh grants:
 *   cmd notification allow_listener app.liftoff.home/app.liftoff.home.OverlayService
 *   appops set app.liftoff.home SYSTEM_ALERT_WINDOW allow
 *   appops set app.liftoff.home APP_AUTO_START allow   (TCL firmware only)
 */
public class OverlayService extends NotificationListenerService {
    static final int GLASS = Color.argb(95, 8, 10, 16), EDGE = Color.argb(55, 255, 255, 255),
            TEXT = Color.WHITE, SOFT = Color.argb(170, 255, 255, 255);

    final Handler ui = new Handler(Looper.getMainLooper());
    MediaSessionManager sessions;
    MediaController player;

    WindowManager wm;
    LinearLayout card;
    ImageView art;
    TextView title, artist;
    boolean shown;

    // Weather from Open-Meteo (free, no key), for the latitude/longitude in liftoff.json.
    String weatherUrl() {
        Config c = Config.get(this);
        return "https://api.open-meteo.com/v1/forecast?latitude=" + c.lat + "&longitude=" + c.lon
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                + "&temperature_unit=" + (c.celsius ? "celsius" : "fahrenheit") + "&timezone=auto&forecast_days=1";
    }
    LinearLayout weather;
    TextView wTemp, wLine;
    boolean weatherShown, weatherReady;
    long weatherFetched;

    final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::pick;
    final MediaController.Callback onMedia = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onSessionDestroyed() { player = null; refresh(); }
    };
    final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); refreshWeather(); ui.postDelayed(this, 1000); }
    };

    @Override public void onListenerConnected() {
        wm = getSystemService(WindowManager.class);
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, OverlayService.class);
        sessions.addOnActiveSessionsChangedListener(onSessions, me, ui);
        pick(sessions.getActiveSessions(me));
        ui.post(tick);
    }

    @Override public void onListenerDisconnected() {
        ui.removeCallbacks(tick);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(onSessions);
        hide();
        hideWeather();
    }

    void pick(List<MediaController> list) {
        if (player != null) player.unregisterCallback(onMedia);
        player = null;
        List<String> wanted = Config.get(this).nowPlayingPackages; // empty = any app
        if (list != null) for (MediaController c : list)
            if (wanted.isEmpty() || wanted.contains(c.getPackageName())) { player = c; break; }
        if (player != null) player.registerCallback(onMedia, ui);
        refresh();
    }

    void refresh() {
        PlaybackState st = player == null ? null : player.getPlaybackState();
        MediaMetadata md = player == null ? null : player.getMetadata();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING && md != null;
        if (playing && HomeActivity.inFront) show(md); else hide();
    }

    void show(MediaMetadata md) {
        if (card == null) build();
        title.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE));
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        artist.setText(TextUtils.isEmpty(a) ? md.getString(MediaMetadata.METADATA_KEY_ALBUM) : a);
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        art.setImageBitmap(b);
        art.setVisibility(b == null ? View.GONE : View.VISIBLE);
        if (!shown) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.x = 64; lp.y = 48;
            try { wm.addView(card, lp); shown = true; } catch (Exception ignored) { }
        }
    }

    void hide() {
        if (shown) { try { wm.removeView(card); } catch (Exception ignored) { } shown = false; }
    }

    void refreshWeather() {
        if (!Config.get(this).weather) return;
        long now = System.currentTimeMillis();
        if (now - weatherFetched > 15 * 60_000) { weatherFetched = now; new Thread(this::fetchWeather).start(); }
        if (weatherReady && HomeActivity.inFront) showWeather(); else hideWeather();
    }

    void fetchWeather() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(weatherUrl()).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            String body;
            try (InputStream in = c.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) { body = sc.hasNext() ? sc.next() : ""; }
            JSONObject j = new JSONObject(body), cur = j.getJSONObject("current"), day = j.getJSONObject("daily");
            int t = (int) Math.round(cur.getDouble("temperature_2m"));
            int hi = (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0));
            int lo = (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0));
            String sky = describe(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
            ui.post(() -> {
                if (weather == null) buildWeather();
                wTemp.setText(deg(t + "°"));
                wLine.setText(deg(sky + "\nH " + hi + "°  L " + lo + "°"));
                weatherReady = true;
            });
        } catch (Exception e) {
            weatherFetched = System.currentTimeMillis() - 14 * 60_000; // retry in about a minute
        }
    }

    /** Michroma's degree sign sits on the baseline, so draw ° in the system font. */
    static CharSequence deg(String s) {
        android.text.SpannableString sp = new android.text.SpannableString(s);
        for (int i = s.indexOf('°'); i >= 0; i = s.indexOf('°', i + 1))
            sp.setSpan(new android.text.style.TypefaceSpan("sans-serif"), i, i + 1, 0);
        return sp;
    }

    static String describe(int code, boolean day) {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "";
    }

    void showWeather() {
        if (weatherShown || weather == null) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 64; lp.y = 22;
        try { wm.addView(weather, lp); weatherShown = true; } catch (Exception ignored) { }
    }

    void hideWeather() {
        if (weatherShown) { try { wm.removeView(weather); } catch (Exception ignored) { } weatherShown = false; }
    }

    void buildWeather() {
        weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.HORIZONTAL);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        wTemp = new TextView(this);
        wTemp.setTextColor(TEXT);
        wTemp.setTypeface(Fonts.michroma(this));
        wTemp.setTextSize(32);
        wTemp.setShadowLayer(8, 0, 2, Color.argb(160, 0, 0, 0));
        wTemp.setTranslationY(-10); // center on the two small lines
        weather.addView(wTemp);
        wLine = new TextView(this);
        wLine.setTextSize(12);
        wLine.setTextColor(TEXT);
        wLine.setTypeface(Fonts.michroma(this));
        wLine.setLetterSpacing(0.03f);
        wLine.setShadowLayer(6, 0, 2, Color.argb(160, 0, 0, 0));
        wLine.setPadding(20, 0, 0, 0);
        wLine.setLineSpacing(8, 1f);
        weather.addView(wLine);
    }

    void build() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(20, 20, 32, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GLASS); bg.setStroke(2, EDGE); bg.setCornerRadius(24);
        card.setBackground(bg);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(112, 112);
        ap.rightMargin = 24;
        card.addView(art, ap);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView np = label("NOW PLAYING", 10, false, 0.3f);
        np.setTextColor(SOFT);
        col.addView(np);
        title = label("", 10, true, 0.05f);
        artist = label("", 10, false, 0.05f);
        artist.setTextColor(SOFT);
        col.addView(title);
        col.addView(artist);
        card.addView(col, new LinearLayout.LayoutParams(420, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    TextView label(String s, int sp, boolean bold, float spacing) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        t.setTypeface(Fonts.michroma(this));
        t.setLetterSpacing(bold ? 0.02f : spacing);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }
}
