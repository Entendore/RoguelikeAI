package com.example.roguelikeai
import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.view.MotionEvent; import android.view.View; import android.widget.*
import androidx.appcompat.app.AppCompatActivity; import com.example.roguelikeai.ai.*; import com.example.roguelikeai.game.*; import com.example.roguelikeai.story.*; import com.example.roguelikeai.ui.GameView; import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var game: Game; private lateinit var gameView: GameView
    private lateinit var tvHp: TextView; private lateinit var tvDepth: TextView; private lateinit var tvHero: TextView; private lateinit var tvRuns: TextView; private lateinit var tvLog: TextView; private lateinit var scrollLog: ScrollView
    private lateinit var btnPlay: Button; private lateinit var btnManual: Button; private lateinit var btnMap: Button; private lateinit var tvSpeed: TextView
    private val agents: List<Agent> by lazy { listOf(RuleAgent(), UtilityAgent(), LookaheadAgent(), QLAgent(applicationContext), MctsAgent(60), NeuralAgent(), LLMAgent()) }
    private lateinit var agent: Agent
    private val handler = Handler(Looper.getMainLooper()); private var running = true; private var manual = false; private var betweenRuns = false; private var speed = 8
    private var episodes = 0; private var deaths = 0; private var totalKills = 0; private var bestDepth = 1; private val logLines = ArrayDeque<String>()
    private lateinit var world: WorldLore; private lateinit var heroStory: HeroStory; private lateinit var chronicler: Chronicler

    private val tick = object : Runnable { override fun run() { if (running && !manual && !betweenRuns) aiTurn(); if (running && !manual) handler.postDelayed(this, 1000L / speed) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        WorldMemory.init(this); game = Game(); game.adoptChampions(WorldMemory.champions()); game.adoptRelics(WorldMemory.relics()); game.newRun()
        agent = agents[0]; (agents[5] as NeuralAgent).distillFrom(agents[3] as QLAgent)
        gameView = findViewById(R.id.gameView); tvHp = findViewById(R.id.tvHp); tvDepth = findViewById(R.id.tvDepth); tvHero = findViewById(R.id.tvHero)
        tvRuns = findViewById(R.id.tvRuns); tvLog = findViewById(R.id.tvLog); scrollLog = findViewById(R.id.scrollLog)
        btnPlay = findViewById(R.id.btnPlay); btnManual = findViewById(R.id.btnManual); btnMap = findViewById(R.id.btnMap); tvSpeed = findViewById(R.id.tvSpeed)
        val btnNew = findViewById<Button>(R.id.btnNew); val speedBar = findViewById<SeekBar>(R.id.speedBar); val spinAgent = findViewById<Spinner>(R.id.spinAgent)
        gameView.game = game; world = LoreGenerator.world(WorldMemory.worldSeed()); startNewSaga()
        findViewById<Button>(R.id.btnLore).setOnClickListener { showLoreDialog() }
        findViewById<Button>(R.id.btnRivals).setOnClickListener { showRivalBoard() }
        findViewById<Button>(R.id.btnEndless).setOnClickListener { game.endless = !game.endless; (it as Button).text = if (game.endless) "♾️ ON" else "♾️ Endless"; log(if (game.endless) "♾️ Endless mode enabled!" else "♾️ Endless mode disabled.") }
        btnPlay.text = "⏸"; btnPlay.setOnClickListener { running = !running; btnPlay.text = if (running) "⏸" else "▶"; if (running) restartLoop() else handler.removeCallbacks(tick) }
        btnNew.setOnClickListener { if (betweenRuns) return@setOnClickListener; game.newRun(); agent.onNewRun(game); gameView.pathOverlay = emptyList(); startNewSaga(); log("↻ Fresh dungeon generated."); updateHud() }
        btnManual.setOnClickListener { manual = !manual; gameView.manual = manual; btnManual.text = if (manual) "🤖 AI plays" else "🎮 Take over"
            if (manual) log("🎮 You're in control — tap a tile.") else { log("🤖 ${agent.name} takes back control."); if (running) restartLoop() }; gameView.invalidate() }
        btnMap.setOnClickListener { gameView.revealAll = !gameView.revealAll; btnMap.text = if (gameView.revealAll) "🌫 Fog" else "🗺 Reveal"; gameView.invalidate() }
        speedBar.max = 29; speedBar.progress = speed - 1; tvSpeed.text = "$speed t/s"
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { speed = progress + 1; tvSpeed.text = "$speed t/s" }; override fun onStartTrackingTouch(sb: SeekBar?) {}; override fun onStopTrackingTouch(sb: SeekBar?) {} })
        spinAgent.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, agents.map { it.name })
        spinAgent.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { agent = agents[position]; log("🤖 Agent: ${agent.name}"); updateHud() }; override fun onNothingSelected(parent: AdapterView<*>?) {} }
        gameView.setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_UP && manual && !betweenRuns) { val t = gameView.tileAtPx(ev.x, ev.y)
            if (t != null) { val h = game.hero; val dx = (t.x - h.x).coerceIn(-1, 1); val dy = (t.y - h.y).coerceIn(-1, 1)
                val a = if (dx == 0 && dy == 0) Action.WAIT else actionFor(dx, dy); if (a != null) performAction(a, "🧑 ${if (a == Action.WAIT) "⏳ waits" else "${a.emoji} moves"}") } }; true }
        log("🎮 Welcome to Roguelike AI!"); log("🎯 Goal: reach depth ${Game.AMULET_DEPTH} and grab the Amulet 💎."); log("🟢wild 🟠horde 🟣undead 🔴infernal — factions war!"); updateHud() }

    private fun startNewSaga() { heroStory = LoreGenerator.hero(System.nanoTime()); chronicler = Chronicler(world, heroStory); chronicler.begin(); chronicler.referencePast(WorldMemory.heroes())
        log(world.title()); log("🧑 " + heroStory.name + ", " + heroStory.epithet + ", enters the dark…"); updateHud() }
    private fun showLoreDialog() { val tv = TextView(this).apply { setPadding(40, 28, 40, 12); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
        val sb = StringBuilder(); sb.append(world.render()).append("\n").append(heroStory.render()).append("\n\n").append("── The Saga So Far ──\n").append(chronicler.saga.joinToString("\n")).append("\n\n").append("── Bestiary ──\n").append(chronicler.bestiaryText())
        sb.append("\n── Rivals ──\n"); val al = game.aliveChampions(); if (al.isEmpty()) sb.append("No rivals have risen yet.\n") else al.forEach { sb.append(game.describeRival(it)).append("\n") }
        sb.append("\n── Relics ──\n"); if (game.collectedRelics.isEmpty()) sb.append("No relics claimed yet.\n") else game.collectedRelics.forEach { sb.append("${it.emoji} ${it.name} — ${it.perk.desc}\n") }
        tv.text = sb.toString(); val sv = ScrollView(this).apply { addView(tv) }; androidx.appcompat.app.AlertDialog.Builder(this).setTitle("📖 Legend of " + world.worldName).setView(sv).setPositiveButton("Close", null).show() }
    private fun showRivalBoard() { val alive = game.aliveChampions(); if (alive.isEmpty()) { log("🎯 No rivals have risen yet."); return }
        val items = alive.map { game.describeRival(it) }.toTypedArray(); androidx.appcompat.app.AlertDialog.Builder(this).setTitle("🎯 Rival Champions — tap one to challenge")
            .setItems(items) { _, which -> val ch = alive[which]; game.challengeRival(ch.name); log("🎯 You challenge ${ch.name}, ${ch.title}!") }.setNegativeButton("Close", null).show() }
    private fun restartLoop() { handler.removeCallbacks(tick); handler.post(tick) }
    override fun onResume() { super.onResume(); if (running && !manual) restartLoop() }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick); (agent as? QLAgent)?.save() }
    private fun aiTurn() { val (action, reason) = agent.decide(game); performAction(action, "🧠 $reason") }

    private fun performAction(action: Action, reasonLine: String?) {
        val r = game.step(action); agent.onTurnResult(game, r); if (reasonLine != null) log(reasonLine); for (l in r.log) log("   $l"); totalKills += r.kills; gameView.pathOverlay = agent.plannedPath
        for (n in r.slain) chronicler.onKill(n, game.depth); if (r.bossSlain) chronicler.onBoss(game.depth); if (r.descended) chronicler.onDescend(game.depth); if (r.pickedAmulet) chronicler.onWin(game.depth)
        for (a in r.championAppearedNames) { val parts = a.split("|"); if (parts.size == 2) chronicler.onChampionAppears(parts[0], parts[1]) }; for (n in r.championSlainNames) chronicler.onChampionSlain(n)
        r.relicPicked?.let { chronicler.onRelic(it); WorldMemory.saveRelics(game.exportRelics()) }
        for (line in chronicler.drain()) log("📜 $line"); updateHud(); if (r.won) endEpisode(true) else if (r.died) endEpisode(false) }

    private fun endEpisode(won: Boolean) { episodes++; if (!won) deaths++; bestDepth = maxOf(bestDepth, game.depth)
        if (won) chronicler.onWin(game.depth) else chronicler.onDeath(game.depth, game.steps)
        val fate = if (won) "claimed the ${world.amuletName}" else "fell at depth ${game.depth}"; WorldMemory.addHero(HeroRecord(heroStory.name, heroStory.epithet, fate, game.depth, won))
        WorldMemory.saveChampions(game.exportChampions()); WorldMemory.saveRelics(game.exportRelics())
        val summary = if (won) "🏆🎉 RUN $episodes: Amulet 💎 recovered on depth ${game.depth} in ${game.steps} turns!" else "💀 RUN $episodes: died on depth ${game.depth} after ${game.steps} turns. ⚰️"
        log(summary); log("📜 " + chronicler.epitaph(won)); agent.onEpisodeEnd(game, summary); Director.onEpisodeEnd(game, !won); updateHud(); betweenRuns = true
        handler.postDelayed({ game.newRun(); agent.onNewRun(game); gameView.pathOverlay = emptyList(); betweenRuns = false; startNewSaga(); log(if (manual) "New dungeon ready." else "🤖 ${agent.name} starts run ${episodes + 1}."); updateHud() }, 1600) }

    private fun updateHud() { val h = game.hero; val frac = (h.hp.toFloat() / h.maxHp).coerceIn(0f, 1f); val full = (frac * 10).roundToInt()
        tvHp.text = "❤️ ${h.hp}/${h.maxHp} " + "▰".repeat(full) + "▱".repeat(10 - full)
        tvHp.setTextColor(when { frac > 0.5f -> 0xFF66BB6A.toInt(); frac > 0.25f -> 0xFFFFA726.toInt(); else -> 0xFFEF5350.toInt() })
        val poison = if (game.poisonTurns > 0) " · 🤢${game.poisonTurns}" else ""; tvDepth.text = "🕳️ Depth ${game.depth}/${Game.AMULET_DEPTH} · 🗺 ${(game.exploredFraction() * 100).toInt()}% · ⏱ ${game.steps}$poison"
        tvHero.text = "${heroStory.name} · ⭐Lv${h.level} · 🗡${h.totalAtk()} · 🛡${h.totalDef()} · 💰${h.gold} · 🧪${h.potions}"
        val ql = agent as? QLAgent; tvRuns.text = buildString { append("🏃 Run ${episodes + 1} · 🏆 D$bestDepth · 💀$deaths · ⚔️$totalKills kills · ${Director.describe(game)}")
            if (ql != null) append(" · 🎲ε${"%.0f".format(ql.epsilon * 100)}% · 📈R ${"%.0f".format(ql.lastReward)}") } }
    private fun log(s: String) { logLines.addLast(s); while (logLines.size > 60) logLines.removeFirst(); tvLog.text = logLines.joinToString("\n"); scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) } }
}
