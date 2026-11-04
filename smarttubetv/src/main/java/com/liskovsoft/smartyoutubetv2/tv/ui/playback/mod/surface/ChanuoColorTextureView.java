package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.TextureView;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChanuoColorPrefs;

/**
 * Applies a GPU-composited ColorMatrix to the actual decoded video layer, not
 * subtitles or controls. No per-frame CPU bitmap copies.
 */
public final class ChanuoColorTextureView extends TextureView
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;

    public ChanuoColorTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prefs = ChanuoColorPrefs.preferences(getContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateVideoColor();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            prefs = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (isAttachedToWindow()) {
            // The settings screen writes these prefs on the main thread.
            updateVideoColor();
        }
    }

    private void updateVideoColor() {
        if (!ChanuoColorPrefs.enabled(getContext())) {
            setLayerPaint(null);
            return;
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(
                ChanuoColorPrefs.buildMatrix(getContext())));
        // TextureView always uses a hardware layer; its layer paint is applied
        // by the Android compositor, while MediaCodec still decodes to a Surface.
        setLayerPaint(paint);
    }
}
