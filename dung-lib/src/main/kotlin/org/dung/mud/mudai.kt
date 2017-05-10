package org.dung.mud

import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.databind.node.JsonNodeType
import org.dung.*
import java.time.Duration
import java.time.LocalDateTime


val nextTimeToMakeFlavourNoiseTrait = mudTraitTypes.newTrait("nextTimeToMakeFlavourNoise", { LocalDateTime.now() }, { node -> node.valueTimestamp() })
class FlavourNoise(private val messages: Array<(MudWorldFrame, Entity)->String>, private val minDelay: Duration = Duration.ofSeconds(10), private val maxDelay: Duration = Duration.ofSeconds(20)) : TickableTrait<MudWorldFrame> {
    override fun update(frame: MudWorldFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<MudWorldFrame>> {
        //  Fighting entities don't chat
        if (entity.get(attackFocusTrait) != null) {
            return listOf()
        }
        val nextTimeToMakeFlavourNoise = entity.get(nextTimeToMakeFlavourNoiseTrait)
        if (nextTimeToMakeFlavourNoise == null) {
            val delay = minDelay
            return listOf(updateEntityCommand(entity.id, { set(nextTimeToMakeFlavourNoiseTrait, LocalDateTime.now() + minDelay)}))
        }
        if (nextTimeToMakeFlavourNoise > updateTimestamp) {
            return listOf()
        }
        return listOf({ frame, nextFrame ->
            val message = messages[rnd.nextInt(messages.size)](frame, entity)
            val echoEntity = nextFrame.newEntity().echoEvent(message, entity.get(location)?:-1)
            nextFrame.addEntity(echoEntity.build())
            nextFrame.updateEntity(entity.id, { set(nextTimeToMakeFlavourNoiseTrait, updateTimestamp + minDelay)})
        })
    }
}
//fun flavourNoise(vararg messages: String): FlavourNoise =
//        flavourNoise(Duration.ofSeconds(5), Duration.ofSeconds(20), *messages)
//fun flavourNoise(minDelay: Duration, maxDelay: Duration, vararg messages: String): FlavourNoise =
//        FlavourNoise(messages, minDelay, maxDelay)
val randomFlavourNoiseTrait = mudTraitTypes.newTrait("randomFlavourNoise", { FlavourNoise(arrayOf())}, { node ->
    val value = node["value"]?:throw RuntimeJsonMappingException("Expected trait to have a value")
    if (value.nodeType != JsonNodeType.ARRAY) {
        throw RuntimeJsonMappingException("Expected value of trait to be an array")
    }
    val noises = mutableListOf<String>()
    for (i in 0..value.size()) {
        noises.add(value[i]?.textValue()?:continue)
    }
    FlavourNoise(noises.map { { frame: MudWorldFrame, entity: Entity -> it }}.toTypedArray())
})

fun EntityBuilder.randomFlavourNoise(entityGen: EntityGenerator, messages: Array<(Entity)->String>, minDelay: Duration = Duration.ofSeconds(10), maxDelay: Duration = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, { entity, _, nextFrame ->
        val message = messages[rnd.nextInt(messages.size)](entity)
        nextFrame.addEntity(nextFrame.newEntity().echoEvent(message, entity.get(location)?:-1).build())
    })
}

fun EntityBuilder.randomlyChatty(minDelay: Duration = Duration.ofSeconds(5), maxDelay: Duration = Duration.ofSeconds(20)): EntityBuilder {
    return repeatAtRandom(minDelay, maxDelay, { entity, currentFrame, nextFrame ->
        if (entity.get(attackFocusTrait) != null) {
            return@repeatAtRandom
        }
        val playersInLocation = currentFrame.getEntitiesInLocation(entity.get(location) ?: -1).filter { it.isHumanPlayer() }
        if (!playersInLocation.isEmpty()) {
            val playerToChatTo = playersInLocation[rnd.nextInt(playersInLocation.size)]
            nextFrame.updateEntity(playerToChatTo.id, { sendSignal(ChatSignal(entity, "Hello ${playerToChatTo.get(name)}")) })
        }
    })
}