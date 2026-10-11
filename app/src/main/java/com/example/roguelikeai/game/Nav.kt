package com.example.roguelikeai.game
object Nav {
    fun pathBfs(g: Game, tx: Int, ty: Int): List<Pt>? { val w = g.width; val start = g.idx(g.hero.x, g.hero.y); val goal = g.idx(tx, ty); if (start == goal) return emptyList()
        val prev = IntArray(g.width * g.height) { -1 }; val q = ArrayDeque<Int>(); prev[start] = start; q.addLast(start)
        while (q.isNotEmpty()) { val cur = q.removeFirst(); val cx = cur % w; val cy = cur / w
            for (d in Game.DIRS8) { val nx = cx + d.x; val ny = cy + d.y; if (!g.inBounds(nx, ny) || !g.canPass(nx, ny)) continue; val ni = g.idx(nx, ny)
                if (ni == goal) { prev[ni] = cur; return buildPath(g, prev, ni) }; if (prev[ni] != -1 || !g.seen[ni]) continue; prev[ni] = cur; q.addLast(ni) } }; return null }
    fun nearestUnseen(g: Game): List<Pt>? { val w = g.width; val start = g.idx(g.hero.x, g.hero.y); val prev = IntArray(g.width * g.height) { -1 }; val q = ArrayDeque<Int>(); prev[start] = start; q.addLast(start)
        while (q.isNotEmpty()) { val cur = q.removeFirst(); val cx = cur % w; val cy = cur / w
            for (d in Game.DIRS8) { val nx = cx + d.x; val ny = cy + d.y; if (!g.inBounds(nx, ny) || !g.canPass(nx, ny)) continue; val ni = g.idx(nx, ny)
                if (!g.seen[ni]) { prev[ni] = cur; return buildPath(g, prev, ni) }; if (prev[ni] != -1) continue; prev[ni] = cur; q.addLast(ni) } }; return null }
    fun nearestVisibleItemPath(g: Game): List<Pt>? { var best: List<Pt>? = null; for (it in g.items) { if (!g.visible[g.idx(it.x, it.y)]) continue; val p = pathBfs(g, it.x, it.y) ?: continue; if (best == null || p.size < best.size) best = p }; return best }
    private fun buildPath(g: Game, prev: IntArray, end: Int): List<Pt> { val out = mutableListOf<Pt>(); var cur = end; val start = g.idx(g.hero.x, g.hero.y); var guard = 0
        while (cur != start && guard++ < 10000) { out += Pt(cur % g.width, cur / g.width); cur = prev[cur]; if (cur == -1) return emptyList() }; out.reverse(); return out }
}
