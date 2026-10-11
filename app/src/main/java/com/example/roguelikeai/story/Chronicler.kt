package com.example.roguelikeai.story
import kotlin.random.Random
class Chronicler(val world: WorldLore, val hero: HeroStory) {
    private val rng = Random.Default; val saga = mutableListOf<String>(); private val pending = mutableListOf<String>(); private val bestiary = LinkedHashMap<String, Int>()
    private fun add(line: String) { saga += line; pending += line }; fun drain(): List<String> { val out = pending.toList(); pending.clear(); return out }
    fun begin() { add("${hero.name} descended into ${world.dungeonName}, ${hero.motive}."); add("They carried one ${if (rng.nextBoolean()) "lantern" else "oath"} and a flaw: ${hero.flaw}.") }
    fun referencePast(past: List<HeroRecord>) { if (past.isEmpty()) return; val last = past.last(); add("They walk in the shadow of ${last.name}, ${last.epithet}, who ${last.fate}."); if (past.count { it.won } > 0) add("The ${world.amuletName} has been claimed before.") }
    fun onDescend(depth: Int) { add(listOf("Deeper they went — depth $depth, where the air tastes of ${listOf("rust", "old prayers", "copper").random(rng)}.", "Depth $depth. The ${world.dungeonName} tightened around them.", "At depth $depth, ${hero.name} heard ${world.factionLegends.keys.random(rng).name.lowercase()} stirring.").random(rng)) }
    fun onKill(name: String, depth: Int) { bestiary[name] = (bestiary[name] ?: 0) + 1; if (rng.nextInt(100) < 40) add("Depth $depth: ${hero.name} felled a $name.") }
    fun onBoss(depth: Int) { add("⚔️ At depth $depth, the great foe was slain — a blow the ${world.worldName} will remember.") }
    fun onWin(depth: Int) { add("🏆 From depth $depth, ${hero.name} lifted the ${world.amuletName} — ${world.amuletPower}."); add("The prophecy turns: \"${world.prophecy}\"") }
    fun onDeath(depth: Int, steps: Int) { add("☠️ At depth $depth, after $steps turns, ${hero.name} fell — ${hero.epithet}, no more.") }
    fun onChampionAppears(name: String, title: String) { add("⚔️ A rival emerges: $name, $title. It will not rest.") }
    fun onChampionSlain(name: String) { add("🏆 The rival $name is vanquished — the ${world.dungeonName} grows quieter.") }
    fun onRelic(name: String) { add("🏺 ${hero.name} claimed the relic $name.") }
    fun epitaph(won: Boolean): String { val slainTotal = bestiary.values.sum(); return if (won) "Thus ends the legend of ${hero.name}, who conquered ${world.dungeonName} and claimed the ${world.amuletName}. $slainTotal foes remember." else "So fell ${hero.name}, ${hero.epithet}, $slainTotal foes to their name. The ${world.dungeonName} keeps its secret a little longer." }
    fun bestiaryText(): String { if (bestiary.isEmpty()) return "No foes recorded yet."; val sb = StringBuilder(); for ((n, c) in bestiary) sb.append("• $n x$c — ").append(LoreGenerator.monsterFlavor(n, rng)).append("\n"); return sb.toString() }
}
