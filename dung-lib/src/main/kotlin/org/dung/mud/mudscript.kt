package org.dung.mud

import org.dung.EntityBuilder
import org.dung.EntityBuilderImpl

fun entityTemplate(buildTemplate: EntityBuilder.()->Unit): EntityBuilder =
        EntityBuilderImpl(0).apply { buildTemplate() }

fun EntityBuilder.derive(buildTemplate: EntityBuilder.() -> Unit): EntityBuilder =
        EntityBuilderImpl(0, this.traits.toMutableMap()).apply { buildTemplate() }

typealias LocationId = Int
class MudLocationBuilder(val locationId: LocationId) {
    val entities = mutableListOf<EntityBuilder>()
    fun linksTo(locationId: LocationId) {
    }
    operator fun EntityBuilder.unaryPlus(): Unit {
        entities.add(this)
    }
}
class MudWorldBuilder {
    fun location(build: MudLocationBuilder.()->Unit):LocationId=0
}
fun buildMud(build: MudWorldBuilder.()->Unit): MudWorldBuilder =
        MudWorldBuilder().apply { build() }