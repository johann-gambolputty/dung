package org.dung.mud

import org.dung.*

data class LocationId(val id: Int)

class ScriptContext(val entityGenerator: EntityGenerator, val locationIdToEntityId: Map<LocationId, Int>) {
    fun locationEntityId(locationId: LocationId): Int = locationIdToEntityId.get(locationId)?:throw RuntimeException("Cannot find entity ID for location with ID $locationId")
}

class TraitAndValue(val trait: TraitType, val add:(EntityBuilder, ScriptContext)->Unit)


open class ObjectTemplate {

    val traits = mutableMapOf<TraitType, TraitAndValue>()

    infix fun <T> TraitTypeT<T>.eq(value: T): Unit {
        traits[this] = TraitAndValue(this, { eb, _ ->
            eb.set(this, value)
        })
    }

    infix fun TraitTypeT<String>.eq(value: String): Unit {
        traits[this] = TraitAndValue(this, { eb, _ -> eb.set(this, value.trimMargin())})
    }

    infix fun <T> TraitType1<T, Int>.eq(value: LocationId): Unit {
        traits[this] = TraitAndValue(this, { eb, context -> eb.set(this, create(context.locationEntityId(value)))})
    }

    operator fun <T> TraitType0<T>.invoke(): Unit {
        traits[this] = TraitAndValue(this, { eb, _ -> eb.set(this, createDefault())})
    }

    operator inline fun <reified T :  Any> TraitType0<T>.invoke(noinline createValue:EntityBuilder.()->T): Unit {
        val createNiceValue: EntityBuilder.()->T = {
            val result = createValue()
            if (result is String) result.toString().trimMargin() as T else result
        }
        traits[this] = TraitAndValue(this, { eb, _ -> eb.set(this, createNiceValue(eb)) })
    }

    fun toEntities(context: ScriptContext, locationEntityId: Int): List<Entity> {
        val builder = context.entityGenerator.newEntity()
        builder.set(locationTrait, locationEntityId)
        traits.values.forEach { traitAndValue -> traitAndValue.add(builder, context) }
        return listOf(builder.build())
    }
}

class LocationTemplate(val locationId: LocationId, private val build: LocationTemplate.() -> Unit) : ObjectTemplate() {

    val entities = mutableListOf<EntityTemplate>()

    operator fun LocationTemplate.unaryPlus(): Unit {
        this@LocationTemplate.traits.putAll(this.traits)
    }

    operator fun EntityTemplate.unaryPlus() : Unit {
        entities.add(this)
    }

    operator fun EntityTemplate.invoke(): Unit {
        entities.add(this)
    }

    operator fun EntityTemplate.invoke(create: ObjectTemplate.()->Unit):Unit {
        val baseTemplate = this
        entities.add(EntityTemplate().apply {
            baseTemplate()
            create()
        })
    }


    fun  toEntities(context: ScriptContext): List<Entity> {
        build()
        val locationEntityId = context.locationEntityId(locationId)
        val builder = EntityBuilderImpl(locationEntityId, mutableMapOf())
        traits.values.forEach { trait -> trait.add(builder, context) }
        return listOf(builder.build()) + entities.flatMap { entityTemplate -> entityTemplate.toEntities(context, locationEntityId) }
    }
}

class EntityTemplate : ObjectTemplate() {

    operator fun EntityTemplate.invoke(): Unit {
        this@EntityTemplate.traits.putAll(this.traits)
    }
}

fun entityTemplate(buildTemplate: EntityTemplate.()->Unit): EntityTemplate =
        EntityTemplate().apply { buildTemplate() }

open class MudWorldBuilder {
    private var currentLocationId: Int = 0
    val locationMap = mutableMapOf<LocationId, LocationTemplate>()
    protected operator fun EntityTemplate.invoke(build: EntityTemplate.()->Unit): EntityTemplate {
        var baseTemplate = this
        return EntityTemplate().apply {
            baseTemplate()
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
