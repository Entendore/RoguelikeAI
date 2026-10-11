package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*; import kotlin.math.ln; import kotlin.math.sqrt; import kotlin.random.Random
class MctsAgent(private val budgetMs: Long = 60) : Agent {
    override val name = "The Seer+ — MCTS"
    override var plannedPath: List<Pt> = emptyList()
    private val rng = Random.Default; private val c = 1.41
    private class Node(val action: Action, val parent: Node?, var sim: Game) { var visits = 0; var value = 0.0; val children = mutableListOf<Node>(); var expanded = false }
    override fun decide(g: Game): Pair<Action, String> { plannedPath = emptyList(); val root = Node(Action.WAIT, null, g.snapshot())
        val deadline = System.currentTimeMillis() + budgetMs; var sims = 0
        while (System.currentTimeMillis() < deadline) { var node = root
            while (node.expanded && node.children.isNotEmpty()) { node = node.children.maxByOrNull { ucb(it) } ?: break }
            if (!node.expanded) { for (a in node.sim.legalActions()) node.children += Node(a, node, node.sim); node.expanded = true }
            val child = if (node.children.isEmpty()) node else node.children.random(rng); val v = rollout(child.sim.snapshot())
            var b: Node? = child; while (b != null) { b.visits++; b.value += v; b = b.parent }; sims++ }
        val best = root.children.maxByOrNull { it.visits } ?: return Action.WAIT to "🌫 no sims"
        return best.action to "🔮 MCTS ${sims} sims → ${best.action.emoji}" }
    private fun ucb(n: Node): Double { val p = n.parent ?: return 0.0; if (n.visits == 0) return Double.MAX_VALUE; return n.value / n.visits + c * sqrt(ln(p.visits + 1.0) / n.visits) }
    private fun rollout(sim: Game): Double { var score = 0.0; for (i in 0 until 12) { val acts = sim.legalActions(); val a = acts.random(rng); val r = sim.step(a)
        score += r.kills * 10.0 + r.damageDealt * 0.5 - r.damageTaken * 1.5 + r.itemsPicked * 4.0 + r.factionCasualties * 3.0
        if (r.won) return score + 200.0; if (r.died) return score - 100.0; if (r.descended) return score + 60.0 }
        return score + sim.hero.hp * 0.3 }
}
