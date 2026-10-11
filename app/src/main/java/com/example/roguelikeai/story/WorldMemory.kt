package com.example.roguelikeai.story
import android.content.Context; import android.content.SharedPreferences; import com.example.roguelikeai.game.*
data class HeroRecord(val name: String, val epithet: String, val fate: String, val depth: Int, val won: Boolean)
object WorldMemory {
    private const val PREF = "roguelike_world_mem"; private var p: SharedPreferences? = null
    fun init(ctx: Context) { p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE) }; private fun prefs() = p!!
    fun worldSeed(): Long { val v = prefs().getLong("seed", 0L); return if (v != 0L) v else { val s = System.currentTimeMillis(); prefs().edit().putLong("seed", s).apply(); s } }
    fun heroes(): List<HeroRecord> { val raw = prefs().getString("heroes", "") ?: return emptyList(); if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { rec -> val f = rec.split("~"); if (f.size >= 5) HeroRecord(f[0], f[1], f[2], f[3].toIntOrNull() ?: 0, f[4] == "1") else null } }
    fun addHero(r: HeroRecord) { val cur = prefs().getString("heroes", "")!!; val entry = "${r.name}~${r.epithet}~${r.fate}~${r.depth}~${if (r.won) "1" else "0"}"; val list = if (cur.isBlank()) entry else "$cur|$entry"
        val trimmed = list.split("|").takeLast(20).joinToString("|"); prefs().edit().putString("heroes", trimmed).apply() }
    fun champions(): List<ChampionInfo> { val raw = prefs().getString("champions", "") ?: return emptyList(); if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { s -> val f = s.split("~"); if (f.size >= 7) ChampionInfo(f[0], f[1], f[2], f[3], f[4] == "1", f[5].toIntOrNull() ?: 1, f[6].toIntOrNull() ?: 0) else null } }
    fun saveChampions(list: List<ChampionInfo>) { val s = list.joinToString("|") { "${it.name}~${it.title}~${it.backstory}~${it.defName}~${if (it.alive) "1" else "0"}~${it.grudge}~${it.floorsSurvived}" }; prefs().edit().putString("champions", s).apply() }
    fun relics(): List<Relic> { val raw = prefs().getString("relics", "") ?: return emptyList(); if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { s -> val f = s.split("~"); if (f.size >= 2) { val perk = runCatching { RelicPerk.valueOf(f[0]) }.getOrNull() ?: return@mapNotNull null; Relic(f[1], perk.emoji, perk) } else null } }
    fun saveRelics(list: List<Relic>) { val s = list.joinToString("|") { "${it.perk.name}~${it.name}" }; prefs().edit().putString("relics", s).apply() }
    fun reset() { prefs().edit().clear().apply() }
}
