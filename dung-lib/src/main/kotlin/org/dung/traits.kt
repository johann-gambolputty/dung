package org.dung

import com.fasterxml.jackson.databind.JsonNode

/**
 * Trait-based object support
 */

fun <T> no_default(): ()->T {
    return { throw RuntimeException("No default value for this trait") }
}

fun <T> no_json(): (JsonNode, JsonEntityResolver)->T? {
    return { _, _ -> throw RuntimeException("No json support") }
}

class TraitTypeMaker() {
    private val allTraitTypes = mutableListOf<TraitType>()
    private val traitTypeByName = mutableMapOf<String, TraitType>()

    fun add(trait: TraitType) = apply {
        allTraitTypes.add(trait)
        traitTypeByName[trait.traitName] = trait
    }
    fun add(traits: List<TraitType>): TraitTypeMaker = apply { traits.forEach { add(it)} }

    fun <T, TP0> newTrait(name: String, create:(TP0)->T): TraitType1<T, TP0> = newTrait(name, create, no_json())

    fun <T, TP0> newTrait(name: String, create:(TP0)->T, fromJson: (JsonNode, JsonEntityResolver)->T?): TraitType1<T, TP0> {
        val trait = TraitType1Impl(name, create, fromJson)
        add(trait)
        return trait
    }

    fun <T> newTrait(name: String, createDefault: ()-> T, fromJson: (JsonNode)->T?): TraitType0<T> =
            newTrait(name, createDefault, { node, _ -> fromJson(node)})

    fun <T> newTrait(name: String, createDefault: ()-> T, fromJson: (JsonNode, JsonEntityResolver)->T?): TraitType0<T> {
        val trait = TraitType0Impl(name, createDefault, fromJson)
        add(trait)
        return trait
    }
    fun <T : TraitType> newTrait(traitType: T): T {
        add(traitType)
        return traitType
    }
    val traits : List<TraitType> get() = allTraitTypes
    fun findTraitType(traitName: String): TraitType = traitTypeByName[traitName]?:throw RuntimeException("Cannot find trait type named $traitName")
}

interface TraitType {
    val traitName: String
}

interface TraitTypeT<T> : TraitType {
    fun fromJson(node: JsonNode, entityResolver: JsonEntityResolver):T?
}

class ProxyTraitType<TP, T>(val trait: TraitTypeT<T>, val map: (value: TP)->T)

class ProxyApplyTraitType<T>(val apply: EntityBuilder.(value: T)->EntityBuilder)

interface TraitType0<T> : TraitTypeT<T> {
    fun createDefault():T
}


interface TraitType1<T, TP0> : TraitTypeT<T> {
    fun create(p: TP0):T
}

open class TraitType0Impl<T>(override val traitName: String, private val createFn: ()->T, private val fromJsonFn: (JsonNode, JsonEntityResolver)->T?) : TraitType0<T> {
    override fun fromJson(node: JsonNode, entityResolver: JsonEntityResolver): T? = fromJsonFn(node, entityResolver)
    override fun createDefault(): T = createFn()
    override fun toString(): String = traitName
}

private class TraitType1Impl<T, TP0>(override val traitName: String, private val createFn: (TP0) -> T, private val fromJsonFn: (JsonNode, JsonEntityResolver) -> T?) : TraitType1<T, TP0> {
    override fun fromJson(node: JsonNode, entityResolver: JsonEntityResolver): T? = fromJsonFn(node, entityResolver)

    override fun create(p: TP0): T = createFn(p)
}

interface TraitBased {
    val traits: Map<TraitType, Any>
    operator fun <T> get(t: TraitTypeT<T>): T?
}
inline fun <reified T> TraitBased.traitsOf() = traits.values.filterIsInstance<T>()
fun <T> TraitBased.get(t: TraitTypeT<T>, defaultValue: T) = get(t)?:defaultValue
fun TraitBased.getByName(t: String): Any? = traits.entries.firstOrNull { entry -> entry.key.traitName == t }?.value
val commonTraitTypes = TraitTypeMaker()
val removeFromWorldTrait = commonTraitTypes.newTrait("removeFromWorld", { false }, { node -> node.valueBoolean() })
