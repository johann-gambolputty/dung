package org

data class KeyedTrait(val key: TraitKey, val trait: Any)

data class EntityData(val id: Int, val traits: Array<KeyedTrait>) {
    private val traitMap = traits.associate { it.key.to(it.trait) }
    fun <T> addMadeTrait(key: TraitKeyT<T>, makeTrait: (EntityData)->T) = addTrait(key, makeTrait(this))
    fun <T> addTrait(key: TraitKeyT<T>, trait: T) = copy(traits = traits + KeyedTrait(key, trait as Any))
    fun <T> addNewTrait(key: TraitKeyT<T>, process: T.()->Unit) = addTrait(key, key.createDefault().apply{ process() })
    fun <T> processNewOrExistingTrait(key: TraitKeyT<T>, process: T.()->Unit): EntityData {
        val trait = traitMap[key]
        if (trait != null) {
            (trait as T).process()
            return this
        }
        return addNewTrait(key, process)
    }
    fun removeTrait(key: TraitKey) = copy(traits = traits.filter { it.key != key }.toTypedArray())
    fun <T> trait(key: TraitKeyT<T>): T? {
        val trait: Any? = traitMap[key]
        return if (trait == null) null else (trait as? T)
    }

    constructor(id: Int) : this(id, arrayOf()) {

    }
}