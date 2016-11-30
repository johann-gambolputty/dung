package org.dung

import rx.Observable
import rx.subjects.PublishSubject
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger


open class WorldFrame(private val entities: Array<Entity>) {
    private val entitiesById = entities.associate { it.id to it }
    fun getEntityById(id: Int) = entitiesById[id]
    fun getAllEntities() = entities
}


class WorldFrameBuilder<TFrame : WorldFrame>(baseFrame: TFrame, private val createFrame: (Array<Entity>)->TFrame) {
    val entitiesById = HashMap(baseFrame.getAllEntities().map { entity -> entity.modify() }.associate { it.id to it })
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

    //fun build(): TFrame = WorldFrame(entitiesById.values.map { it.build() }.toTypedArray())
    fun build(): TFrame = createFrame(entitiesById.values.map { it.build() }.toTypedArray())
}


interface TickableTrait<TFrame : WorldFrame> {
    //  TODO Should this be able to directly update an EntityBuilder
    //  NOTE Probably not because there is no equivalent capacity to update another entity
    fun update(entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<TFrame>>
}

interface WorldCommand<TFrame : WorldFrame> {
    fun run(currentFrame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)
}

interface WorldEntityCommand<TFrame : WorldFrame> {
    fun run(entity: Entity, frame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)
}

fun <TFrame : WorldFrame> command(f: (currentFrame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)->Unit): WorldCommand<TFrame> {
    return object : WorldCommand<TFrame> {
        override fun run(frame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)= f(frame, nextFrame)
    }
}

fun <TFrame : WorldFrame> entityCommand(f: (Entity, TFrame, WorldFrameBuilder<TFrame>)->Unit): WorldEntityCommand<TFrame> {
    return object : WorldEntityCommand<TFrame> {
        override fun run(entity: Entity, frame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)= f(entity, frame, nextFrame)
    }
}

interface EntityGenerator {
    fun newEntity():EntityBuilder
}

open class UpdatingWorld<TFrame : WorldFrame>(private val createFrame: (Array<Entity>)->TFrame) : EntityGenerator {
    var lastFrame = createFrame(arrayOf())
    var currentFrame = lastFrame
    val entityIdGen = AtomicInteger()
    val commandQueue = ArrayBlockingQueue<WorldCommand<TFrame>>(1000)
    private val newFrameSubject = PublishSubject.create<Pair<TFrame, TFrame>>()

    val newFrame: Observable<Pair<TFrame, TFrame>> get() = newFrameSubject

    fun nextEntityId() = entityIdGen.andIncrement

    fun addCommand(cmd: WorldCommand<TFrame>) {
        commandQueue.add(cmd)
    }

    override fun newEntity(): EntityBuilder = EntityBuilderImpl(nextEntityId(), mutableMapOf())

    fun process() {
        val commands = mutableListOf<WorldCommand<TFrame>>()
        commandQueue.drainTo(commands)

        val frameTimestamp = LocalDateTime.now()
        commands.addAll(currentFrame.getAllEntities().flatMap { e -> e.traits.values.filterIsInstance<TickableTrait<TFrame>>().flatMap { it.update(e, frameTimestamp) } })

        val currentFrameBuilder = WorldFrameBuilder<TFrame>(currentFrame, createFrame)
        commands.forEach { command -> command.run(currentFrame, currentFrameBuilder) }

        currentFrame = currentFrameBuilder.build()
        newFrameSubject.onNext(lastFrame to currentFrame )
        lastFrame = currentFrame
    }
}

//  TODO Affordances
//  TODO Triggers
//  TODO Battles
//  TODO Different descriptions based on different conditions (embedded rule based descriptions)

interface WorldInitializer<TFrame : WorldFrame> {
    fun createCommandsForInitialState(): Array<WorldCommand<TFrame>>
}

