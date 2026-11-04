package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import com.liskovsoft.smartyoutubetv2.common.prefs.ChanuoColorPrefs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Hardware MediaCodec -> SurfaceTexture -> GLES2 OES shader -> GLSurfaceView.
 * No readPixels(), no CPU bitmap copies, and uniforms refresh when preferences
 * change. This avoids Android 8.1 vendor TextureView layer paint update issues.
 *
 * Experimental: if the projector's EGL driver misbehaves, disable correction
 * in Player settings and reopen the video for the unmodified SurfaceView path.
 */
public final class ChanuoGlSurfaceWrapper implements SurfaceWrapper,
        GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ChanuoColorGL";
    private static final String VERT =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() { gl_Position = aPosition; " +
            "vTexCoord = (uTextureMatrix * vec4(aTexCoord, 0.0, 1.0)).xy; }\n";
    private static final String FRAG =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uVideo;\n" +
            "uniform float uBrightness;\n" +
            "uniform float uContrast;\n" +
            "uniform float uSaturation;\n" +
            "uniform float uGamma;\n" +
            "uniform float uWarmth;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  vec3 rgb = texture2D(uVideo, vTexCoord).rgb;\n" +
            "  rgb = clamp((rgb - vec3(0.5)) * uContrast + vec3(0.5 + uBrightness), 0.0, 1.0);\n" +
            "  float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
            "  rgb = mix(vec3(luma), rgb, uSaturation);\n" +
            "  rgb = clamp(rgb + vec3(uWarmth, 0.0, -uWarmth), 0.0, 1.0);\n" +
            "  rgb = pow(rgb, vec3(uGamma));\n" +
            "  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);\n" +
            "}\n";

    private static final float[] QUAD = {
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private final GLSurfaceView mView;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Object mLock = new Object();
    private final FloatBuffer mQuad;
    private final float[] mTexMatrix = new float[16];

    private volatile SurfaceHolder.Callback mCallback;
    private volatile Surface mDecoderSurface;
    private SurfaceTexture mDecoderTexture;
    private boolean mFrameAvailable;
    private boolean mHasFrame;
    private int mProgram;
    private int mTextureId;
    private int mWidth;
    private int mHeight;

    public ChanuoGlSurfaceWrapper(Context context, ViewGroup root) {
        mContext = context.getApplicationContext();
        mPrefs = ChanuoColorPrefs.preferences(context);
        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD.length * 4);
        bb.order(ByteOrder.nativeOrder());
        mQuad = bb.asFloatBuffer();
        mQuad.put(QUAD).position(0);

        // Keep the SurfaceView's on-screen lifecycle tied to the fragment.
        mView = new GLSurfaceView(context) {
            @Override protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                mPrefs.registerOnSharedPreferenceChangeListener(
                        ChanuoGlSurfaceWrapper.this);
            }

            @Override protected void onDetachedFromWindow() {
                mPrefs.unregisterOnSharedPreferenceChangeListener(
                        ChanuoGlSurfaceWrapper.this);
                closeDecoderSurface();
                super.onDetachedFromWindow();
            }
        };
        mView.setEGLContextClientVersion(2);
        mView.setRenderer(this);
        mView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public View getSurfaceView() {
        return mView;
    }

    @Override
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        mCallback = callback;
        Surface current = mDecoderSurface;
        if (callback != null && current != null && current.isValid()) {
            notifyCreated(current);
        }
    }

    private void notifyCreated(Surface current) {
        mView.post(() -> {
            SurfaceHolder.Callback cb = mCallback;
            if (cb != null && mDecoderSurface == current && current.isValid()) {
                cb.surfaceCreated(new TextureViewSurfaceHolder(current));
                if (mWidth > 0 && mHeight > 0) {
                    cb.surfaceChanged(new TextureViewSurfaceHolder(current),
                            4, mWidth, mHeight);
                }
            }
        });
    }

    private void closeDecoderSurface() {
        Surface old = mDecoderSurface;
        mDecoderSurface = null;
        if (old != null) {
            SurfaceHolder.Callback cb = mCallback;
            if (cb != null) {
                // Always callback on the UI thread, just as a SurfaceView does.
                cb.surfaceDestroyed(new TextureViewSurfaceHolder(old));
            }
            old.release();
        }
        synchronized (mLock) {
            mFrameAvailable = false;
            mHasFrame = false;
            if (mDecoderTexture != null) {
                mDecoderTexture.setOnFrameAvailableListener(null);
                mDecoderTexture.release();
                mDecoderTexture = null;
            }
        }
    }

    private int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Shader error: " + error);
        }
        return shader;
    }

    private int createProgram() {
        int v = compile(GLES20.GL_VERTEX_SHADER, VERT);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, FRAG);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, v);
        GLES20.glAttachShader(program, f);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Program error: " + error);
        }
        return program;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            // EGL context can be recreated by the system; rebuild all GL resources.
            mProgram = createProgram();
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTextureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            // Callback delivery and SurfaceTexture release are separated by
            // context lifecycle; only this renderer owns the OES texture.
            Surface old = mDecoderSurface;
            SurfaceTexture oldTexture;
            synchronized (mLock) {
                oldTexture = mDecoderTexture;
                mFrameAvailable = false;
                mHasFrame = false;
                mDecoderTexture = null;
            }
            if (old != null) {
                mDecoderSurface = null;
                mView.post(() -> {
                    SurfaceHolder.Callback cb = mCallback;
                    if (cb != null) cb.surfaceDestroyed(
                            new TextureViewSurfaceHolder(old));
                    old.release();
                });
            }
            if (oldTexture != null) {
                oldTexture.setOnFrameAvailableListener(null);
                oldTexture.release();
            }
            SurfaceTexture texture = new SurfaceTexture(mTextureId);
            texture.setOnFrameAvailableListener(this);
            Surface surface = new Surface(texture);
            synchronized (mLock) {
                mDecoderTexture = texture;
            }
            mDecoderSurface = surface;
            mView.post(() -> notifyCreated(surface));
            Log.i(TAG, "OpenGL ES2 shader and decoder Surface ready");
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize color shader", e);
            ChanuoColorPrefs.setEnabled(mContext, false);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mWidth = width;
        mHeight = height;
        GLES20.glViewport(0, 0, width, height);
        Surface current = mDecoderSurface;
        if (current != null) {
            mView.post(() -> {
                SurfaceHolder.Callback cb = mCallback;
                if (cb != null && mDecoderSurface == current)
                    cb.surfaceChanged(new TextureViewSurfaceHolder(current),
                            4, mWidth, mHeight);
            });
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mLock) {
            if (surfaceTexture != mDecoderTexture) return;
            mFrameAvailable = true;
        }
        mView.requestRender();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // Redraw on the GL thread with current uniforms, even when paused.
        mView.requestRender();
    }

    private void uniform(String name, float value) {
        int loc = GLES20.glGetUniformLocation(mProgram, name);
        if (loc >= 0) GLES20.glUniform1f(loc, value);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glViewport(0, 0, Math.max(1, mWidth), Math.max(1, mHeight));
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (mProgram == 0 || mDecoderTexture == null) return;
        synchronized (mLock) {
            try {
                if (mFrameAvailable) {
                    mDecoderTexture.updateTexImage();
                    mDecoderTexture.getTransformMatrix(mTexMatrix);
                    mFrameAvailable = false;
                    mHasFrame = true;
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Decoder SurfaceTexture frame error", ex);
                return;
            }
        }
        if (!mHasFrame) return;
        GLES20.glUseProgram(mProgram);
        mQuad.position(0);
        int pos = GLES20.glGetAttribLocation(mProgram, "aPosition");
        int uv = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, mQuad);
        mQuad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, mQuad);

        GLES20.glUniformMatrix4fv(
                GLES20.glGetUniformLocation(mProgram, "uTextureMatrix"),
                1, false, mTexMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uVideo"), 0);

        boolean enabled = ChanuoColorPrefs.enabled(mContext);
        uniform("uBrightness", enabled
                ? ChanuoColorPrefs.get(mContext, ChanuoColorPrefs.BRIGHTNESS) / 255f : 0f);
        uniform("uContrast", enabled
                ? ChanuoColorPrefs.get(mContext, ChanuoColorPrefs.CONTRAST) / 100f : 1f);
        uniform("uSaturation", enabled
                ? ChanuoColorPrefs.get(mContext, ChanuoColorPrefs.SATURATION) / 100f : 1f);
        uniform("uGamma", enabled
                ? ChanuoColorPrefs.get(mContext, ChanuoColorPrefs.GAMMA) / 100f : 1f);
        uniform("uWarmth", enabled
                ? ChanuoColorPrefs.get(mContext, ChanuoColorPrefs.WARMTH) / 255f : 0f);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(pos);
        GLES20.glDisableVertexAttribArray(uv);
    }
}
