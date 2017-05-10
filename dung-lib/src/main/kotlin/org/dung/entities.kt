package org.dung

import java.util.*

/**
 * Entity classes
 */


interface Entity : TraitBased {
    val id: Int
    fun modify(): EntityBuilder
}

fun <T : WorldFrame> Entity.updateInNextFrame(fnc: EntityBuilder.(currentFrame: T, nextFrame: WorldFrameBuilder<T>)->EntityBuilder): WorldCommand<T> {
    val entityId = id
    return { currentFrame, nextFrame -> nextFrame.updateEntity(entityId, { this.fnc(currentFrame, nextFrame) })}
}

interface EntityBuilder : TraitBased {
    val id: Int
    fun putAll(traitValues: Map<TraitType, Any>): EntityBuilder
    fun <T> set(t: TraitTypeT<T>, value: T): EntityBuilder
    fun <T> set(t: TraitTypeT<T>, value: EntityBuilder.()->T): EntityBuilder
    fun <T> remove(t: TraitTypeT<T>): EntityBuilder
    fun build(): Entity
    fun build(id: Int): Entity
}
fun <T> EntityBuilder.set(t: ProxyApplyTraitType<T>, value: T): EntityBuilder {
    return t.apply(this, value)
}
fun <T> EntityBuilder.setDefault(t: TraitType0<T>) = set(t, t.createDefault())
fun <T> EntityBuilder.getOrCreate(t: TraitType0<T>, create: ()->T = { t.createDefault() }): T {
    var trait = get(t)
    if (trait != null) {
        return trait
    }
    trait = create()
    set(t, trait)
    return trait
}

open class EntityBuilderImpl(override val id: Int, override val traits: MutableMap<TraitType, Any> = mutableMapOf()) : EntityBuilder {

    override fun <T> set(t: TraitTypeT<T>, value: EntityBuilder.() -> T): EntityBuilder {
        funTraits.add({ set(t, value(this)) })
        return this
    }

    private val funTraits = mutableListOf<EntityBuilder.()->Unit>()

    override fun putAll(traitValues: Map<TraitType, Any>): EntityBuilder {
        traits.putAll(traitValues)
        return this
    }

    override fun <T> get(t: TraitTypeT<T>): T? {
        val traitValue = traits[t]
        return if (traitValue == null) null else traitValue as T
    }

    override fun <T> set(t: TraitTypeT<T>, value: T): EntityBuilder {
        traits[t] = value as Any
        return this
    }

    override fun <T> remove(t: TraitTypeT<T>): EntityBuilder {
        traits.remove(t)
        return this
    }

    override fun build(): Entity = build(id)
    override fun build(id: Int): Entity = EntityImpl(id, traits)
}

class EntityImpl(override val id: Int, override val traits: Map<TraitType, Any> = mapOf()) : Entity, EntityBuilder {

    override fun <T> set(t: TraitTypeT<T>, value: EntityBuilder.() -> T): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).set(t, value)

    override fun putAll(traitValues: Map<TraitType, Any>): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).putAll(traitValues)

    override fun <T> remove(t: TraitTypeT<T>): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).remove(t)

    override fun <T> set(t: TraitTypeT<T>, value: T): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).set(t, value)

    override fun build(id: Int): Entity = if (id == this.id) this else EntityImpl(id, traits)

    override fun build(): Entity = this

    override fun <T> get(t: TraitTypeT<T>): T? = traits[t]?.run { this as T }
    override fun modify(): EntityBuilder = this
}


