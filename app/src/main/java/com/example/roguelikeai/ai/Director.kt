package com.example.roguelikeai.ai
import com.example.roguelikeai.game.Game
object Director {
    private val recent = ArrayDeque<Boolean>()
    fun onEpisodeEnd(g: Game, died: Boolean) { recent.addLast(died); if (recent.size > 6) recent.removeFirst()
        val deathRate = recent.count { it } / recent.size.coerceAtLeast(1)
        g.directorTension = when { deathRate > 0.6 -> (g.directorTension * 0.85).coerceAtLeast(0.6); deathRate < 0.2 -> (g.directorTension * 1.15).coerceAtMost(2.0); else -> g.directorTension } }
    fun describe(g: Game) = "🎬 tension x${"%.2f".format(g.directorTension)}"
}
