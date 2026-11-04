package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorMatrix;
import android.os.Build;

/**
 * Chanuo A1-specific SDR video color calibration.
 *
 * Uses a 4x5 linear color transform on the already hardware-decoded video
 * TextureView. This is NOT optical brightness control or true nonlinear gamma.
 */
public final class ChanuoColorPrefs {
    private static final String PREF_NAME = "chanuo_a1_video_color";
    public static final String BRIGHTNESS = "brightness";    // -20..40, additive RGB offset
    public static final String CONTRAST = "contrast";        // percent, 70..130
    public static final String SATURATION = "saturation";    // percent, 70..170
    public static final String WARMTH = "warmth";            // red/blue offset, -20..20
    private static final String ENABLED = "enabled";

    private ChanuoColorPrefs() {}

    public static boolean isChanuoA1() {
        return "A1".equalsIgnoreCase(Build.MODEL)
                && "mt6735".equalsIgnoreCase(Build.HARDWARE)
                && "Chanuo".equalsIgnoreCase(Build.BRAND);
    }

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return isChanuoA1() && preferences(context).getBoolean(ENABLED, false);
    }

    public static void setEnabled(Context context, boolean value) {
        preferences(context).edit().putBoolean(ENABLED, value).apply();
    }

    public static int get(Context context, String key) {
        int def = (CONTRAST.equals(key) || SATURATION.equals(key)) ? 100 : 0;
        return preferences(context).getInt(key, def);
    }

    public static void set(Context context, String key, int value) {
        final int low, high;
        if (BRIGHTNESS.equals(key)) { low = -20; high = 40; }
        else if (CONTRAST.equals(key)) { low = 70; high = 130; }
        else if (SATURATION.equals(key)) { low = 70; high = 170; }
        else if (WARMTH.equals(key)) { low = -20; high = 20; }
        else return;
        preferences(context).edit().putInt(key, Math.max(low, Math.min(high, value))).apply();
    }

    public static void applyDarkWallPreset(Context context) {
        // Safe starter preset: raise near-black levels with gentler contrast.
        preferences(context).edit()
                .putInt(BRIGHTNESS, 18)
                .putInt(CONTRAST, 90)
                .putInt(SATURATION, 110)
                .putInt(WARMTH, 0)
                .apply();
    }

    public static void resetNeutral(Context context) {
        preferences(context).edit()
                .putInt(BRIGHTNESS, 0)
                .putInt(CONTRAST, 100)
                .putInt(SATURATION, 100)
                .putInt(WARMTH, 0)
                .apply();
    }

    public static ColorMatrix buildMatrix(Context context) {
        float s = get(context, SATURATION) / 100.0f;
        float c = get(context, CONTRAST) / 100.0f;
        float b = get(context, BRIGHTNESS);
        float t = get(context, WARMTH);
        float rw = 0.2126f * (1f - s);
        float gw = 0.7152f * (1f - s);
        float bw = 0.0722f * (1f - s);
        float offset = 128f * (1f - c) + b;
        return new ColorMatrix(new float[] {
                c * (rw + s), c * gw, c * bw, 0f, offset + t * 0.75f,
                c * rw, c * (gw + s), c * bw, 0f, offset,
                c * rw, c * gw, c * (bw + s), 0f, offset - t * 0.75f,
                0f, 0f, 0f, 1f, 0f
        });
    }
}
