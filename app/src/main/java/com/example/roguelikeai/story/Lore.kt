package com.example.roguelikeai.story
import com.example.roguelikeai.game.Faction; import kotlin.random.Random
class NameGen(private val rng: Random) {
    private val onsets = listOf("K","G","Th","V","Dr","M","S","R","B","Z","F","Gr","Sk","N","Ash","Vor","Mal","Eld","Uth","Gor","Bel","Mor")
    private val middles = listOf("a","o","u","e","i","ar","or","ei","yr","al","en","ir","ol")
    private val codas = listOf("n","r","s","th","k","nd","ld","m","g","x","ion","ath","eth","dor","wyn")
    fun name(minSyl: Int = 2, maxSyl: Int = 3): String { val syl = rng.nextInt(minSyl, maxSyl + 1); val sb = StringBuilder(onsets.random(rng)).append(middles.random(rng))
        for (i in 1 until syl) { sb.append(onsets.random(rng).lowercase()); sb.append(middles.random(rng)) }; if (rng.nextBoolean()) sb.append(codas.random(rng)); return sb.toString() }
    fun place(): String = name(2, 3) + listOf("heim", "gard", "march", "fell", "reach", "hold", "waste", "deep").random(rng)
}
data class WorldLore(val worldName: String, val deity: String, val deityDomain: String, val cataclysm: String, val dungeonName: String, val dungeonOrigin: String, val amuletName: String, val amuletPower: String, val prophecy: String, val factionLegends: Map<Faction, String>) {
    fun title() = "🌍 The Legend of $worldName"
    fun render(): String = buildString { append("🌍 THE LEGEND OF ").append(worldName.uppercase()).append("\nIn the first age, $deity, $deityDomain, sang the world into being.\nThen came $cataclysm, and the light was broken.\nBeneath the scar, $dungeonName was carved — $dungeonOrigin\nThere lies the $amuletName, $amuletPower.\nThe prophecy whispers: \"$prophecy\"\n\n── The Factions ──\n")
        for ((f, l) in factionLegends) append("${f.emoji} ${f.name}: $l\n") }
}
data class HeroStory(val name: String, val epithet: String, val homeland: String, val motive: String, val flaw: String) {
    fun render(): String = "🧑 $name, $epithet, of $homeland.\n   Motive: $motive\n   Flaw: $flaw"
}
object LoreGenerator {
    fun world(seed: Long): WorldLore { val rng = Random(seed); val ng = NameGen(rng)
        val worldName = ng.name() + " " + listOf("the Sundered", "of a Thousand Spires", "the Hollow", "Unending", "the Last").random(rng); val deity = ng.name()
        val deityDomain = listOf("weaver of dawn", "keeper of oaths", "the Silent Flame", "mother of tides", "architect of stars").random(rng)
        val cataclysm = listOf("the Ashfall", "the Betrayal of Gods", "the Long Winter", "the Star-Plague", "the Breaking of the Moon").random(rng)
        val dungeonName = "the ${ng.name()} ${listOf("Vault", "Deep", "Labyrinth", "Sepulcher", "Maw").random(rng)}"
        val dungeonOrigin = listOf("grown from the bones of a fallen god.", "dug by those fleeing the surface's ruin.", "a prison built to hold something that must never wake.", "a wound in the world that never healed.").random(rng)
        val amuletName = "Amulet of ${ng.name()}"; val amuletPower = listOf("the last ember of the first sunrise", "a shard of the deity's own heart", "the key to mending the broken sky", "a bargain sealed in starlight").random(rng)
        val prophecy = listOf("only one who seeks nothing shall claim it", "the unworthy shall be devoured by their wanting", "it chooses the brave and abandons the wise", "it will be carried up by the smallest of hearts").random(rng)
        val fl = mapOf(Faction.WILD to "the old beasts who remember the world before the $cataclysm.", Faction.HORDE to "the children of ruin, forging a kingdom in the dark.", Faction.UNDEAD to "those who refused the grave, bound to a will not their own.", Faction.INFERNAL to "sparks that fell when the sky broke, still burning.")
        return WorldLore(worldName, deity, deityDomain, cataclysm, dungeonName, dungeonOrigin, amuletName, amuletPower, prophecy, fl) }
    fun hero(seed: Long): HeroStory { val rng = Random(seed); val ng = NameGen(rng)
        return HeroStory(ng.name(), listOf("the Unbroken", "the Last Candle", "Oathbound", "the Quiet Blade", "the Twice-Born", "the Ashwalker").random(rng), ng.place(),
            listOf("to undo the ${listOf("Ashfall", "curse", "silence").random(rng)} that took their home", "to prove a name the world forgot", "to bring back someone lost below", "to pay a debt written in blood", "because no one else would go").random(rng),
            listOf("cannot leave treasure behind", "hears voices in the walls", "never retreats, even when they should", "trusts too easily", "collects cursed things", "speaks to the dead").random(rng)) }
    fun monsterFlavor(name: String, rng: Random): String = listOf("Legends say the first $name was born of ${listOf("moonlight", "hunger", "a broken vow", "cold iron").random(rng)}.", "Scholars argue whether the $name hunts for ${listOf("sport", "memory", "spite").random(rng)}.", "A $name's ${listOf("howl", "shadow", "gaze").random(rng)} is said to ${listOf("steal warmth", "foretell rain", "curse gold").random(rng)}.", "Old songs warn never to ${listOf("name", "outstare", "pity").random(rng)} a $name.").random(rng)
}
