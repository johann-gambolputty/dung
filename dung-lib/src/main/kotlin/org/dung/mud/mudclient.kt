package org.dung.mud

import org.dung.*

interface MudClientView {
    val playerId: Int
    fun show(text: String)
}

class ClientSignalRenderer(private val clientView: MudClientView) : SignalProcessor {
    override val precedence: SignalHandlerPrecedence = SignalHandlerPrecedence.Low

    override fun process(frame: WorldFrame, entity: Entity, signal: Signal): Signal {
        when (signal) {
            is DeadSignal -> clientView.show("You are dead")
            is ChatSignal -> clientView.show("${signal.chattyEntity.name()}: ${signal.message}")
            is WieldSignal -> clientView.show("${frame.getEntityById(signal.itemId)?.get(nameTrait)}")
        }
        return signal
    }

}
val showSignalsOnClientTrait = mudTraitTypes.newTrait<SignalProcessor>("showSignalsOnClient", no_default(), no_json())

class MudClientConsoleView(private val world: UpdatingMudWorld) : MudClientView {
    override val playerId: Int = world.newEntityId()
    override fun show(text: String) = println(text)

    init {
        val startingLocations = world.currentFrame.entitiesWithTraitValue(startingLocation, true)
        if (startingLocations.isEmpty()) {
            throw RuntimeException("Cannot find any starting locations - please add the '${startingLocation.traitName}' trait to a location")
        }
        val player = EntityBuilderImpl(playerId)
                .defaultCreature("Dylan", "It's Dylan!", startingHealth = 10)
                .set(locationTrait, startingLocations.first().id)
                .setHumanPlayer()
                .set(critTrait, 50)
                .set(skillTrait, 10)
                .set(currentXpTrait, 0)
                .set(showSignalsOnClientTrait, ClientSignalRenderer(this))
                .build()
        world.addCommand({ _, frameBuilder ->
            frameBuilder.addEntity(player)
            show("Hello ${player.get(nameTrait)}")
        })
    }
    fun run() {
        var quit = false
        do {
            val line = readLine()?.split(" ")?:listOf()
            if (line.isEmpty()) {
                continue
            }
            when {
                line[0] in arrayOf("q", "quit", "exit") -> quit = true
                else -> {
                    world.addCommand(cmd@ { currentFrame, nextFrame ->
                        val player = currentFrame.getEntityById(playerId)
                        if (player == null) {
                            show("You died")
                            return@cmd
                        }
                        val playerLocationId = player.get(locationTrait) ?: -1
                        val verb = line[0].toUpperCase()

                        fun getTargetEntities(name: String?): List<Entity> {
                            if (name != null) {
                                val namedEntity = currentFrame.entityWithNameAtLocation(name, playerLocationId)
                                if (namedEntity == null) {
                                    show("There is no '$name' here")
                                    return listOf()
                                }
                                return listOf(namedEntity)
                            }
                            val locationEntity = currentFrame.getEntityById(playerLocationId)
                            return if (locationEntity == null) listOf(player) else listOf(player, locationEntity)
                        }

                        val targetEntities = getTargetEntities(if (line.size > 1) line[1] else null)
                        val targetAffordanceTrait = targetEntities
                                .map { entity ->
                                    val affordanceTrait = entity.traitsOf<AffordanceTrait>().firstOrNull { it.matches(verb) }
                                    if (affordanceTrait == null) null else entity to affordanceTrait
                                }
                                .filterNotNull()
                                .firstOrNull()
                        if (targetAffordanceTrait != null) {
                            targetAffordanceTrait.second.apply(currentFrame, nextFrame, player, targetAffordanceTrait.first)
                        }
                        else {
                            show("Can't do that")
                        }
                    })
                }
            }
        } while (!quit);
    }
}


class MudWorldView(val entityId: Int, world: UpdatingMudWorld, view: MudClientView) {
    init {
        world.newFrame.subscribe {
            val (lastFrame, currentFrame) = it
            val entityInLastFrame = lastFrame.getEntityById(entityId)
            val entityInCurrentFrame = currentFrame.getEntityById(entityId)
            if (entityInCurrentFrame == null) {
                return@subscribe
            }

            fun <T> compareTraits(trait: TraitTypeT<T>, runIfDifferent: (previousValue: T?, currentValue: T?) -> Unit) {
                val previousValue = entityInLastFrame?.get(trait)
                val currentValue = entityInCurrentFrame.get(trait)
                if (previousValue != currentValue) {
                    runIfDifferent(previousValue, currentValue)
                }
            }

            //  Check for changed traits
            compareTraits(currentXpTrait, { previousXp, currentXp -> view.show("Gained ${(currentXp?:0) - (previousXp?:0)} experience")})
            compareTraits(levelTrait, { _, currentLevel -> view.show("Congratulations! You are now level $currentLevel")})

            //  Compare locations
            val locationInLastFrame = if (entityInLastFrame == null) -1 else entityInLastFrame.get(locationTrait) ?: -1
            val locationInCurrentFrame = entityInCurrentFrame.get(locationTrait) ?: -1

            //  Show any echos in the current location
            currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                    .map { entity -> entity.get(echoTrait) }
                    .filterNotNull()
                    .forEach { view.show(it)}

            //  If the current location has changed from the previous frame, show the descripton of the new location
            if (locationInLastFrame != locationInCurrentFrame) {
                currentFrame.getEntityById(locationInCurrentFrame)?.let { location->
                    view.show(location.description())
                    val entitiesInLocation = currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                    if (!entitiesInLocation.isEmpty()) {
                        view.show("You can see:")
                        entitiesInLocation.map { it.get(nameTrait) }
                                .filterNotNull()
                                .forEach { view.show(it) }
                    }
                }
            }
        }
    }
}
