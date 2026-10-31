package com.farias.prismatower.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Interleaved vertex data: position (3), normal (3), uv (2). */
class MeshData {
    val v = ArrayList<Float>(4096)
    val i = ArrayList<Int>(4096)
    val count get() = v.size / 8

    fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f): Int {
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz); v.add(u); v.add(w)
        return count - 1
    }

    fun tri(a: Int, b: Int, c: Int) {
        i.add(a); i.add(b); i.add(c)
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) {
        tri(a, b, c); tri(a, c, d)
    }
}

/** A GPU mesh (VAO + buffers). Must be created and drawn on the GL thread. */
class Mesh(data: MeshData) {
    private val vao = IntArray(1)
    private val bufs = IntArray(2)
    private val indexCount = data.i.size

    init {
        val vb: FloatBuffer = ByteBuffer.allocateDirect(data.v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        data.v.forEach { vb.put(it) }
        vb.position(0)
        val ib: IntBuffer = ByteBuffer.allocateDirect(data.i.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        data.i.forEach { ib.put(it) }
        ib.position(0)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glGenBuffers(2, bufs, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.v.size * 4, vb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, data.i.size * 4, ib, GLES30.GL_STATIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }
}

/**
 * Shape builders. Angles follow the game: a point at angle θ sits at (r·sinθ, y, r·cosθ), so θ = 0
 * faces the camera (where the ball is) and rotating about +Y adds to θ.
 */
object Shapes {

    /**
     * A ring sector from angle 0 to [span], radii [rIn]..[rOut], height [h] centred on y = 0, with a
     * chamfer [b] on all four long edges (catches highlights for a premium look) and flat end caps.
     */
    fun wedge(span: Float, rIn: Float, rOut: Float, h: Float, b: Float, steps: Int = 8): MeshData {
        val m = MeshData()
        val top = h / 2f
        val bot = -h / 2f
        // Cross-section profile (r, y), counter-clockwise seen with r to the right and y up.
        val prof = arrayOf(
            floatArrayOf(rIn + b, top), floatArrayOf(rOut - b, top),
            floatArrayOf(rOut, top - b), floatArrayOf(rOut, bot + b),
            floatArrayOf(rOut - b, bot), floatArrayOf(rIn + b, bot),
            floatArrayOf(rIn, bot + b), floatArrayOf(rIn, top - b),
        )
        // Sweep each profile edge along the arc, flat-shaded per edge.
        for (e in prof.indices) {
            val p0 = prof[e]
            val p1 = prof[(e + 1) % prof.size]
            // The profile runs clockwise (r right, y up), so the outward normal is the edge
            // direction turned +90°.
            val dr = p1[0] - p0[0]
            val dy = p1[1] - p0[1]
            val len = sqrt(dr * dr + dy * dy)
            val nr = -dy / len
            val ny = dr / len
            var prevA = -1
            var prevB = -1
            for (s in 0..steps) {
                val a = span * s / steps
                val sa = sin(a)
                val ca = cos(a)
                val ia = m.vert(p0[0] * sa, p0[1], p0[0] * ca, nr * sa, ny, nr * ca, s.toFloat() / steps, p0[0])
                val ib = m.vert(p1[0] * sa, p1[1], p1[0] * ca, nr * sa, ny, nr * ca, s.toFloat() / steps, p1[0])
                if (prevA >= 0) m.quad(prevA, ia, ib, prevB)
                prevA = ia
                prevB = ib
            }
        }
        // End caps (convex octagon fan).
        for (end in 0..1) {
            val a = if (end == 0) 0f else span
            val sa = sin(a)
            val ca = cos(a)
            // Tangent direction of increasing angle is (cos a, 0, -sin a); cap at 0 faces -tangent.
            val sign = if (end == 0) -1f else 1f
            val nx = ca * sign
            val nz = -sa * sign
            val base = m.count
            for (p in prof) m.vert(p[0] * sa, p[1], p[0] * ca, nx, 0f, nz, 0f, p[0])
            for (k in 1 until prof.size - 1) {
                if (end == 0) m.tri(base, base + k + 1, base + k) else m.tri(base, base + k, base + k + 1)
            }
        }
        return m
    }

    /** Open cylinder of [radius] from y = [y0] to [y1], with a dome-ish cap on top. */
    fun cylinder(radius: Float, y0: Float, y1: Float, seg: Int = 64): MeshData {
        val m = MeshData()
        for (s in 0..seg) {
            val a = (2 * PI * s / seg).toFloat()
            val x = sin(a)
            val z = cos(a)
            m.vert(radius * x, y0, radius * z, x, 0f, z, s.toFloat() / seg, y0)
            m.vert(radius * x, y1, radius * z, x, 0f, z, s.toFloat() / seg, y1)
            if (s > 0) {
                val b = m.count - 4
                m.quad(b, b + 2, b + 3, b + 1)
            }
        }
        // Top cap
        val c = m.vert(0f, y1, 0f, 0f, 1f, 0f)
        val ring = m.count
        for (s in 0..seg) {
            val a = (2 * PI * s / seg).toFloat()
            m.vert(radius * sin(a), y1, radius * cos(a), sin(a) * 0.3f, 0.95f, cos(a) * 0.3f)
        }
        for (s in 0 until seg) m.tri(c, ring + s, ring + s + 1)
        return m
    }

    fun sphere(r: Float, lat: Int = 28, lon: Int = 40): MeshData {
        val m = MeshData()
        for (i in 0..lat) {
            val th = (PI * i / lat).toFloat()
            for (j in 0..lon) {
                val ph = (2 * PI * j / lon).toFloat()
                val x = sin(th) * sin(ph)
                val y = cos(th)
                val z = sin(th) * cos(ph)
                m.vert(r * x, r * y, r * z, x, y, z, j.toFloat() / lon, i.toFloat() / lat)
            }
        }
        val row = lon + 1
        for (i in 0 until lat) for (j in 0 until lon) {
            val a = i * row + j
            m.quad(a, a + row, a + row + 1, a + 1)
        }
        return m
    }

    /** Flat square in XZ, uv in -1..1 (for paint splats and glows). */
    fun disc(size: Float): MeshData {
        val m = MeshData()
        val s = size
        val a = m.vert(-s, 0f, -s, 0f, 1f, 0f, -1f, -1f)
        val b = m.vert(s, 0f, -s, 0f, 1f, 0f, 1f, -1f)
        val c = m.vert(s, 0f, s, 0f, 1f, 0f, 1f, 1f)
        val d = m.vert(-s, 0f, s, 0f, 1f, 0f, -1f, 1f)
        m.quad(a, d, c, b)
        return m
    }

    /** Full-screen triangle. */
    fun screen(): MeshData {
        val m = MeshData()
        m.vert(-1f, -1f, 0f, 0f, 0f, 1f, 0f, 0f)
        m.vert(3f, -1f, 0f, 0f, 0f, 1f, 2f, 0f)
        m.vert(-1f, 3f, 0f, 0f, 0f, 1f, 0f, 2f)
        m.tri(0, 1, 2)
        return m
    }
}

/** Compiles and links a shader program; caches uniform locations. */
class Program(vs: String, fs: String) {
    val id: Int = GLES30.glCreateProgram()
    private val locs = HashMap<String, Int>()

    init {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        GLES30.glAttachShader(id, v)
        GLES30.glAttachShader(id, f)
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("link: " + GLES30.glGetProgramInfoLog(id))
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("shader: " + GLES30.glGetShaderInfoLog(s))
        return s
    }

    fun use() = GLES30.glUseProgram(id)
    fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    fun f(name: String, x: Float) = GLES30.glUniform1f(loc(name), x)
    fun i(name: String, x: Int) = GLES30.glUniform1i(loc(name), x)
    fun v2(name: String, x: Float, y: Float) = GLES30.glUniform2f(loc(name), x, y)
    fun v3(name: String, c: FloatArray) = GLES30.glUniform3f(loc(name), c[0], c[1], c[2])
    fun v3(name: String, x: Float, y: Float, z: Float) = GLES30.glUniform3f(loc(name), x, y, z)
    fun m4(name: String, m: FloatArray) = GLES30.glUniformMatrix4fv(loc(name), 1, false, m, 0)
}
