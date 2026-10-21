package app.liftoff.home;

import android.content.Context;
import android.graphics.Typeface;

/** Michroma (SIL Open Font License, Google Fonts), bundled in assets/. */
final class Fonts {
    private static Typeface michroma;
    static Typeface michroma(Context c) {
        if (michroma == null) michroma = Typeface.createFromAsset(c.getAssets(), "michroma.ttf");
        return michroma;
    }
}
