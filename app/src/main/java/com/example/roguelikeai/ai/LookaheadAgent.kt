package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
class LookaheadAgent : Agent {
    override val name = "The Seer — 2-ply lookahead"
    override var plannedPath: List<Pt> = emptyList()
    private class Node(val action: Action, val sim: Game, val report: TurnReport, val score: Double)
    override fun decide(g: Game): Pair<Action, String> {
        plannedPath = emptyList(); val first = g.legalActions(); if (first.isEmpty()) return Action.WAIT to "🌫 no options"
        val layer1 = first.map { a -> val sim = g.snapshot(); val r = sim.step(a); Node(a, sim, r, evaluate(sim, r)) }
        var best = layer1.maxByOrNull { it.score }!!; var bestWhy = describe(best.report)
        val top = layer1.sortedByDescending { it.score }.take(4)
        for (n in top) { if (n.report.died || n.report.descended || n.report.won) continue
            for (a2 in n.sim.legalActions()) { val sim2 = n.sim.snapshot(); val r2 = sim2.step(a2); val s2 = n.score + evaluate(sim2, r2) * 0.6
                if (s2 > best.score) { best = Node(n.action, sim2, r2, s2); bestWhy = "${describe(n.report)} → ${describe(r2)}" } } }
        return best.action to "🔮 sees ahead: $bestWhy" }
    private fun evaluate(sim: Game, r: TurnReport): Double { val h = sim.hero; var s = h.hp * 1.2 + r.kills * 14.0 + r.factionCasualties * 8.0 + r.damageDealt * 0.8 - r.damageTaken * 2.2 + r.itemsPicked * 6.0 + r.newSeenTiles * 0.15
        if (r.levelUps > 0) s += 8.0; if (r.descended) s += 50.0; if (r.won) s += 1000.0; if (r.died) s -= 1000.0; s -= sim.poisonTurns * 2.0
        for (m in sim.monsters) { if (!m.alive || !sim.visible[sim.idx(m.x, m.y)]) continue; val d = cheb(m.x, m.y, h.x, h.y).coerceAtLeast(1); var t = m.def.atk * 1.5 / d
            if (m.def.behavior == MonsterBehavior.EXPLOSIVE && d <= 1) t += 12.0; if (m.def.behavior == MonsterBehavior.SUMMONER) t += 3.0; if (m.def.behavior == MonsterBehavior.RANGED) t += 1.5; s -= t }
        for (n in sim.npcs) { if (cheb(n.x, n.y, h.x, h.y) > 1) continue; when (n.kind) {
            NpcKind.SAGE -> if (!n.used) s += 8.0; NpcKind.MERCHANT -> if (h.gold >= 25 && h.potions < 3) s += 5.0
            NpcKind.HEALER -> s += 2.0; NpcKind.VILLAGER -> if (!n.greeted) s += 3.0; NpcKind.COOK -> if (!n.used && h.hp < h.maxHp) s += 6.0
            NpcKind.BLACKSMITH -> if (n.forgeLevel < 2 && h.gold >= 40) s += 6.0; NpcKind.GAMBLER -> if (n.rounds < 3 && h.gold >= 20) s += 2.5; NpcKind.BARD -> s += 1.5 } }
        return s }
    private fun describe(r: TurnReport): String { val p = mutableListOf<String>()
        if (r.kills > 0) p += "☠️kill"; if (r.damageDealt > 0 && r.damageTaken == 0) p += "⚔️hit"; if (r.damageTaken > 0) p += "-${r.damageTaken}hp"
        if (r.itemsPicked > 0) p += "🎁loot"; if (r.descended) p += "🕳️descend"; if (r.won) p += "💎AMULET"; if (r.died) p += "💀death!"
        return if (p.isEmpty()) "🌫quiet" else p.joinToString("/") }
}
