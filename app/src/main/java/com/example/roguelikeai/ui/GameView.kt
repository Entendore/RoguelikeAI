package com.example.roguelikeai.ui
import android.content.Context; import android.graphics.Canvas; import android.graphics.Paint; import android.graphics.Typeface; import android.util.AttributeSet; import android.view.View
import com.example.roguelikeai.game.*; import kotlin.math.min
class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var game: Game? = null; set(value) { field = value; invalidate() }
    var pathOverlay: List<Pt> = emptyList(); var revealAll = false; var manual = false
    private val tilePaint = Paint()
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textAlign = Paint.Align.CENTER }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x994DD0E1.toInt() }
    private val heroRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val barBg = Paint().apply { color = 0x88000000.toInt() }; private val barFg = Paint().apply { color = 0xFFE53935.toInt() }
    private val factionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawColor(0xFF05070A.toInt()); val g = game ?: return
        val tile = min(width.toFloat() / g.width, height.toFloat() / g.height).toInt().coerceAtLeast(6)
        val ox = (width - tile * g.width) / 2f; val oy = (height - tile * g.height) / 2f; emojiPaint.textSize = tile * 0.82f
        for (y in 0 until g.height) for (x in 0 until g.width) { val i = g.idx(x, y); val seen = revealAll || g.seen[i]; if (!seen) continue; val vis = revealAll || g.visible[i]; val t = g.map[i]; val left = ox + x * tile; val top = oy + y * tile
            tilePaint.color = when (t) { T.WALL -> if (vis) 0xFF3D4A56.toInt() else 0xFF222A32.toInt(); T.STAIRS -> if (vis) 0xFF4A4430.toInt() else 0xFF2A2720.toInt(); T.CORRIDOR -> if (vis) 0xFF1F252D.toInt() else 0xFF12161C.toInt()
                T.GRASS -> if (vis) 0xFF24331F.toInt() else 0xFF161F14.toInt(); T.WATER -> if (vis) 0xFF173A5E.toInt() else 0xFF0F2438.toInt(); T.LAVA -> if (vis) 0xFF8C2F0D.toInt() else 0xFF4A1B0A.toInt()
                T.DOOR_CLOSED -> if (vis) 0xFF5D4037.toInt() else 0xFF33241F.toInt(); T.DOOR_OPEN -> if (vis) 0xFF2A2320.toInt() else 0xFF191512.toInt(); T.PILLAR -> if (vis) 0xFF39434E.toInt() else 0xFF20262D.toInt()
                T.RUBBLE -> if (vis) 0xFF33302B.toInt() else 0xFF1D1B18.toInt(); T.FOUNTAIN -> if (vis) 0xFF1E4D5C.toInt() else 0xFF132C35.toInt(); else -> if (vis) 0xFF232A33.toInt() else 0xFF141920.toInt() }
            canvas.drawRect(left, top, left + tile, top + tile, tilePaint); emojiPaint.alpha = if (vis) 255 else 110
            when (t) { T.STAIRS -> emoji(canvas, "🕳️", left, top, tile); T.DOOR_CLOSED -> emoji(canvas, "🚪", left, top, tile)
                T.DOOR_OPEN -> { emojiPaint.alpha = if (vis) 120 else 60; emoji(canvas, "🚪", left, top, tile) }; T.PILLAR -> emoji(canvas, "🏛️", left, top, tile)
                T.RUBBLE -> emoji(canvas, "🧱", left, top, tile); T.WATER -> emoji(canvas, "💧", left, top, tile); T.LAVA -> emoji(canvas, "🔥", left, top, tile)
                T.FOUNTAIN -> emoji(canvas, "⛲", left, top, tile); T.GRASS -> if ((x * 7 + y * 13) % 6 == 0) { emojiPaint.alpha = if (vis) 150 else 70; emoji(canvas, "🌿", left, top, tile) } }
            val trap = g.traps[i]; if (trap != null) { val a = when { trap.sprung -> if (vis) 255 else 130; revealAll -> 160; else -> 0 }; if (a > 0) { emojiPaint.alpha = a; emoji(canvas, "⚠️", left, top, tile) } } }
        for (it in g.items) { val i = g.idx(it.x, it.y); if (!revealAll && !g.seen[i]) continue; emojiPaint.alpha = if (revealAll || g.visible[i]) 255 else 120; emoji(canvas, it.kind.emoji, ox + it.x * tile, oy + it.y * tile, tile) }
        for (m in g.monsters) { if (!m.alive) continue; if (!revealAll && !g.visible[g.idx(m.x, m.y)]) continue; val left = ox + m.x * tile; val top = oy + m.y * tile; emojiPaint.alpha = 255; emoji(canvas, m.def.emoji, left, top, tile)
            if (m.hp < m.maxHp) { val frac = (m.hp.toFloat() / m.maxHp).coerceIn(0f, 1f); val bw = tile * 0.8f; val bx = left + tile * 0.1f; canvas.drawRect(bx, top + 1f, bx + bw, top + 1f + tile * 0.14f, barBg); canvas.drawRect(bx, top + 1f, bx + bw * frac, top + 1f + tile * 0.14f, barFg) }
            val fc = m.def.faction.color; if (fc != 0) { factionPaint.color = fc; canvas.drawCircle(left + tile * 0.16f, top + tile * 0.16f, tile * 0.11f, factionPaint) }
            m.elite?.let { e -> emojiPaint.alpha = 255; emoji(canvas, e.emoji, left + tile * 0.55f, top - tile * 0.15f, tile) }
            if (m.status.isNotEmpty()) { emojiPaint.textSize = tile * 0.4f; emoji(canvas, m.status.keys.first().emoji, left, top + tile * 0.55f, tile); emojiPaint.textSize = tile * 0.82f } }
        for (n in g.npcs) { val i = g.idx(n.x, n.y); if (!revealAll && !g.visible[i]) continue; val left = ox + n.x * tile; val top = oy + n.y * tile; emojiPaint.alpha = 255; emoji(canvas, n.kind.emoji, left, top, tile)
            if (n.kind == NpcKind.VILLAGER && n.greeted) canvas.drawCircle(left + tile * 0.85f, top + tile * 0.15f, tile * 0.1f, dotPaint) }
        val h = g.hero; val hLeft = ox + h.x * tile; val hTop = oy + h.y * tile; heroRing.color = if (manual) 0xFFFFB74D.toInt() else 0xFF4DD0E1.toInt()
        canvas.drawCircle(hLeft + tile / 2f, hTop + tile / 2f, tile * 0.45f, heroRing); emojiPaint.alpha = 255; emoji(canvas, "🦸", hLeft, hTop, tile)
        if (pathOverlay.isNotEmpty()) { emojiPaint.alpha = 170; emojiPaint.textSize = tile * 0.5f; for (p in pathOverlay.dropLast(1)) emoji(canvas, "👣", ox + p.x * tile, oy + p.y * tile, tile)
            emojiPaint.textSize = tile * 0.82f; emojiPaint.alpha = 235; pathOverlay.last().let { p -> emoji(canvas, "🎯", ox + p.x * tile, oy + p.y * tile, tile) }; emojiPaint.textSize = tile * 0.82f; emojiPaint.alpha = 255 } }
    private fun emoji(canvas: Canvas, s: String, left: Float, top: Float, tile: Int) { canvas.drawText(s, left + tile / 2f, top + tile / 2f - (emojiPaint.ascent() + emojiPaint.descent()) / 2f, emojiPaint) }
    fun tileAtPx(px: Float, py: Float): Pt? { val g = game ?: return null; val tile = min(width.toFloat() / g.width, height.toFloat() / g.height).toInt().coerceAtLeast(6)
        val ox = (width - tile * g.width) / 2f; val oy = (height - tile * g.height) / 2f; val x = ((px - ox) / tile).toInt(); val y = ((py - oy) / tile).toInt()
        return if (g.inBounds(x, y)) Pt(x, y) else null }
}
