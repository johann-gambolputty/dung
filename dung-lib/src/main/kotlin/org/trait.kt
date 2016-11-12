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

//  Default traits
val tickableTrait = TraitKeyData<EntityData.(WorldFrame)->Array<WorldFrameEntityCommand>>("tickable", { { frame -> arrayOf(WorldFrameEntityCommandCopy(this)) } })
