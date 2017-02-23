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
            is ChatSignal -> clientView.show("${signal.chattyEntity.name()}: ${signal.message}")
            is WieldSignal -> clientView.show("${frame.getEntityById(signal.itemId)?.get(nameTrait)}")
        }
        return signal
    }

}
val showSignalsOnClientTrait = mudTraitTypes.newTraitWithNoDefault<SignalProcessor>("showSignalsOnClient", { node -> null })

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
                .set(showSignalsOnClientTrait, ClientSignalRenderer(this))
                .build()
        world.addCommand(command({ frame, frameBuilder ->
            frameBuilder.addEntity(player)
            show("Hello ${player.get(nameTrait)}")
        }))
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
                    world.addCommand(command(cmd@ { currentFrame, nextFrame ->
                        val player = currentFrame.getEntityById(playerId) ?: return@cmd
                        val playerLocationId = player.get(locationTrait) ?: -1
                        val verb = line[0].toUpperCase()
                        val targetEntity = if (line.size > 1) currentFrame.entityWithNameAtLocation(line[1], playerLocationId) else player
                        if (targetEntity != null) {
                            targetEntity.traitsOf<AffordanceTrait>().firstOrNull { it.matches(verb) }?.apply { apply(currentFrame, nextFrame, player, targetEntity) }
                        }
                        else {
                            show("There is no '${line[1]}' here")
                        }
                    }))
                        /*
                    nextFrame.updateEntity(playerId, fun EntityBuilder.(): EntityBuilder {
                        var eb = when (line[0]) {
                            "attack" -> processVerb { e->
                                nextFrame.updateEntity(e.id, { setAttackFocus(playerId) })
                                setAttackFocus(e.id)
                            }
                            "inspect" -> processVerb { e->
                                show("${e.get(nameTrait)} - ${e.get(descriptionTrait)}")
                                if (e.id == playerId) {
                                    show("Health: ${e.get(healthTrait)}")
                                }
                                val inventory = e.get(inventoryTrait)
                                if (inventory != null) {
                                    inventory.items.forEach { show("${it.get(nameTrait)}") }
                                }
                                this
                            }
                            "get" -> processVerb { e ->
                                pickUpToInventory(nextFrame, e.id)
                            }
                            else -> this
                        }
                        val currentLocation = eb.get(locationTrait)?.let { locationId->currentFrame.getEntityById(locationId) }
                        if (currentLocation == null) {
                            return eb
                        }
                        val links = currentLocation.get(locationLinkTrait)
                        if (links == null) {
                            return eb
                        }
                        val link = links.firstOrNull { it.commandBinding.contains(line[0]) }
                        return if (link == null) eb else eb.set(locationTrait, link.destination)
                    })
                    }))
                    */
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
            if (entityInCurrentFrame != null) {
                val locationInLastFrame = if (entityInLastFrame == null) -1 else entityInLastFrame.get(locationTrait) ?: -1
                val locationInCurrentFrame = entityInCurrentFrame.get(locationTrait) ?: -1

                currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                        .map { entity -> entity.get(echoTrait) }
                        .filterNotNull()
                        .forEach { view.show(it)}

                if (locationInLastFrame != locationInCurrentFrame) {
                    currentFrame.getEntityById(locationInCurrentFrame)?.let { location->
                        view.show(location.description())
                        val entitiesInLocation = currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                        if (!entitiesInLocation.isEmpty()) {
                            view.show("You can see:")
                            entitiesInLocation.map { it.get(nameTrait)?.toString(it) }
                                    .filterNotNull()
                                    .forEach { view.show(it) }
                        }
                    }
                }
            }
        }
    }
}
