package org.dung.mud

import org.dung.*

data class LocationId(val id: Int)

class ScriptContext(val entityGenerator: EntityGenerator, val locationIdToEntityId: Map<LocationId, Int>) {
    fun locationEntityId(locationId: LocationId): Int = locationIdToEntityId.get(locationId)?:throw RuntimeException("Cannot find entity ID for location with ID $locationId")
}

class TraitAndValue(val trait: TraitType, val add:(EntityBuilder, ScriptContext)->Unit)

open class ObjectTemplate {

    val setup = mutableListOf<(EntityBuilder, ScriptContext)->Unit>()

    fun inherit(template: EntityTemplate) {
        setup.addAll(template.setup)
    }

    infix fun <T> ProxyApplyTraitType<T>.set(value: T): Unit {
        setup.add({eb, _ -> this.apply(eb, value) })
    }

    infix fun <TP, T> ProxyTraitType<TP, T>.set(value: TP): Unit {
        this.trait.set(this.map(value))
    }

    infix fun <T> TraitTypeT<T>.set(value: T): Unit {
        setup.add({eb, _ -> eb.set(this, value)})
    }

    infix fun TraitTypeT<String>.set(value: String): Unit {
        setup.add({eb, _ -> eb.set(this, value.trimMargin())})
    }

    infix fun <T> TraitType1<T, Int>.set(value: LocationId): Unit {
        setup.add({eb, context -> eb.set(this, create(context.locationEntityId(value)))})
    }

    operator fun <T> TraitType0<T>.invoke(): Unit {
        setup.add({ eb, _ -> eb.set(this, createDefault())})
    }

    operator inline fun <reified T :  Any> TraitType0<T>.invoke(noinline createValue:EntityBuilder.()->T): Unit {
        val createNiceValue: EntityBuilder.()->T = {
            val result = createValue()
            if (result is String) result.toString().trimMargin() as T else result
        }
        setup.add({ eb, _ -> eb.set(this, createNiceValue(eb)) })
    }

    fun toEntities(context: ScriptContext, locationEntityId: Int): List<Entity> {
        val builder = context.entityGenerator.newEntity()
        builder.set(location, locationEntityId)
        setup.forEach { it(builder, context) }
        return listOf(builder.build())
    }
}

class LocationTemplate(val locationId: LocationId, private val build: LocationTemplate.() -> Unit) : ObjectTemplate() {

    val entities = mutableListOf<EntityTemplate>()

    operator fun EntityTemplate.unaryPlus() : Unit {
        entities.add(this)
    }

    operator fun EntityTemplate.invoke(create: ObjectTemplate.()->Unit):Unit {
        val baseTemplate = this
        entities.add(EntityTemplate().apply {
            inherit(baseTemplate)
            create()
        })
    }


    fun  toEntities(context: ScriptContext): List<Entity> {
        build()
        val locationEntityId = context.locationEntityId(locationId)
        val builder = EntityBuilderImpl(locationEntityId, mutableMapOf())
        setup.forEach { it(builder, context) }
        return listOf(builder.build()) + entities.flatMap { entityTemplate -> entityTemplate.toEntities(context, locationEntityId) }
    }
}

class EntityTemplate : ObjectTemplate() {
}

fun entityTemplate(buildTemplate: EntityTemplate.()->Unit): EntityTemplate =
        EntityTemplate().apply { buildTemplate() }

open class MudWorldBuilder {
    private var currentLocationId: Int = 0
    val locationMap = mutableMapOf<LocationId, LocationTemplate>()
    protected operator fun EntityTemplate.invoke(build: EntityTemplate.()->Unit): EntityTemplate {
        val baseTemplate = this
        return EntityTemplate().apply {
            inherit(baseTemplate)
            build()
        }
    }
    fun location(build: LocationTemplate.()->Unit):LocationId {
        val id = LocationId(++currentLocationId)
        locationMap[id] = LocationTemplate(id, build)
        return id
    }
    fun build(world: UpdatingMudWorld) {
        val locationIdToEntityId = locationMap.mapValues { world.newEntityId() }
        val context = ScriptContext(world, locationIdToEntityId)
        val locationEntities = locationMap.values.flatMap { location -> location.toEntities(context)}
        world.addCommand({ _, nextFrame ->
            locationEntities.forEach({ entity -> nextFrame.addEntity(entity) })
        })
    }
}
