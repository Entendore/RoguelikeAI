package com.example.roguelikeai.game
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class Game(val width:Int=40, val height:Int=28) {
    companion object { const val FOV_RADIUS=8; const val AMULET_DEPTH=8
        val DIRS8=listOf(Pt(0,-1),Pt(1,0),Pt(0,1),Pt(-1,0),Pt(1,-1),Pt(1,1),Pt(-1,1),Pt(-1,-1)) }
    private var rng=Random.Default
    var depth=1; private set; var steps=0; private set; var poisonTurns=0; private set
    var ascension=0; var endless=false; var directorTension=1.0
    var map=IntArray(width*height); private set
    var seen=BooleanArray(width*height); private set
    var visible=BooleanArray(width*height); private set
    val traps=HashMap<Int,Trap>(); val hero=Hero()
    val monsters=mutableListOf<Monster>(); val items=mutableListOf<Item>(); val npcs=mutableListOf<Npc>()
    val champions=mutableListOf<Champion>(); private val championMonsters=mutableMapOf<Champion,Monster>()
    private val pendingChampionMsgs=mutableListOf<String>(); private val pendingChampionAppeared=mutableListOf<String>()
    val collectedRelics=mutableListOf<Relic>()
    private var stairsX=0; private var stairsY=0; private var exploredCount=0; private var totalWalkable=1

    fun idx(x:Int,y:Int)=y*width+x
    fun inBounds(x:Int,y:Int)=x in 0 until width && y in 0 until height
    fun tileAt(x:Int,y:Int)=if(inBounds(x,y)) map[idx(x,y)] else T.WALL
    fun walkable(x:Int,y:Int):Boolean{ if(!inBounds(x,y))return false
        return when(map[idx(x,y)]){ T.WALL,T.PILLAR,T.RUBBLE,T.DOOR_CLOSED->false; else->true } }
    fun canPass(x:Int,y:Int)=walkable(x,y)||tileAt(x,y)==T.DOOR_CLOSED
    fun monsterAt(x:Int,y:Int)=monsters.firstOrNull{it.alive&&it.x==x&&it.y==y}
    fun itemAt(x:Int,y:Int)=items.firstOrNull{it.x==x&&it.y==y}
    fun npcAt(x:Int,y:Int)=npcs.firstOrNull{it.x==x&&it.y==y}
    fun exploredFraction()=exploredCount.toFloat()/totalWalkable.coerceAtLeast(1)
    private fun explorable(t:Int)=t!=T.WALL&&t!=T.PILLAR&&t!=T.RUBBLE
    private fun lavaSafe(m:Monster)=m.def.lavaImmune||m.def.flier
    fun isBossFloor(d:Int)=d==4||d==AMULET_DEPTH
    fun hasRelic(p:RelicPerk)=collectedRelics.any{it.perk==p}
    private fun fovR()=FOV_RADIUS+(if(hasRelic(RelicPerk.SCOUT))2 else 0)

    fun newRun(){ hero.reset(); applyRelicPerks(); depth=1; steps=0; poisonTurns=0; ascension=0; newLevel() }
    fun applyRelicPerks(){ for(r in collectedRelics) applySinglePerk(r.perk) }
    private fun applySinglePerk(p:RelicPerk){ when(p){
        RelicPerk.VITALITY->{hero.maxHp+=6;hero.hp+=6}; RelicPerk.FURY->hero.atk+=2
        RelicPerk.IRON->hero.def+=1; RelicPerk.SWIFT->hero.knives+=2; else->{} } }

    private data class Room(val x:Int,val y:Int,val w:Int,val h:Int){
        val cx get()=x+w/2; val cy get()=y+h/2
        fun intersects(o:Room,pad:Int)=x-pad<o.x+o.w&&x+w+pad>o.x&&y-pad<o.y+o.h&&y+h+pad>o.y }

    private fun newLevel(){
        rng=Random.Default; monsters.clear(); items.clear(); npcs.clear(); traps.clear()
        map=IntArray(width*height); championMonsters.clear()
        if(isBossFloor(depth)) buildBossFloor() else buildNormalFloor()
        handleChampions()
        seen=BooleanArray(width*height); visible=BooleanArray(width*height)
        totalWalkable=map.count{explorable(it)}; exploredCount=computeFov() }

    private fun buildNormalFloor(){
        val rooms=genRooms(); for(i in 1 until rooms.size) connect(rooms[i-1],rooms[i])
        hero.x=rooms.first().cx; hero.y=rooms.first().cy
        stairsX=rooms.last().cx; stairsY=rooms.last().cy
        if(hero.x==stairsX&&hero.y==stairsY) hero.x+=1; map[idx(stairsX,stairsY)]=T.STAIRS
        placeDoors(); decorate(rooms); placeTraps(); spawnActors(false); spawnNpcs() }

    private fun buildBossFloor(){
        for(y in 3 until height-3) for(x in 3 until width-3) map[idx(x,y)]=T.FLOOR
        for(x in 6 until width-6 step 4) for(y in 6 until height-6 step 4){
            map[idx(x,y)]=T.PILLAR; map[idx(x+1,y)]=T.PILLAR; map[idx(x,y+1)]=T.PILLAR; map[idx(x+1,y+1)]=T.PILLAR }
        for(x in 8 until width-8){ map[idx(x,5)]=T.LAVA; map[idx(x,height-6)]=T.WATER }
        hero.x=5; hero.y=height/2; stairsX=width-6; stairsY=height/2; map[idx(stairsX,stairsY)]=T.STAIRS
        val boss=Bestiary.byName(if(depth==4)"wyvern" else "dragon")
        val bm=Monster(boss,width/2,height/2,boss.hp+ascension*10,boss.hp+ascension*10); bm.isBoss=true; bm.awake=true; monsters+=bm
        spawnActors(true) }

    private fun genRooms():List<Room>{
        val rooms=mutableListOf<Room>(); val target=7+rng.nextInt(0,4); var guard=0
        while(rooms.size<target&&guard++<400){
            val w=rng.nextInt(4,10); val h=rng.nextInt(3,8); if(w+2>=width||h+2>=height)continue
            val x=rng.nextInt(1,width-w-1); val y=rng.nextInt(1,height-h-1); val r=Room(x,y,w,h)
            if(rooms.any{it.intersects(r,1)})continue; rooms+=r
            val tile=if(rng.nextInt(100)<30)T.GRASS else T.FLOOR
            for(yy in y until y+h)for(xx in x until x+w)map[idx(xx,yy)]=tile }
        if(rooms.isEmpty()){ val r=Room(2,2,width-4,height-4); rooms+=r
            for(yy in r.y until r.y+r.h)for(xx in r.x until r.x+r.w)map[idx(xx,yy)]=T.FLOOR }; return rooms }

    private fun connect(a:Room,b:Room){ if(rng.nextBoolean()){carveH(a.cx,b.cx,a.cy);carveV(a.cy,b.cy,b.cx)}else{carveV(a.cy,b.cy,a.cx);carveH(a.cx,b.cx,b.cy)} }
    private fun carveH(x1:Int,x2:Int,y:Int){ for(x in minOf(x1,x2)..maxOf(x1,x2))map[idx(x,y)]=T.CORRIDOR }
    private fun carveV(y1:Int,y2:Int,x:Int){ for(y in minOf(y1,y2)..maxOf(y1,y2))map[idx(x,y)]=T.CORRIDOR }
    private fun placeDoors(){ for(y in 1 until height-1)for(x in 1 until width-1){ val i=idx(x,y); if(map[i]!=T.CORRIDOR)continue
        val l=map[idx(x-1,y)]; val r=map[idx(x+1,y)]; val u=map[idx(x,y-1)]; val d=map[idx(x,y+1)]
        if((isOpenT(l)&&isOpenT(r)&&u==T.WALL&&d==T.WALL)||(isOpenT(u)&&isOpenT(d)&&l==T.WALL&&r==T.WALL)){
            if(!DIRS8.any{tileAt(x+it.x,y+it.y)==T.DOOR_CLOSED}&&rng.nextInt(100)<60)map[i]=T.DOOR_CLOSED} } }
    private fun isOpenT(t:Int)=t==T.FLOOR||t==T.CORRIDOR||t==T.GRASS||t==T.DOOR_OPEN||t==T.STAIRS
    private fun decorate(rooms:List<Room>){ for(room in rooms){
        if(room.w>=6&&room.h>=5&&rng.nextInt(100)<60) for(px in room.x+2 until room.x+room.w-2 step 2) for(py in room.y+2 until room.y+room.h-2 step 2) if(rng.nextInt(100)<70)setIfFloor(px,py,T.PILLAR)
        if(room.w>=3&&room.h>=3) repeat(rng.nextInt(0,3)){setIfFloor(rng.nextInt(room.x+1,room.x+room.w-1),rng.nextInt(room.y+1,room.y+room.h-1),T.RUBBLE)}
        if(room.w>=5&&room.h>=4&&rng.nextInt(100)<45)blob(room,T.WATER,2+rng.nextInt(0,4))
        if(depth>=2&&room.w>=5&&room.h>=4&&rng.nextInt(100)<30)blob(room,T.LAVA,1+rng.nextInt(0,2)) }
        if(rooms.size>1&&rng.nextInt(100)<60){val r=rooms.random(rng);setIfFloor(r.x+rng.nextInt(r.w),r.y+rng.nextInt(r.h),T.FOUNTAIN)} }
    private fun blob(room:Room,tile:Int,size:Int){ var x=rng.nextInt(room.x+1,room.x+room.w-1); var y=rng.nextInt(room.y+1,room.y+room.h-1)
        repeat(size){setIfFloor(x,y,tile);x=(x+rng.nextInt(-1,2)).coerceIn(room.x+1,room.x+room.w-2);y=(y+rng.nextInt(-1,2)).coerceIn(room.y+1,room.y+room.h-2)} }
    private fun setIfFloor(x:Int,y:Int,t:Int):Boolean{ if(!inBounds(x,y)||x==hero.x&&y==hero.y||x==stairsX&&y==stairsY)return false
        val i=idx(x,y); if(map[i]!=T.FLOOR&&map[i]!=T.GRASS)return false; map[i]=t; return true }
    private fun placeTraps(){ repeat(2+depth/2){ val p=randomSpot(4){t->t==T.FLOOR||t==T.CORRIDOR||t==T.GRASS}?:return@repeat
        if(cheb(p.x,p.y,stairsX,stairsY)<=1)return@repeat; traps[idx(p.x,p.y)]=Trap(if(rng.nextBoolean())TrapType.SPIKE else TrapType.TELEPORT) } }
    private fun randomSpot(minDist:Int,ok:(Int)->Boolean):Pt?{ repeat(250){ val x=rng.nextInt(width); val y=rng.nextInt(height)
        if(!ok(tileAt(x,y))||cheb(x,y,hero.x,hero.y)<minDist||monsterAt(x,y)!=null||itemAt(x,y)!=null||npcAt(x,y)!=null||traps.containsKey(idx(x,y)))return@repeat
        return Pt(x,y) }; return null }

    private fun spawnActors(light:Boolean){ val count=((if(light)2 else 4)+depth+rng.nextInt(0,3))*directorTension.toInt().coerceAtLeast(1)
        repeat(count){ val def=Bestiary.pick(depth,rng)
            val p=if(def.aquatic)randomSpot(5){t->t==T.WATER}?:randomSpot(6){t->t==T.FLOOR||t==T.GRASS}
            else randomSpot(6){t->t==T.FLOOR||t==T.GRASS||t==T.CORRIDOR||t==T.WATER}?:return@repeat
            val m=Monster(def,p.x,p.y,def.hp+(depth-def.minDepth)*2+ascension*4,def.hp+(depth-def.minDepth)*2+ascension*4); applyElite(m); monsters+=m }
        repeat(2+rng.nextInt(0,3)){randomSpot(3){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.POTION,it.x,it.y)}}
        repeat(3+rng.nextInt(0,3)){randomSpot(2){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.GOLD,it.x,it.y,4+rng.nextInt(0,8+depth*3))}}
        if(rng.nextInt(100)<45)randomSpot(4){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.WEAPON,it.x,it.y,1)}
        if(rng.nextInt(100)<45)randomSpot(4){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.ARMOR,it.x,it.y,1)}
        repeat(1+rng.nextInt(0,2)){randomSpot(3){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.KNIFE,it.x,it.y,2)}}
        repeat(2+rng.nextInt(0,2)){randomSpot(3){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.FOOD,it.x,it.y,30)}}
        repeat(1+rng.nextInt(0,2)){randomSpot(4){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.SCROLL,it.x,it.y,0,ScrollType.values().random(rng))}}
        if(depth>=AMULET_DEPTH&&!isBossFloor(depth))randomSpot(8){t->t==T.FLOOR||t==T.GRASS}?.let{items+=Item(ItemKind.AMULET,it.x,it.y)} }
    private fun applyElite(m:Monster){ if(rng.nextInt(100)>=12||m.def.stationary)return; m.elite=Elite.values().random(rng)
        if(m.elite==Elite.ALPHA) repeat(2){val d=DIRS8.random(rng);val nx=m.x+d.x;val ny=m.y+d.y
            if(walkable(nx,ny)&&monsterAt(nx,ny)==null){val def=Bestiary.pick(depth,rng);monsters+=Monster(def,nx,ny,def.hp/2+1,def.hp/2+1)}}
        m.hp=(m.hp*1.25).toInt() }
    private fun spawnNpcs(){ val ok:(Int)->Boolean={t->t==T.FLOOR||t==T.GRASS}
        if(rng.nextInt(100)<50)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.MERCHANT,it.x,it.y)}
        if(rng.nextInt(100)<30)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.SAGE,it.x,it.y)}
        if(rng.nextInt(100)<35)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.VILLAGER,it.x,it.y)}
        if(rng.nextInt(100)<30)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.HEALER,it.x,it.y)}
        if(rng.nextInt(100)<25)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.COOK,it.x,it.y)}
        if(rng.nextInt(100)<22)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.BLACKSMITH,it.x,it.y)}
        if(rng.nextInt(100)<25)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.BARD,it.x,it.y)}
        if(rng.nextInt(100)<25)randomSpot(5,ok)?.let{npcs+=Npc(NpcKind.GAMBLER,it.x,it.y)} }

    fun handleChampions(){ if(isBossFloor(depth))return
        if(champions.count{it.alive}<3&&depth>=3&&rng.nextInt(100)<40) champions+=genChampion()
        var shown=0; for(ch in champions.filter{it.alive}.shuffled(rng)){ if(shown>=2)break
            if(ch.encounters==0||rng.nextInt(100)<55){ch.encounters++;placeChampion(ch,ch.encounters==1);shown++} } }
    private fun placeChampion(ch:Champion,first:Boolean){ val hp=ch.def.hp+depth*3+ascension*5+ch.grudge*4
        val spot=randomSpot(5){t->t==T.FLOOR||t==T.GRASS||t==T.CORRIDOR}?:Pt(width/2,height/2)
        val m=Monster(ch.def,spot.x,spot.y,hp,hp,true); m.isChampion=true; m.bonusAtk=ch.grudge; m.elite=Elite.values().random(rng)
        monsters+=m; championMonsters[ch]=m
        pendingChampionMsgs+=if(first)"⚔️ ${ch.name}, ${ch.title}, begins the hunt! (${ch.backstory})" else "⚔️ ${ch.name}, ${ch.title}, stalks this floor!"
        pendingChampionAppeared+= "${ch.name}|${ch.title}" }
    private fun genChampion():Champion{ val def=Bestiary.poolFor(depth).filter{!it.stationary}.random(rng)
        val ng=com.example.roguelikeai.story.NameGen(rng); val name=ng.name(2,3)
        val title=listOf("the Scarred","the Unforgiven","Oathbreaker","the Hollow King","the Last Hunt","Doombringer").random(rng)
        val back=listOf("it remembers the hero who wronged it","it collects the names of the fallen","it was promised your heart","it cannot die until it faces an equal").random(rng)
        return Champion(name,title,back,def,Relics.derive(name)) }
    fun challengeRival(name:String):Boolean{ val ch=champions.firstOrNull{it.alive&&it.name==name}?:return false
        if(championMonsters.containsKey(ch))return true; placeChampion(ch,false); return true }
    fun aliveChampions():List<Champion>=champions.filter{it.alive}
    fun describeRival(ch:Champion):String{ val here=if(championMonsters.containsKey(ch))"⚔️ HERE" else "🕳️ lurking"
        return "${ch.name}, ${ch.title} ${ch.def.faction.emoji}${ch.def.emoji} · grudge ${ch.grudge} · ${ch.floorsSurvived} floors · $here" }
    fun adoptChampions(list:List<ChampionInfo>){ champions.clear(); for(ci in list){
        val c=Champion(ci.name,ci.title,ci.backstory,Bestiary.byName(ci.defName),Relics.derive(ci.name)); c.alive=ci.alive; c.grudge=ci.grudge; c.floorsSurvived=ci.floorsSurvived; champions+=c } }
    fun exportChampions():List<ChampionInfo>=champions.map{ChampionInfo(it.name,it.title,it.backstory,it.def.name,it.alive,it.grudge,it.floorsSurvived)}
    fun adoptRelics(list:List<Relic>){ collectedRelics.clear(); collectedRelics.addAll(list) }
    fun exportRelics():List<Relic>=collectedRelics.toList()

    fun legalActions():List<Action>{ val out=mutableListOf(Action.WAIT)
        if(hero.potions>0)out+=Action.POTION; if(hero.knives>0&&monsters.any{it.alive&&visible[idx(it.x,it.y)]})out+=Action.THROW
        for((s,c) in hero.scrolls) if(c>0) out+=when(s){ScrollType.FIREBALL->Action.CAST_FIREBALL;ScrollType.BLINK->Action.CAST_BLINK;ScrollType.FEAR->Action.CAST_FEAR;ScrollType.REVEAL->Action.CAST_REVEAL}
        if(tileAt(hero.x,hero.y)==T.STAIRS)out+=Action.DESCEND
        for(a in Action.values()) if(a.isMove&&canPass(hero.x+a.dx,hero.y+a.dy))out+=a; return out.distinct() }

    fun step(a:Action):TurnReport{ val r=TurnReport(); steps++
        if(pendingChampionMsgs.isNotEmpty()){r.log+=pendingChampionMsgs;r.championAppearedNames.addAll(pendingChampionAppeared);pendingChampionMsgs.clear();pendingChampionAppeared.clear()}
        val heroFrozen=hero.status.getOrDefault(Status.FREEZE,0)>0
        when{ heroFrozen->r.log+="🥶 @ is frozen!"
            a==Action.POTION->usePotion(r); a==Action.DESCEND->tryDescend(r); a==Action.THROW->throwKnife(r)
            a==Action.CAST_FIREBALL->castFireball(r); a==Action.CAST_BLINK->castBlink(r)
            a==Action.CAST_FEAR->castFear(r); a==Action.CAST_REVEAL->castReveal(r)
            a==Action.WAIT->Unit; else->moveHero(a,r) }
        if(!r.descended){monstersAct(r);npcsAct(r);exploredCount+=computeFov()}
        tickStatusAndHunger(r)
        if(poisonTurns>0){poisonTurns--;hero.hp--;r.damageTaken++;if(poisonTurns==0)r.log+="🤢✅ Poison cured."}
        for((ch,cm) in championMonsters.toMap()){ if(cm.hp<=0&&ch.alive){ ch.alive=false; monsters.remove(cm); championMonsters.remove(ch)
            r.championSlainNames+=ch.name; r.log+="🏆 ${ch.name}, ${ch.title}, is vanquished!"
            items+=Item(ItemKind.RELIC,cm.x,cm.y,0,null,ch.relic); r.log+="🏺 It dropped the ${ch.relic.emoji} ${ch.relic.name}!" } }
        if(hero.hp<=0&&!r.died){r.died=true;r.log+="💀☠️ The hero falls on depth $depth."}
        return r }

    private fun tickStatusAndHunger(r:TurnReport){ val it=hero.status.iterator()
        while(it.hasNext()){val e=it.next(); if(e.key==Status.BURN){hero.hp--;r.damageTaken++;r.log+="🔥 @ burns!"}
            val v=e.value-1; if(v<=0)it.remove() else e.setValue(v)}
        hero.hunger=(hero.hunger-1).coerceAtLeast(0); if(hero.hunger==0){hero.hp--;r.damageTaken++;r.log+="🍖 @ is starving!"}
        for(m in monsters.toList()){val mit=m.status.iterator(); while(mit.hasNext()){val e=mit.next()
            if(e.key==Status.BURN){m.hp--;if(m.hp<=0){monsters.remove(m);r.factionCasualties++}}
            val v=e.value-1; if(v<=0)mit.remove() else e.setValue(v)} } }

    private fun throwKnife(r:TurnReport){ if(hero.knives<=0){r.log+="🚫🔪 No knives!";return}
        val t=monsters.filter{it.alive&&visible[idx(it.x,it.y)]}.minByOrNull{cheb(it.x,it.y,hero.x,hero.y)}
        if(t==null||cheb(t.x,t.y,hero.x,hero.y)>5||!los(hero.x,hero.y,t.x,t.y)){r.log+="🔪 Out of range.";return}
        hero.knives--; val dmg=maxOf(1,hero.totalAtk()-t.def.armored-(if(t.elite==Elite.WARDED)2 else 0)+rng.nextInt(-1,2))
        t.hp-=dmg; r.damageDealt+=dmg; r.log+="🔪 @ throws a knife at ${t.def.emoji} for $dmg."
        if(t.hp<=0){r.kills++;r.slain+=t.def.name;if(t.isBoss)r.bossSlain=true;monsters.remove(t);gainXp(t.def.xp,r);onKillDrop(t,r);r.log+="☠️ ${t.def.emoji} slain!"} }
    private fun castFireball(r:TurnReport){ if(!consumeScroll(ScrollType.FIREBALL,r))return; var hits=0
        for(m in monsters.toList()){if(m.alive&&cheb(m.x,m.y,hero.x,hero.y)<=2){val dmg=8+rng.nextInt(0,4);m.hp-=dmg;r.damageDealt+=dmg;hits++
            if(m.hp<=0){r.kills++;r.slain+=m.def.name;monsters.remove(m);gainXp(m.def.xp,r);onKillDrop(m,r)}}}; r.log+="🔥📜 Fireball ($hits hits)!" }
    private fun castBlink(r:TurnReport){ if(!consumeScroll(ScrollType.BLINK,r))return
        randomSpot(3){t->t==T.FLOOR||t==T.GRASS||t==T.CORRIDOR}?.let{hero.x=it.x;hero.y=it.y;r.log+="🌀📜 Blink!"} }
    private fun castFear(r:TurnReport){ if(!consumeScroll(ScrollType.FEAR,r))return
        for(m in monsters) if(cheb(m.x,m.y,hero.x,hero.y)<=4){m.status[Status.FREEZE]=2;m.awake=false}; r.log+="😱📜 Terror!" }
    private fun castReveal(r:TurnReport){ if(!consumeScroll(ScrollType.REVEAL,r))return; var rev=0
        for(i in map.indices) if(!seen[i]&&explorable(map[i])){seen[i]=true;rev++}; exploredCount+=rev;r.newSeenTiles+=rev;r.log+="🗺️📜 Revealed!" }
    private fun consumeScroll(s:ScrollType,r:TurnReport):Boolean{ val c=hero.scrolls.getOrDefault(s,0); if(c<=0){r.log+="🚫📜 No scroll";return false}; hero.scrolls[s]=c-1;return true }
    private fun onKillDrop(m:Monster,r:TurnReport){ if(m.def.behavior==MonsterBehavior.EXPLOSIVE)explode(m.x,m.y,r)
        if(m.def.splits&&m.maxHp>=8)splitSlime(m,r); if(m.isBoss&&depth==AMULET_DEPTH){items+=Item(ItemKind.AMULET,m.x,m.y);r.log+="💎 Dragon drops Amulet!"}
        if(rng.nextInt(100)<20)items+=Item(ItemKind.KNIFE,m.x,m.y,1) }

    private fun moveHero(a:Action,r:TurnReport){ if(hero.status.getOrDefault(Status.WEB,0)>0){r.log+="🕸️ Webbed!";return}
        val nx=hero.x+a.dx; val ny=hero.y+a.dy; val t=tileAt(nx,ny)
        if(t==T.DOOR_CLOSED){map[idx(nx,ny)]=T.DOOR_OPEN;r.log+="🚪 Opens door.";return}
        if(!walkable(nx,ny)){r.log+="🧱 Bumps.";return}
        val npc=npcAt(nx,ny); if(npc!=null){interactNpc(npc,r);return}
        val m=monsterAt(nx,ny); if(m!=null){heroAttack(m,r);return}
        hero.x=nx;hero.y=ny;enterTileEffects(r);pickup(r) }
    private fun enterTileEffects(r:TurnReport){ val i=idx(hero.x,hero.y); val t=map[i]
        if(t==T.LAVA){val dmg=3+depth;hero.hp-=dmg;r.damageTaken+=dmg;r.log+="🔥🌋 Lava -$dmg!"}
        traps[i]?.let{if(!it.sprung)triggerTrapOnHero(it,r)}
        if(map[i]==T.FOUNTAIN){map[i]=T.FLOOR;hero.hp=hero.maxHp;hero.potions++;poisonTurns=0;r.log+="⛲✨ Fountain!"} }
    private fun triggerTrapOnHero(trap:Trap,r:TurnReport){ trap.sprung=true
        if(trap.type==TrapType.SPIKE){val dmg=4+depth;hero.hp-=dmg;r.damageTaken+=dmg;r.log+="⚠️🗡️ Spike -$dmg!"}
        else randomSpot(0){t->t==T.FLOOR||t==T.GRASS||t==T.CORRIDOR}?.let{hero.x=it.x;hero.y=it.y;r.log+="🌀 Teleport!"} }
    private fun heroAttack(m:Monster,r:TurnReport){ val inspired=npcs.any{it.kind==NpcKind.BARD&&cheb(it.x,it.y,hero.x,hero.y)<=2}
        var dmg=maxOf(1,hero.totalAtk()-m.def.armored-(if(m.elite==Elite.WARDED)2 else 0)+rng.nextInt(-1,2)); if(inspired)dmg++
        m.hp-=dmg;r.damageDealt+=dmg;r.log+="⚔️ 🦸 hits ${m.def.emoji} for $dmg."
        if(m.hp<=0){r.kills++;r.slain+=m.def.name;if(m.isBoss)r.bossSlain=true;r.log+="☠️ ${m.def.emoji} slain! +${m.def.xp}XP";monsters.remove(m);gainXp(m.def.xp,r);onKillDrop(m,r)
            if(hasRelic(RelicPerk.VAMPIRIC))hero.hp=minOf(hero.maxHp,hero.hp+2)} }
    private fun splitSlime(m:Monster,r:TurnReport){ var s=0; for(d in DIRS8.shuffled(rng)){if(s>=2)break;val nx=m.x+d.x;val ny=m.y+d.y
        if(!walkable(nx,ny)||tileAt(nx,ny)==T.LAVA||monsterAt(nx,ny)!=null||nx==hero.x&&ny==hero.y)continue
        monsters+=Monster(m.def,nx,ny,m.maxHp/2,m.maxHp/2,true);s++}; if(s>0)r.log+="🦠✂️ Slime splits!" }
    private fun explode(x:Int,y:Int,r:TurnReport){ val dmg=4+depth; r.log+="💥💣 Boom!"
        if(cheb(x,y,hero.x,hero.y)<=1){hero.hp-=dmg;r.damageTaken+=dmg;r.log+="💥 Caught -$dmg!"}
        for(o in monsters.toList()) if(o.alive&&cheb(x,y,o.x,o.y)<=1){o.hp-=maxOf(1,dmg-o.def.armored);if(o.hp<=0){monsters.remove(o);r.log+="💥 ${o.def.emoji} caught!"}}
        for(n in npcs.toList()) if(cheb(x,y,n.x,n.y)<=1){npcs.remove(n);r.log+="💥 ${n.kind.emoji} caught!"} }
    private fun gainXp(amount:Int,r:TurnReport){ val mult=if(hasRelic(RelicPerk.SCHOLAR))1.25 else 1.0; hero.xp+=(amount*mult).toInt()
        while(hero.xp>=hero.xpNext){hero.xp-=hero.xpNext;hero.level++;hero.maxHp+=4;hero.atk++;if(hero.level%3==0)hero.def++
            hero.hp=minOf(hero.maxHp,hero.hp+hero.maxHp/2);r.levelUps++;r.log+="🎉⭐ Level ${hero.level}!"} }
    private fun pickup(r:TurnReport){ val it=itemAt(hero.x,hero.y)?:return
        when(it.kind){ ItemKind.POTION->{hero.potions++;r.log+="🧪 Potion"}; ItemKind.GOLD->{val amt=if(hasRelic(RelicPerk.FORTUNE))(it.value*1.5).toInt() else it.value;hero.gold+=amt;r.log+="💰 +$amt"}
            ItemKind.WEAPON->{hero.weaponBonus+=it.value;r.log+="⚔️ ATK+${it.value}"}; ItemKind.ARMOR->{hero.armorBonus+=it.value;r.log+="🛡️ DEF+${it.value}"}
            ItemKind.KNIFE->{hero.knives+=it.value;r.log+="🔪 +${it.value} knives"}; ItemKind.FOOD->{hero.hunger=(hero.hunger+it.value).coerceAtMost(100);r.log+="🍖 Yum!"}
            ItemKind.SCROLL->{val s=it.scroll?:ScrollType.FIREBALL;hero.scrolls[s]=hero.scrolls.getOrDefault(s,0)+1;r.log+="📜 ${s.emoji} scroll"}
            ItemKind.AMULET->{r.pickedAmulet=true;if(endless){ascension++;depth++;newLevel();hero.hp=minOf(hero.maxHp,hero.hp+10);r.descended=true;r.log+="💎🔁 Ascension $ascension!"}else{r.won=true;r.log+="🏆💎 Amulet!"}}
            ItemKind.RELIC->{val rl=it.relic;if(rl!=null&&collectedRelics.none{x->x.name==rl.name}){collectedRelics+=rl;applySinglePerk(rl.perk);r.relicPicked=rl.name;r.log+="🏺 Relic: ${rl.name} (${rl.perk.desc})!"}else r.log+="🏺 Already claimed."} }
        items.remove(it);r.itemsPicked++ }
    private fun usePotion(r:TurnReport){ if(hero.potions<=0){r.log+="🚫🧪";return}; if(hero.hp>=hero.maxHp&&poisonTurns==0){r.log+="❤️ Full";return}
        hero.potions--;val heal=10+hero.level*2;hero.hp=minOf(hero.maxHp,hero.hp+heal)
        if(poisonTurns>0){poisonTurns=0;r.log+="🧪✨ Heals & cures!"}else r.log+="🧪 +$heal HP" }
    private fun tryDescend(r:TurnReport){ if(tileAt(hero.x,hero.y)!=T.STAIRS){r.log+="🚫 No stairs";return}
        for(ch in champions) if(ch.alive){ch.floorsSurvived++;ch.grudge=1+ch.floorsSurvived/2}
        val grateful=npcs.count{it.kind==NpcKind.VILLAGER&&it.greeted}; if(grateful>0){hero.gold+=grateful*15;r.log+="👦💰 +${grateful*15}g"}
        depth++;newLevel();hero.hp=minOf(hero.maxHp,hero.hp+4+depth);r.descended=true;r.newSeenTiles=25;r.log+="🕳️⬇️ Depth $depth" }

    private fun npcsAct(r:TurnReport){for(n in npcs.toList())npcTurn(n,r)}
    private fun npcTurn(n:Npc,r:TurnReport){ val dist=cheb(n.x,n.y,hero.x,hero.y)
        when(n.kind){ NpcKind.SAGE->Unit
            NpcKind.MERCHANT,NpcKind.COOK,NpcKind.BLACKSMITH,NpcKind.GAMBLER->{if(awakeMonsterNear(n,3))npcFlee(n)?.let{n.x=it.x;n.y=it.y}else if(rng.nextInt(4)==0)npcWander(n)}
            NpcKind.VILLAGER->{if(!n.greeted){if(dist<=1){n.greeted=true;r.log+="👋👦 Follows!"}else if(rng.nextInt(3)==0)npcWander(n)}else if(dist>2)npcStepToward(n,hero.x,hero.y)?.let{n.x=it.x;n.y=it.y}}
            NpcKind.HEALER->{if(awakeMonsterNear(n,2))npcFlee(n)?.let{n.x=it.x;n.y=it.y}else if(dist>3)npcStepToward(n,hero.x,hero.y)?.let{n.x=it.x;n.y=it.y}else if(dist<=1)npcFlee(n)?.let{n.x=it.x;n.y=it.y}
                if(cheb(n.x,n.y,hero.x,hero.y)<=2&&hero.hp<hero.maxHp)hero.hp++}
            NpcKind.BARD->{if(awakeMonsterNear(n,2))npcFlee(n)?.let{n.x=it.x;n.y=it.y}else if(dist>3)npcStepToward(n,hero.x,hero.y)?.let{n.x=it.x;n.y=it.y}else if(rng.nextInt(4)==0)npcWander(n)} } }
    private fun interactNpc(npc:Npc,r:TurnReport){ when(npc.kind){
        NpcKind.MERCHANT->{if(hero.gold>=25){hero.gold-=25;hero.potions++;r.itemsPicked++;r.log+="🛒🧪 Bought"}else r.log+="🛒💸 25g"}
        NpcKind.SAGE->{if(!npc.used){npc.used=true;var rev=0;val si=idx(stairsX,stairsY);if(!seen[si]){seen[si]=true;rev++}
            for(it in items){val i=idx(it.x,it.y);if(!seen[i]){seen[i]=true;rev++}};exploredCount+=rev;r.newSeenTiles+=rev;r.log+="🔮✨ Revealed!"}else r.log+="🔮 Nods"}
        NpcKind.VILLAGER->{if(!npc.greeted){npc.greeted=true;r.log+="👋👦 Follows!"}else r.log+="👦 Close"}
        NpcKind.HEALER->{val h=minOf(4,hero.maxHp-hero.hp);if(h>0){hero.hp+=h;r.log+="💉❤️ +$h"}else r.log+="💉😊"}
        NpcKind.COOK->{if(!npc.used){npc.used=true;val h=minOf(10,hero.maxHp-hero.hp);hero.hp+=h;poisonTurns=0;r.log+="🍖😋 +$h"}else r.log+="🍖 Hums"}
        NpcKind.BLACKSMITH->{when{npc.forgeLevel>=2->r.log+="⚒️ Done";hero.gold<40->r.log+="⚒️💸 40g";npc.forgeLevel==0->{hero.gold-=40;hero.weaponBonus++;npc.forgeLevel=1;r.log+="⚒️⚔️ ATK+1"};else->{hero.gold-=40;hero.armorBonus++;npc.forgeLevel=2;r.log+="⚒️🛡️ DEF+1"}}}
        NpcKind.GAMBLER->{when{npc.rounds>=3->r.log+="🎰 Broke";hero.gold<10->r.log+="🎰💸 10g";else->{hero.gold-=10;npc.rounds++;if(rng.nextBoolean()){hero.gold+=30;r.log+="🎰🎉 +30g"}else r.log+="🎰😩 Lost"}}}
        NpcKind.BARD->{val h=minOf(2,hero.maxHp-hero.hp);if(h>0)hero.hp+=h;r.log+="🎻🎶 +$h"} } }
    private fun awakeMonsterNear(n:Npc,rad:Int)=monsters.any{it.alive&&it.awake&&cheb(it.x,it.y,n.x,n.y)<=rad}
    private fun npcStepToward(n:Npc,tx:Int,ty:Int):Pt?{for(d in DIRS8.shuffled(rng).sortedBy{cheb(n.x+it.x,n.y+it.y,tx,ty)}){val nx=n.x+d.x;val ny=n.y+d.y
        if(nx==hero.x&&ny==hero.y||!walkable(nx,ny)||tileAt(nx,ny)==T.LAVA||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null)continue;return Pt(nx,ny)};return null}
    private fun npcFlee(n:Npc):Pt?{var best:Pt?=null;var bs=Int.MIN_VALUE;for(d in DIRS8){val nx=n.x+d.x;val ny=n.y+d.y
        if(!walkable(nx,ny)||tileAt(nx,ny)==T.LAVA||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null||nx==hero.x&&ny==hero.y)continue
        val s=monsters.filter{it.alive&&it.awake}.minOfOrNull{cheb(it.x,it.y,nx,ny)}?:10;if(s>bs){bs=s;best=Pt(nx,ny)}};return best}
    private fun npcWander(n:Npc){val d=DIRS8.random(rng);val nx=n.x+d.x;val ny=n.y+d.y
        if(nx==hero.x&&ny==hero.y||!walkable(nx,ny)||tileAt(nx,ny)==T.LAVA||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null)return;n.x=nx;n.y=ny}

    private fun monstersAct(r:TurnReport){ for(m in monsters.toList()){if(!m.alive)continue
        if(m.status.getOrDefault(Status.FREEZE,0)>0)continue; val canMove=m.status.getOrDefault(Status.WEB,0)<=0
        val times=if(m.def.fast||m.elite==Elite.SWIFT)2 else 1; repeat(times){if(m.alive&&hero.hp>0)monsterTurn(m,r,canMove)} } }
    private fun nearestVisibleFoe(m:Monster):Monster?{var best:Monster?=null;var bd=Int.MAX_VALUE
        for(o in monsters){if(o===m||!o.alive||!hostile(m.def.faction,o.def.faction))continue;val d=cheb(m.x,m.y,o.x,o.y)
            if(d>8||!los(m.x,m.y,o.x,o.y))continue;if(d<bd){bd=d;best=o}};return best}
    private fun nearestVisibleRival(m:Monster):Monster?{var best:Monster?=null;var bd=Int.MAX_VALUE
        for((_,cm) in championMonsters){if(cm===m||!cm.alive)continue;val d=cheb(m.x,m.y,cm.x,cm.y)
            if(d>8||!los(m.x,m.y,cm.x,cm.y))continue;if(d<bd){bd=d;best=cm}};return best}

    private fun monsterTurn(m:Monster,r:TurnReport,canMove:Boolean){
        if(m.def.behavior==MonsterBehavior.REGENERATOR&&m.hp<m.maxHp)m.hp++
        val dist=cheb(m.x,m.y,hero.x,hero.y);val sees=dist<=8&&los(m.x,m.y,hero.x,hero.y)
        if(!m.awake){val wakes=if(m.def.ambushRange>0)sees&&dist<=m.def.ambushRange else sees||dist<=2
            if(wakes){m.awake=true;r.log+=if(m.def.ambushRange>0)"🐊💦 Ambush!"else if(sees)"👀 ${m.def.emoji} wakes!"else "👂 ${m.def.emoji} hears..."
                if(m.def.behavior==MonsterBehavior.PACK)for(o in monsters)if(o!==m&&o.alive&&!o.awake&&o.def===m.def&&cheb(o.x,o.y,m.x,m.y)<=6){o.awake=true;r.log+="🐺⚔️ Pack joins!"}
            }else if(!m.def.stationary&&rng.nextInt(6)==0&&canMove)wander(m);return}
        if(sees){m.lastSeenX=hero.x;m.lastSeenY=hero.y;m.lostTurns=0}else{m.lostTurns++;if(!m.isChampion&&m.lostTurns>14){m.awake=false;m.lastSeenX=-1;return}}
        if(m.def.slow){m.slowToggle=!m.slowToggle;if(m.slowToggle)return}
        if(m.def.blinker){m.blinkCooldown--;if(m.blinkCooldown<=0){m.blinkCooldown=3+rng.nextInt(0,3);if(tryBlink(m,r))return}}
        if(m.isChampion){val rival=nearestVisibleRival(m);if(rival!=null){monsterFight(m,rival,cheb(m.x,m.y,rival.x,rival.y),r,canMove);return}}
        val foe=nearestVisibleFoe(m);val foeDist=foe?.let{cheb(m.x,m.y,it.x,it.y)}?:Int.MAX_VALUE
        if(foe!=null&&(!sees||foeDist<dist)){monsterFight(m,foe,foeDist,r,canMove);return}
        when(m.def.behavior){MonsterBehavior.RANGED->rangedAct(m,dist,sees,r,canMove);MonsterBehavior.COWARD->cowardAct(m,dist,r,canMove)
            MonsterBehavior.SUMMONER->summonerAct(m,dist,sees,r,canMove);else->meleeAct(m,dist,sees,r,canMove)} }
    private fun monsterFight(m:Monster,foe:Monster,d:Int,r:TurnReport,canMove:Boolean){
        if(m.def.behavior==MonsterBehavior.EXPLOSIVE&&d<=1){monsters.remove(m);explode(m.x,m.y,r);return}
        if(m.def.stationary){if(d<=1)strikeMonster(m,foe,r);return}
        if(m.def.behavior==MonsterBehavior.COWARD){if(canMove)fleeStepFrom(m,foe.x,foe.y)?.let{applyMove(m,it,r)};return}
        if((m.def.behavior==MonsterBehavior.RANGED||m.def.behavior==MonsterBehavior.SUMMONER)&&d<=m.def.range&&los(m.x,m.y,foe.x,foe.y)){shootMonster(m,foe,r);return}
        if(d<=1){strikeMonster(m,foe,r);return}
        if(canMove){val s=chaseStep(m,foe.x,foe.y);if(s!=null)applyMove(m,s,r)else if(rng.nextInt(3)==0)wander(m)} }
    private fun strikeMonster(m:Monster,foe:Monster,r:TurnReport){val dmg=maxOf(1,m.def.atk-foe.def.armored+rng.nextInt(-1,2));foe.hp-=dmg;foe.awake=true
        r.log+="⚔️ ${m.def.emoji}${m.def.faction.emoji} strikes ${foe.def.emoji} for $dmg."
        if(m.def.lifesteal)m.hp=min(m.maxHp,m.hp+dmg/2+1);if(foe.hp<=0)killByMonster(m,foe,r)}
    private fun shootMonster(m:Monster,foe:Monster,r:TurnReport){val dmg=maxOf(1,m.def.atk-foe.def.armored+rng.nextInt(-1,2));foe.hp-=dmg;foe.awake=true
        r.log+="🏹 ${m.def.emoji} shoots ${foe.def.emoji} for $dmg.";if(foe.hp<=0)killByMonster(m,foe,r)}
    private fun killByMonster(k:Monster,v:Monster,r:TurnReport){monsters.remove(v);r.factionCasualties++;r.log+="☠️ ${k.def.emoji}${k.def.faction.emoji} slays ${v.def.emoji} ${v.def.name}!"
        if(v.def.behavior==MonsterBehavior.EXPLOSIVE)explode(v.x,v.y,r);if(v.def.splits&&v.maxHp>=8)splitSlime(v,r)}
    private fun tryBlink(m:Monster,r:TurnReport):Boolean{val low=m.hp*3<=m.maxHp;repeat(25){val x=rng.nextInt(width);val y=rng.nextInt(height)
        if(!walkable(x,y)||tileAt(x,y)==T.LAVA&&!lavaSafe(m)||x==hero.x&&y==hero.y||monsterAt(x,y)!=null||npcAt(x,y)!=null)continue
        val d=cheb(x,y,hero.x,hero.y);if(if(low)d>=6 else d in 2..4){m.x=x;m.y=y;r.log+="✨🧞 Blinks!";return true}};return false}
    private fun meleeAct(m:Monster,dist:Int,sees:Boolean,r:TurnReport,canMove:Boolean){
        if(m.def.behavior==MonsterBehavior.EXPLOSIVE&&dist<=1){monsters.remove(m);explode(m.x,m.y,r);return}
        if(m.def.stationary){if(dist<=1)monsterAttack(m,r);return}
        val flee=(m.def.morale&&m.hp*4<=m.maxHp&&dist<=2)||(m.def.lifesteal&&m.hp*3<=m.maxHp&&dist<=2)
        if(flee){if(canMove)fleeStepFrom(m,hero.x,hero.y)?.let{applyMove(m,it,r)};return}
        if(dist<=1){monsterAttack(m,r);return}
        val tx=if(sees)hero.x else m.lastSeenX;val ty=if(sees)hero.y else m.lastSeenY
        if(tx<0){if(canMove&&rng.nextInt(3)==0)wander(m);return}
        if(canMove){val s=if(m.def.behavior==MonsterBehavior.GHOST)ghostStep(m)else chaseStep(m,tx,ty);if(s!=null)applyMove(m,s,r)else if(rng.nextInt(3)==0)wander(m)} }
    private fun rangedAct(m:Monster,dist:Int,sees:Boolean,r:TurnReport,canMove:Boolean){
        if(dist<=1){if(canMove){val s=fleeStepFrom(m,hero.x,hero.y);if(s!=null){applyMove(m,s,r);return}};monsterAttack(m,r);return}
        if(sees&&dist<=m.def.range){shoot(m,r);return}
        val tx=if(sees)hero.x else m.lastSeenX;val ty=if(sees)hero.y else m.lastSeenY
        if(tx<0){if(canMove&&rng.nextInt(3)==0)wander(m);return}
        if(canMove){val s=chaseStep(m,tx,ty);if(s!=null)applyMove(m,s,r)else if(rng.nextInt(3)==0)wander(m)} }
    private fun cowardAct(m:Monster,dist:Int,r:TurnReport,canMove:Boolean){
        if(m.def.stealsGold&&dist<=1&&hero.gold>0){val st=min(hero.gold,1+rng.nextInt(5));hero.gold-=st;r.log+="💰🏃 Snatches $st!"
            if(canMove)fleeStepFrom(m,hero.x,hero.y)?.let{m.x=it.x;m.y=it.y;monsterEnterTile(m,r)};return}
        if(dist>=2&&dist<=m.def.range&&los(m.x,m.y,hero.x,hero.y)){shoot(m,r);return}
        if(dist<=2){if(canMove)fleeStepFrom(m,hero.x,hero.y)?.let{m.x=it.x;m.y=it.y;monsterEnterTile(m,r)}}else if(canMove&&rng.nextInt(3)==0)wander(m)}
    private fun summonerAct(m:Monster,dist:Int,sees:Boolean,r:TurnReport,canMove:Boolean){m.summonCooldown--
        if(m.summonCooldown<=0&&monsters.size<30){val spot=DIRS8.shuffled(rng).map{Pt(m.x+it.x,m.y+it.y)}.firstOrNull{inBounds(it.x,it.y)&&walkable(it.x,it.y)&&monsterAt(it.x,it.y)==null&&npcAt(it.x,it.y)==null&&!(it.x==hero.x&&it.y==hero.y)}
            if(spot!=null){val sk=Bestiary.skeleton;monsters+=Monster(sk,spot.x,spot.y,sk.hp+(depth-sk.minDepth)*2,sk.hp+(depth-sk.minDepth)*2,true);m.summonCooldown=5;r.log+="⚰️✨ Summons 💀!"}else m.summonCooldown=2}
        rangedAct(m,dist,sees,r,canMove)}
    private fun packBonus(m:Monster):Int{if(m.def.behavior!=MonsterBehavior.PACK)return 0;return minOf(2,monsters.count{it!==m&&it.alive&&it.def===m.def&&cheb(it.x,it.y,m.x,m.y)<=1})}
    private fun monsterAttack(m:Monster,r:TurnReport){val dmg=maxOf(1,m.def.atk+packBonus(m)+m.bonusAtk+(if(m.status.containsKey(Status.STRENGTH))2 else 0)+rng.nextInt(-1,2)-hero.totalDef())
        hero.hp-=dmg;r.damageTaken+=dmg;r.log+="💢 ${m.def.emoji} hits 🦸 for $dmg."
        if(m.def.venomous&&dmg>0){poisonTurns=maxOf(poisonTurns,4);r.log+="🤢 Poisoned!"}
        if(m.elite==Elite.BURNING&&dmg>0){hero.status[Status.BURN]=3;r.log+="🔥 Ablaze!"}
        if(m.def.lifesteal&&dmg>0){m.hp=min(m.maxHp,m.hp+dmg/2+1);r.log+="🩸 Drains!"}}
    private fun shoot(m:Monster,r:TurnReport){val dmg=maxOf(1,m.def.atk+rng.nextInt(-1,2)-hero.totalDef()/2);hero.hp-=dmg;r.damageTaken+=dmg;r.log+="🏹 ${m.def.emoji} shoots 🦸 for $dmg."
        if(m.elite==Elite.BURNING)hero.status[Status.BURN]=3}
    private fun applyMove(m:Monster,s:Pt,r:TurnReport){if(tileAt(s.x,s.y)==T.DOOR_CLOSED)map[idx(s.x,s.y)]=T.DOOR_OPEN else{m.x=s.x;m.y=s.y;monsterEnterTile(m,r)}}
    private fun monsterEnterTile(m:Monster,r:TurnReport){if(tileAt(m.x,m.y)==T.LAVA&&!lavaSafe(m)){val dmg=3+depth;m.hp-=dmg
        if(m.hp<=0){r.log+="🔥 ${m.def.emoji} burns!";monsters.remove(m);r.kills++;gainXp(m.def.xp,r);if(m.def.behavior==MonsterBehavior.EXPLOSIVE)explode(m.x,m.y,r);return}}
        traps[idx(m.x,m.y)]?.let{trap->if(trap.sprung)return@let;trap.sprung=true
            if(trap.type==TrapType.SPIKE){m.hp-=maxOf(1,4+depth-m.def.armored);r.log+="⚠️ ${m.def.emoji} spike!"
                if(m.hp<=0){monsters.remove(m);r.kills++;gainXp(m.def.xp,r);if(m.def.behavior==MonsterBehavior.EXPLOSIVE)explode(m.x,m.y,r)}}
            else randomSpot(0){t->t==T.FLOOR||t==T.GRASS||t==T.CORRIDOR}?.let{m.x=it.x;m.y=it.y;m.awake=false;r.log+="🌀 ${m.def.emoji} warped!"}}}
    private fun chaseStep(m:Monster,tx:Int,ty:Int):Pt?{for(d in DIRS8.shuffled(rng).sortedBy{cheb(m.x+it.x,m.y+it.y,tx,ty)}){val nx=m.x+d.x;val ny=m.y+d.y
        if(nx==hero.x&&ny==hero.y)continue;val t=tileAt(nx,ny);if(t==T.LAVA&&!lavaSafe(m))continue;if(t==T.DOOR_CLOSED)return Pt(nx,ny)
        if(!walkable(nx,ny)||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null)continue
        if(!m.def.flier&&d.x!=0&&d.y!=0&&!walkable(m.x+d.x,m.y)&&!walkable(m.x,m.y+d.y))continue;return Pt(nx,ny)};return null}
    private fun ghostStep(m:Monster):Pt?{for(d in DIRS8.sortedBy{cheb(m.x+it.x,m.y+it.y,hero.x,hero.y)}){val nx=m.x+d.x;val ny=m.y+d.y
        if(!inBounds(nx,ny)||nx==hero.x&&ny==hero.y)continue;val t=tileAt(nx,ny);if(t==T.WALL||t==T.PILLAR||t==T.RUBBLE)continue
        if(monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null)continue;return Pt(nx,ny)};return null}
    private fun fleeStepFrom(m:Monster,tx:Int,ty:Int):Pt?{var best:Pt?=null;var bd=cheb(m.x,m.y,tx,ty);for(d in DIRS8){val nx=m.x+d.x;val ny=m.y+d.y
        if(!walkable(nx,ny)||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null||nx==hero.x&&ny==hero.y)continue
        if(tileAt(nx,ny)==T.LAVA&&!lavaSafe(m))continue;val dd=cheb(nx,ny,tx,ty);if(dd>bd){bd=dd;best=Pt(nx,ny)}};return best}
    private fun wander(m:Monster){if(m.def.stationary)return;val d=DIRS8.random(rng);val nx=m.x+d.x;val ny=m.y+d.y
        if(nx==hero.x&&ny==hero.y||tileAt(nx,ny)==T.LAVA&&!lavaSafe(m)||!walkable(nx,ny)||monsterAt(nx,ny)!=null||npcAt(nx,ny)!=null)return;m.x=nx;m.y=ny}

    fun computeFov():Int{visible.fill(false);var newSeen=0;val hx=hero.x;val hy=hero.y;val r=fovR()
        for(dy in -r..r)for(dx in -r..r){if(dx*dx+dy*dy>r*r+2)continue;val tx=hx+dx;val ty=hy+dy
            if(!inBounds(tx,ty)||!los(hx,hy,tx,ty))continue;val i=idx(tx,ty);visible[i]=true
            if(!seen[i]){seen[i]=true;if(explorable(map[i]))newSeen++}};return newSeen}
    private fun blocksSight(t:Int)=t==T.WALL||t==T.DOOR_CLOSED
    private fun los(x0:Int,y0:Int,x1:Int,y1:Int):Boolean{var x=x0;var y=y0;val dx=abs(x1-x0);val dy=-abs(y1-y0)
        val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var err=dx+dy
        while(true){if((x!=x0||y!=y0)&&blocksSight(map[idx(x,y)]))return x==x1&&y==y1
            if(x==x1&&y==y1)return true;val e2=2*err;if(e2>=dy){err+=dy;x+=sx};if(e2<=dx){err+=dx;y+=sy}}}

    fun snapshot():Game{val c=Game(width,height);c.map=map.copyOf();c.seen=seen.copyOf();c.visible=visible.copyOf()
        for((k,t) in traps)c.traps[k]=Trap(t.type,t.sprung);c.hero.copyFrom(hero)
        for(m in monsters){val mc=Monster(m.def,m.x,m.y,m.hp,m.maxHp,m.awake);mc.slowToggle=m.slowToggle;mc.summonCooldown=m.summonCooldown;mc.blinkCooldown=m.blinkCooldown
            mc.lastSeenX=m.lastSeenX;mc.lastSeenY=m.lastSeenY;mc.lostTurns=m.lostTurns;mc.isChampion=m.isChampion;mc.bonusAtk=m.bonusAtk;mc.elite=m.elite;mc.status.putAll(m.status);c.monsters+=mc}
        for(it in items)c.items+=Item(it.kind,it.x,it.y,it.value,it.scroll,it.relic)
        for(n in npcs){val nc=Npc(n.kind,n.x,n.y);nc.greeted=n.greeted;nc.used=n.used;nc.forgeLevel=n.forgeLevel;nc.rounds=n.rounds;c.npcs+=nc}
        c.depth=depth;c.steps=steps;c.poisonTurns=poisonTurns;c.ascension=ascension;c.endless=endless;c.directorTension=directorTension
        c.stairsX=stairsX;c.stairsY=stairsY;c.exploredCount=exploredCount;c.totalWalkable=totalWalkable
        c.collectedRelics.addAll(collectedRelics);c.champions.addAll(champions);return c}
}
