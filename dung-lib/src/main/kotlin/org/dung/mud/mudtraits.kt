package org.dung.mud

import com.fasterxml.jackson.databind.JsonNode
import org.dung.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.*

/**
 * Traits for muds.
 */
val mudTraitTypes = TraitTypeMaker().add(commonTraitTypes.traits)
val nameTrait = mudTraitTypes.newTrait("name", { "Unnamed" }, { node -> node.valueString() })
val descriptionTrait = mudTraitTypes.newTrait("description", { "Undescribed"}, { node -> node.valueString() })
val locationTrait = TraitType("location", { 0 }, { node -> node.valueInt() })
fun EntityBuilder.setOrClearLocation(location: Int?): EntityBuilder {
    if (location == null) {
        return  remove(locationTrait)
    }
    return set(locationTrait, location)
}

data class EntityScheduleEntry(val executeAt: LocalDateTime, val commandToRun: WorldEntityCommand<MudWorldFrame>, val nextEntries: ()->Array<EntityScheduleEntry>)
data class EntityScheduler(val schedule: List<EntityScheduleEntry> = listOf()) : TickableTrait<MudWorldFrame> {
    override fun update(frfame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {

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
val schedulerTrait = TraitType("scheduler", { EntityScheduler() }, { node -> null })// TODO

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

val echoTrait = TraitType("echo", { "echo message" }, { node -> node.valueString() })
fun EntityBuilder.echoEvent(message: String, location: Int) = set(echoTrait, message)
        .set(lifetimeTrait, Lifetime(1))
        .set(locationTrait, location)

val playerTrait = TraitType("isHumanPlayer", { true }, { node -> node.valueBoolean() })
fun EntityBuilder.setHumanPlayer() = set(playerTrait, true)
fun Entity.isHumanPlayer() = get(playerTrait)?:false

val lastChattedTrait = mudTraitTypes.newTrait("lastChatted", { LocalDateTime.now() }, { node -> node.valueTimestamp() })
class FlavourNoiseTrait(messages: Array<String>, minDelay: TemporalAmount = Duration.ofSeconds(10), maxDelay: TemporalAmount = Duration.ofSeconds(20)) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        val lastChattedTimestamp = entity.get(lastChattedTrait)
        if (lastChattedTimestamp == null) {
            return listOf(command({ frame, nextFrame -> nextFrame.updateEntity()}))
        }
    }
}
val randomFlavourNoiseTrait = mudTraitTypes.newTrait("randomFlavourNoise", { })

fun EntityBuilder.randomFlavourNoise(entityGen: EntityGenerator, messages: Array<(Entity)->String>, minDelay: TemporalAmount = Duration.ofSeconds(10), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, mudEntityCommand({ entity, currentFrame, nextFrame ->
        val message = messages[rnd.nextInt(messages.size)](entity)
        nextFrame.addEntity(nextFrame.newEntity().echoEvent(message, entity.get(locationTrait)?:-1).build())
    }))
}

fun EntityBuilder.randomlyChatty(minDelay: TemporalAmount = Duration.ofSeconds(5), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, mudEntityCommand({ entity, currentFrame, nextFrame ->
        if (entity.get(attackFocusTrait) != null) {
            return@mudEntityCommand
        }
        val playersInLocation = currentFrame.getEntitiesInLocation(entity.get(locationTrait) ?: -1).filter { it.isHumanPlayer() }
        if (!playersInLocation.isEmpty()) {
            val playerToChatTo = playersInLocation[rnd.nextInt(playersInLocation.size)]
            nextFrame.updateEntity(playerToChatTo.id, { sendSignal(ChatSignal(entity, "Hello ${playerToChatTo.get(nameTrait)}")) })
        }
    }))
}

data class Lifetime(val ticksLeftToLive: Int): TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
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
val lifetimeTrait = mudTraitTypes.newTrait("lifetime", { Lifetime(1) }, { node -> node.valueInt()?.let { Lifetime(it) } })


enum class SignalHandlerPrecedence {
    Critical, Important, Normal, Low
}
interface SignalProcessor {
    val precedence: SignalHandlerPrecedence
    fun process(frame: WorldFrame, entity: Entity, signal: Signal): Signal
}
interface SignalTransformer {
    val precedence: SignalHandlerPrecedence
    fun transform(entity: Entity, signal: Signal): List<WorldCommand<MudWorldFrame>>
}

interface Signal
data class SignalQueue(val signals: Array<Signal>) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        if (signals.isEmpty()) {
            return listOf()
        }
        val signalProcessors = entity.traitsOf<SignalProcessor>()
        val signalTransformers = entity.traitsOf<SignalTransformer>()
        return signals.flatMap { signal ->
            val finalSignal = signalProcessors.fold(signal, { lastSignal, signalProcessor -> signalProcessor.process(frame, entity, lastSignal) })
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
val signalQueueTrait = mudTraitTypes.newTrait("signals", { SignalQueue(arrayOf()) }, { node -> null }) // TODO
fun EntityBuilder.sendSignal(signal: Signal) = set(signalQueueTrait, getOrCreate(signalQueueTrait).addSignal(signal))
fun sendSignal(toEntityId: Int, signal: Signal): WorldCommand<MudWorldFrame> {
    return mudCommand({ currentFrame, nextFrame -> nextFrame.updateEntity(toEntityId, { sendSignal(signal) })})
}

class DeadSignal() : Signal
data class DamageSignal(val attackerEntity: Entity, val weaponEntity: Entity?, val damage: Int) : Signal
data class ChatSignal(val chattyEntity: Entity, val message: String) : Signal

val wieldTrait = mudTraitTypes.newTrait<Int?>("wield", { null }, { node -> node.valueInt() })
data class WieldSignal(val itemId: Int) : Signal
fun EntityBuilder.wield(itemId: Int): EntityBuilder {
    return set(wieldTrait, itemId)
            .sendSignal(WieldSignal(itemId))
}
fun EntityBuilder.unwield(): EntityBuilder {
    return set(wieldTrait, null)
}

val deadTrait = mudTraitTypes.newTrait("dead", { false }, { node -> node.valueBoolean() })


data class Inventory(val items: Array<Entity>) {
    fun get(id: Int) = items.firstOrNull { it.id == id }
    fun add(entity: Entity) = copy(items = items + entity)
}
val inventoryTrait = mudTraitTypes.newTrait("inventory", { Inventory(arrayOf()) }, { node, entityResolver -> Inventory(entityResolver.loadEntities(node["items"]))})
fun EntityBuilder.addToInventory(entity: Entity) = set(inventoryTrait, getOrCreate(inventoryTrait).add(entity))
fun EntityBuilder.pickUpToInventory(frameBuilder: WorldFrameBuilder<MudWorldFrame>, entityId: Int): EntityBuilder {
    val entityToPickUp = frameBuilder.entitiesById[entityId]
    if (entityToPickUp == null) {
        return this
    }
    frameBuilder.removeEntity(entityId)
    entityToPickUp.remove(locationTrait)
    frameBuilder.addEntity(frameBuilder.newEntity().echoEvent("${get(nameTrait)} picks up ${entityToPickUp.get(nameTrait)}", get(locationTrait)?:-1).build())
    var eb = this
    if (eb.get(wieldTrait) == null) {
        eb = eb.wield(entityToPickUp.id)
    }
    return eb.set(inventoryTrait, eb.getOrCreate(inventoryTrait).add(entityToPickUp.build()))
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
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
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

                if (get(locationTrait)?:-1 != entity.get(locationTrait)) {
                    return this
                }
                if (get(deadTrait)?:false) {
                    return clearAttackFocus()
                }
                return sendSignal(DamageSignal(entity, null, 1))
            })
        })
        )
    }
}
val attackFocusTrait = mudTraitTypes.newTrait<AttackFocus>("attackFocus",
        { AttackFocus(null, LocalDateTime.now()) },
        { node -> AttackFocus(node.valueInt("entityId"), node.valueTimestamp("lastAttackTimestamp")?:LocalDateTime.now())})
fun EntityBuilder.setAttackFocus(entityId: Int) = set(attackFocusTrait, AttackFocus(entityId, LocalDateTime.now()))
fun EntityBuilder.clearAttackFocus() = set(attackFocusTrait, AttackFocus(null, LocalDateTime.now()))

val healthTrait = mudTraitTypes.newTrait("health", {0}, { node -> node.valueInt()?:0 })
fun EntityBuilder.updateHealth(delta: Int): EntityBuilder {
    val health = get(healthTrait)
    if (health != null) {
        return set(healthTrait, health + delta)
    }
    return this
}

val damageMonitorTrait = mudTraitTypes.newTrait("damageMonitor", { DamageMonitor() }, { node -> null })

class DamageMonitor() : SignalTransformer {
    override val precedence: SignalHandlerPrecedence = SignalHandlerPrecedence.Low
    override fun transform(entity: Entity, signal: Signal): List<WorldCommand<MudWorldFrame>> {
        if (signal !is DamageSignal) {
            return listOf()
        }
        return listOf(mudCommand { currentFrame, nextFrame ->
            nextFrame.updateEntity(entity.id, fun EntityBuilder.():EntityBuilder {
                var eb = this
                var health = eb.get(healthTrait)
                if (health != null) {
                    health -= 1
                    eb = eb.set(healthTrait, health)
                    if (health <= 0) {
                        eb = eb.set(deadTrait, true)
                                .sendSignal(DeadSignal())
                        //.set(removeFromWorldTrait, true)
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(nameTrait)} killed ${get(nameTrait)}!", eb.get(locationTrait)?:-1).build())
                    }
                    else {
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(nameTrait)} hits ${get(nameTrait)} for ${signal.damage} damage", eb.get(locationTrait)?:-1).build())
                    }
                }
                return eb
            })
        })
    }

}

enum class Gender {
    Male, Female, Object
}
val genderTrait = mudTraitTypes.newTrait("gender", { Gender.Male }, { node -> Gender.valueOf(node.valueString()?:throw RuntimeException("Expected value")) })

interface AffordanceTrait {
    fun matches(verb: String): Boolean
    fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity)
}
val affordanceGetPicksUpTrait = mudTraitTypes.newTrait<AffordanceTrait>("affordanceGet", { object : AffordanceTrait {
        override fun matches(verb: String): Boolean = (verb == "GET") || (verb == "PICKUP")
        override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
    }
}}, { node -> null })

data class GoAffordanceTrait(val exitNames: Array<String>, val destinationId: Int) : AffordanceTrait {

    constructor(exitNames: Array<String>, node: JsonNode, entityResolver: JsonEntityResolver) : this(exitNames, entityResolver.entityId(node, "destination")) {
    }

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
    }

    override fun matches(verb: String): Boolean = verb in exitNames
}
val affordanceGoNorthTrait = mudTraitTypes.newTraitWithNoDefault("affordanceGoNorth", { node, entityResolver -> GoAffordanceTrait(arrayOf("north"), node, entityResolver)})
val affordanceGoSouthTrait = mudTraitTypes.newTraitWithNoDefault("affordanceGoSouth", { node, entityResolver -> GoAffordanceTrait(arrayOf("south"), node, entityResolver)})
val affordanceGoEastTrait = mudTraitTypes.newTraitWithNoDefault("affordanceGoEast", { node, entityResolver -> GoAffordanceTrait(arrayOf("east"), node, entityResolver)})
val affordanceGoWestTrait = mudTraitTypes.newTraitWithNoDefault("affordanceGoWest", { node, entityResolver -> GoAffordanceTrait(arrayOf("west"), node, entityResolver)})
val affordanceGoTrait = mudTraitTypes.newTraitWithNoDefault("affordanceGo", { node, entityResolver -> GoAffordanceTrait(arrayOf(node.valueString("exitName")?:throw RuntimeException("Expected exitName value")), node, entityResolver)})

class AttackAffordanceTrait() : AffordanceTrait {
    override fun matches(verb: String): Boolean = verb == "ATTACK"

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { setAttackFocus(verbObject.id) })
        nextFrame.updateEntity(verbObject.id, { setAttackFocus(source.id) })
    }

}
val affordanceAttackTrait = mudTraitTypes.newTrait("affordanceAttack", { AttackAffordanceTrait() }, { node -> null })