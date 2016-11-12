package org

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

data class WorldFrame(val entities: Array<EntityData>, val events: Array<EventData>) {
    fun makeNextFrame(): WorldFrame {
        val nextFrameEntities = entities.fold(entities.asSequence(), { seq, entity -> foldEntities(seq, entity) }).toList().toTypedArray()
        return WorldFrame(nextFrameEntities)
    }

    fun foldEntities(entities: Sequence<EntityData>, entity: EntityData) = (entity.trait(tickableTrait)?.invoke(entity, this)?: arrayOf(WorldFrameEntityCommandCopy(entity))).fold(entities, { seq, cmd -> cmd.apply(seq) })
}