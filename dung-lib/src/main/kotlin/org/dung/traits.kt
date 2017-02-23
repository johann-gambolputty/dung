package org.dung

import com.fasterxml.jackson.databind.JsonNode

/**
 * Trait-based object support
 */

interface ITraitType<T> {
    val traitName: String
    fun createDefault(): T
    fun fromJson(node: JsonNode, entityResolver: JsonEntityResolver): T
}

class TraitTypeMaker() {
    private val allTraitTypes = mutableListOf<TraitType<*>>()
    private val traitTypeByName = mutableMapOf<String, TraitType<*>>()

    fun add(trait: TraitType<*>) = apply {
        allTraitTypes.add(trait)
        traitTypeByName[trait.traitName] = trait
    }
    fun add(traits: List<TraitType<*>>) = apply { traits.forEach { add(it)} }

    fun <T> newTrait(name: String, createDefault: ()-> T, fromJson: (JsonNode)->T?): TraitType<T> {
        val trait = TraitType(name, createDefault, fromJson)
        add(trait)
        return trait
    }
    fun <T> newTrait(name: String, createDefault: ()-> T, fromJson: (JsonNode, JsonEntityResolver)->T?): TraitType<T> {
        val trait = TraitType(name, createDefault, fromJson)
        add(trait)
        return trait
    }
    fun <T> newTraitWithNoDefault(name: String, fromJson: (JsonNode)->T?): TraitType<T> = newTrait(name, { throw RuntimeException("Cannot create default for trait type $name") }, fromJson)
    fun <T> newTraitWithNoDefault(name: String, fromJson: (JsonNode, JsonEntityResolver)->T?): TraitType<T> = newTrait(name, { throw RuntimeException("Cannot create default for trait type $name") }, fromJson)
    val traits : List<TraitType<*>> get() = allTraitTypes
    fun findTraitType(traitName: String): TraitType<*> = traitTypeByName[traitName]?:throw RuntimeException("Cannot find trait type named $traitName")
}

data class TraitType<T> (override val traitName: String, val createDefaultFun: ()->T, val fromJsonFun: (JsonNode, JsonEntityResolver)->T?) : ITraitType<T> {
    constructor(traitName: String, createDefaultFun: ()->T, fromJson: (JsonNode)->T?) :
            this(traitName, createDefaultFun, { node, entityResolver -> fromJson(node) }) {
    }

    override fun createDefault(): T = createDefaultFun()

    override fun fromJson(node: JsonNode, entityResolver: JsonEntityResolver): T = fromJsonFun(node, entityResolver)?:createDefault()

    override fun toString() = traitName
}

interface TraitBased {
    val traits: Map<TraitType<*>, Any>
    operator fun <T> get(t: TraitType<T>): T?
}
inline fun <reified T> TraitBased.traitsOf() = traits.values.filterIsInstance<T>()
fun <T> TraitBased.get(t: TraitType<T>, defaultValue: T) = get(t)?:defaultValue
fun TraitBased.getByName(t: String): Any? = traits.entries.firstOrNull { entry -> entry.key.traitName == t }?.value
val commonTraitTypes = TraitTypeMaker()
val removeFromWorldTrait = commonTraitTypes.newTrait("removeFromWorld", { false }, { node -> node.valueBoolean() })
