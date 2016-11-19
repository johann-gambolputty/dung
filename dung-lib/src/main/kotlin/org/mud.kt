package org

import rx.Observable
import rx.subjects.PublishSubject
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val locationTrait = TraitKeyData("location", { 0 })
val nameTrait = TraitKeyData("name", { "Bob" })
val descriptionTrait = TraitKeyData("name", { "Description of Bob" })
val inventoryTrait = TraitKeyData("inventory", { arrayOf<EntityData>() })
val attackFocusTrait = TraitKeyData("attackFocus", { setOf<Int>() })
val holdingTrait = TraitKeyData("holding", { arrayOf<EntityData>() })
val attacksWhenTouched = TraitKeyData("attacksWhenTouched", { true })
val touchEventTrait = TraitKeyData<TouchEvent>("touchEvent", { throw RuntimeException("No default touch event trait") })
data class TouchEvent(val idOfTouchedEntity: Int, val idOfTouchingEntity: Int)
val framePersistentTrait = TraitKeyData<TickableTrait>("framePersistent", {
    object:TickableTrait {
        override fun tick(entity: EntityData, lastFrame: WorldFrame, frame: WorldFrameBuilder) = entity
    }
})
data class DamageEvent(val idOfCause: Int, val idOfEntityDamaged: Int)
val damageEventTrait = TraitKeyData<DamageEvent>("damageEvent", { throw RuntimeException("No default damage event trait") })
val attacksWhenDamagedTrait = TraitKeyData<TickableTrait>("attacksWhenAttacked", {
    object: TickableTrait {
        override fun tick(entity: EntityData, lastFrame: WorldFrame, frame: WorldFrameBuilder): EntityData? {
            if (entity.trait(attackFocusTrait) != null) {
                return entity;
            }
            val firstDamageEvent = lastFrame.entities.filterIsInstance<DamageEvent>().filter { it.idOfEntityDamaged == entity.id }.firstOrNull()
            if (firstDamageEvent == null) {
                return entity;
            }
            return entity.addTrait(attackFocusTrait, setOf(firstDamageEvent.idOfCause))
        }
    }
})

fun sword(entityIdGenerator: ()->Int) =
        EntityData(entityIdGenerator())
                .addTrait(nameTrait, "Sword")
                .addTrait(descriptionTrait, "A rusty, blunt, generally useless hunk of junk")

fun enemy(entityIdGenerator: ()->Int, locationId: Int, name: String): EntityData {
    val weapon = sword(entityIdGenerator)
    return EntityData(entityIdGenerator())
            .addTrait(locationTrait, locationId)
            .addTrait(nameTrait, name)
            .addTrait(inventoryTrait, arrayOf(weapon))
            .addTrait(holdingTrait, arrayOf(weapon))
            .addTrait(attacksWhenDamagedTrait)

}

fun player(entityIdGenerator: () -> Int, locationId: Int, name: String) =
        EntityData(entityIdGenerator())
            .addTrait(locationTrait, locationId)
            .addTrait(nameTrait, name)

fun main(args: Array<String>) {
    val idGen = AtomicInteger()
    val player = player( { idGen.andIncrement }, 0, "Player")
    val bob = enemy( { idGen.andIncrement }, 0, "Bob")
    val initialFrame = WorldFrame(arrayOf(player, bob))
    val commandQueue: BlockingQueue<EntityData> = ArrayBlockingQueue<EntityData>(1000)
    var quit = false
    val thread = Thread({
        var lastFrame: WorldFrame
        var currentFrame = initialFrame
        try {
            while (!quit) {
                //  Poll command queue
                lastFrame = currentFrame
                currentFrame = currentFrame.makeNextFrame(commandQueue.poll(5000L, TimeUnit.MILLISECONDS)?.let { arrayOf(it) } ?: arrayOf())

                val lastFramePlayer = lastFrame.getEntity(player.id)
                val curFramePlayer = currentFrame.getEntity(player.id)
                if (lastFramePlayer?.trait(locationTrait) != curFramePlayer?.trait(locationTrait)) {
                    println("Player moved to " + curFramePlayer?.trait(locationTrait))
                }
            }
        }
        catch (e: Exception) {
            quit = true
        }
    })
    thread.isDaemon = true
    thread.start()
    while (!quit) {
        val line = readLine()
        when (line) {
            "n" -> commandQueue.add(EntityData(idGen.andIncrement))
            "q" -> quit = true
            else -> println(line)
        }
    }
}