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
    fun <T> entitiesWithTraitValue(startingLocation: TraitTypeT<T>, value: T) = entities.filter { entity -> entity[startingLocation] == value }
}

class WorldFrameBuilder<TFrame : WorldFrame>(baseFrame: TFrame, private val entityGen: EntityGenerator, private val createFrame: (Array<Entity>)->TFrame) {
    val entitiesById = HashMap(baseFrame.getAllEntities().map { entity -> entity.modify() }.associate { it.id to it })
    fun newEntity() = entityGen.newEntity()
    fun newEntityId() = entityGen.newEntityId()
    fun updateEntity(id: Int, updateFnc: EntityBuilder.()->EntityBuilder): WorldFrameBuilder<TFrame> {
        entitiesById[id]?.run { entitiesById[id] = updateFnc() }
        return this
    }
    fun addEntity(entity: Entity) {
        val eb = entity.modify()
        entitiesById[eb.id] = eb
    }
    fun removeEntity(entityId: Int) {
        entitiesById.remove(entityId)
    }

    fun build(): TFrame = createFrame(entitiesById.values.filter { !it.get(removeFromWorldTrait, false) }.map { it.build() }.toTypedArray())
}


interface TickableTrait<TFrame : WorldFrame> {
    //  TODO Should this be able to directly update an EntityBuilder
    //  NOTE Probably not because there is no equivalent capacity to update another entity
    fun update(frame: TFrame, entity: Entity, updateTimestamp: LocalDateTime): List<WorldCommand<TFrame>>
}

typealias WorldCommand<TFrame> = (currentFrame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)->Unit
typealias WorldEntityCommand<TFrame> = (entity: Entity, currentFrame: TFrame, nextFrame: WorldFrameBuilder<TFrame>)->Unit

fun <TFrame : WorldFrame> updateEntityCommand(entityId: Int, command: EntityBuilder.()->EntityBuilder): WorldCommand<TFrame> =
        { _, nextFrame -> nextFrame.updateEntity(entityId, command) }

interface EntityGenerator {
    fun newEntity():EntityBuilder
    fun newEntityId(): Int
}

open class UpdatingWorld<TFrame : WorldFrame>(private val createFrame: (Array<Entity>)->TFrame) : EntityGenerator {


    var lastFrame = createFrame(arrayOf())
    var currentFrame = lastFrame
    val entityIdGen = AtomicInteger()
    val commandQueue = ArrayBlockingQueue<WorldCommand<TFrame>>(1000)
    private val newFrameSubject = PublishSubject.create<Pair<TFrame, TFrame>>()

    val newFrame: Observable<Pair<TFrame, TFrame>> get() = newFrameSubject

    override fun newEntityId(): Int = entityIdGen.andIncrement

    fun addCommand(cmd: WorldCommand<TFrame>) {
        commandQueue.add(cmd)
    }

    override fun newEntity(): EntityBuilder = EntityBuilderImpl(newEntityId(), mutableMapOf())

    fun process() {
        val commands = mutableListOf<WorldCommand<TFrame>>()
        commandQueue.drainTo(commands)

        val frameTimestamp = LocalDateTime.now()
        commands.addAll(currentFrame.getAllEntities().flatMap { e -> e.traitsOf<TickableTrait<TFrame>>().flatMap { it.update(currentFrame, e, frameTimestamp) } })

        val currentFrameBuilder = WorldFrameBuilder<TFrame>(currentFrame, this, createFrame)
        commands.forEach { command -> command(currentFrame, currentFrameBuilder) }

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

