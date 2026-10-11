package com.example.roguelikeai.ai
enum class Personality(val emoji: String, val lootW: Double, val combatW: Double, val exploreW: Double) {
    NEUTRAL("😐", 1.0, 1.0, 1.0), GREEDY("💰", 2.0, 0.6, 0.8), PACIFIST("🕊️", 1.0, 0.1, 1.4), BERSERKER("😡", 0.6, 2.2, 0.7)
}
