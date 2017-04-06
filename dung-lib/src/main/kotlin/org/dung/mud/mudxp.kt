package org.dung.mud

import org.dung.Entity
import org.dung.EntityBuilder
import org.dung.no_json


val xpValueTrait = mudTraitTypes.newTrait<Int>("xpValue", { 1 }, no_json())
val currentXpTrait = mudTraitTypes.newTrait<Int>("currentXp", { 0 }, no_json())
val levelTrait = mudTraitTypes.newTrait<Int>("level", { 0 }, no_json())

fun EntityBuilder.youKilledMe(deadEntity: Entity): EntityBuilder {
    val currentXp = get(currentXpTrait)
    if (currentXp != null) {
        deadEntity.get(xpValueTrait)?.let { xp ->
            val killer = this@youKilledMe
            val currentLevel = killer.get(levelTrait)?:0
            val newXp = currentXp + xp
            val levelXpCap = calcLevelXpCap(killer)
            if (newXp > levelXpCap) {
                return@youKilledMe killer.set(currentXpTrait, newXp - levelXpCap).set(levelTrait, )
            }
            return@youKilledMe this@youKilledMe.set(currentXpTrait, currentXp + this)
        }
    }
    return this
}