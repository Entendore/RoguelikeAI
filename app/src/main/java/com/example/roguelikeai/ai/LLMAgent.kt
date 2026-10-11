package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*; import kotlin.concurrent.thread; import java.net.HttpURLConnection; import java.net.URL
class LLMAgent : Agent {
    override val name = "Oracle — LLM player"
    override var plannedPath: List<Pt> = emptyList()
    var apiKey = "YOUR_API_KEY"; var baseUrl = "https://api.openai.com/v1/chat/completions"; var model = "gpt-4o-mini"
    @Volatile private var pending: Action? = null; @Volatile private var narration: String = ""; @Volatile private var busy = false
    private fun snapshot(g: Game): String { val h = g.hero; val vis = g.monsters.filter { g.visible[g.idx(it.x, it.y)] }.joinToString { "${it.def.emoji}${it.def.name}@(${it.x},${it.y})" }
        return "Roguelike state. Reply exactly ONE action keyword and short reason. Allowed: N,S,E,W,NE,NW,SE,SW,WAIT,POTION,DESCEND,THROW,CAST_FIREBALL,CAST_BLINK,CAST_FEAR,CAST_REVEAL.\nHP ${h.hp}/${h.maxHp}, lvl ${h.level}, gold ${h.gold}, potions ${h.potions}, knives ${h.knives}, hunger ${h.hunger}.\nDepth ${g.depth}, explored ${(g.exploredFraction()*100).toInt()}%, poison ${g.poisonTurns}.\nVisible: $vis. Tile ${g.tileAt(h.x,h.y)} at (${h.x},${h.y})." }
    override fun decide(g: Game): Pair<Action, String> { pending?.let { a -> pending = null; return a to "🤖LLM: $narration" }
        if (!busy) { busy = true; val s = snapshot(g); thread { try { val c = URL(baseUrl).openConnection() as HttpURLConnection; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $apiKey"); c.setRequestProperty("Content-Type", "application/json")
            val body = """{"model":"$model","messages":[{"role":"user","content":${'"'}$s${'"'}}],"max_tokens":40}"""; c.outputStream.use { it.write(body.toByteArray()) }
            val resp = c.inputStream.bufferedReader().readText(); val msg = Regex("\"content\"\\s*:\\s*\"([^\"]*)\"").find(resp)?.groupValues?.get(1) ?: ""; narration = msg; pending = parse(msg)
        } catch (e: Exception) { narration = "error ${e.message}" }; busy = false } }; return Action.WAIT to "🤖LLM thinking…" }
    private fun parse(s: String): Action? { val u = s.uppercase(); return when { "CAST_FIREBALL" in u -> Action.CAST_FIREBALL; "CAST_BLINK" in u -> Action.CAST_BLINK; "CAST_FEAR" in u -> Action.CAST_FEAR; "CAST_REVEAL" in u -> Action.CAST_REVEAL
        "THROW" in u -> Action.THROW; "POTION" in u -> Action.POTION; "DESCEND" in u -> Action.DESCEND; "WAIT" in u -> Action.WAIT
        "NE" in u -> Action.NE; "NW" in u -> Action.NW; "SE" in u -> Action.SE; "SW" in u -> Action.SW; "N" in u -> Action.N; "S" in u -> Action.S; "E" in u -> Action.E; "W" in u -> Action.W; else -> null } }
}
