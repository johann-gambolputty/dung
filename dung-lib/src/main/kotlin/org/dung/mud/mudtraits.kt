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
val name = mudTraitTypes.newTrait("name", { "Unnamed" }, { node -> node.valueString()?:"Unnamed" })
fun Entity.name(): String = get(name)?:"Unnamed"
val description = mudTraitTypes.newTrait("description", { "Undescribed" }, { node -> node.valueString()?:"Undescribed" })
fun Entity.description(currentFrame: MudWorldFrame): String {
    val baseDescription = get(description)?:"Undescribed"
    val additionalDescriptions = traitsOf<HasDescription>()
            .sortedBy { it.decriptionOrder }
            .joinToString("\n") { it.description(currentFrame, this) }
    return baseDescription + (if (additionalDescriptions.isEmpty()) "" else "\n") + additionalDescriptions
}
val location = mudTraitTypes.newTrait("location", { 0 }, { node -> node.valueInt() })
fun EntityBuilder.setOrClearLocation(locationId: Int?): EntityBuilder {
    if (locationId == null) {
        return  remove(location)
    }
    return set(location, locationId)
}

val aliases = mudTraitTypes.newTrait<Array<String>>("aliases", { arrayOf() }, no_json())
fun Entity.matchesUcAlias(name: String): Boolean = get(aliases)?.contains(name)?:false

val startingLocation = mudTraitTypes.newTrait("startingLocation", { true },  { node -> null })

data class EntityScheduleEntry(val executeAt: LocalDateTime, val commandToRun: WorldEntityCommand<MudWorldFrame>, val nextEntries: ()->Array<EntityScheduleEntry>)
data class EntityScheduler(val schedule: List<EntityScheduleEntry> = listOf()) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {

        val newSchedule = mutableListOf<EntityScheduleEntry>()
        val commands = mutableListOf<WorldCommand<MudWorldFrame>>()
        for (entry in schedule) {
            if (entry.executeAt > updateTimestamp) {
                newSchedule.add(entry)
            }
            else {
                newSchedule.addAll(entry.nextEntries())
                if (commands.isEmpty()) {
                    commands.add({ _, nextFrame ->
                        nextFrame.updateEntity(entity.id, { set(scheduler, EntityScheduler(newSchedule) )})
                    })
                }
                commands.add(({ frame, nextFrame ->entry.commandToRun(entity, frame, nextFrame)}))
            }
        }

        return commands
    }
    fun addEntry(entry: EntityScheduleEntry) = copy(schedule = schedule + entry)
}
val scheduler = mudTraitTypes.newTrait("scheduler", { EntityScheduler() }, { node -> null })// TODO

fun EntityBuilder.addToSchedule(entry: EntityScheduleEntry): EntityBuilder {
    return set(scheduler, getOrCreate(scheduler).addEntry(entry))
}
fun EntityBuilder.addToSchedule(delay: Duration, command: WorldEntityCommand<MudWorldFrame>): EntityBuilder {
    return addToSchedule(EntityScheduleEntry(LocalDateTime.now().plus(delay), command, { arrayOf() }))
}
fun repeatingSheduleEntry(nextAt: ()-> LocalDateTime, command: WorldEntityCommand<MudWorldFrame>): EntityScheduleEntry {
    return EntityScheduleEntry(nextAt(), command, { arrayOf(repeatingSheduleEntry(nextAt, command)) })
}
fun EntityBuilder.repeatAtRandom(minDelay: Duration, maxDelay: Duration, command: WorldEntityCommand<MudWorldFrame>): EntityBuilder {
    return addToSchedule(repeatingSheduleEntry({ randomTimeFromNow(minDelay, maxDelay) }, command))
}
val rnd = Random(-1)
fun randomTimeFromNow(minDelay: Duration, maxDelay: Duration): LocalDateTime {
    val minDelayUnit = minDelay.toMillis()
    val maxDelayUnit = maxDelay.toMillis()
    val boundedDelayUnit = rnd.nextInt((maxDelayUnit - minDelayUnit).toInt()) + minDelayUnit
    return LocalDateTime.now() + Duration.ofMillis(boundedDelayUnit)
}

val echo = mudTraitTypes.newTrait("echo", { "echo message" }, { node -> node.valueString() })
fun EntityBuilder.echoEvent(message: String, location: Int) = set(echo, message)
        .set(lifetime, Lifetime(1))
        .set(org.dung.mud.location, location)

val isHumanPlayer = mudTraitTypes.newTrait("isHumanPlayer", { true }, { node -> node.valueBoolean() })
fun EntityBuilder.setHumanPlayer() = set(isHumanPlayer, true)
fun Entity.isHumanPlayer() = get(isHumanPlayer)?:false

data class Lifetime(val ticksLeftToLive: Int): TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        return listOf({ _, nextFrame ->
            if (ticksLeftToLive == 1) {
                nextFrame.removeEntity(entity.id)
            }
            else {
                nextFrame.updateEntity(entity.id, { set(lifetime, Lifetime(ticksLeftToLive - 1)) })
            }
        })
    }
}
val lifetime = mudTraitTypes.newTrait("lifetime", { Lifetime(1) }, { node -> node.valueInt()?.let { Lifetime(it) } })


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
            signalTransformers.flatMap { it.transform(entity, finalSignal)} + { _, nextFrame ->
                nextFrame.updateEntity(entity.id, {
                    val existingSignals = getOrCreate(signalQueue)
                    val existingSignalsWithProcessedSignalsRemoved = existingSignals.signals.filter { it !in signals }.toTypedArray()
                    set(signalQueue, existingSignals.copy(signals = existingSignalsWithProcessedSignalsRemoved))
                })
            }
        }
    }

    fun addSignal(signal: Signal) = copy(signals = signals + signal)
}
val signalQueue = mudTraitTypes.newTrait("signals", { -> SignalQueue(arrayOf()) }, { node -> null }) // TODO
fun EntityBuilder.sendSignal(signal: Signal) = set(signalQueue, getOrCreate(signalQueue).addSignal(signal))
fun sendSignal(toEntityId: Int, signal: Signal): WorldCommand<MudWorldFrame> {
    return { _, nextFrame -> nextFrame.updateEntity(toEntityId, { sendSignal(signal) })}
}

class DeadSignal : Signal
data class DamageSignal(val attackerEntity: Entity, val weaponEntity: Entity?, val criticalHit: Boolean, val damage: Int) : Signal
data class ChatSignal(val chattyEntity: Entity, val message: String) : Signal

val wield = mudTraitTypes.newTrait<Int?>("wield", { null }, { node -> node.valueInt() })
data class WieldSignal(val itemId: Int) : Signal
fun EntityBuilder.wield(itemId: Int): EntityBuilder {
    return set(wield, itemId)
            .sendSignal(WieldSignal(itemId))
}
fun EntityBuilder.unwield(): EntityBuilder {
    return set<Int?>(wield, null)
}
fun TraitBased.wieldedWeapon(): Entity? {
    val wieldedItemId = get(wield)
    return if (wieldedItemId == null) null else get(inventory)?.get(wieldedItemId)
}

val dead = mudTraitTypes.newTrait("dead", { false }, { node -> node.valueBoolean() })


data class Inventory(val items: Array<Entity>) {
    fun get(id: Int) = items.firstOrNull { it.id == id }
    fun add(entity: Entity) = copy(items = items + entity)
}
val inventory = mudTraitTypes.newTrait("inventory", { -> Inventory(arrayOf()) }, { node, entityResolver -> Inventory(entityResolver.loadEntities(node["items"]).map { eb -> eb.build() }.toTypedArray())})
fun EntityBuilder.addToInventory(entity: Entity) = set(inventory, getOrCreate(inventory).add(entity))
fun EntityBuilder.pickUpToInventory(frameBuilder: WorldFrameBuilder<MudWorldFrame>, entityId: Int): EntityBuilder {
    val entityToPickUp = frameBuilder.entitiesById[entityId]
    if (entityToPickUp == null) {
        return this
    }
    frameBuilder.removeEntity(entityId)
    entityToPickUp.remove(location)
    frameBuilder.addEntity(frameBuilder.newEntity().echoEvent("${get(name)} picks up ${entityToPickUp.get(name)}", get(location)?:-1).build())
    var eb = this
    if (eb.get(wield) == null) {
        eb = eb.wield(entityToPickUp.id)
    }
    return eb.set(inventory, eb.getOrCreate(inventory).add(entityToPickUp.build()))
}

fun EntityBuilder.dropFromInventory(frameBuilder: WorldFrameBuilder<MudWorldFrame>, entityId: Int): EntityBuilder {
    val inventory = get(inventory)
    if (inventory == null) {
        return this
    }
    val itemInInventory = inventory.get(entityId) ?: return this
    val location = get(location)
    if (location != null) {
        frameBuilder.addEntity(itemInInventory.modify().set(org.dung.mud.location, location).build())
    }
    else {
        frameBuilder.addEntity(itemInInventory)
    }
    return set(org.dung.mud.inventory, inventory.copy(items = inventory.items.filter { it.id != entityId }.toTypedArray()))
}

val health = mudTraitTypes.newTrait("health", {0}, { node -> node.valueInt()?:0 })
fun EntityBuilder.updateHealth(delta: Int): EntityBuilder {
    val health = get(health)
    if (health != null) {
        return set(org.dung.mud.health, health + delta)
    }
    return this
}

val damageMonitor = mudTraitTypes.newTrait("damageMonitor", { DamageMonitor() }, { node -> null })

class DamageMonitor() : SignalTransformer {
    override val precedence: SignalHandlerPrecedence = SignalHandlerPrecedence.Low
    override fun transform(entity: Entity, signal: Signal): List<WorldCommand<MudWorldFrame>> {
        if (signal !is DamageSignal) {
            return listOf()
        }
        return listOf({ _, nextFrame ->
            nextFrame.updateEntity(entity.id, fun EntityBuilder.():EntityBuilder {
                var eb = this
                var health = eb.get(health)
                if (health != null) {
                    health -= signal.damage
                    eb = eb.set(org.dung.mud.health, health)
                    if (health <= 0) {
                        eb = eb.set(dead, true)
                                .sendSignal(DeadSignal())
                                .set(removeFromWorldTrait, true)
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(name)} killed ${get(name)}!", eb.get(location)?:-1).build())
                        nextFrame.updateEntity(signal.attackerEntity.id, { youKilledMe(entity) })
                    }
                    else {
                        val hitDescription = if (signal.criticalHit) "critically hits" else "hits"
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(name)} $hitDescription ${get(name)} for ${signal.damage} damage", eb.get(location)?:-1).build())
                    }
                }
                return eb
            })
        })
    }

}

//  Don't use an enum as currently reflection on enums doesn't work and that is required for
data class Gender(val noun: String, val objectivePronoun: String, val subjectivePronoun: String)
class Genders {
    val male = Gender("male", "his", "he")
    val female = Gender("female", "her", "she")
    val thing = Gender("thing", "its", "it")
    val byName = listOf(male, female, thing).associateBy { gender -> gender.noun }
}
val genders = Genders()
val gender = mudTraitTypes.newTrait("gender", { genders.male }, { node -> genders.byName[node.valueString()?:throw RuntimeException("Expected value")] })

interface AffordanceTrait {
    fun matches(verb: String): Boolean
    fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity)
}
val affordanceGetPicksUp = mudTraitTypes.newTrait<AffordanceTrait>("affordanceGet", { object : AffordanceTrait {
        override fun matches(verb: String): Boolean = (verb == "GET") || (verb == "PICKUP")
        override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
    }
}}, { node -> null })

interface HasDescription {
    val decriptionOrder: Int
    fun description(currentFrame: MudWorldFrame, entity: Entity): String
}

class CommandAffordanceTrait(val openCommand: WorldEntityCommand<MudWorldFrame>, vararg val validVerbs: String) : AffordanceTrait {
    override fun matches(verb: String): Boolean = validVerbs.contains(verb)

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        openCommand(verbObject, currentFrame, nextFrame)
    }
}
data class GoAffordanceTrait(val exitNames: Array<String>, val destinationId: Int) : AffordanceTrait, HasDescription {
    override val decriptionOrder: Int = 2
    constructor(exitNames: Array<String>, node: JsonNode, entityResolver: JsonEntityResolver) : this(exitNames, entityResolver.entityId(node, "destination")) {
    }

    override fun description(currentFrame: MudWorldFrame, entity: Entity): String = "You can exit ${exitNames[0]} (type ${exitNames.joinToString { "'$it'" }})"

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { set(location, destinationId) })
    }

    override fun matches(verb: String): Boolean = verb.toLowerCase() in exitNames
}

val goNorth = mudTraitTypes.newTrait("goNorth'", { locationId: Int -> GoAffordanceTrait(arrayOf("north", "n"), locationId) }, no_json())
val goSouth = mudTraitTypes.newTrait("goSouth'", { locationId: Int -> GoAffordanceTrait(arrayOf("south", "s"), locationId) }, no_json())
val goEast = mudTraitTypes.newTrait("goEast'",   { locationId: Int -> GoAffordanceTrait(arrayOf("east", "e"), locationId)}, no_json())
val goWest = mudTraitTypes.newTrait("goWest'",   { locationId: Int -> GoAffordanceTrait(arrayOf("west", "w"), locationId)}, no_json())

class InspectAffordanceTrait() : AffordanceTrait {
    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { sendSignal(ChatSignal(verbObject, verbObject.description(currentFrame))) })
    }

    override fun matches(verb: String): Boolean = verb == "INSPECT"
}
val inspectable = mudTraitTypes.newTrait("inspectable", { InspectAffordanceTrait() }, { node -> null })

data class CurrencyUnit(val amount: Int) {
    operator fun plus(rhs: CurrencyUnit): CurrencyUnit = CurrencyUnit(amount + rhs.amount)
}
fun gold(amount:Int): CurrencyUnit = silver(10 * amount)
fun silver(amount: Int): CurrencyUnit = copper(10 * amount)
fun copper(amount: Int): CurrencyUnit = CurrencyUnit(amount)
val worth = mudTraitTypes.newTrait("worth", { -> CurrencyUnit(0) }, no_json())
