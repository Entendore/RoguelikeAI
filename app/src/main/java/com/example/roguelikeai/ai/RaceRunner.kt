package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
import kotlin.concurrent.thread
object RaceRunner {
    fun race(a: Agent, b: Agent, onDone: (String) -> Unit) {
        thread { val ga = Game(); ga.newRun(); val gb = ga.snapshot(); a.onNewRun(ga); b.onNewRun(gb)
            var guard = 0; var winner = "draw"
            while (guard++ < 6000) { val (aa, ra) = a.decide(ga) to ga.step(a.decide(ga).first); a.onTurnResult(ga, ra); if (ra.won) { winner = a.name; break }
                val (ab, rb) = b.decide(gb) to gb.step(b.decide(gb).first); b.onTurnResult(gb, rb); if (rb.won) { winner = b.name; break }
                if (ra.died && rb.died) break }; onDone(winner) } }
}
