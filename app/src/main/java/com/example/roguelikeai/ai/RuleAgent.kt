package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
import kotlin.math.abs
import kotlin.random.Random
class RuleAgent : Agent {
    override val name = "Sir Logicus — rule based"
    override var plannedPath: List<Pt> = emptyList()
    private val rng = Random(13)
    override fun onNewRun(g: Game) { plannedPath = emptyList() }
    override fun decide(g: Game): Pair<Action, String> {
        plannedPath = emptyList(); val h = g.hero; val hpFrac = h.hp.toFloat() / h.maxHp
        val visible = g.monsters.filter { it.alive && g.visible[g.idx(it.x, it.y)] }
        val adjacentThreat = visible.any { cheb(it.x, it.y, h.x, h.y) <= 1 }
        if (h.potions > 0 && (hpFrac <= 0.35f || (g.poisonTurns > 0 && !adjacentThreat)))
            return Action.POTION to if (g.poisonTurns > 0) "🤢 poisoned → 🧪" else "🚨 HP critical → 🧪"
        val bomberAdj = visible.any { it.def.behavior == MonsterBehavior.EXPLOSIVE && cheb(it.x, it.y, h.x, h.y) <= 1 }
        if (bomberAdj) return fleeFrom(g, visible) to "💣🏃 bomber adjacent — RUN!"
        val nearestDist = visible.minOfOrNull { cheb(it.x, it.y, h.x, h.y) } ?: Int.MAX_VALUE
        val factionWar = visible.size >= 2 && visible.any { a -> visible.any { b -> a !== b && hostile(a.def.faction, b.def.faction) && cheb(a.x, a.y, b.x, b.y) <= 3 } }
        if (factionWar && nearestDist > 2 && hpFrac > 0.5f) return Action.WAIT to "🍿 factions are fighting — watch"
        if (visible.isNotEmpty()) {
            val target = visible.maxByOrNull { threatPriority(it, h) }!!; val dist = cheb(target.x, target.y, h.x, h.y)
            val isBomber = target.def.behavior == MonsterBehavior.EXPLOSIVE
            val confident = hpFrac > 0.55f && !(isBomber && dist <= 2) && (target.hp <= h.totalAtk() * 2 || dist <= 3)
            return if (confident) stepToward(g, target.x, target.y) to "⚔️ fight ${target.def.emoji} ${target.def.name}"
            else fleeFrom(g, visible) to "🏃 too dangerous — flee"
        }
        if (g.tileAt(h.x, h.y) == T.STAIRS && (g.exploredFraction() > 0.55f || hpFrac > 0.85f)) return Action.DESCEND to "🕳️ stairs mapped → descend"
        val npc = g.npcs.firstOrNull { npcDesired(it, g) && g.visible[g.idx(it.x, it.y)] }
        if (npc != null) { val p = Nav.pathBfs(g, npc.x, npc.y); if (p != null && p.isNotEmpty()) { plannedPath = p; return stepFromPath(g, p) to "visit ${npc.kind.emoji}" } }
        val lootPath = Nav.nearestVisibleItemPath(g); if (lootPath != null && lootPath.isNotEmpty()) { plannedPath = lootPath; return stepFromPath(g, lootPath) to "🎁 loot" }
        val frontier = Nav.nearestUnseen(g); if (frontier != null && frontier.isNotEmpty()) { plannedPath = frontier; return stepFromPath(g, frontier) to "🧭 explore" }
        val stairs = findSeenStairs(g); if (stairs != null) { if (g.tileAt(h.x, h.y) == T.STAIRS) return Action.DESCEND to "🕳️ nothing left"
            val p = Nav.pathBfs(g, stairs.x, stairs.y); if (p != null && p.isNotEmpty()) { plannedPath = p; return stepFromPath(g, p) to "🕳️ walk to stairs" } }
        val opts = Action.values().filter { it.isMove && g.canPass(h.x + it.dx, h.y + it.dy) && g.tileAt(h.x + it.dx, h.y + it.dy) != T.LAVA }
        return (opts.randomOrNull(rng) ?: Action.WAIT) to "🚶 wander"
    }
    private fun npcDesired(n: Npc, g: Game): Boolean = when (n.kind) {
        NpcKind.MERCHANT -> g.hero.gold >= 25 && g.hero.potions < 2; NpcKind.SAGE -> !n.used; NpcKind.VILLAGER -> !n.greeted
        NpcKind.HEALER -> g.hero.hp < g.hero.maxHp * 0.7f; NpcKind.COOK -> !n.used && g.hero.hp < g.hero.maxHp * 0.8f
        NpcKind.BLACKSMITH -> n.forgeLevel < 2 && g.hero.gold >= 40; NpcKind.GAMBLER -> n.rounds < 3 && g.hero.gold >= 60; NpcKind.BARD -> false }
    private fun threatPriority(m: Monster, h: Hero): Double { val d = cheb(m.x, m.y, h.x, h.y); val base = m.def.atk.toDouble() / (d + 1)
        return base * when (m.def.behavior) { MonsterBehavior.SUMMONER -> 2.5; MonsterBehavior.RANGED -> 1.8; MonsterBehavior.EXPLOSIVE -> if (d <= 2) 3.0 else 0.6; MonsterBehavior.GHOST -> 1.3; else -> 1.0 } }
    private fun stepToward(g: Game, tx: Int, ty: Int): Action { val h = g.hero
        if (abs(tx - h.x) <= 1 && abs(ty - h.y) <= 1) return actionFor((tx - h.x).coerceIn(-1, 1), (ty - h.y).coerceIn(-1, 1)) ?: Action.WAIT
        val p = Nav.pathBfs(g, tx, ty); if (p != null && p.isNotEmpty()) { plannedPath = p; return stepFromPath(g, p) }
        val opts = Action.values().filter { it.isMove }.map { it to cheb(h.x + it.dx, h.y + it.dy, tx, ty) }.filter { g.canPass(h.x + it.first.dx, h.y + it.first.dy) }
        val nonLava = opts.filter { g.tileAt(h.x + it.first.dx, h.y + it.first.dy) != T.LAVA }
        return (nonLava.ifEmpty { opts }).minByOrNull { it.second }?.first ?: Action.WAIT }
    private fun fleeFrom(g: Game, threats: List<Monster>): Action { val h = g.hero; var best = Action.WAIT; var bestScore = Double.NEGATIVE_INFINITY
        for (a in Action.values()) { if (!a.isMove) continue; val nx = h.x + a.dx; val ny = h.y + a.dy; val t = g.tileAt(nx, ny)
            if (t == T.LAVA || !g.canPass(nx, ny) || g.monsterAt(nx, ny) != null) continue
            if (threats.any { it.def.behavior == MonsterBehavior.EXPLOSIVE && cheb(it.x, it.y, nx, ny) <= 1 }) continue
            var score = threats.minOf { cheb(it.x, it.y, nx, ny).toDouble() }; if (!g.seen[g.idx(nx, ny)]) score += 0.25
            if (t == T.FOUNTAIN && h.hp < h.maxHp) score += 1.5; if (score > bestScore) { bestScore = score; best = a } }
        if (bestScore == Double.NEGATIVE_INFINITY) { val m = threats.minByOrNull { cheb(it.x, it.y, h.x, h.y) } ?: return Action.WAIT; return stepToward(g, m.x, m.y) }
        return best }
    private fun stepFromPath(g: Game, path: List<Pt>): Action { val p = path.first(); return actionFor(p.x - g.hero.x, p.y - g.hero.y) ?: Action.WAIT }
    private fun findSeenStairs(g: Game): Pt? { for (y in 0 until g.height) for (x in 0 until g.width) { val i = g.idx(x, y); if (g.map[i] == T.STAIRS && g.seen[i]) return Pt(x, y) }; return null }
}
