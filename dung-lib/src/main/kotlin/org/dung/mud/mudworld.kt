package org.dung.mud

import org.dung.*
import java.time.LocalDateTime

class UpdatingMudWorld() : UpdatingWorld<MudWorldFrame>({ entities -> MudWorldFrame(entities) })

class MudWorldFrame(entities: Array<Entity>) : WorldFrame(entities) {
    fun getEntitiesInLocation(id: Int) = getAllEntities().filter { entity -> entity.get(location)==id }
}

fun MudWorldFrame.entityWithNameAtLocation(name: String, locationId: Int): Entity? {
    val ucName = name.toUpperCase()
    return getEntitiesInLocation(locationId).firstOrNull { ucName == it.name().toUpperCase() || it.matchesUcAlias(ucName) }
}


//  TODO use type aliases when available
typealias MudWorldCommand = WorldCommand<MudWorldFrame>

fun EntityBuilder.defaultEntity(name: String, description: String) =
        set(org.dung.mud.name, name)
        .set(org.dung.mud.description, description)

fun EntityBuilder.defaultCreature(name: String, description: String, startingHealth: Int, startingInventory: Array<Entity> = arrayOf()): EntityBuilder {
    var eb = defaultEntity(name, description)
            .setDefault(damageMonitor)
            .set(health, startingHealth)
            .set(inventory, Inventory(startingInventory))
    if (!startingInventory.isEmpty()) {
        eb = eb.set(wield, startingInventory[0].id)
    }
    return eb
}

open class MudWorldInitializer(val world: UpdatingMudWorld) : WorldInitializer<MudWorldFrame> {

    override fun createCommandsForInitialState(): Array<WorldCommand<MudWorldFrame>> =
            entityMap.values.map { entityBuilder -> { _: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame> -> nextFrame.addEntity(entityBuilder.build())} }.toTypedArray()

    private val entityMap = mutableMapOf<Int, EntityBuilder>()

    protected fun createEntity(setup: EntityBuilder.()-> EntityBuilder): Int {
        val entity = EntityBuilderImpl(world.newEntityId(), mutableMapOf()).setup()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun createEntity(name: String, description: String, setup: EntityBuilder.()-> EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.newEntityId(), mutableMapOf()).defaultEntity(name, description).setup()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun createActor(name: String, description: String, startingHealth: Int, startingInventory: Array<Entity> = arrayOf(), setup: EntityBuilder.()-> EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.newEntityId())
                .defaultCreature(name, description, startingHealth, startingInventory)
                .setup()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun updateEntity(id: Int, builder: EntityBuilder.()-> EntityBuilder) {
        val existingBuilder = entityMap[id]?:throw IllegalArgumentException("Entity with id $id does not exist")
        entityMap[id] = existingBuilder.builder()
    }

}

fun runMud(world: UpdatingMudWorld, makeWorldInitializer: (UpdatingMudWorld) -> WorldInitializer<MudWorldFrame>) {
    makeWorldInitializer(world).createCommandsForInitialState().forEach { world.addCommand(it) }
    world.process()

    val cv = MudClientConsoleView(world)
    val view = MudWorldView(cv.playerId, world, cv)
    val tickThread = Thread({
        var lastUpdateTime = LocalDateTime.now()
        while (true) {
            world.process()
            Thread.sleep(30)
        }
    })
    tickThread.isDaemon = true
    tickThread.start()
    cv.run()
}
