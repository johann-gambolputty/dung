package org.dung.mud

import org.dung.*
import java.time.LocalDateTime

class UpdatingMudWorld() : UpdatingWorld<MudWorldFrame>({ entities -> MudWorldFrame(entities) })

class MudWorldFrame(entities: Array<Entity>) : WorldFrame(entities) {
    fun getEntitiesInLocation(id: Int) = getAllEntities().filter { entity -> entity.get(locationTrait)==id }
}

//  TODO use type aliases when available
interface MudWorldCommand : WorldCommand<MudWorldFrame>

fun mudCommand(f: (currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>) -> Unit) =
        object : MudWorldCommand {
            override fun run(frame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>)= f(frame, nextFrame)
        }

fun mudEntityCommand(f: (entity: Entity, currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>) -> Unit) = entityCommand(f)

fun defaultEntity(entityGen: EntityGenerator, name: String, description: String, buildMore: EntityBuilder.()->EntityBuilder = { this }) =
        entityGen.newEntity()
                .set(nameTrait, name)
                .set(descriptionTrait, description)
                .buildMore()

fun defaultCreature(entityGen: EntityGenerator, name: String, description: String, startingHealth: Int, startingInventory: Array<Entity> = arrayOf(), buildMore: EntityBuilder.()->EntityBuilder = { this }): EntityBuilder {
    var eb = defaultEntity(entityGen, name, description)
            .setDefault(damageMonitorTrait)
            .set(healthTrait, startingHealth)
            .set(inventoryTrait, Inventory(startingInventory))
    if (!startingInventory.isEmpty()) {
        eb = eb.set(wieldTrait, startingInventory[0].id)
    }
    return eb.buildMore()
}

open class MudWorldInitializer(val world: UpdatingMudWorld) : WorldInitializer<MudWorldFrame> {

    override fun createCommandsForInitialState(): Array<WorldCommand<MudWorldFrame>> = entityMap.values.map { mudCommand({ currentFrame, nextFrame -> nextFrame.addEntity(it.build())}) }.toTypedArray()

    private val entityMap = mutableMapOf<Int, EntityBuilder>()

    protected fun createEntity(name: String, description: String, buildMore: EntityBuilder.()-> EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.nextEntityId(), mutableMapOf())
                .set(nameTrait, name)
                .set(descriptionTrait, description)
                .buildMore()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun updateEntity(id: Int, builder: EntityBuilder.()-> EntityBuilder) {
        val existingBuilder = entityMap[id]?:throw IllegalArgumentException("Entity with id $id does not exist")
        entityMap[id] = existingBuilder.builder()
    }

    fun linkLocations(a: Int, b: Int, aToB: (Int) -> LocationLink, bToA: (Int) -> LocationLink) {
        updateEntity(a, { addLocationLink(aToB(b)) })
        updateEntity(b, { addLocationLink(bToA(a)) })
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