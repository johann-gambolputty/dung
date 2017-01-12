package org.dung.mud

import org.dung.*
import java.time.LocalDateTime

class UpdatingMudWorld() : UpdatingWorld<MudWorldFrame>({ entities -> MudWorldFrame(entities) })

class MudWorldFrame(entities: Array<Entity>) : WorldFrame(entities) {
    fun getEntitiesInLocation(id: Int) = getAllEntities().filter { entity -> entity.get(locationTrait)==id }
}

fun MudWorldFrame.entityWithNameAtLocation(name: String, locationId: Int): Entity? {
    val ucName = name.toUpperCase()
    return getEntitiesInLocation(locationId).first { ucName == it.get(nameTrait)?.toUpperCase() }
}


//  TODO use type aliases when available
interface MudWorldCommand : WorldCommand<MudWorldFrame>

fun mudCommand(f: (currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>) -> Unit) =
        object : MudWorldCommand {
            override fun run(frame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>)= f(frame, nextFrame)
        }

fun mudEntityCommand(f: (entity: Entity, currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>) -> Unit) = entityCommand(f)

fun EntityBuilder.defaultEntity(name: String, description: String) =
        set(nameTrait, name)
        .set(descriptionTrait, description)

fun EntityBuilder.defaultCreature(name: String, description: String, startingHealth: Int, startingInventory: Array<Entity> = arrayOf()): EntityBuilder {
    var eb = defaultEntity(name, description)
            .setDefault(damageMonitorTrait)
            .set(healthTrait, startingHealth)
            .set(inventoryTrait, Inventory(startingInventory))
    if (!startingInventory.isEmpty()) {
        eb = eb.set(wieldTrait, startingInventory[0].id)
    }
    return eb
}

open class MudWorldInitializer(val world: UpdatingMudWorld) : WorldInitializer<MudWorldFrame> {

    override fun createCommandsForInitialState(): Array<WorldCommand<MudWorldFrame>> = entityMap.values.map { mudCommand({ currentFrame, nextFrame -> nextFrame.addEntity(it.build())}) }.toTypedArray()

    private val entityMap = mutableMapOf<Int, EntityBuilder>()

    protected fun createEntity(setup: EntityBuilder.()-> EntityBuilder): Int {
        val entity = EntityBuilderImpl(world.nextEntityId(), mutableMapOf()).setup()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun createEntity(name: String, description: String, setup: EntityBuilder.()-> EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.nextEntityId(), mutableMapOf()).defaultEntity(name, description).setup()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun createActor(name: String, description: String, startingHealth: Int, startingInventory: Array<Entity> = arrayOf(), setup: EntityBuilder.()-> EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.nextEntityId())
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

fun runMud(makeWorldInitializer: (UpdatingMudWorld)-> WorldInitializer<MudWorldFrame>) {
    val world = UpdatingMudWorld()
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
