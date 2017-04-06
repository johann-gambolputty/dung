package org.dung.mud

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.databind.node.JsonNodeType
import org.dung.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAmount
import java.util.*

/**
 * Traits for muds.
 */
class EntityFormatString(val format: String) {
    fun toString(e: Entity): String = e.processText(format)
}
val mudTraitTypes = TraitTypeMaker().add(commonTraitTypes.traits)
val nameTrait = mudTraitTypes.newTrait("name", { "Unnamed" }, { node -> node.valueString()?:"Unnamed" })
fun Entity.name(): String = get(nameTrait)?:"Unnamed"
val descriptionTrait = mudTraitTypes.newTrait("description", { "Undescribed" }, { node -> node.valueString()?:"Undescribed" })
fun Entity.description(): String = get(descriptionTrait)?:"Undescribed"
val locationTrait = mudTraitTypes.newTrait("location", { 0 }, { node -> node.valueInt() })
fun EntityBuilder.setOrClearLocation(location: Int?): EntityBuilder {
    if (location == null) {
        return  remove(locationTrait)
    }
    return set(locationTrait, location)
}

val aliasesTrait = mudTraitTypes.newTrait<Array<String>>("aliases", { arrayOf() }, no_json())
fun Entity.matchesUcAlias(name: String): Boolean = get(aliasesTrait)?.contains(name)?:false

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
                        nextFrame.updateEntity(entity.id, { set(schedulerTrait, EntityScheduler(newSchedule) )})
                    })
                }
                commands.add(({ frame, nextFrame ->entry.commandToRun(entity, frame, nextFrame)}))
            }
        }

        return commands
    }
    fun addEntry(entry: EntityScheduleEntry) = copy(schedule = schedule + entry)
}
val schedulerTrait = mudTraitTypes.newTrait("scheduler", { EntityScheduler() }, { node -> null })// TODO

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

val echoTrait = mudTraitTypes.newTrait("echo", { "echo message" }, { node -> node.valueString() })
fun EntityBuilder.echoEvent(message: String, location: Int) = set(echoTrait, message)
        .set(lifetimeTrait, Lifetime(1))
        .set(locationTrait, location)

val playerTrait = mudTraitTypes.newTrait("isHumanPlayer", { true }, { node -> node.valueBoolean() })
fun EntityBuilder.setHumanPlayer() = set(playerTrait, true)
fun Entity.isHumanPlayer() = get(playerTrait)?:false

private val textTokenRegex = Regex("\\#\\{([^}]*)\\}")
fun Entity.processText(message: String): String {
    val firstToken:Any? = Any()
    fun tokenToObj(context: Any?, token: String): Any? {
        if (context == firstToken) {
            return if (token == "entity") this else throw RuntimeException("Unknown starting token '$token' in '$message'")
        }
        if (context == null) {
            return null
        }
        if (context is TraitBased) {
            return context.getByName(token)
        }
        val result = context.javaClass.kotlin.members
                .firstOrNull { it.name == token }
                ?.call(context)
        return result
    }
    var text = message
    var passCount = 0
    while (textTokenRegex.containsMatchIn(text)) {
        if (passCount++ > 10) break
        text = textTokenRegex.replace(text, fun(match: MatchResult):CharSequence {
            var startChar = (match.groups.get(1)?.range?.start)?:0
            val capitalize = (startChar < 3) || (startChar-3..0)
                    .map { message[it] }
                    .firstOrNull { ch -> !ch.isWhitespace() }=='.'
            val replacementValue = match.groupValues.get(1)
                    .split('.')
                    .fold(firstToken, { r, t -> tokenToObj(r, t) })
            val replacementText = if (replacementValue is EntityFormatString) replacementValue.toString(this) else replacementValue?.toString()?:"???"
            return if (!capitalize) replacementText else replacementText.capitalize()
        })
    }
    return text
}

val nextTimeToMakeFlavourNoiseTrait = mudTraitTypes.newTrait("nextTimeToMakeFlavourNoise", { LocalDateTime.now() }, { node -> node.valueTimestamp() })
class FlavourNoiseTrait(private val messages: Array<(MudWorldFrame, Entity)->String>, private val minDelay: TemporalAmount = Duration.ofSeconds(10), private val maxDelay: TemporalAmount = Duration.ofSeconds(20)) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        val nextTimeToMakeFlavourNoise = entity.get(nextTimeToMakeFlavourNoiseTrait)
        if (nextTimeToMakeFlavourNoise == null) {
            return listOf(updateEntityCommand(entity.id, { set(nextTimeToMakeFlavourNoiseTrait, LocalDateTime.now() + minDelay)}))
        }
        if (nextTimeToMakeFlavourNoise > updateTimestamp) {
            return listOf()
        }
        return listOf({ frame, nextFrame ->
            val message = messages[rnd.nextInt(messages.size)](frame, entity)
            val processedMessage = entity.processText(message)
            val echoEntity = nextFrame.newEntity().echoEvent(processedMessage, entity.get(locationTrait)?:-1)
            nextFrame.addEntity(echoEntity.build())
            nextFrame.updateEntity(entity.id, { set(nextTimeToMakeFlavourNoiseTrait, updateTimestamp + minDelay)})
        })
    }
}
//fun flavourNoise(vararg messages: String): FlavourNoiseTrait =
//        flavourNoise(Duration.ofSeconds(5), Duration.ofSeconds(20), *messages)
//fun flavourNoise(minDelay: TemporalAmount, maxDelay: TemporalAmount, vararg messages: String): FlavourNoiseTrait =
//        FlavourNoiseTrait(messages, minDelay, maxDelay)
val randomFlavourNoiseTrait = mudTraitTypes.newTrait("randomFlavourNoise", { FlavourNoiseTrait(arrayOf())}, { node ->
    val value = node["value"]?:throw RuntimeJsonMappingException("Expected trait to have a value")
    if (value.nodeType != JsonNodeType.ARRAY) {
        throw RuntimeJsonMappingException("Expected value of trait to be an array")
    }
    val noises = mutableListOf<String>()
    for (i in 0..value.size()) {
        noises.add(value[i]?.textValue()?:continue)
    }
    FlavourNoiseTrait(noises.map { { frame: MudWorldFrame, entity: Entity -> it }}.toTypedArray())
})

fun EntityBuilder.randomFlavourNoise(entityGen: EntityGenerator, messages: Array<(Entity)->String>, minDelay: TemporalAmount = Duration.ofSeconds(10), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, { entity, _, nextFrame ->
        val message = messages[rnd.nextInt(messages.size)](entity)
        nextFrame.addEntity(nextFrame.newEntity().echoEvent(message, entity.get(locationTrait)?:-1).build())
    })
}

fun EntityBuilder.randomlyChatty(minDelay: TemporalAmount = Duration.ofSeconds(5), maxDelay: TemporalAmount = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, { entity, currentFrame, nextFrame ->
        if (entity.get(attackFocusTrait) != null) {
            return@repeatAtRandom
        }
        val playersInLocation = currentFrame.getEntitiesInLocation(entity.get(locationTrait) ?: -1).filter { it.isHumanPlayer() }
        if (!playersInLocation.isEmpty()) {
            val playerToChatTo = playersInLocation[rnd.nextInt(playersInLocation.size)]
            nextFrame.updateEntity(playerToChatTo.id, { sendSignal(ChatSignal(entity, "Hello ${playerToChatTo.get(nameTrait)}")) })
        }
    })
}

data class Lifetime(val ticksLeftToLive: Int): TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        return listOf({ _, nextFrame ->
            if (ticksLeftToLive == 1) {
                nextFrame.removeEntity(entity.id)
            }
            else {
                nextFrame.updateEntity(entity.id, { set(lifetimeTrait, Lifetime(ticksLeftToLive - 1)) })
            }
        })
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
            signalTransformers.flatMap { it.transform(entity, finalSignal)} + { _, nextFrame ->
                nextFrame.updateEntity(entity.id, {
                    val existingSignals = getOrCreate(signalQueueTrait)
                    val existingSignalsWithProcessedSignalsRemoved = existingSignals.signals.filter { it !in signals }.toTypedArray()
                    set(signalQueueTrait, existingSignals.copy(signals = existingSignalsWithProcessedSignalsRemoved))
                })
            }
        }
    }

    fun addSignal(signal: Signal) = copy(signals = signals + signal)
}
val signalQueueTrait = mudTraitTypes.newTrait("signals", { -> SignalQueue(arrayOf()) }, { node -> null }) // TODO
fun EntityBuilder.sendSignal(signal: Signal) = set(signalQueueTrait, getOrCreate(signalQueueTrait).addSignal(signal))
fun sendSignal(toEntityId: Int, signal: Signal): WorldCommand<MudWorldFrame> {
    return { _, nextFrame -> nextFrame.updateEntity(toEntityId, { sendSignal(signal) })}
}

class DeadSignal : Signal
data class DamageSignal(val attackerEntity: Entity, val weaponEntity: Entity?, val criticalHit: Boolean, val damage: Int) : Signal
data class ChatSignal(val chattyEntity: Entity, val message: String) : Signal

val wieldTrait = mudTraitTypes.newTrait<Int?>("wield", { null }, { node -> node.valueInt() })
data class WieldSignal(val itemId: Int) : Signal
fun EntityBuilder.wield(itemId: Int): EntityBuilder {
    return set(wieldTrait, itemId)
            .sendSignal(WieldSignal(itemId))
}
fun EntityBuilder.unwield(): EntityBuilder {
    return set<Int?>(wieldTrait, null)
}
fun TraitBased.wieldedWeapon(): Entity? {
    val wieldedItemId = get(wieldTrait)
    return if (wieldedItemId == null) null else get(inventoryTrait)?.get(wieldedItemId)
}

val deadTrait = mudTraitTypes.newTrait("dead", { false }, { node -> node.valueBoolean() })


data class Inventory(val items: Array<Entity>) {
    fun get(id: Int) = items.firstOrNull { it.id == id }
    fun add(entity: Entity) = copy(items = items + entity)
}
val inventoryTrait = mudTraitTypes.newTrait("inventory", { -> Inventory(arrayOf()) }, { node, entityResolver -> Inventory(entityResolver.loadEntities(node["items"]).map { eb -> eb.build() }.toTypedArray())})
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
        return listOf({ _, nextFrame ->
            nextFrame.updateEntity(entity.id, fun EntityBuilder.():EntityBuilder {
                var eb = this
                var health = eb.get(healthTrait)
                if (health != null) {
                    health -= signal.damage
                    eb = eb.set(healthTrait, health)
                    if (health <= 0) {
                        eb = eb.set(deadTrait, true)
                                .sendSignal(DeadSignal())
                                .set(removeFromWorldTrait, true)
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(nameTrait)} killed ${get(nameTrait)}!", eb.get(locationTrait)?:-1).build())
                        nextFrame.updateEntity(signal.attackerEntity.id, { youKilledMe(entity) })
                    }
                    else {
                        val hitDescription = if (signal.criticalHit) "critically hits" else "hits"
                        nextFrame.addEntity(nextFrame.newEntity().echoEvent("${signal.attackerEntity.get(nameTrait)} $hitDescription ${get(nameTrait)} for ${signal.damage} damage", eb.get(locationTrait)?:-1).build())
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
val genderTrait = mudTraitTypes.newTrait("gender", { genders.male }, { node -> genders.byName[node.valueString()?:throw RuntimeException("Expected value")] })

interface AffordanceTrait {
    fun matches(verb: String): Boolean
    fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity)
}
val affordanceGetPicksUpTrait = mudTraitTypes.newTrait<AffordanceTrait>("affordanceGet", { object : AffordanceTrait {
        override fun matches(verb: String): Boolean = (verb == "GET") || (verb == "PICKUP")
        override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
    }
}}, { node -> null })



class CommandAffordanceTrait(val openCommand: WorldEntityCommand<MudWorldFrame>, vararg val validVerbs: String) : AffordanceTrait {
    override fun matches(verb: String): Boolean = validVerbs.contains(verb)

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        openCommand(verbObject, currentFrame, nextFrame)
    }
}
fun commandCreateExit(destination: String): WorldEntityCommand<MudWorldFrame> {
    return { entity, currentFrame, nextFrame ->
        val destinationName = entity.processText(destination)
    }
}
fun commandDestroyAllExits(): WorldEntityCommand<MudWorldFrame> {
    return { entity, currentFrame, nextFrame ->

    }
}

val affordanceOpenableTrait = mudTraitTypes.newTrait("affordanceOpenable", { command:WorldEntityCommand<MudWorldFrame> ->
    CommandAffordanceTrait(command, "OPEN")
}, no_json())

data class GoAffordanceTrait(val exitNames: Array<String>, val destinationId: Int) : AffordanceTrait {

    constructor(exitNames: Array<String>, node: JsonNode, entityResolver: JsonEntityResolver) : this(exitNames, entityResolver.entityId(node, "destination")) {
    }

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { set(locationTrait, destinationId) })
    }

    override fun matches(verb: String): Boolean = verb.toLowerCase() in exitNames
}

val affordanceGoNorthTrait = mudTraitTypes.newTrait("affordanceGoNorth'", { locationId: Int -> GoAffordanceTrait(arrayOf("north", "n"), locationId) }, no_json())
val affordanceGoSouthTrait = mudTraitTypes.newTrait("affordanceGoSouth'", { locationId: Int -> GoAffordanceTrait(arrayOf("south", "s"), locationId) }, no_json())
val affordanceGoEastTrait = mudTraitTypes.newTrait("affordanceGoEast'",   { locationId: Int -> GoAffordanceTrait(arrayOf("east", "e"), locationId)}, no_json())
val affordanceGoWestTrait = mudTraitTypes.newTrait("affordanceGoWest'",   { locationId: Int -> GoAffordanceTrait(arrayOf("west", "w"), locationId)}, no_json())


class AttackAffordanceTrait() : AffordanceTrait {
    override fun matches(verb: String): Boolean = verb == "ATTACK"

    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { setAttackFocus(verbObject.id) })
        nextFrame.updateEntity(verbObject.id, { setAttackFocus(source.id) })
    }

}
val affordanceAttackTrait = mudTraitTypes.newTrait("affordanceAttack", { AttackAffordanceTrait() }, { node -> null })


class InspectAffordanceTrait() : AffordanceTrait {
    override fun apply(currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>, source: Entity, verbObject: Entity) {
        nextFrame.updateEntity(source.id, { sendSignal(ChatSignal(verbObject, verbObject.description())) })
    }

    override fun matches(verb: String): Boolean = verb == "INSPECT"

}
val affordanceInspectTrait = mudTraitTypes.newTrait("affordanceInspect", { InspectAffordanceTrait() }, { node -> null })