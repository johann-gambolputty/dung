package org.dung

/**
 * Trait-based object support
 */

data class TraitType<T> (val traitName: String, val createDefault: ()->T) {
    override fun toString() = traitName
}

interface TraitBased {
    val traits: Map<TraitType<*>, Any>
    fun <T> get(t: TraitType<T>): T?
}
