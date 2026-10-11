package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*; import kotlin.math.exp; import kotlin.random.Random
class NeuralAgent : Agent {
    override val name = "Neuron — distilled policy net"
    override var plannedPath: List<Pt> = emptyList()
    private val rng = Random(3)
    private val acts = arrayOf(Action.N,Action.E,Action.S,Action.W,Action.NE,Action.SE,Action.SW,Action.NW,Action.WAIT,Action.POTION,Action.DESCEND,Action.THROW)
    private val inDim = 12; private val hid = 16
    private var w1 = Array(inDim) { DoubleArray(hid) { rng.nextDouble(-0.5, 0.5) } }
    private var w2 = Array(hid) { DoubleArray(acts.size) { rng.nextDouble(-0.5, 0.5) } }
    var epsilon = 0.15
    fun distillFrom(q: QLAgent) { val sum = DoubleArray(acts.size); for (v in q.table.values()) { for (i in 0 until minOf(v.size, acts.size)) sum[i] += v[i] }
        val n = q.statesLearned.coerceAtLeast(1); for (h in 0 until hid) for (a in acts.indices) w2[h][a] = sum[a] / n * 0.3 }
    private fun features(g: Game): DoubleArray { val h = g.hero; val vis = g.monsters.filter { it.alive && g.visible[g.idx(it.x, it.y)] }; val eDist = vis.minOfOrNull { cheb(it.x, it.y, h.x, h.y) } ?: 9
        return doubleArrayOf(h.hp.toDouble() / h.maxHp, h.potions / 3.0, h.knives / 5.0, h.hunger / 100.0, vis.size / 5.0, eDist / 8.0, g.exploredFraction().toDouble(),
            (if (g.poisonTurns > 0) 1.0 else 0.0), g.depth / 8.0, g.items.count { g.visible[g.idx(it.x, it.y)] } / 5.0, (if (g.tileAt(h.x, h.y) == T.STAIRS) 1.0 else 0.0), g.ascension / 3.0) }
    override fun decide(g: Game): Pair<Action, String> { plannedPath = emptyList()
        if (rng.nextDouble() < epsilon) { val legal = g.legalActions(); return legal.random(rng) to "🧠 explore" }
        val x = features(g); val h = DoubleArray(hid); for (j in 0 until hid) { var s = 0.0; for (i in 0 until inDim) s += x[i] * w1[i][j]; h[j] = tanh(s) }
        val logits = DoubleArray(acts.size); for (a in acts.indices) { var s = 0.0; for (j in 0 until hid) s += h[j] * w2[j][a]; logits[a] = s }
        val legal = g.legalActions().toSet(); var bestA = Action.WAIT; var bestV = Double.NEGATIVE_INFINITY
        for (i in acts.indices) { if (acts[i] in legal && logits[i] > bestV) { bestV = logits[i]; bestA = acts[i] } }
        return bestA to "🧠 net picks ${bestA.emoji}" }
    private fun tanh(v: Double) = (exp(2 * v) - 1) / (exp(2 * v) + 1)
}
