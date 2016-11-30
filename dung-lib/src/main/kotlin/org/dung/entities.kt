package org.dung

import java.util.*

/**
 * Entity classes
 */

interface Entity : TraitBased {
    val id: Int
    fun modify(): EntityBuilder
}

interface EntityBuilder : TraitBased {
    val id: Int
    fun <T> set(t: TraitType<T>, value: T): EntityBuilder
    fun <T> remove(t: TraitType<T>): EntityBuilder
    fun build(): Entity
}
fun <T> EntityBuilder.setDefault(t: TraitType<T>) = set(t, t.createDefault())
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
    override fun <T> get(t: TraitType<T>): T? {
        val traitValue = traits[t]
        return if (traitValue == null) null else traitValue as T
    }

    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder {
        traits[t] = value as Any
        return this
    }

    override fun <T> remove(t: TraitType<T>): EntityBuilder {
        traits.remove(t)
        return this
    }

    override fun build(): Entity = EntityImpl(id, traits)
}

class EntityImpl(override val id: Int, override val traits: Map<TraitType<*>, Any> = mapOf()) : Entity, EntityBuilder {
    override fun <T> remove(t: TraitType<T>): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).remove(t)

    override fun <T> set(t: TraitType<T>, value: T): EntityBuilder = EntityBuilderImpl(id, HashMap(traits)).set(t, value)

    override fun build(): Entity = this

    override fun <T> get(t: TraitType<T>): T? = traits[t]?.run { this as T }
    override fun modify(): EntityBuilder = this
}


