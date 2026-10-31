package com.farias.prismatower.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.farias.prismatower.game.SliceType
import com.farias.prismatower.game.Tower
import com.farias.prismatower.game.TowerGame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Draws a [TowerGame] with OpenGL ES 3.0 and advances it every frame. */
class TowerRenderer(private val game: TowerGame) : GLSurfaceView.Renderer {

    private lateinit var lit: Program
    private lateinit var splat: Program
    private lateinit var bg: Program
    private lateinit var pts: Program
    private lateinit var slice: Mesh
    private lateinit var goalRing: Mesh
    private lateinit var pillar: Mesh
    private lateinit var sphere: Mesh
    private lateinit var disc: Mesh
    private lateinit var screen: Mesh

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val tmp = FloatArray(16)
    private val vec = FloatArray(4)
    private val out4 = FloatArray(4)
    private val eye = FloatArray(3)

    private var width = 1
    private var height = 1
    private var lastNanos = 0L

    // Particle stream: pos(3) colour(4) size(1)
    private val ptVao = IntArray(1)
    private val ptVbo = IntArray(1)
    private lateinit var ptBuf: FloatBuffer
    private val ptCapacity = 2200

    private val seam = 0.035f
    private val sliceSpan = Tower.SLICE_ANGLE - seam

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        lit = Program(Shaders.LIT_VS, Shaders.LIT_FS)
        splat = Program(Shaders.LIT_VS, Shaders.SPLAT_FS)
        bg = Program(Shaders.BG_VS, Shaders.BG_FS)
        pts = Program(Shaders.PT_VS, Shaders.PT_FS)
        slice = Mesh(Shapes.wedge(sliceSpan, Tower.RING_INNER, Tower.RING_OUTER, Tower.THICKNESS, 0.075f))
        goalRing = Mesh(Shapes.wedge(TowerGame.TWO_PI, Tower.RING_INNER, Tower.RING_OUTER, Tower.THICKNESS * 1.3f, 0.09f, steps = 96))
        pillar = Mesh(Shapes.cylinder(Tower.PILLAR_R, 0f, 1f))
        sphere = Mesh(Shapes.sphere(Tower.BALL_R))
        disc = Mesh(Shapes.disc(0.62f))
        screen = Mesh(Shapes.screen())

        ptBuf = ByteBuffer.allocateDirect(ptCapacity * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES30.glGenVertexArrays(1, ptVao, 0)
        GLES30.glBindVertexArray(ptVao[0])
        GLES30.glGenBuffers(1, ptVbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ptVbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, ptCapacity * 8 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 32, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 32, 28)
        GLES30.glBindVertexArray(0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        lastNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        // Fit the tower's width on any screen: pick the vertical FOV from the aspect ratio.
        val halfH = atan(3.6f / 13.5f)
        val vfov = Math.toDegrees(2.0 * atan(tan(halfH) / aspect)).toFloat().coerceIn(38f, 72f)
        Matrix.perspectiveM(proj, 0, vfov, aspect, 0.5f, 90f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1e9f
        lastNanos = now
        game.update(dt)

        val t = game.tower
        val sim = game.sim
        val time = game.clock
        val sh = game.shake
        val shx = sh * sin(time * 61f) * 0.6f
        val shy = sh * sin(time * 47f + 1f) * 0.4f
        val camY = game.cameraY
        eye[0] = shx; eye[1] = camY + 5.4f + shy; eye[2] = 12.6f
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], shx * 0.5f, camY - 4.4f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES30.glClearColor(t.bgBottom[0], t.bgBottom[1], t.bgBottom[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackground(t, time, camY)

        // ---------------------------------------------------------- lit scene
        lit.use()
        lit.m4("uVP", vp)
        lit.v3("uCam", eye)
        lit.v3("uLight", 0.45f, 0.85f, 0.55f)
        lit.v3("uSky", mix(t.bgTop, floatArrayOf(1f, 1f, 1f), 0.55f))
        lit.v3("uGround", mix(t.bgBottom, t.aurora[0], 0.4f))
        lit.v3("uRim", t.rim)
        val fog = mix(t.bgBottom, t.bgTop, 0.35f)
        lit.v3("uFog", fog)
        lit.v2("uFogRange", 16f, 34f)
        lit.f("uTime", time)
        lit.f("uSpacing", Tower.SPACING)
        lit.f("uAlpha", 1f)

        // Pillar
        val first = max(0, sim.next - 1)
        val last = min(sim.level.platforms.size - 1, sim.next + 9)
        val pillarTop = camY + 12f
        val pillarBottom = Tower.platformTop(sim.level.platforms.size - 1) - 6f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, pillarBottom, 0f)
        Matrix.rotateM(model, 0, deg(sim.rotation), 0f, 1f, 0f)
        Matrix.scaleM(model, 0, 1f, pillarTop - pillarBottom, 1f)
        material(1, t.pattern(), t.pillar, t.pillar2, zero, gloss = 70f, metal = 0.25f)
        lit.m4("uModel", model)
        pillar.draw()

        // Rings
        for (i in first..last) {
            if (i < sim.next) continue
            val p = sim.level.platforms[i]
            val y = Tower.platformTop(i) - Tower.THICKNESS / 2f
            val rot = sim.ringRotation(i)
            if (p.goal) {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, y, 0f)
                Matrix.rotateM(model, 0, deg(rot), 0f, 1f, 0f)
                material(5, 0, floatArrayOf(1f, 0.78f, 0.25f), zero, zero, gloss = 120f, metal = 1f)
                lit.m4("uModel", model)
                goalRing.draw()
                continue
            }
            val c = game.ringColor(i)
            for (k in 0 until Tower.SLICES) {
                val type = p.slices[k]
                if (type == SliceType.GAP) continue
                sliceModel(y, rot + k * Tower.SLICE_ANGLE + seam / 2f, 1f)
                if (type == SliceType.DANGER) {
                    material(4, 0, t.danger, zero, zero, gloss = 90f, metal = 0f)
                } else {
                    material(0, 0, c, zero, zero, gloss = 55f + 60f * t.metalRings, metal = t.metalRings)
                }
                lit.m4("uModel", model)
                slice.draw()
            }
        }

        // Ball
        val (sy, sxz) = game.ballScale()
        val b = game.ball
        Matrix.setIdentityM(model, 0)
        val ballY = sim.ballY - Tower.BALL_R * (1f - sy)
        Matrix.translateM(model, 0, 0f, ballY, Tower.BALL_PATH_R)
        Matrix.scaleM(model, 0, sxz, sy, sxz)
        // Rolls as the tower turns under it, plus a slow idle spin to show off its pattern.
        Matrix.rotateM(model, 0, deg(sim.rotation * Tower.BALL_PATH_R / Tower.BALL_R), 0f, 0f, 1f)
        Matrix.rotateM(model, 0, time * 40f, 0f, 1f, 0f)
        val dead = sim.dead
        val fire = sim.onFire && !dead
        val emis = when {
            fire -> floatArrayOf(0.9f, 0.35f + 0.15f * sin(time * 20f), 0.05f)
            else -> zero
        }
        val ballCol = if (dead) gray(b.c1) else b.c1
        material(2, b.style, ballCol, if (dead) gray(b.c2) else b.c2, emis, gloss = b.gloss, metal = b.metal)
        lit.m4("uModel", model)
        sphere.draw()
        projectBall(ballY)

        // ---------------------------------------------------------- transparent passes
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)

        // Paint splats
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        splat.use()
        splat.m4("uVP", vp)
        for (s in game.splats) {
            if (s.ring < sim.next) continue
            val age = time - s.born
            val grow = min(1f, age * 9f)
            val alpha = 0.9f * (1f - ((age - 4f) / 2f).coerceIn(0f, 1f))
            val a = s.angle + sim.ringRotation(s.ring)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, Tower.BALL_PATH_R * sin(a), Tower.platformTop(s.ring) + 0.004f, Tower.BALL_PATH_R * kotlin.math.cos(a))
            Matrix.rotateM(model, 0, deg(s.spin), 0f, 1f, 0f)
            val sc = 0.35f + 0.65f * grow
            Matrix.scaleM(model, 0, sc, 1f, sc)
            splat.m4("uModel", model)
            splat.v3("uColor", s.color)
            splat.f("uAlpha", alpha)
            splat.f("uSeed", s.seed)
            disc.draw()
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        // Rings flying apart
        lit.use()
        for (br in game.breaks) {
            val age = time - br.born
            val dur = if (br.smash) 0.9f else 0.7f
            val k01 = (age / dur).coerceIn(0f, 1f)
            if (k01 >= 1f) continue
            val p = sim.level.platforms.getOrNull(br.ring) ?: continue
            val c = game.ringColor(br.ring)
            val y0 = Tower.platformTop(br.ring) - Tower.THICKNESS / 2f
            lit.f("uAlpha", (1f - k01) * (1f - k01))
            for (k in 0 until Tower.SLICES) {
                val type = p.slices[k]
                if (type == SliceType.GAP) continue
                val a = br.rotation + k * Tower.SLICE_ANGLE + seam / 2f
                val mid = a + sliceSpan / 2f
                val out = age * (if (br.smash) 9f else 5.5f) + age * age * 4f
                val lift = age * 3f - age * age * 9f
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, sin(mid) * out, y0 + lift, kotlin.math.cos(mid) * out)
                // Tumble around the slice's own tangent.
                Matrix.rotateM(model, 0, age * (if (k % 2 == 0) 320f else -260f), kotlin.math.cos(mid), 0f, -sin(mid))
                Matrix.rotateM(model, 0, deg(a), 0f, 1f, 0f)
                val s = 1f - 0.35f * k01
                Matrix.scaleM(model, 0, s, s, s)
                if (type == SliceType.DANGER) material(4, 0, t.danger, zero, zero, 90f, 0f)
                else material(0, 0, c, if (br.smash) floatArrayOf(1f, 0.5f, 0.1f) else zero, if (br.smash) floatArrayOf(0.6f * (1 - k01), 0.25f * (1 - k01), 0f) else zero, 60f, t.metalRings)
                lit.m4("uModel", model)
                slice.draw()
            }
        }
        lit.f("uAlpha", 1f)

        // Additive glows: danger halos, goal halo, ball glow
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        for (i in first..last) {
            if (i < sim.next) continue
            val p = sim.level.platforms[i]
            val y = Tower.platformTop(i) - Tower.THICKNESS / 2f
            val rot = sim.ringRotation(i)
            if (p.goal) {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, y, 0f)
                Matrix.scaleM(model, 0, 1.06f, 1.4f, 1.06f)
                material(3, 0, floatArrayOf(1f, 0.75f, 0.2f), zero, zero, 1f, 0f)
                lit.f("uAlpha", 0.35f + 0.15f * sin(time * 4f))
                lit.m4("uModel", model)
                goalRing.draw()
                continue
            }
            for (k in 0 until Tower.SLICES) {
                if (p.slices[k] != SliceType.DANGER) continue
                sliceModel(y, rot + k * Tower.SLICE_ANGLE + seam / 2f, 1.05f)
                material(3, 0, t.danger, zero, zero, 1f, 0f)
                lit.f("uAlpha", 0.28f + 0.12f * sin(time * 5f + k))
                lit.m4("uModel", model)
                slice.draw()
            }
        }
        lit.f("uAlpha", 1f)

        drawParticles(sim.ballY, fire, dead)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    // ------------------------------------------------------------------ helpers

    private val zero = floatArrayOf(0f, 0f, 0f)

    private fun TowerLook.pattern() = pillarStyle

    private fun material(kind: Int, pattern: Int, c: FloatArray, c2: FloatArray, emis: FloatArray, gloss: Float, metal: Float) {
        lit.i("uKind", kind)
        lit.i("uPattern", pattern)
        lit.v3("uColor", c)
        lit.v3("uColor2", c2)
        lit.v3("uEmissive", emis)
        lit.f("uGloss", gloss)
        lit.f("uMetal", metal)
    }

    private fun sliceModel(y: Float, angle: Float, scale: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, y, 0f)
        Matrix.rotateM(model, 0, deg(angle), 0f, 1f, 0f)
        if (scale != 1f) {
            // Grow around the slice centre (for halos).
            Matrix.scaleM(model, 0, scale, 1f + (scale - 1f) * 6f, scale)
        }
    }

    private fun drawBackground(t: TowerLook, time: Float, camY: Float) {
        GLES30.glDepthMask(false)
        bg.use()
        bg.v3("uTop", t.bgTop)
        bg.v3("uBottom", t.bgBottom)
        bg.v3("uA1", t.aurora[0])
        bg.v3("uA2", t.aurora[1])
        bg.v3("uA3", t.aurora[2])
        bg.f("uTime", time)
        bg.f("uScroll", -camY)
        bg.f("uAspect", width.toFloat() / height)
        bg.f("uStars", if (t.stars) 1f else 0f)
        screen.draw()
        GLES30.glDepthMask(true)
    }

    private fun drawParticles(ballY: Float, fire: Boolean, dead: Boolean) {
        val pa = game.particles
        ptBuf.clear()
        var n = 0
        // Comet trail behind the ball.
        if (!dead) {
            val tc = if (fire) floatArrayOf(1f, 0.45f, 0.1f) else game.ball.trail
            val tr = game.trail
            val count = tr.size
            for ((idx, y) in tr.withIndex()) {
                if (idx == count - 1) continue
                val k = idx.toFloat() / count
                if (ballY - y > -0.05f && kotlin.math.abs(ballY - y) < 0.05f) continue
                put(0f, y, Tower.BALL_PATH_R, tc[0], tc[1], tc[2], 0.55f * k, Tower.BALL_R * (0.9f + 1.4f * k))
                n++
            }
            // Soft glow around the ball.
            put(0f, ballY, Tower.BALL_PATH_R, tc[0], tc[1], tc[2], if (fire) 0.9f else 0.35f, Tower.BALL_R * (if (fire) 6f else 4f))
            n++
        }
        val limit = min(pa.count, ptCapacity - n)
        for (i in 0 until limit) {
            val l = (pa.life[i] / pa.maxLife[i]).coerceIn(0f, 1f)
            put(pa.px[i], pa.py[i], pa.pz[i], pa.r[i], pa.g[i], pa.b[i], pa.a[i] * l, pa.size[i] * (0.5f + 0.5f * l) * 3f)
            n++
        }
        if (n == 0) return
        ptBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ptVbo[0])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * 32, ptBuf)
        pts.use()
        pts.m4("uVP", vp)
        pts.f("uScale", height * 1.1f)
        GLES30.glBindVertexArray(ptVao[0])
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, n)
        GLES30.glBindVertexArray(0)
    }

    private fun put(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float, s: Float) {
        ptBuf.put(x); ptBuf.put(y); ptBuf.put(z)
        ptBuf.put(r); ptBuf.put(g); ptBuf.put(b); ptBuf.put(a)
        ptBuf.put(s)
    }

    private fun projectBall(y: Float) {
        vec[0] = 0f; vec[1] = y; vec[2] = Tower.BALL_PATH_R; vec[3] = 1f
        Matrix.multiplyMV(out4, 0, vp, 0, vec, 0)
        if (out4[3] != 0f) {
            game.ballScreenX = (out4[0] / out4[3]) * 0.5f + 0.5f
            game.ballScreenY = 1f - ((out4[1] / out4[3]) * 0.5f + 0.5f)
        }
    }

    private fun deg(r: Float) = Math.toDegrees(r.toDouble()).toFloat()
    private fun mix(a: FloatArray, b: FloatArray, t: Float) =
        floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)
    private fun gray(c: FloatArray): FloatArray {
        val l = c[0] * 0.3f + c[1] * 0.59f + c[2] * 0.11f
        return floatArrayOf(l * 0.6f, l * 0.6f, l * 0.65f)
    }
}
