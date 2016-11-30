package org.dung.mud

import org.dung.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.*

/**
 * Traits for muds.
 */


val nameTrait = TraitType("name", { "Unnamed" })
val descriptionTrait = TraitType("description", { "Undescribed"})
val locationTrait = TraitType("location", { 0 })
fun EntityBuilder.setOrClearLocation(location: Int?): EntityBuilder {
    if (location == null) {
        return  remove(locationTrait)
    }
    return set(locationTrait, location)
}
data class LocationLink(val destination: Int, val name: String, val commandBinding: Array<String>)

val locationLinkTrait = TraitType("locationLink", { arrayOf<LocationLink>() })

fun EntityBuilder.addLocationLink(link: LocationLink) = set(locationLinkTrait, (get(locationLinkTrait)?: arrayOf()) + link)

fun north(id: Int) = LocationLink(id, "north", arrayOf("n", "north"))
fun east(id: Int) = LocationLink(id, "east", arrayOf("e", "east"))
fun south(id: Int) = LocationLink(id, "south", arrayOf("s", "south"))
fun west(id: Int) = LocationLink(id, "west", arrayOf("w", "west"))
fun down(id: Int) = LocationLink(id, "down", arrayOf("d", "down"))
fun up(id: Int) = LocationLink(id, "up", arrayOf("u", "up"))

data class EntityScheduleEntry(val executeAt: LocalDateTime, val commandToRun: WorldEntityCommand<MudWorldFrame>, val nextEntries: ()->Array<EntityScheduleEntry>)
data class EntityScheduler(val schedule: List<EntityScheduleEntry> = listOf()) : TickableTrait<MudWorldFrame> {
    override fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {

        val newSchedule = mutableListOf<EntityScheduleEntry>()
        val commands = mutableListOf<WorldCommand<MudWorldFrame>>()
        for (entry in schedule) {
            if (entry.executeAt > updateTimestamp) {
                newSchedule.add(entry)
            }
            else {
                newSchedule.addAll(entry.nextEntries())
                if (commands.isEmpty()) {
                    commands.add(command { currentFrame, nextFrame ->
                        nextFrame.updateEntity(entity.id, { set(schedulerTrait, EntityScheduler(newSchedule) )})
                    })
                }
                commands.add(command({ frame, nextFrame->entry.commandToRun.run(entity, frame, nextFrame)}))
            }
        }

        return commands
    }
    fun addEntry(entry: EntityScheduleEntry) = copy(schedule = schedule + entry)
}
val schedulerTrait = TraitType("scheduler", { EntityScheduler() })

fun EntityBuilder.addToSchedule(entry: EntityScheduleEntry): EntityBuilder {
    return set(schedulerTrait, getOrCreate(schedulerTrait).addEntry(entry))
}
fun EntityBuilder.addToSchedule(delay: TemporalAmount, command: WorldEntityCommand<MudWorldFrame>): EntityBuilder {
    return addToSchedule(EntityScheduleEntry(LocalDateTime.now().plus(delay), command, { arrayOf() }))
}
fun repeatingSheduleEntry(nextAt: ()-> LocalDateTime, command: WorldEntityCommand<MudWorldFrame>): EntityScheduleEntry {
    return EntityScheduleEntry(nextAt(), command, { arrayOf(repeatingSheduleEntry(nextAt, command)) })
}
fun EntityBuilder.repeatAtRandom(minDelay: TemporalAmount, maxDelay: TemporalAmount, command: WorldEntityCommand<MudWorldFrame>): EntityBuilder {
    return addToSchedule(repeatingSheduleEntry({ randomTimeFromNow(minDelay, maxDelay) }, command))
}
val rnd = Random(-1)
fun randomTimeFromNow(minDelay: TemporalAmount, maxDelay: TemporalAmount): LocalDateTime {
    return LocalDateTime.now() + minDelay // TODO
}

val echoTrait = TraitType("echo", { "echo message" })
fun echoEvent(entityGen: EntityGenerator, message: String, location: Int) = entityGen.newEntity()
        .set(echoTrait, message)
        .set(lifetimeTrait, Lifetime(1))
        .set(locationTrait, location)
        .build()

val playerTrait = TraitType("isHumanPlayer", { true })
fun EntityBuilder.setHumanPlayer() = set(playerTrait, true)
fun Entity.isHumanPlayer() = get(playerTrait)?:false

fun EntityBuilder.randomFlavourNoise(entityGen: EntityGenerator, messages: Array<(Entity)->String>, minDelay: TemporalAmount = Duration.ofSeconds(5), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, mudEntityCommand({ entity, currentFrame, nextFrame ->
        val message = messages[rnd.nextInt(messages.size)](entity)
        nextFrame.addEntity(echoEvent(entityGen, message, entity.get(locationTrait)?:-1))
    }))
}

fun EntityBuilder.randomlyChatty(minDelay: TemporalAmount = Duration.ofSeconds(5), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, mudEntityCommand({ entity, currentFrame, nextFrame ->
        val playersInLocation = currentFrame.getEntitiesInLocation(entity.get(locationTrait) ?: -1).filter { it.isHumanPlayer() }
        if (!playersInLocation.isEmpty()) {
            val playerToChatTo = playersInLocation[rnd.nextInt(playersInLocation.size)]
            nextFrame.updateEntity(playerToChatTo.id, { sendSignal(ChatSignal(entity, "Hello ${playerToChatTo.get(nameTrait)}")) })
        }
    }))
}

data class Lifetime(val ticksLeftToLive: Int): TickableTrait<MudWorldFrame> {
    override fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        return listOf(mudCommand({ currentFrame, nextFrame ->
            if (ticksLeftToLive == 1) {
                nextFrame.removeEntity(entity.id)
            }
            else {
                nextFrame.updateEntity(entity.id, { set(lifetimeTrait, Lifetime(ticksLeftToLive - 1)) })
            }
        }))
    }

}
val lifetimeTrait = TraitType("lifetime", { Lifetime(1) })


enum class SignalProcessorPrecedence {
    Critical, Important, Normal, Low
}
data class SignalProcessor(val precedence: SignalProcessorPrecedence, val process: (signal: Signal)->Signal)
val signalProcessorsTrait = TraitType("signalProcessors", { arrayOf<SignalProcessor>()})
fun EntityBuilder.addSignalProcessor(signalProcessor: SignalProcessor): EntityBuilder {
    val signalProcessors = getOrCreate(signalProcessorsTrait) + signalProcessor
    signalProcessors.sortBy { it.precedence }
    return set(signalProcessorsTrait, signalProcessors)
}

enum class SignalTransformerPrecedence {
    Critical, Important, Normal, Low
}
data class SignalTransformer(val precedence: SignalTransformerPrecedence, val transform: Entity.(signal: Signal)->List<WorldCommand<MudWorldFrame>>)
val signalTransformersTrait = TraitType("signalTransformers", { arrayOf<SignalTransformer>() })
fun EntityBuilder.addSignalTransformer(signalTransformer: SignalTransformer): EntityBuilder {
    val signalTransformers = getOrCreate(signalTransformersTrait) + signalTransformer
    signalTransformers.sortBy { it.precedence }
    return set(signalTransformersTrait, signalTransformers)
}

interface Signal
data class SignalQueue(val signals: Array<Signal>) : TickableTrait<MudWorldFrame> {
    override fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        if (signals.isEmpty()) {
            return listOf()
        }
        val signalProcessors = entity.get(signalProcessorsTrait)?:arrayOf()
        val signalTransformers = entity.get(signalTransformersTrait)?:arrayOf()
        return signals.flatMap { signal ->
            val finalSignal = signalProcessors.fold(signal, { lastSignal, signalProcessor -> signalProcessor.process(lastSignal) })
            signalTransformers.flatMap { it.transform(entity, finalSignal)} + mudCommand({ currentFrame, nextFrame ->
                nextFrame.updateEntity(entity.id, {
                    val existingSignals = getOrCreate(signalQueueTrait)
                    val existingSignalsWithProcessedSignalsRemoved = existingSignals.signals.filter { it !in signals }.toTypedArray()
                    set(signalQueueTrait, existingSignals.copy(signals = existingSignalsWithProcessedSignalsRemoved))
                })
            })
        }
    }

    fun addSignal(signal: Signal) = copy(signals = signals + signal)
}
val signalQueueTrait = TraitType("signals", { SignalQueue(arrayOf()) })
fun EntityBuilder.sendSignal(signal: Signal) = set(signalQueueTrait, getOrCreate(signalQueueTrait).addSignal(signal))
fun sendSignal(toEntityId: Int, signal: Signal): WorldCommand<MudWorldFrame> {
    return mudCommand({ currentFrame, nextFrame -> nextFrame.updateEntity(toEntityId, { sendSignal(signal) })})
}

data class DamageSignal(val attackerEntity: Entity, val weaponEntity: Entity?, val damage: Int) : Signal
data class ChatSignal(val chattyEntity: Entity, val message: String) : Signal

val wieldTrait = TraitType<Int?>("wield", { null })

data class Inventory(val items: Array<Entity>) {
    fun get(id: Int) = items.firstOrNull { it.id == id }
    fun add(entity: Entity) = copy(items = items + entity)
}
val inventoryTrait = TraitType("inventory", { Inventory(arrayOf()) })
fun EntityBuilder.addToInventory(entity: Entity) = set(inventoryTrait, getOrCreate(inventoryTrait).add(entity))
fun EntityBuilder.pickUpToInventory(frameBuilder: WorldFrameBuilder<MudWorldFrame>, entityId: Int): EntityBuilder {
    val entityToPickUp = frameBuilder.entitiesById[entityId]
    if (entityToPickUp == null) {
        return this
    }
    frameBuilder.removeEntity(entityId)
    entityToPickUp.remove(locationTrait)
    return set(inventoryTrait, getOrCreate(inventoryTrait).add(entityToPickUp.build()))
}

fun EntityBuilder.dropFromInventory(frameBuilder: WorldFrameBuilder<MudWorldFrame>, entityId: Int): EntityBuilder {
    val inventory = get(inventoryTrait)
    if (inventory == null) {
        return this
    }
    val itemInInventory = inventory.get(entityId) ?: return this
    val location = get(locationTrait)
    if (location != null) {
        frameBuilder.addEntity(itemInInventory.modify().set(locationTrait, location).build())
    }
    else {
        frameBuilder.addEntity(itemInInventory)
    }
    return set(inventoryTrait, inventory.copy(items = inventory.items.filter { it.id != entityId }.toTypedArray()))
}

data class AttackFocus(val entityId: Int?, val lastAttackTimestamp: LocalDateTime) : TickableTrait<MudWorldFrame> {
    override fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        if (entityId == null) {
            return listOf()
        }
        if (ChronoUnit.MILLIS.between(lastAttackTimestamp, updateTimestamp) < 1000) {
            return listOf()
        }
        return listOf(command({ currentFrame, nextFrame ->
            nextFrame.updateEntity(entity.id, { set(attackFocusTrait, AttackFocus(entityId, updateTimestamp)) })
        }), command({ currentFrame, nextFrame ->
            nextFrame.updateEntity(entityId, fun EntityBuilder.(): EntityBuilder {
                if (get(locationTrait)?:-1 == entity.get(locationTrait)) {
                    return sendSignal(DamageSignal(entity, null, 1))
                }
                return this
            })
        })
        )
    }
}
val attackFocusTrait = TraitType<AttackFocus>("attackFocus", { AttackFocus(null, LocalDateTime.now()) })
fun EntityBuilder.setAttackFocus(entityId: Int) = set(attackFocusTrait, AttackFocus(entityId, LocalDateTime.now()))
fun EntityBuilder.clearAttackFocus() = set(attackFocusTrait, AttackFocus(null, LocalDateTime.now()))

val healthTrait = TraitType("health", {0})
fun EntityBuilder.updateHealth(delta: Int): EntityBuilder {
    val health = get(healthTrait)
    if (health != null) {
        return set(healthTrait, health + delta)
    }
    return this
}

val damageMonitorTrait = TraitType("damageMonitor", {
    SignalTransformer(SignalTransformerPrecedence.Normal, fun Entity.(signal: Signal): List<WorldCommand<MudWorldFrame>> {
        if (signal is DamageSignal) {
            return listOf(mudCommand { currentFrame, nextFrame ->
                nextFrame.updateEntity(id, { updateHealth(-1) })
            })
        }
        return listOf()
    })
})