package org.dung

import rx.Observable
import rx.subjects.PublishSubject
import java.time.LocalDateTime
import java.util.HashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger


class TraitType<T>(val createDefault: ()->T) {

}

interface TraitBased {
    fun <T> get(t: TraitType<T>): T?
}

interface Entity : TraitBased {
    val id: Int
    fun modify(): EntityBuilder
}

interface EntityBuilder : TraitBased {
    val id: Int
    fun <T> set(t: TraitType<T>, value: T): EntityBuilder
    fun build(): Entity
}

class EntityBuilderImpl(override val id: Int, private val traits: MutableMap<TraitType<*>, Any>) : EntityBuilder {
    override fun <T> get(t: TraitType<T>): T? = traits[t]?.run { this as T }

    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder = apply { traits[t] = value as Any }

    override fun build(): Entity = EntityImpl(id, traits)

}

class EntityImpl(override val id: Int, private val traits: Map<TraitType<*>, Any> = mapOf()) : Entity, EntityBuilder {
    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).set(t, value)

    override fun build(): Entity = this

    override fun <T> get(t: TraitType<T>): T? = traits[t]?.run { this as T }
    override fun modify(): EntityBuilder = this
}


class WorldFrame(val entities: Array<Entity>) {
    val entitiesById = entities.associate { it.id to it }
}

class WorldFrameBuilder(private val baseFrame: WorldFrame) {
    val entitiesById = HashMap(baseFrame.entities.map { entity -> entity.modify() }.associate { it.id to it })
    fun updateEntity(id: Int, updateFnc: EntityBuilder.()->EntityBuilder) {
        entitiesById[id]?.run { entitiesById[id] = updateFnc() }
    }
    fun addEntity(entity: Entity) {
        val eb = entity.modify()
        entitiesById[eb.id] = eb
    }
    fun removeEntity(entityId: Int) {
        entitiesById.remove(entityId)
    }

    fun build() = WorldFrame(entitiesById.values.map { it.build() }.toTypedArray())
}

interface MudClientView {
    val playerId: Int
    fun show(text: String)
}

class MudClientConsoleView(private val world: MudWorld) : MudClientView {
    override val playerId: Int = world.nextEntityId()
    override fun show(text: String) = println(text)

    init {
        val player = EntityImpl(playerId).modify()
                .set(nameTrait, "Dylan")
                .set(locationTrait, 0)
                .build()
        world.addCommand { frame, frameBuilder ->
            frameBuilder.addEntity(player)
            show("Hello ${player.get(nameTrait)}")
            val playerLocationId = player.get(locationTrait)
            if (playerLocationId == null) {
                show("You are... nowhere")
            }
            else {
                show(frame.entitiesById[playerLocationId]?.get(descriptionTrait)?:"You are at an unknown location")
            }
        }
    }

    fun run() {
        var quit = false
        do {
            val line = readLine()
            when (line) {
                in arrayOf("q", "quit", "exit") -> quit = true
                else -> {
                    world.addCommand { f, fb ->
                        fb.updateEntity(playerId, fun EntityBuilder.(): EntityBuilder {
                            val currentLocation = get(locationTrait)?.run { f.entitiesById[this] }
                            if (currentLocation == null) {
                                return this
                            }
                            val links = currentLocation.get(locationLinkTrait)
                            if (links == null) {
                                return this
                            }
                            val link = links.firstOrNull { it.commandBinding.contains(line) }
                            return if (link == null) this else set(locationTrait, link.destination)
                        })
                    }
                }
            }
        } while (!quit);
    }
}

class MudWorldView(val entityId: Int, world: MudWorld, view: MudClientView) {
    init {
        world.newFrame.subscribe {
            val (lastFrame, currentFrame) = it
            val entityInLastFrame = lastFrame.entitiesById[entityId]
            val entityInCurrentFrame = currentFrame.entitiesById[entityId]
            if (entityInLastFrame != null && entityInCurrentFrame != null) {
                val locationInLastFrame = entityInLastFrame.get(locationTrait) ?: -1
                val locationInCurrentFrame = entityInCurrentFrame.get(locationTrait) ?: -1
                if (locationInLastFrame != locationInCurrentFrame) {
                    currentFrame.entitiesById[locationInCurrentFrame]?.apply {
                        view.show(get(descriptionTrait)?:"")
                        val entitiesInLocation = currentFrame.entities.filter { it.get(locationTrait) == locationInCurrentFrame }
                        if (!entitiesInLocation.isEmpty()) {
                            view.show("You can see:")
                            entitiesInLocation.forEach { view.show(it.get(nameTrait)?:"[Unnamed Thing ${it.id}]") }
                        }

                    }
                }
            }
        }
    }
}

class SceneGraph(val locations: Array<Entity>) {
    val locationsById = locations.associate { it.id to it }
}

class MudWorld() {
    var lastFrame = WorldFrame(arrayOf())
    var currentFrame = lastFrame
    val entityIdGen = AtomicInteger()
    val commandQueue = ArrayBlockingQueue<(WorldFrame, WorldFrameBuilder)->Unit>(1000)
    private val newFrameSubject = PublishSubject.create<Pair<WorldFrame, WorldFrame>>()

    val newFrame: Observable<Pair<WorldFrame, WorldFrame>> get() = newFrameSubject

    fun nextEntityId() = entityIdGen.andIncrement

    fun addCommand(cmd: (WorldFrame, WorldFrameBuilder)->Unit) {
        commandQueue.add(cmd)
    }

    fun process() {
        val commands = mutableListOf<(WorldFrame, WorldFrameBuilder)->Unit>()
        commandQueue.drainTo(commands)

        val currentFrameBuilder = WorldFrameBuilder(currentFrame)
        commands.forEach { command -> command(currentFrame, currentFrameBuilder) }

        currentFrame = currentFrameBuilder.build()
        newFrameSubject.onNext(lastFrame to currentFrame )
        lastFrame = currentFrame
    }
}


val nameTrait = TraitType( { "Unnamed" })
val descriptionTrait = TraitType({ "Undescribed"})
val locationTrait = TraitType( { 0 })
data class LocationLink(val destination: Int, val name: String, val commandBinding: Array<String>)

val locationLinkTrait = TraitType({ arrayOf<LocationLink>() })

fun EntityBuilder.addLocationLink(link: LocationLink) = set(locationLinkTrait, (get(locationLinkTrait)?:arrayOf()) + link)

fun north(id: Int) = LocationLink(id, "north", arrayOf("n", "north"))
fun east(id: Int) = LocationLink(id, "east", arrayOf("e", "east"))
fun south(id: Int) = LocationLink(id, "south", arrayOf("s", "south"))
fun west(id: Int) = LocationLink(id, "west", arrayOf("w", "west"))
fun down(id: Int) = LocationLink(id, "down", arrayOf("d", "down"))
fun up(id: Int) = LocationLink(id, "up", arrayOf("u", "up"))


//  TODO Entities in locations
//  TODO Different descriptions based on different conditions (embedded rule based descriptions)

interface Mud {
    fun createCommandsForInitialState(): Array<(WorldFrame, WorldFrameBuilder)->Unit>
}

open class MudCore(private val world: MudWorld) : Mud {

    override fun createCommandsForInitialState(): Array<(WorldFrame, WorldFrameBuilder)->Unit> = entityMap.values.map { { frame: WorldFrame, frameBuilder: WorldFrameBuilder -> frameBuilder.addEntity(it.build()) } }.toTypedArray()

    private val entityMap = mutableMapOf<Int, EntityBuilder>()

    protected fun createEntity(name: String, description: String): Int {
        val entity = EntityImpl(world.nextEntityId()).modify()
            .set(nameTrait, name)
            .set(descriptionTrait, description)
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun updateEntity(id: Int, builder: EntityBuilder.()->EntityBuilder) {
        val existingBuilder = entityMap[id]?:throw IllegalArgumentException("Entity with id $id does not exist")
        entityMap[id] = existingBuilder.builder()
    }

    fun linkLocations(a: Int, b: Int, aToB: (Int) -> LocationLink, bToA: (Int) -> LocationLink) {
        updateEntity(a, { addLocationLink(aToB(a)) })
        updateEntity(b, { addLocationLink(bToA(b)) })
    }

}

fun runMud(makeMud: (MudWorld)->Mud) {
    val world = MudWorld()
    makeMud(world).createCommandsForInitialState().forEach { world.addCommand(it) }
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
