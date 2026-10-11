package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
import java.util.Locale; import kotlin.random.Random
class UtilityAgent : Agent {
    override val name = "Aria — utility-based"
    override var plannedPath: List<Pt> = emptyList()
    var personality: Personality = Personality.NEUTRAL
    private val rng = Random(7)
    override fun decide(g: Game): Pair<Action, String> {
        plannedPath = emptyList(); val h = g.hero; val hpFrac = h.hp.toFloat() / h.maxHp
        val visible = g.monsters.filter { it.alive && g.visible[g.idx(it.x, it.y)] }
        val exploreNeed = (1.0 - g.exploredFraction()).coerceAtLeast(0.12)
        var bestA = Action.WAIT; var bestU = -1e9; var bestWhy = "rest"
        fun consider(a: Action, u: Double, why: String) { val noisy = u + rng.nextDouble(-0.3, 0.3); if (noisy > bestU) { bestU = noisy; bestA = a; bestWhy = why } }
        var potionU = (1.0 - hpFrac) * 9.0 - 1.5; if (g.poisonTurns > 0) potionU += 3.5
        if (h.potions > 0) consider(Action.POTION, potionU, "🧪 heal/cure")
        if (g.tileAt(h.x, h.y) == T.STAIRS) { val ready = g.exploredFraction() > 0.55f || hpFrac > 0.85f; consider(Action.DESCEND, if (ready) 9.0 else 0.2, "🕳️ descend") }
        for (a in Action.values()) { if (!a.isMove) continue; val nx = h.x + a.dx; val ny = h.y + a.dy; if (!g.canPass(nx, ny)) continue; val t = g.tileAt(nx, ny)
            if (t == T.DOOR_CLOSED) { consider(a, 1.6, "🚪 open"); continue }
            var u = 0.0; val why = mutableListOf<String>()
            val m = g.monsterAt(nx, ny); if (m != null) { val kill = m.hp <= h.totalAtk(); u += (if (kill) 7.0 else 3.2) * personality.combatW; u -= m.def.atk * 0.35
                if (hpFrac < 0.4) u -= 4.0; when (m.def.behavior) { MonsterBehavior.SUMMONER -> { u += 3.0; why += "⚰️ silence" }; MonsterBehavior.RANGED -> { u += 2.0; why += "🏹 close" }; MonsterBehavior.EXPLOSIVE -> { u -= 8.0; why += "💣 bomber" }; else -> {} }
                why += "⚔️ fight ${m.def.emoji}" }
            val npc = g.npcAt(nx, ny); if (npc != null) { when (npc.kind) {
                NpcKind.MERCHANT -> if (h.gold >= 25 && h.potions < 3) { u += 4.5 * personality.lootW; why += "🛒 buy" }
                NpcKind.SAGE -> if (!npc.used) { u += 6.0; why += "🔮 sage" } else u += 0.2
                NpcKind.VILLAGER -> if (!npc.greeted) { u += 3.0; why += "👦 rescue" }
                NpcKind.HEALER -> if (h.hp < h.maxHp) { u += 2.5; why += "💉 heal" }
                NpcKind.COOK -> if (!npc.used && h.hp < h.maxHp) { u += 5.0; why += "🍖 stew" }
                NpcKind.BLACKSMITH -> if (npc.forgeLevel < 2 && h.gold >= 40) { u += 5.5; why += "⚒️ upgrade" }
                NpcKind.GAMBLER -> if (npc.rounds < 3 && h.gold >= 20) { u += 3.0; why += "🎰 gamble" }
                NpcKind.BARD -> { u += 1.0; why += "🎻 music" } } }
            var threat = 0.0; for (mm in visible) { if (mm === m) continue; val d = cheb(mm.x, mm.y, nx, ny).coerceAtLeast(1); var tt = mm.def.atk.toDouble() / d
                if (mm.def.behavior == MonsterBehavior.RANGED || mm.def.behavior == MonsterBehavior.SUMMONER) tt *= 1.5; if (mm.def.behavior == MonsterBehavior.EXPLOSIVE && d <= 1) tt += 9.0; threat += tt }
            u -= threat * (0.9 + (1.0 - hpFrac)) * personality.combatW
            val it = g.itemAt(nx, ny); if (it != null) { u += (when (it.kind) { ItemKind.POTION -> 3.0 + (1.0 - hpFrac) * 3.0; ItemKind.GOLD -> 1.2; ItemKind.WEAPON, ItemKind.ARMOR -> 2.8; ItemKind.AMULET -> 30.0; ItemKind.RELIC -> 15.0; else -> 1.0 }) * personality.lootW; why += "🎁 grab ${it.kind.emoji}" }
            when (t) { T.LAVA -> { u -= 5.0; why += "🌋 lava" }; T.WATER -> u += 0.2; T.FOUNTAIN -> { u += if (hpFrac < 0.9) 6.0 else 1.0; why += "⛲ drink" }; else -> {} }
            var unseenN = 0; for (d in Game.DIRS8) { val ax = nx + d.x; val ay = ny + d.y; if (g.inBounds(ax, ay) && g.canPass(ax, ay) && !g.seen[g.idx(ax, ay)]) unseenN++ }
            if (!g.seen[g.idx(nx, ny)]) unseenN += 2; if (unseenN > 0) { u += (0.4 + unseenN * 1.1 * exploreNeed) * personality.exploreW; why += "🧭 explore" }
            if (t == T.STAIRS) { val ready = g.exploredFraction() > 0.55f || hpFrac > 0.85f; u += if (ready) 6.0 else 0.3; why += "🕳️ stairs" }
            consider(a, u, if (why.isEmpty()) "🚶 reposition" else why.joinToString(" + ")) }
        consider(Action.WAIT, 0.1, "⏳ wait"); return bestA to "🧮 score ${String.format(Locale.US, "%.1f", bestU)} → $bestWhy" }
}
