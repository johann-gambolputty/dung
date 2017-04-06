package org.dung.mud

import org.dung.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class AttackFocus(val targetEntityId: Int?, val lastAttackTimestamp: LocalDateTime) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        if (targetEntityId == null) {
            return listOf()
        }
        if (ChronoUnit.MILLIS.between(lastAttackTimestamp, updateTimestamp) < 1000) {
            return listOf()
        }
        return listOf({ _, nextFrame ->
            nextFrame.updateEntity(entity.id, fun EntityBuilder.(): EntityBuilder {
                val target = nextFrame.entitiesById[targetEntityId]
                if (target == null) {
                    return clearAttackFocus()
                }
                if (get(locationTrait) != target.get(locationTrait)) {
                    return clearAttackFocus()
                }
                if (target.get(deadTrait)?:false) {
                    return clearAttackFocus()
                }

                return set(attackFocusTrait, AttackFocus(targetEntityId, updateTimestamp))
            })
        }, { _, nextFrame ->
            nextFrame.updateEntity(targetEntityId, fun EntityBuilder.(): EntityBuilder {
                val entityInNextFrame = nextFrame.entitiesById[entity.id]
                if (entityInNextFrame == null) {
                    return this
                }
                val attackRoll = rnd.nextDouble()
                if (attackRoll > probabilityToHit(entityInNextFrame, this)) {
                    return this
                }
                var critMultiplier = 1.0;
                var criticalHit = false
                if (attackRoll < probabilityToCrit(entityInNextFrame, this)) {
                    critMultiplier = 2.0
                    criticalHit = true
                }
                val weapon = entityInNextFrame.wieldedWeapon()
                var weaponDamage = 1
                if (weapon != null) {
                    weaponDamage = weapon.get(damageTrait)?.invoke()?:1
                }

                return sendSignal(DamageSignal(entity, weapon, criticalHit, (weaponDamage * critMultiplier).toInt()))
            })
        })
    }
}

val attackFocusTrait = mudTraitTypes.newTrait<AttackFocus>("attackFocus",
        { AttackFocus(null, LocalDateTime.now()) },
        { node -> AttackFocus(node.valueInt("entityId"), node.valueTimestamp("lastAttackTimestamp")?: LocalDateTime.now())})
fun EntityBuilder.setAttackFocus(entityId: Int) = set(attackFocusTrait, AttackFocus(entityId, LocalDateTime.now()))
fun EntityBuilder.clearAttackFocus() = set(attackFocusTrait, AttackFocus(null, LocalDateTime.now()))

typealias DamageFun = ()->Int

fun doesFixedDamage(damage: Int): DamageFun = { damage }
fun doesDamageInRange(minDamage: Int, maxDamage: Int): DamageFun = { rnd.nextInt(maxDamage - minDamage) + minDamage }

val damageTrait = mudTraitTypes.newTrait<DamageFun>("damage", { doesFixedDamage(1) }, no_json())
val skillTrait = mudTraitTypes.newTrait<Int>("skill", { 0 }, no_json())
val critTrait = mudTraitTypes.newTrait<Int>("crit", { 0 }, no_json())

fun probabilityToHit(attacker: TraitBased, target: TraitBased): Double {

    val attackerSkill = attacker.get(skillTrait, 0)
    val targetSkill = target.get(skillTrait, 0)

    val diff = (attackerSkill - targetSkill)

    return 0.5 + ((Math.min(diff, 10) / 10.0) * 0.49)
}

fun probabilityToCrit(attacker: TraitBased, target: TraitBased): Double {

    val critAbility = Math.max(attacker.get(critTrait, 0), 1)
    return critAbility / 100.0
}
