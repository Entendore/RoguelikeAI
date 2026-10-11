package com.example.roguelikeai.ai
import com.example.roguelikeai.game.*
interface Agent {
    val name: String; var plannedPath: List<Pt>
    fun onNewRun(g: Game) {}
    fun decide(g: Game): Pair<Action, String>
    fun onTurnResult(g: Game, r: TurnReport) {}
    fun onEpisodeEnd(g: Game, summary: String) {}
}
