package org.dung.mud

import org.dung.*

interface MudClientView {
    val playerId: Int
    fun show(text: String)
}

class MudClientConsoleView(private val world: UpdatingMudWorld) : MudClientView {
    override val playerId: Int = world.nextEntityId()
    override fun show(text: String) = println(text)

    init {
        val player = EntityImpl(playerId).modify()
                .set(nameTrait, "Dylan")
                .set(descriptionTrait, "It's you!")
                .set(locationTrait, 0)
                .setHumanPlayer()
                .set(healthTrait, 10)
                .addSignalProcessor(SignalProcessor(SignalProcessorPrecedence.Low, { signal ->
                    when (signal) {
                        is ChatSignal -> show("${signal.chattyEntity.get(nameTrait)}: ${signal.message}")
                        is DamageSignal -> show("${signal.attackerEntity.get(nameTrait)} hits you for ${signal.damage} damage")
                    }
                    signal
                }))
                .build()
        world.addCommand(command({ frame, frameBuilder ->
            frameBuilder.addEntity(player)
            show("Hello ${player.get(nameTrait)}")
            val playerLocationId = player.get(locationTrait)
            if (playerLocationId == null) {
                show("You are... nowhere")
            }
            else {
                show(frame.getEntityById(playerLocationId)?.get(descriptionTrait)?:"You are at an unknown location")
            }
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
                    world.addCommand(command({ currentFrame, nextFrame ->
                        nextFrame.updateEntity(playerId, fun EntityBuilder.(): EntityBuilder {
                            fun processVerb(onMatch: (Entity)->Unit) {
                                if (line.size != 2) {
                                    return
                                }
                                val targetName = line[1].toUpperCase()
                                val target = currentFrame.getAllEntities().firstOrNull { e -> e.get(nameTrait)?.toUpperCase()?.equals(targetName)?:false }
                                if (target != null) {
                                    onMatch(target)
                                }
                            }
                            when (line[0]) {
                                "attack" -> processVerb { e-> nextFrame.updateEntity(e.id, { setAttackFocus(playerId) })}
                                "inspect" -> processVerb { e->
                                    show("${e.get(nameTrait)} - ${e.get(descriptionTrait)}")
                                    if (e.id == playerId) {
                                        show("Health: ${e.get(healthTrait)}")
                                    }
                                    val inventory = e.get(inventoryTrait)
                                    if (inventory != null) {
                                        inventory.items.forEach { show("${it.get(nameTrait)}") }
                                    }
                                }
                                "get" -> processVerb { e -> nextFrame.updateEntity(playerId, { pickUpToInventory(nextFrame, e.id) })}
                            }
                            val currentLocation = get(locationTrait)?.run { currentFrame.getEntityById(this) }
                            if (currentLocation == null) {
                                return this
                            }
                            val links = currentLocation.get(locationLinkTrait)
                            if (links == null) {
                                return this
                            }
                            val link = links.firstOrNull { it.commandBinding.contains(line[0]) }
                            return if (link == null) this else set(locationTrait, link.destination)
                        })
                    }))
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
            if (entityInLastFrame != null && entityInCurrentFrame != null) {
                val locationInLastFrame = entityInLastFrame.get(locationTrait) ?: -1
                val locationInCurrentFrame = entityInCurrentFrame.get(locationTrait) ?: -1

                currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                        .map { entity -> entity.get(echoTrait) }
                        .filterNotNull()
                        .forEach { view.show(it)}

                if (locationInLastFrame != locationInCurrentFrame) {
                    currentFrame.getEntityById(locationInCurrentFrame)?.let { location->
                        view.show(location.get(descriptionTrait)?:"")
                        val entitiesInLocation = currentFrame.getEntitiesInLocation(locationInCurrentFrame)
                        if (!entitiesInLocation.isEmpty()) {
                            view.show("You can see:")
                            entitiesInLocation.map { it.get(nameTrait) }.filterNotNull().forEach { view.show(it) }
                        }
                        val exits = location.get(locationLinkTrait)?:arrayOf()
                        if (exits.isEmpty()) {
                            view.show("There are no exits")
                        }
                        else {
                            view.show("Exits are:")
                            exits.forEach { exit -> view.show("${exit.name} (type ${exit.commandBinding.joinToString(", or ")})")}
                        }

                    }
                }
            }
        }
    }
}
