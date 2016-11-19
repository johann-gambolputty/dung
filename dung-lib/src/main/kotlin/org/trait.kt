package org


interface TraitKey {
    val name: String
}

interface TraitKeyT<T> : TraitKey {
    fun createDefault():T
}

data class TraitKeyData<T>(override val name: String, val createDefaultFn: ()->T): TraitKeyT<T> {
    override fun createDefault() = createDefaultFn()
}

interface TickableTrait {
    fun tick(entity: EntityData, lastFrame: WorldFrame, frame: WorldFrameBuilder): EntityData?
}