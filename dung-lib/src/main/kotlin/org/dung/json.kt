package org.dung

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val jsonTimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")

fun JsonNode.valueBoolean(valueField: String="value") = this[valueField]?.textValue()?.toBoolean()
fun JsonNode.valueString(valueField: String="value") = this[valueField]?.textValue()
fun JsonNode.valueInt(valueField: String="value") = this[valueField]?.textValue()?.toInt()
fun JsonNode.valueTimestamp(valueField: String="value") = this[valueField]?.textValue()?.let { LocalDateTime.parse(it, jsonTimestampFormat) }

interface JsonEntityResolver {
    fun entityId(entityRefNode: JsonNode): Int
    fun loadEntities(node: JsonNode): Array<Entity>
}

fun JsonEntityResolver.entityId(node: JsonNode, entityRefNodeName: String): Int = entityId(node[entityRefNodeName])
fun <TFrame : WorldFrame> createWorldInitializerFromJson(source: URL, traitTypeFromName: (name: String)->TraitType<*>): (UpdatingWorld<TFrame>)-> WorldInitializer<TFrame> {
    val jsonTree = ObjectMapper().readTree(source)

    if (!jsonTree.isArray) {
        throw RuntimeJsonMappingException("Expected array as root, was " + jsonTree.nodeType)
    }

    val entityThingType = 0
    val templateThingType = 1

    class EntityTemplate(val name: String, node: JsonNode, entityResolver: JsonEntityResolver, templatesByName: Map<String, EntityTemplate>) {
        val entityBuilder = EntityBuilderImpl(0)
         init {
             this.apply {
                 val inherits = node["inherits"]
                         ?.run { if (isArray()) toList().filterNotNull().map { it.textValue() }.filterNotNull() else listOf(textValue()?:throw com.fasterxml.jackson.databind.RuntimeJsonMappingException("Unexpected node type in inherits section " + nodeType)) }
                         ?.map { templatesByName[it]?:throw RuntimeJsonMappingException("Template ${name} tried to inherit template named ${it}, but it has not been defined (yet)") }
                         ?:listOf()
                 entityBuilder.traits.putAll(inherits.flatMap { it.entityBuilder.traits.entries.map { Pair(it.key, it.value) } })
                 val traits = node["traits"] ?: return@apply
                 if (!traits.isArray) {
                     throw RuntimeJsonMappingException("Expected traits to be an array, was " + traits.nodeType)
                 }
                 entityBuilder.traits.putAll(traits
                         .toList()
                         .filterNotNull()
                         .map {
                             fun extractTrait(): TraitType<*> {
                                 val dynTraitName = it["dyntrait"]?.textValue()
                                 if (dynTraitName != null) {
                                     return TraitType<String>(dynTraitName, { throw RuntimeException("Did not expect to call default on dynamic trait")}, { node -> node.valueString() })
                                 }
                                 val traitName = it["trait"]?.textValue()?:throw RuntimeJsonMappingException("Expected trait name")
                                 return traitTypeFromName(traitName)
                             }
                             val traitType = extractTrait()
                             Pair(traitType, traitType.fromJson(it, entityResolver)?:throw RuntimeJsonMappingException("Null returned from trait instance creation for trait ${traitType.traitName}"))
                         }
                 )
             }
        }
    }

    val templatesByName = mutableMapOf<String, EntityTemplate>()
    val entities = mutableListOf<Entity>()
    val entitiesByName = mutableMapOf<String, Entity>()
    val entitiesById = mutableMapOf<Int, Entity>()

    fun getName(node: JsonNode): String = node["name"]?.textValue()?:throw RuntimeJsonMappingException("Expected name in thing " + node)

    val entityResolver = object : JsonEntityResolver {
        override fun loadEntities(node: JsonNode): Array<Entity> {
            if (!node.isArray) {
                throw RuntimeJsonMappingException("Can only load entities from an array node")
            }
            val entities = mutableListOf<Entity>()
            for (i in 0..node.size()) {
                val entityNode = node[i]
                entities.add(EntityTemplate(getName(entityNode), entityNode, this, templatesByName).entityBuilder.build())
            }
            return entities.toTypedArray()
        }

        override fun entityId(entityRefNode: JsonNode) =
                (when(entityRefNode.nodeType) {
                    JsonNodeType.NUMBER -> entityRefNode.numberValue().toInt()
                    JsonNodeType.STRING -> entitiesByName[entityRefNode.textValue()]?.id?:entityRefNode.textValue().toInt()
                    else -> null
                })?:throw RuntimeJsonMappingException("Unable to map entity reference node ${entityRefNode} to an entity ID")

    }

    fun loadTemplate(node: JsonNode) {
        val template = EntityTemplate(getName(node), node, entityResolver, templatesByName)
        templatesByName[template.name] = template
    }

    fun loadEntity(node: JsonNode) {
        val name = getName(node)
        val entity = EntityTemplate(name, node, entityResolver, templatesByName).entityBuilder.build()
        entities.add(entity)
        entitiesById[entity.id] = entity
        entitiesByName[name] = entity
    }

    fun  getThingType(thing: JsonNode): Int? {
        val thingType = thing["thing"] ?: return entityThingType
        return when (thingType.textValue()) {
            null -> entityThingType
            "entity" -> entityThingType
            "entityTemplate" -> templateThingType
            else -> throw RuntimeJsonMappingException("Unexpected thing type " + thingType.textValue())
        }
    }

    for (i in 0..jsonTree.size()) {
        val thing = jsonTree[i] ?: continue
        try {
            val thingType = getThingType(thing)
            when (thingType) {
                entityThingType -> loadEntity(thing)
                templateThingType -> loadTemplate(thing)
            }
        }
        catch (e: Exception) {
            throw RuntimeException("Error parsing json at " + thing, e)
        }
    }

    return { world -> object : WorldInitializer<TFrame> {
        override fun createCommandsForInitialState(): Array<WorldCommand<TFrame>> {
            return entities.map { entity -> command { currentFrame: TFrame, nextFrame -> nextFrame.addEntity(entity) } }.toTypedArray()
        }
    } }
}
