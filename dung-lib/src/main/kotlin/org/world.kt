package org

import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

//
//  Server loop
//      Poll for inputs
//      Build new frame
//
//  Client input: request/reply to server, synchronized to server loop
//  Client loop: Receive push messages from server
//
//
interface WorldFrameEntityCommand {
    fun apply(entities: Sequence<EntityData>): Sequence<EntityData>
}

class WorldFrameEntityCommandCopy(val entity: EntityData) : WorldFrameEntityCommand {
    override fun apply(entities: Sequence<EntityData>) = entities + entity
}

class WorldFrameEntityCommandDelete(val predicate: (EntityData)->Boolean) : WorldFrameEntityCommand {
    override fun apply(entities: Sequence<EntityData>) = entities.filter(predicate)
}

class WorldFrameEntityCommandModify(val predicate: (EntityData)->Boolean, val modify: (EntityData)->EntityData) : WorldFrameEntityCommand {
    override fun apply(entities : Sequence<EntityData>) = entities.map { if (predicate(it)) modify(it) else it }
}


interface WorldFrameBuilder {
    fun getEntity(id: Int): EntityData?
    val entities: Collection<EntityData>
}

class WorldFrame(val entities: Array<EntityData>) {
    private val entityMap = entities.associate { it.id.to(it) }

    fun makeNextFrame(additionalEntities: Array<EntityData> = arrayOf()): WorldFrame {
        val lastFrameEntities = entities
        val builder = object: WorldFrameBuilder {
            val entityMap = HashMap((lastFrameEntities + additionalEntities).associate { it.id to it })
            override fun getEntity(id: Int) = entityMap[id]
            override val entities: Collection<EntityData> get() = entityMap.values
        }
        for (entity in entities) {
            for (tickable in entity.traits.map { it.trait }.filterIsInstance<TickableTrait>()) {
                val newEntity = tickable.tick(entity, this, builder)
                if (newEntity != null) {
                    builder.entityMap[newEntity.id] = newEntity
                } else {
                    builder.entityMap.remove(entity.id)
                }
            }
        }
        //val nextFrameEntities = entities.fold(entities.asSequence(), { seq, entity -> foldEntities(seq, entity) }).toList().toTypedArray()
        return WorldFrame(builder.entityMap.values.toTypedArray())
    }

    fun  getEntity(id: Int) = entityMap[id]

}

interface WorldCommand {
    fun apply(frameBuilder: WorldFrameBuilder)
}

class World {
    var currentFrame:WorldFrame = WorldFrame(arrayOf())
    var commandQueue: BlockingQueue<WorldCommand> = ArrayBlockingQueue(1000)

    fun process() {

    }
}