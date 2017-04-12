package org.dung.mud

import org.dung.Entity
import org.dung.EntityBuilder
import org.dung.no_json


val xpValueTrait = mudTraitTypes.newTrait<Int>("xpValue", { 1 }, no_json())
val currentXpTrait = mudTraitTypes.newTrait<Int>("currentXp", { 0 }, no_json())
val levelTrait = mudTraitTypes.newTrait<Int>("level", { 0 }, no_json())

fun calcLevelXpCap(currentLevel: Int): Int {
    if (currentLevel <= 1) {
        return 5
    }
    val previousLvl = calcLevelXpCap(currentLevel - 1)
    return previousLvl + (previousLvl * 1.1).toInt()
}

fun EntityBuilder.youKilledMe(deadEntity: Entity): EntityBuilder {
    val currentXp = get(currentXpTrait)
    if (currentXp != null) {
        deadEntity.get(xpValueTrait)?.let { xp ->
            val killer = this@youKilledMe
            val newXp = currentXp + xp
            val currentLevel = killer.get(levelTrait)?:0
            val levelXpCap = calcLevelXpCap(currentLevel)
            if (newXp > levelXpCap) {
                return@youKilledMe killer.set(currentXpTrait, newXp).set(levelTrait, currentLevel + 1)
            }
            return@youKilledMe this@youKilledMe.set(currentXpTrait, newXp)
        }
    }
    return this
}