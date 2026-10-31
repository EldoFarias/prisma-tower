package com.farias.prismatower.ui

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.farias.prismatower.game.GameHost
import com.farias.prismatower.gl.TowerRenderer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** The live 3D tower, drawn behind every screen. */
@Composable
fun TowerView(host: GameHost, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setEGLConfigChooser(MsaaChooser())
                preserveEGLContextOnPause = true
                setRenderer(TowerRenderer(host.game).apply {
                    detailed = { com.farias.prismatower.data.Progress.detailedScenery }
                    onSlow = { post { com.farias.prismatower.data.Progress.setDetailedScenery(false) } }
                })
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                host.view = this
            }
        },
        onRelease = { if (host.view === it) host.view = null },
    )
}

/** Picks a 4× multisampled RGBA8888 + depth config when the GPU has one (smooth ring edges), else plain. */
private class MsaaChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
        fun pick(samples: Int): EGLConfig? {
            val attrs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_RENDERABLE_TYPE, es3,
                EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0,
                EGL10.EGL_SAMPLES, samples,
                EGL10.EGL_NONE,
            )
            val n = IntArray(1)
            val out = arrayOfNulls<EGLConfig>(1)
            if (!egl.eglChooseConfig(display, attrs, out, 1, n) || n[0] == 0) return null
            return out[0]
        }
        return pick(4) ?: pick(0) ?: run {
            // Last resort: 16-bit depth.
            val attrs = intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE)
            val n = IntArray(1)
            val out = arrayOfNulls<EGLConfig>(1)
            egl.eglChooseConfig(display, attrs, out, 1, n)
            out[0] ?: throw IllegalStateException("No EGL config")
        }
    }
}
