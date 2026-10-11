package com.example.roguelikeai.game

import kotlin.math.abs

data class Pt(val x:Int, val y:Int)

object T {
    const val WALL=0; const val FLOOR=1; const val STAIRS=2
    const val CORRIDOR=3; const val GRASS=4; const val WATER=5
    const val LAVA=6; const val DOOR_CLOSED=7; const val DOOR_OPEN=8
    const val PILLAR=9; const val RUBBLE=10; const val FOUNTAIN=11
}

enum class Action(val dx:Int, val dy:Int){
    N(0,-1),E(1,0),S(0,1),W(-1,0),NE(1,-1),SE(1,1),SW(-1,1),NW(-1,-1),
    WAIT(0,0),POTION(0,0),DESCEND(0,0),
    THROW(0,0),CAST_FIREBALL(0,0),CAST_BLINK(0,0),CAST_FEAR(0,0),CAST_REVEAL(0,0);
    val isMove get() = ordinal<8
}
val Action.emoji:String get() = when(this){
    Action.N->"⬆️";Action.S->"⬇️";Action.E->"➡️";Action.W->"⬅️"
    Action.NE->"↗️";Action.NW->"↖️";Action.SE->"↘️";Action.SW->"↙️"
    Action.WAIT->"⏳";Action.POTION->"🧪";Action.DESCEND->"🕳️"
    Action.THROW->"🔪";Action.CAST_FIREBALL->"🔥";Action.CAST_BLINK->"🌀"
    Action.CAST_FEAR->"😱";Action.CAST_REVEAL->"🗺️"
}
fun actionFor(dx:Int,dy:Int):Action? = Action.values().firstOrNull{ it.isMove&&it.dx==dx&&it.dy==dy }
fun cheb(x1:Int,y1:Int,x2:Int,y2:Int) = maxOf(abs(x1-x2),abs(y1-y2))

enum class ItemKind(val glyph:Char,val color:Int,val emoji:String){
    POTION('!',0xFFEF5350.toInt(),"🧪"), GOLD('$',0xFFFFD54F.toInt(),"💰"),
    WEAPON(')',0xFF64B5F6.toInt(),"⚔️"), ARMOR('[',0xFF81C784.toInt(),"🛡️"),
    AMULET('♦',0xFFFFEB3B.toInt(),"💎"), KNIFE('/',0xFFB0BEC5.toInt(),"🔪"),
    FOOD('%',0xFFA1887F.toInt(),"🍖"), SCROLL('?',0xFFCE93D8.toInt(),"📜"),
    RELIC('★',0xFFFFEB3B.toInt(),"🏺")
}
class Item(val kind:ItemKind,var x:Int,var y:Int,val value:Int=0,
           val scroll:ScrollType?=null,val relic:Relic?=null)

enum class ScrollType(val emoji:String,val label:String){
    FIREBALL("🔥","fireball"),BLINK("🌀","blink"),FEAR("😱","fear"),REVEAL("🗺️","reveal map")
}
enum class Status(val emoji:String){ FREEZE("🥶"),BURN("🔥"),WEB("🕸️"),STRENGTH("💪") }
enum class MonsterBehavior{ MELEE,RANGED,COWARD,GHOST,EXPLOSIVE,REGENERATOR,SUMMONER,PACK }
enum class Faction(val emoji:String,val color:Int){
    NONE("⚪",0),WILD("🟢",0xFF66BB6A.toInt()),HORDE("🟠",0xFFFF7043.toInt()),
    UNDEAD("🟣",0xFFB39DDB.toInt()),INFERNAL("🔴",0xFFEF5350.toInt())
}
fun hostile(a:Faction,b:Faction)=a!=Faction.NONE&&b!=Faction.NONE&&a!=b
enum class Elite(val emoji:String){ SWIFT("⚡"),BURNING("🔥"),WARDED("🛡️"),ALPHA("👑") }
enum class RelicPerk(val emoji:String,val desc:String){
    VITALITY("❤️","+6 max HP"),FURY("🗡️","+2 ATK"),IRON("🛡️","+1 DEF"),
    SWIFT("🔪","+2 knives"),FORTUNE("💰","+50% gold"),VAMPIRIC("🩸","heal on kill"),
    SCHOLAR("📚","+25% XP"),SCOUT("👁️","+2 FOV")
}
data class Relic(val name:String,val emoji:String,val perk:RelicPerk)
object Relics{
    fun derive(seedName:String):Relic{
        val rng=kotlin.random.Random(seedName.hashCode().toLong())
        val perk=RelicPerk.values().random(rng)
        val pre=listOf("Heart","Fang","Crown","Ember","Sigil","Halo","Eye","Brand").random(rng)
        val god=listOf("Vor","Ash","Mal","Eld","Gor","Bel","Uth","Mor").random(rng)+
                listOf("a","o","u","ar","or","ei").random(rng)+listOf("n","r","s","th").random(rng)
        return Relic("$pre of $god",perk.emoji,perk)
    }
}

class MonsterDef(val glyph:Char,val name:String,val hp:Int,val atk:Int,val xp:Int,
    val minDepth:Int,val weight:Int,val color:Int,val emoji:String,
    val faction:Faction=Faction.NONE,val behavior:MonsterBehavior=MonsterBehavior.MELEE,
    val range:Int=1,val fast:Boolean=false,val slow:Boolean=false,
    val venomous:Boolean=false,val lifesteal:Boolean=false,val stealsGold:Boolean=false,
    val lavaImmune:Boolean=false,val splits:Boolean=false,val morale:Boolean=false,
    val flier:Boolean=false,val aquatic:Boolean=false,val stationary:Boolean=false,
    val armored:Int=0,val ambushRange:Int=0,val blinker:Boolean=false)

class Monster(val def:MonsterDef,var x:Int,var y:Int,var hp:Int,val maxHp:Int,var awake:Boolean=false){
    val alive get()=hp>0
    var slowToggle=false; var summonCooldown=3; var blinkCooldown=3
    var lastSeenX=-1; var lastSeenY=-1; var lostTurns=0
    var isChampion=false; var bonusAtk=0
    var elite:Elite?=null
    val status=mutableMapOf<Status,Int>()
}

enum class NpcKind(val glyph:Char,val name:String,val color:Int,val emoji:String){
    MERCHANT('K',"merchant",0xFF4FC3F7.toInt(),"🛒"),SAGE('Y',"sage",0xFFB39DDB.toInt(),"🔮"),
    VILLAGER('v',"lost villager",0xFFFFCC80.toInt(),"👦"),HEALER('H',"wandering healer",0xFF81C784.toInt(),"💉"),
    COOK('C',"wandering cook",0xFFFF8A65.toInt(),"🍖"),BLACKSMITH('B',"blacksmith",0xFF90A4AE.toInt(),"⚒️"),
    BARD('D',"bard",0xFFF48FB1.toInt(),"🎻"),GAMBLER('G',"gambler",0xFFFFD54F.toInt(),"🎰")
}
class Npc(val kind:NpcKind,var x:Int,var y:Int){var greeted=false;var used=false;var forgeLevel=0;var rounds=0}

enum class TrapType(val label:String,val emoji:String){SPIKE("spike trap","⚠️"),TELEPORT("teleport trap","🌀")}
class Trap(val type:TrapType,var sprung:Boolean=false)

class Hero(var x:Int=0,var y:Int=0){
    var maxHp=32;var hp=32;var atk=5;var def=1
    var level=1;var xp=0;val xpNext get()=10+level*6
    var gold=0;var potions=1;var weaponBonus=0;var armorBonus=0
    var knives=3;val scrolls=mutableMapOf<ScrollType,Int>();var hunger=100
    val status=mutableMapOf<Status,Int>()
    fun totalAtk()=atk+weaponBonus+(if(status.containsKey(Status.STRENGTH))3 else 0)
    fun totalDef()=def+armorBonus
    fun reset(){maxHp=32;hp=32;atk=5;def=1;level=1;xp=0;gold=0;potions=1;weaponBonus=0;armorBonus=0
        knives=3;scrolls.clear();hunger=100;status.clear()}
    fun copyFrom(o:Hero){x=o.x;y=o.y;maxHp=o.maxHp;hp=o.hp;atk=o.atk;def=o.def;level=o.level;xp=o.xp
        gold=o.gold;potions=o.potions;weaponBonus=o.weaponBonus;armorBonus=o.armorBonus
        knives=o.knives;scrolls.clear();scrolls.putAll(o.scrolls);hunger=o.hunger
        status.clear();status.putAll(o.status)}
}

class TurnReport{
    var damageDealt=0;var damageTaken=0;var kills=0;var itemsPicked=0;var levelUps=0
    var newSeenTiles=0;var factionCasualties=0;var descended=false;var died=false;var won=false
    val slain=mutableListOf<String>();var bossSlain=false;var pickedAmulet=false
    val championAppearedNames=mutableListOf<String>();val championSlainNames=mutableListOf<String>()
    var relicPicked:String?=null
    val log=mutableListOf<String>()
}

class Champion(val name:String,val title:String,val backstory:String,val def:MonsterDef,val relic:Relic){
    var alive=true;var encounters=0;var grudge=1;var floorsSurvived=0
}
data class ChampionInfo(val name:String,val title:String,val backstory:String,
    val defName:String,val alive:Boolean,val grudge:Int,val floorsSurvived:Int)
