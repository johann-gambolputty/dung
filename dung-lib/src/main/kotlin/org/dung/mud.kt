package org.dung

import rx.Observable
import rx.subjects.PublishSubject
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
    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder = EntityBuilderImpl(id, HashMap(traits))

    override fun build(): Entity = this

    override fun <T> get(t: TraitType<T>): T? = traits[t]?.run { this as T }
    override fun modify(): EntityBuilder = this
}


class WorldFrame(val entities: Array<Entity>) {
    val entitiesById = entities.associate { it.id to it }
}

class WorldFrameBuilder(frame: WorldFrame) {
    val entities = frame.entities.map { entity -> entity.modify() }.toMutableList()
    val entitiesById = HashMap(entities.associate { it.id to it })
    fun updateEntity(id: Int, updateFnc: (EntityBuilder)->EntityBuilder) {
        entitiesById[id]?.run { updateFnc(this) }
    }
    fun addEntity(entity: Entity) {
        val eb = entity.modify()
        entities.add(eb)
        entitiesById[eb.id] = eb
    }
    fun removeEntity(entityId: Int) {
        entities.removeAll { it.id == entityId }
        entitiesById.remove(entityId)
    }

    fun build() = WorldFrame(entities.map { it.build() }.toTypedArray())
}

val mudLocationTrait = TraitType({ 0 })

class MudClient(world: MudWorld) {
    val playerId = world.nextEntityId()
    init {
        val playerEntity = EntityImpl(playerId).modify()
                .set(mudLocationTrait, 0)
                .build()
        world.addCommand({ fb -> fb.addEntity(playerEntity)})
    }
}
interface MudClientView {
    fun show(text: String)
}

class MudWorldView(val entityId: Int, world: MudWorld, view: MudClientView) {
    init {
        world.newFrame.subscribe {
            val (lastFrame, currentFrame) = it
        }
    }
}

class MudWorld {
    var lastFrame = WorldFrame(arrayOf())
    var currentFrame = lastFrame
    val entityIdGen = AtomicInteger()
    val commandQueue = ArrayBlockingQueue<(WorldFrameBuilder)->Unit>(1000)
    private val newFrameSubject = PublishSubject.create<Pair<WorldFrame, WorldFrame>>()

    val newFrame: Observable<Pair<WorldFrame, WorldFrame>> get() = newFrameSubject

    fun nextEntityId() = entityIdGen.andIncrement

    fun addCommand(cmd: (WorldFrameBuilder)->Unit) {
        commandQueue.add(cmd)
    }

    fun process() {
        val commands = mutableListOf<(WorldFrameBuilder)->Unit>()
        commandQueue.drainTo(commands)

        val currentFrameBuilder = WorldFrameBuilder(currentFrame)
        commands.forEach { command -> command(currentFrameBuilder) }

        currentFrame = currentFrameBuilder.build()
        newFrameSubject.onNext(lastFrame.to(currentFrame))
    }
}

fun main(args: Array<String>) {

}