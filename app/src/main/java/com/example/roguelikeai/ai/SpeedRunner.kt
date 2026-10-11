package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
data class EpisodeResult(val run: Int, val depth: Int, val reward: Double, val won: Boolean)
object SpeedRunner {
    fun run(agent: Agent, episodes: Int, onProgress: (Int) -> Unit, onDone: (List<EpisodeResult>) -> Unit) {
        kotlin.concurrent.thread { val out = mutableListOf<EpisodeResult>()
            for (i in 1..episodes) { val g = Game(); g.newRun(); agent.onNewRun(g); var reward = 0.0; var done = false; var guard = 0
                while (!done && guard++ < 4000) { val (a, _) = agent.decide(g); val r = g.step(a); agent.onTurnResult(g, r)
                    reward += r.kills * 5.0 - r.damageTaken * 1.5 + r.itemsPicked * 2.5 + r.factionCasualties * 3.0 + r.newSeenTiles * 0.05
                    if (r.died) { done = true; agent.onEpisodeEnd(g, "died") }; if (r.won) { done = true; reward += 50.0; agent.onEpisodeEnd(g, "won") } }
                out += EpisodeResult(i, g.depth, reward, false); onProgress(i) }; onDone(out) } }
}
