package com.example.roguelikeai.ai
import android.content.Context; import com.example.roguelikeai.game.*; import java.util.Locale; import kotlin.random.Random
class QLAgent(ctx: Context) : Agent {
    override val name = "Q-Bert — Q-learning"
    override var plannedPath: List<Pt> = emptyList()
    private val actions = arrayOf(Action.N, Action.E, Action.S, Action.W, Action.NE, Action.SE, Action.SW, Action.NW, Action.WAIT, Action.POTION, Action.DESCEND, Action.THROW)
    val table = HashMap<Long, DoubleArray>()
    val statesLearned get() = table.size
    private val alpha = 0.25; private val gamma = 0.92; var epsilon = 0.35; var episodes = 0; private set; var lastReward = 0.0; private set
    private var episodeReward = 0.0; private var lastState: Long? = null; private var lastAction = -1
    private val rng = Random.Default; private val prefs = ctx.getSharedPreferences("roguelike_ai_ql", Context.MODE_PRIVATE)
    init { load() }
    override fun decide(g: Game): Pair<Action, String> { plannedPath = emptyList(); val s = stateKey(g); lastState = s
        val qs = table.getOrPut(s) { DoubleArray(actions.size) }; val exploring = rng.nextDouble() < epsilon
        val idx = if (exploring) randomLegal(g) else argmax(qs); lastAction = idx; val a = actions[idx]
        return a to "${if (exploring) "🎲 explores (ε=${pct(epsilon)})" else "📈 exploits Q=${f1(qs[idx])}"} → ${a.emoji} $a" }
    private fun randomLegal(g: Game): Int { val legal = mutableListOf<Int>()
        for (i in actions.indices) { when (val a = actions[i]) {
            Action.POTION -> if (g.hero.potions > 0) legal += i; Action.DESCEND -> if (g.tileAt(g.hero.x, g.hero.y) == T.STAIRS) legal += i
            Action.THROW -> if (g.hero.knives > 0) legal += i; Action.WAIT -> legal += i
            else -> { val nx = g.hero.x + a.dx; val ny = g.hero.y + a.dy; if (g.walkable(nx, ny)) legal += i } } }
        return if (legal.isEmpty()) rng.nextInt(actions.size) else legal[rng.nextInt(legal.size)] }
    private fun argmax(qs: DoubleArray): Int { var bi = 0; for (i in 1 until qs.size) if (qs[i] > qs[bi]) bi = i; return bi }
    override fun onTurnResult(g: Game, r: TurnReport) { val reward = rewardOf(r); episodeReward += reward; val s = lastState ?: return; val a = lastAction; if (a < 0) return
        val qs = table.getOrPut(s) { DoubleArray(actions.size) }; val terminal = r.died || r.won || r.descended
        val target = if (terminal) reward else reward + gamma * (table[stateKey(g)]?.maxOrNull() ?: 0.0); qs[a] += alpha * (target - qs[a]) }
    override fun onEpisodeEnd(g: Game, summary: String) { episodes++; lastReward = episodeReward; episodeReward = 0.0; epsilon = maxOf(0.05, epsilon * 0.96); save() }
    private fun rewardOf(r: TurnReport): Double { var v = -0.02 + r.damageDealt * 1.0 - r.damageTaken * 1.5 + r.kills * 5.0 + r.factionCasualties * 3.0 + r.itemsPicked * 2.5 + r.newSeenTiles * 0.05
        if (r.levelUps > 0) v += 6.0; if (r.descended) v += 10.0; if (r.won) v += 60.0; if (r.died) v -= 20.0; return v }
    private fun stateKey(g: Game): Long { val h = g.hero; val hpB = when { h.hp * 4 <= h.maxHp -> 0; h.hp * 2 <= h.maxHp -> 1; h.hp * 4 <= h.maxHp * 3 -> 2; else -> 3 }
        val vis = g.monsters.filter { it.alive && g.visible[g.idx(it.x, it.y)] }; val eCnt = minOf(3, vis.size)
        val eDist = vis.minOfOrNull { cheb(it.x, it.y, h.x, h.y) }?.let { minOf(4, it - 1) } ?: 4; val pot = minOf(2, h.potions)
        val stairs = if (g.tileAt(h.x, h.y) == T.STAIRS) 1 else 0; var unseenN = 0
        for (d in Game.DIRS8) { val nx = h.x + d.x; val ny = h.y + d.y; if (g.inBounds(nx, ny) && g.walkable(nx, ny) && !g.seen[g.idx(nx, ny)]) unseenN++ }
        unseenN = minOf(3, unseenN); val itemVis = if (g.items.any { g.visible[g.idx(it.x, it.y)] }) 1 else 0; val poisoned = if (g.poisonTurns > 0) 1 else 0
        var k = hpB.toLong(); k = k * 4 + eCnt; k = k * 5 + eDist; k = k * 3 + pot; k = k * 2 + stairs; k = k * 4 + unseenN; k = k * 2 + itemVis; k = k * 2 + poisoned; return k }
    fun save() { try { val sb = StringBuilder(); sb.append(episodes).append(';').append(String.format(Locale.US, "%.4f", epsilon)).append('\n')
        var count = 0; for ((k, v) in table) { if (count++ >= 40000) break; sb.append(k).append('='); for (i in v.indices) { if (i > 0) sb.append(','); sb.append(String.format(Locale.US, "%.2f", v[i])) }; sb.append('\n') }
        prefs.edit().putString("qtable", sb.toString()).apply() } catch (_: Exception) {} }
    fun load() { try { val data = prefs.getString("qtable", null) ?: return; val lines = data.lines(); if (lines.isEmpty()) return
        val head = lines[0].split(';'); episodes = head.getOrNull(0)?.toIntOrNull() ?: 0; epsilon = head.getOrNull(1)?.toDoubleOrNull() ?: 0.35; table.clear()
        for (i in 1 until lines.size) { val line = lines[i]; val eq = line.indexOf('='); if (eq <= 0) continue; val k = line.substring(0, eq).toLongOrNull() ?: continue
            val vals = line.substring(eq + 1).split(',').mapNotNull { it.toDoubleOrNull() }; if (vals.size == actions.size) table[k] = vals.toDoubleArray() } } catch (_: Exception) {} }
    private fun pct(v: Double) = "${(v * 100).toInt()}%"; private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
}
