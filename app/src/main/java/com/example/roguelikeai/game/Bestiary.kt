package com.example.roguelikeai.game
import kotlin.random.Random
object Bestiary{
    private val defs=listOf(
        MonsterDef('r',"rat",5,2,3,1,9,0xFFA1887F.toInt(),"🐀",Faction.WILD),
        MonsterDef('b',"bat",4,2,3,1,9,0xFF9575CD.toInt(),"🦇",Faction.WILD),
        MonsterDef('S',"snake",6,2,4,1,7,0xFFAED581.toInt(),"🐍",Faction.WILD,venomous=true,morale=true),
        MonsterDef('i',"imp",5,2,4,1,7,0xFFFF8A65.toInt(),"👿",Faction.INFERNAL,fast=true),
        MonsterDef('g',"goblin",9,3,5,1,8,0xFF9CCC65.toInt(),"👺",Faction.HORDE,morale=true),
        MonsterDef('t',"thief",8,2,6,2,6,0xFFCE93D8.toInt(),"🦹",Faction.HORDE,behavior=MonsterBehavior.COWARD,stealsGold=true),
        MonsterDef('s',"skeleton",12,4,8,2,7,0xFFE0E0E0.toInt(),"💀",Faction.UNDEAD),
        MonsterDef('j',"slime",12,3,7,2,6,0xFFAEEA00.toInt(),"🦠",Faction.NONE,splits=true),
        MonsterDef('d',"hound",11,4,7,2,6,0xFFBCAAA4.toInt(),"🐕",Faction.WILD,fast=true),
        MonsterDef('A',"archer",10,4,9,2,6,0xFF80CBC4.toInt(),"🏹",Faction.HORDE,behavior=MonsterBehavior.RANGED,range=4,morale=true),
        MonsterDef('f',"fungus",14,2,8,2,6,0xFF8BC34A.toInt(),"🍄",Faction.NONE,stationary=true,venomous=true),
        MonsterDef('p',"spider",8,3,7,2,7,0xFF78909C.toInt(),"🕷️",Faction.WILD,venomous=true),
        MonsterDef('w',"boar",12,4,8,2,7,0xFF8D6E63.toInt(),"🐗",Faction.WILD,fast=true),
        MonsterDef('o',"orc",16,5,12,3,7,0xFF66BB6A.toInt(),"👹",Faction.HORDE),
        MonsterDef('W',"wolf",13,6,11,3,6,0xFFB0BEC5.toInt(),"🐺",Faction.WILD,behavior=MonsterBehavior.PACK),
        MonsterDef('a',"soldier ant",9,4,8,3,6,0xFFA1887F.toInt(),"🐜",Faction.WILD,behavior=MonsterBehavior.PACK),
        MonsterDef('e',"eagle",10,5,10,3,5,0xFFFFF176.toInt(),"🦅",Faction.WILD,flier=true),
        MonsterDef('U',"bear",20,6,14,3,5,0xFF795548.toInt(),"🐻",Faction.WILD),
        MonsterDef('q',"scorpion",13,5,12,3,5,0xFFFFB74D.toInt(),"🦂",Faction.WILD,venomous=true),
        MonsterDef('B',"bomber",10,3,10,3,5,0xFFEF9A9A.toInt(),"💣",Faction.HORDE,behavior=MonsterBehavior.EXPLOSIVE),
        MonsterDef('Z',"zombie",20,5,13,4,6,0xFF4DB6AC.toInt(),"🧟",Faction.UNDEAD,slow=true),
        MonsterDef('M',"fire mage",14,6,16,4,5,0xFFFF7043.toInt(),"🧙",Faction.INFERNAL,behavior=MonsterBehavior.RANGED,range=5),
        MonsterDef('O',"ogre",30,7,18,4,4,0xFFC5E1A5.toInt(),"🗿",Faction.HORDE,slow=true),
        MonsterDef('c',"crocodile",18,6,16,4,5,0xFF558B2F.toInt(),"🐊",Faction.WILD,aquatic=true,ambushRange=3),
        MonsterDef('Q',"kraken",26,6,20,4,4,0xFF7986CB.toInt(),"🦑",Faction.WILD,aquatic=true),
        MonsterDef('N',"necromancer",18,5,24,5,4,0xFF9575CD.toInt(),"⚰️",Faction.UNDEAD,behavior=MonsterBehavior.SUMMONER,range=3),
        MonsterDef('T',"troll",30,8,25,5,4,0xFF7CB342.toInt(),"👾",Faction.NONE,behavior=MonsterBehavior.REGENERATOR),
        MonsterDef('G',"golem",34,7,24,5,4,0xFF90A4AE.toInt(),"🤖",Faction.NONE,armored=2,slow=true),
        MonsterDef('J',"djinn",16,6,22,5,4,0xFF4DD0E1.toInt(),"🧞",Faction.INFERNAL,blinker=true),
        MonsterDef('E',"lava elemental",22,7,26,6,4,0xFFFF5722.toInt(),"🌋",Faction.NONE,lavaImmune=true),
        MonsterDef('V',"vampire",24,8,30,6,3,0xFFE91E63.toInt(),"🧛",Faction.UNDEAD,lifesteal=true),
        MonsterDef('Y',"wyvern",20,7,28,6,3,0xFFFFB300.toInt(),"🐲",Faction.INFERNAL,behavior=MonsterBehavior.RANGED,range=3,flier=true),
        MonsterDef('X',"wraith",24,9,28,6,3,0xFFB39DDB.toInt(),"👻",Faction.UNDEAD,behavior=MonsterBehavior.GHOST),
        MonsterDef('L',"lich",26,9,40,7,3,0xFF7E57C2.toInt(),"☠️",Faction.UNDEAD,behavior=MonsterBehavior.RANGED,range=5),
        MonsterDef('I',"devil",36,10,50,7,2,0xFFD32F2F.toInt(),"😈",Faction.INFERNAL),
        MonsterDef('D',"dragon",45,12,60,7,2,0xFFEF5350.toInt(),"🐉",Faction.INFERNAL))
    val skeleton:MonsterDef=defs.first{it.name=="skeleton"}
    fun byName(n:String):MonsterDef=defs.first{it.name==n}
    fun poolFor(depth:Int)=defs.filter{it.minDepth<=depth}
    fun pick(depth:Int,rng:Random):MonsterDef{
        val pool=poolFor(depth);val total=pool.sumOf{it.weight}
        var roll=rng.nextInt(total)
        for(d in pool){roll-=d.weight;if(roll<0)return d}
        return pool.last()
    }
}
