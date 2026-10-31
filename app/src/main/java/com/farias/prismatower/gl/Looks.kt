package com.farias.prismatower.gl

import androidx.annotation.StringRes
import com.farias.prismatower.R

/** Colour as 3 floats (0..1) from 0xRRGGBB. */
fun rgb(c: Long): FloatArray = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)

/** Pillar surface styles, implemented in the shader. */
object PillarStyle {
    const val NEON_BANDS = 0
    const val RIBS = 1
    const val SPIRAL = 2
    const val CRYSTAL = 3
    const val BRICKS = 4
    const val CIRCUIT = 5
}

/** A tower look: background, pillar, rings, danger slices and the goal. */
class TowerLook(
    val id: String,
    @StringRes val nameRes: Int,
    val price: Int,
    bgTop: Long,
    bgBottom: Long,
    aurora: List<Long>,
    pillar: Long,
    pillar2: Long,
    val pillarStyle: Int,
    ringA: Long,
    ringB: Long,
    danger: Long,
    rim: Long,
    val stars: Boolean = false,
    val metalRings: Float = 0f,
) {
    val bgTop = rgb(bgTop)
    val bgBottom = rgb(bgBottom)
    val aurora = aurora.map { rgb(it) }
    val pillar = rgb(pillar)
    val pillar2 = rgb(pillar2)
    val ringA = rgb(ringA)
    val ringB = rgb(ringB)
    val danger = rgb(danger)
    val rim = rgb(rim)
    /** For UI previews (ARGB ints). */
    val uiColors: List<Long> = listOf(bgTop, bgBottom, pillar, ringA, ringB, danger)
}

/** Ball surface styles, implemented in the shader. */
object BallStyle {
    const val PEARL = 0
    const val METAL = 1
    const val STRIPES = 2
    const val BEACH = 3
    const val RAINBOW = 4
    const val NEON = 5
    const val DISCO = 6
    const val PLANET = 7
    const val EIGHT = 8
    const val LAVA = 9
}

class BallLook(
    val id: String,
    @StringRes val nameRes: Int,
    val price: Int,
    c1: Long,
    c2: Long,
    val style: Int,
    trail: Long,
    val gloss: Float = 60f,
    val metal: Float = 0f,
) {
    val c1 = rgb(c1)
    val c2 = rgb(c2)
    val trail = rgb(trail)
    val uiColors: List<Long> = listOf(c1, c2, trail)
}

object Looks {
    val towers = listOf(
        TowerLook(
            "neon", R.string.tower_neon, 0,
            0x0A0620, 0x1A0B3D, listOf(0x7C3AED, 0x06B6D4, 0xDB2777),
            0x2B1B5E, 0x22D3EE, PillarStyle.NEON_BANDS,
            0x22D3EE, 0xA855F7, 0xFF2D55, 0xE879F9,
        ),
        TowerLook(
            "ocean", R.string.tower_ocean, 400,
            0x021A2E, 0x04395A, listOf(0x0EA5E9, 0x14B8A6, 0x6366F1),
            0x0B4F6C, 0x7DD3FC, PillarStyle.RIBS,
            0x2DD4BF, 0x38BDF8, 0xFF5A5F, 0x99F6E4,
        ),
        TowerLook(
            "candy", R.string.tower_candy, 700,
            0x3B1238, 0x6B1E4F, listOf(0xF472B6, 0xC4B5FD, 0xFDBA74),
            0xFFF1F7, 0xFF7EB6, PillarStyle.SPIRAL,
            0xFF9EC4, 0x9BE8D8, 0x8B5CF6, 0xFFFFFF,
        ),
        TowerLook(
            "lava", R.string.tower_lava, 1000,
            0x120303, 0x2E0A04, listOf(0xDC2626, 0xEA580C, 0xBE185D),
            0x1C1412, 0xFF6A1A, PillarStyle.CRYSTAL,
            0x3A2A26, 0x55362C, 0xFF7A00, 0xFF8A3D,
        ),
        TowerLook(
            "galaxy", R.string.tower_galaxy, 1400,
            0x02010A, 0x0D0B30, listOf(0x4F46E5, 0x9333EA, 0x0891B2),
            0x1E1B4B, 0xC4B5FD, PillarStyle.CRYSTAL,
            0x818CF8, 0xF0ABFC, 0xFF3B6B, 0xA5B4FC, stars = true,
        ),
        TowerLook(
            "gold", R.string.tower_gold, 2000,
            0x0B0904, 0x231A07, listOf(0xCA8A04, 0xB45309, 0x92400E),
            0x2A2110, 0xFACC15, PillarStyle.RIBS,
            0xF5C542, 0xE8A317, 0x111111, 0xFFE8A3, stars = true, metalRings = 0.8f,
        ),
        TowerLook(
            "ice", R.string.tower_ice, 2600,
            0x0B1E33, 0x1B4767, listOf(0x7DD3FC, 0xE0F2FE, 0x818CF8),
            0xCFE9FF, 0x7DD3FC, PillarStyle.CRYSTAL,
            0xE0F2FE, 0x93C5FD, 0x3B82F6, 0xFFFFFF, metalRings = 0.3f,
        ),
        TowerLook(
            "jungle", R.string.tower_jungle, 3200,
            0x04140C, 0x0B3320, listOf(0x16A34A, 0x84CC16, 0x0D9488),
            0x3F2A17, 0x86EFAC, PillarStyle.BRICKS,
            0x4ADE80, 0xA3E635, 0xF97316, 0xBBF7D0,
        ),
        TowerLook(
            "sakura", R.string.tower_sakura, 4000,
            0x2A1424, 0x5A2A4A, listOf(0xF9A8D4, 0xFBCFE8, 0xC084FC),
            0x3B2230, 0xFBCFE8, PillarStyle.SPIRAL,
            0xFFFFFF, 0xF9A8D4, 0xBE123C, 0xFFE4F1, stars = true,
        ),
        TowerLook(
            "cyber", R.string.tower_cyber, 5000,
            0x020607, 0x06171A, listOf(0x22C55E, 0x06B6D4, 0x84CC16),
            0x061316, 0x39FF88, PillarStyle.CIRCUIT,
            0x0F2A2E, 0x123A33, 0xFF2BD6, 0x39FF88, metalRings = 0.5f,
        ),
    )

    val balls = listOf(
        BallLook("pearl", R.string.ball_pearl, 0, 0xF8F7FF, 0xD9D4FF, BallStyle.PEARL, 0xE879F9),
        BallLook("ruby", R.string.ball_ruby, 250, 0xE11D48, 0xFF8FAB, BallStyle.PEARL, 0xFF4D6D, gloss = 90f),
        BallLook("sapphire", R.string.ball_sapphire, 250, 0x2563EB, 0x93C5FD, BallStyle.PEARL, 0x60A5FA, gloss = 90f),
        BallLook("beach", R.string.ball_beach, 400, 0xFFFFFF, 0xFF4D6D, BallStyle.BEACH, 0xFACC15),
        BallLook("candy", R.string.ball_candy, 500, 0xFF7EB6, 0xFFFFFF, BallStyle.STRIPES, 0xFF7EB6),
        BallLook("gold", R.string.ball_gold, 800, 0xFACC15, 0xB45309, BallStyle.METAL, 0xFDE047, gloss = 120f, metal = 1f),
        BallLook("chrome", R.string.ball_chrome, 900, 0xE5E7EB, 0x6B7280, BallStyle.METAL, 0xBAE6FD, gloss = 140f, metal = 1f),
        BallLook("neon", R.string.ball_neon, 1100, 0x39FF88, 0x22D3EE, BallStyle.NEON, 0x39FF88),
        BallLook("rainbow", R.string.ball_rainbow, 1400, 0xFF0000, 0x0000FF, BallStyle.RAINBOW, 0xF0ABFC),
        BallLook("disco", R.string.ball_disco, 1700, 0xD1D5DB, 0xF0ABFC, BallStyle.DISCO, 0xC4B5FD, gloss = 160f, metal = 0.9f),
        BallLook("planet", R.string.ball_planet, 2000, 0xF59E0B, 0x7C2D12, BallStyle.PLANET, 0xFDBA74),
        BallLook("eight", R.string.ball_eight, 2400, 0x0B0B0F, 0xFFFFFF, BallStyle.EIGHT, 0x94A3B8, gloss = 120f),
        BallLook("lava", R.string.ball_lava, 3000, 0xFF4500, 0x1C0A05, BallStyle.LAVA, 0xFF7A00),
    )

    fun tower(id: String) = towers.firstOrNull { it.id == id } ?: towers[0]
    fun ball(id: String) = balls.firstOrNull { it.id == id } ?: balls[0]
}
