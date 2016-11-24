package org.dung

import rx.Observable
import rx.subjects.PublishSubject
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAmount
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger


class TraitType<T>(val createDefault: ()->T)

interface TraitBased {
    val traits: Map<TraitType<*>, Any>
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
fun <T> EntityBuilder.getOrCreate(t: TraitType<T>, create: ()->T = t.createDefault): T {
    var trait = get(t)
    if (trait != null) {
        return trait
    }
    trait = create()
    set(t, trait)
    return trait
}

class EntityBuilderImpl(override val id: Int, override val traits: MutableMap<TraitType<*>, Any>) : EntityBuilder {
    override fun <T> get(t: TraitType<T>): T? = traits[t]?.run { this as T }

    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder = apply { traits[t] = value as Any }

    override fun build(): Entity = EntityImpl(id, traits)

}

class EntityImpl(override val id: Int, override val traits: Map<TraitType<*>, Any> = mapOf()) : Entity, EntityBuilder {
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
        world.addCommand(command({ frame, frameBuilder ->
            frameBuilder.addEntity(player)
            show("Hello ${player.get(nameTrait)}")
            val playerLocationId = player.get(locationTrait)
            if (playerLocationId == null) {
                show("You are... nowhere")
            }
            else {
                show(frame.entitiesById[playerLocationId]?.get(descriptionTrait)?:"You are at an unknown location")
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
                    world.addCommand(command({ f, fb ->
                        fb.updateEntity(playerId, fun EntityBuilder.(): EntityBuilder {
                            fun processVerb(onMatch: (Entity)->Unit) {
                                if (line.size != 2) {
                                    return
                                }
                                val targetName = line[1].toUpperCase()
                                val target = f.entities.firstOrNull { e -> e.get(nameTrait)?.toUpperCase()?.equals(targetName)?:false }
                                if (target != null) {
                                    onMatch(target)
                                }
                            }
                            when (line[0]) {
                                "inspect" -> processVerb { e->show(e.get(descriptionTrait)?:"") }
                                "get" -> processVerb {
                                }
                            }
                            val currentLocation = get(locationTrait)?.run { f.entitiesById[this] }
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
                    currentFrame.entitiesById[locationInCurrentFrame]?.let { location->
                        view.show(location.get(descriptionTrait)?:"")
                        val entitiesInLocation = currentFrame.entities.filter { it.get(locationTrait) == locationInCurrentFrame }
                        if (!entitiesInLocation.isEmpty()) {
                            view.show("You can see:")
                            entitiesInLocation.forEach { view.show(it.get(nameTrait)?:"[Unnamed Thing ${it.id}]") }
                        }
                        val exits = location.get(locationLinkTrait)?:arrayOf()
                        if (exits.size == 0) {
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

class SceneGraph(val locations: Array<Entity>) {
    val locationsById = locations.associate { it.id to it }
}

interface TickableTrait {
    fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand>
}

interface WorldCommand {
    fun run(frame: WorldFrame, nextFrame: WorldFrameBuilder)
}
interface WorldEntityCommand {
    fun run(entity: Entity, frame: WorldFrame, nextFrame: WorldFrameBuilder)
}
fun command(f: (WorldFrame, WorldFrameBuilder)->Unit): WorldCommand {
    return object : WorldCommand {
        override fun run(frame: WorldFrame, nextFrame: WorldFrameBuilder)= f(frame, nextFrame)
    }
}
fun entityCommand(f: (Entity, WorldFrame, WorldFrameBuilder)->Unit): WorldEntityCommand {
    return object : WorldEntityCommand {
        override fun run(entity: Entity, frame: WorldFrame, nextFrame: WorldFrameBuilder)= f(entity, frame, nextFrame)
    }
}

class MudWorld() {
    var lastFrame = WorldFrame(arrayOf())
    var currentFrame = lastFrame
    val entityIdGen = AtomicInteger()
    val commandQueue = ArrayBlockingQueue<WorldCommand>(1000)
    private val newFrameSubject = PublishSubject.create<Pair<WorldFrame, WorldFrame>>()

    val newFrame: Observable<Pair<WorldFrame, WorldFrame>> get() = newFrameSubject

    fun nextEntityId() = entityIdGen.andIncrement

    fun addCommand(cmd: WorldCommand) {
        commandQueue.add(cmd)
    }


    fun process() {
        val commands = mutableListOf<WorldCommand>()
        commandQueue.drainTo(commands)

        val frameTimestamp = LocalDateTime.now()
        commands.addAll(currentFrame.entities.flatMap { e -> e.traits.values.filterIsInstance<TickableTrait>().flatMap { it.update(e, frameTimestamp) } })

        val currentFrameBuilder = WorldFrameBuilder(currentFrame)
        commands.forEach { command -> command.run(currentFrame, currentFrameBuilder) }

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

data class EntityScheduleEntry(val executeAt: LocalDateTime, val commandToRun: WorldEntityCommand, val nextEntries: ()->Array<EntityScheduleEntry>)
data class EntityScheduler(val schedule: List<EntityScheduleEntry> = listOf()) : TickableTrait {
    override fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand> {

        val newSchedule = mutableListOf<EntityScheduleEntry>()
        val commands = mutableListOf<WorldCommand>()
        for (entry in schedule) {
            if (entry.executeAt > updateTimestamp) {
                newSchedule.add(entry)
            }
            else {
                newSchedule.addAll(entry.nextEntries())
                if (commands.isEmpty()) {
                    commands.add(command { currentFrame, nextFrame ->
                        nextFrame.updateEntity(entity.id, { set(schedulerTrait, EntityScheduler(newSchedule) )})
                    })
                }
                commands.add(command({frame, nextFrame->entry.commandToRun.run(entity, frame, nextFrame)}))
            }
        }

        return commands
    }
    fun addEntry(entry: EntityScheduleEntry) = copy(schedule = schedule + entry)
}
val schedulerTrait = TraitType({ EntityScheduler() })

fun EntityBuilder.addToSchedule(entry: EntityScheduleEntry): EntityBuilder {
    return set(schedulerTrait, getOrCreate(schedulerTrait).addEntry(entry))
}
fun EntityBuilder.addToSchedule(delay: TemporalAmount, command: WorldEntityCommand): EntityBuilder {
    return addToSchedule(EntityScheduleEntry(LocalDateTime.now().plus(delay), command, { arrayOf() }))
}
fun repeatingSheduleEntry(nextAt: ()->LocalDateTime, command: WorldEntityCommand): EntityScheduleEntry {
    return EntityScheduleEntry(nextAt(), command, { arrayOf(repeatingSheduleEntry(nextAt, command)) })
}
fun EntityBuilder.repeatAtRandom(minDelay: TemporalAmount, maxDelay: TemporalAmount, command: WorldEntityCommand): EntityBuilder {
    return addToSchedule(repeatingSheduleEntry({ randomTimeFromNow(minDelay, maxDelay) }, command))
}
val rnd = Random(-1)
fun randomTimeFromNow(minDelay: TemporalAmount, maxDelay: TemporalAmount):LocalDateTime {
    return LocalDateTime.now() + minDelay // TODO
}

//  TODO Affordances
//  TODO Triggers
//  TODO Battles

//data class Affordance(commandBinding: Array<String>, ())

val healthTrait = TraitType( { 0 })
val strengthTrait = TraitType( { 0 })
val accuracyTrait = TraitType( { 0 })
val magicTrait = TraitType( { 0 })

//  TODO Entities in locations
//  TODO Different descriptions based on different conditions (embedded rule based descriptions)

interface Mud {
    fun createCommandsForInitialState(): Array<WorldCommand>
}

open class MudCore(private val world: MudWorld) : Mud {

    override fun createCommandsForInitialState(): Array<WorldCommand> = entityMap.values.map {command({ frame: WorldFrame, frameBuilder: WorldFrameBuilder -> frameBuilder.addEntity(it.build())}) }.toTypedArray()

    private val entityMap = mutableMapOf<Int, EntityBuilder>()

    protected fun createEntity(name: String, description: String, buildMore: EntityBuilder.()->EntityBuilder = { this }): Int {
        val entity = EntityBuilderImpl(world.nextEntityId(), mutableMapOf())
                .set(nameTrait, name)
                .set(descriptionTrait, description)
                .buildMore()
        entityMap[entity.id] = entity
        return entity.id
    }

    protected fun updateEntity(id: Int, builder: EntityBuilder.()->EntityBuilder) {
        val existingBuilder = entityMap[id]?:throw IllegalArgumentException("Entity with id $id does not exist")
        entityMap[id] = existingBuilder.builder()
    }

    fun linkLocations(a: Int, b: Int, aToB: (Int) -> LocationLink, bToA: (Int) -> LocationLink) {
        updateEntity(a, { addLocationLink(aToB(b)) })
        updateEntity(b, { addLocationLink(bToA(a)) })
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
