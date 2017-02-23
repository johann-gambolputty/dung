package org.dung

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.databind.node.JsonNodeType
import org.dung.mud.locationTrait
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private val jsonTimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")

fun JsonNode.valueBoolean(valueField: String="value") = this[valueField]?.textValue()?.toBoolean()
fun JsonNode.valueString(valueField: String="value") = this[valueField]?.textValue()
fun JsonNode.valueInt(valueField: String="value") = this[valueField]?.textValue()?.toInt()
fun JsonNode.valueTimestamp(valueField: String="value") = this[valueField]?.textValue()?.let { LocalDateTime.parse(it, jsonTimestampFormat) }

interface JsonEntityResolver {
    fun entityId(entityRefNode: JsonNode): Int
    fun loadEntities(node: JsonNode): Array<EntityBuilder>
}

fun JsonEntityResolver.entityId(node: JsonNode, entityRefNodeName: String): Int = entityId(node[entityRefNodeName])
fun <TFrame : WorldFrame> createWorldInitializerFromJson(source: URL, traitTypeFromName: (name: String)->TraitType<*>, entityGenerator: EntityGenerator): (UpdatingWorld<TFrame>)-> WorldInitializer<TFrame> {
    val jsonTree = ObjectMapper().readTree(source)

    if (!jsonTree.isArray) {
        throw RuntimeJsonMappingException("Expected array as root, was " + jsonTree.nodeType)
    }

    val entityThingType = 0
    val templateThingType = 1

    /*

        unify f("", x->x.length) with f<T, X>(T, Function<T, X> l)

         - unify String with T: f {T=String}
         - unify lambda<A, B> with Function<T, X> - Function { T=String, A=T, B=X }
            - After evaluation of lambda - Function { T=String, A=T, B=X, X=Int }
            - When binding a variable in a to type t in type environment A
                - Find existing mapping for type var a of x
                - If no mapping exists, add binding for a to t
                - Otherwise
                    - If type t is a variable type then add a binding from t to x
                    - Else if type x is a variable type then add a binding from from x to t (recursive)
                    - Else if type t and type x are incompatible, throw an exception
            - Algorithm for merging type environments A and B:
                - Create new empty environment C
                - foreach var a in A bound to type t
                    - Add binding in C from a to t
                - foreach var b in B bound to type t
                    - Add binding in C from b to T

          Alternate algorithm:
            C<Y>
            A<X> : C<X> - A has internal environment {Y=X} - this is *inherited* by all unification results
            Type environment variable resolution iterates over inheritance chain to resolve vars
            f<S, T>(A {X=S, {Y=X}}, A{X=T {Y=X}})
            unify f(A{X=String {Y=X}}, A{X=Int, {Y=X}})

            --> unify A{X=String, {Y=X}} with A{X=S, {Y=X}}
            -->     unify X{X=String} with X{X=S}
            -->         Follow X{X=String} --> String
            -->         Follow X{X=S} --> S
            -->             unify String with S
             -->            S{S=String}
            -->     X {X=S{S=String}} -- would be good to get X {X=S, S=String}; collapse S{S=String} when binding
            --> A{X=S, S=String}

            pseudocode
            unify class to class
                - exit if var lists are unequal sizes
                - foreach A.var, B.var pair
                    unify A.var, B.var=C.var
                    add B.var=C.var to type environment
                - new type environment inherits B.env
                - return B with new type environment
            unify var S to var T
                - follow var S in type env(S) --> S'
                - follow var T in type env(T) --> T'
                - if S'!=S or T'!=T
                    - unify S', T'
                - else FAIL! ambiguous type vars
            unify var S to type T
                - T{S=T}
             unify type S to var T
                - T{T=S}




     */

    class EntityTemplate(val name: String, node: JsonNode, val entityBuilder: EntityBuilder, entityResolver: JsonEntityResolver, templatesByName: Map<String, EntityTemplate>) {
        //val entityBuilder = EntityBuilderImpl(0)
         init {
             this.apply {
                 val inherits = node["inherits"]
                         ?.run { if (isArray()) toList().filterNotNull().map { it.textValue() }.filterNotNull() else listOf(textValue()?:throw com.fasterxml.jackson.databind.RuntimeJsonMappingException("Unexpected node type in inherits section " + nodeType)) }
                         ?.map { templatesByName[it]?:throw RuntimeJsonMappingException("Template ${name} tried to inherit template named ${it}, but it has not been defined (yet)") }
                         ?:listOf()
                 entityBuilder.putAll(inherits.flatMap { it.entityBuilder.traits.entries.map { Pair(it.key, it.value) } }.toMap())

                 for (field in node.fields()) {
                     if (field.key == null) {
                          continue
                     }
                     if (field.key.startsWith("trait-")) {
                         fun <T> addTrait(t: TraitType<T>) {
                             val traitValue: T = t.fromJson(field.value, entityResolver) ?: throw RuntimeJsonMappingException("Null returned from trait instance creation for trait ${t.traitName}")
                             entityBuilder.set(t, traitValue)
                         }
                         addTrait(traitTypeFromName(field.key.substring("trait-".length)))
                     }
                     else if (field.key == "entities") {
                         entityResolver.loadEntities(field.value).forEach { childEntity ->
                             childEntity.set(locationTrait, entityBuilder.id)
                         }
                     }
                 }
                 val traits = node["traits"] ?: return@apply
                 if (!traits.isArray) {
                     throw RuntimeJsonMappingException("Expected traits to be an array, was " + traits.nodeType)
                 }
                 entityBuilder.putAll(traits
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
                         }.toMap()
                 )
             }
        }

        fun copyEntityBuilder(): EntityBuilder {
            return EntityBuilderImpl(entityGenerator.newEntityId(), HashMap(entityBuilder.traits))
        }
    }

    val templatesByName = mutableMapOf<String, EntityTemplate>()
    val entities = mutableListOf<EntityBuilder>()
    val entitiesByName = mutableMapOf<String, EntityBuilder>()

    fun getName(node: JsonNode): String = node["name"]?.textValue()?:throw RuntimeJsonMappingException("Expected name in thing " + node)

    val entityResolver = object : JsonEntityResolver {
        override fun loadEntities(node: JsonNode): Array<EntityBuilder> {
            if (!node.isArray) {
                throw RuntimeJsonMappingException("Can only load entities from an array node")
            }
            val loadedEntities = mutableListOf<EntityBuilder>()
            for (i in 0..node.size()) {
                val entityNode = node[i]
                if (entityNode == null) {
                    continue
                }

                if (entityNode.isTextual) {
                    loadedEntities.add(templatesByName[entityNode.textValue()]?.copyEntityBuilder()?:throw RuntimeJsonMappingException("Cannot find entity named ${entityNode.textValue()}"))
                }
                else {
                    val entityBuilder = EntityBuilderImpl(entityGenerator.newEntityId())
                    loadedEntities.add(EntityTemplate(getName(entityNode), entityNode, entityBuilder, this, templatesByName).entityBuilder)
                }
            }
            entities.addAll(loadedEntities)

            return loadedEntities.toTypedArray()
        }

        override fun entityId(entityRefNode: JsonNode) =
                (when(entityRefNode.nodeType) {
                    JsonNodeType.NUMBER -> entityRefNode.numberValue().toInt()
                    JsonNodeType.STRING -> entitiesByName[entityRefNode.textValue()]?.id?:entityRefNode.textValue().toInt()
                    else -> null
                })?:throw RuntimeJsonMappingException("Unable to map entity reference node ${entityRefNode} to an entity ID")

    }

    fun loadTemplate(node: JsonNode) {
        val template = EntityTemplate(getName(node), node, EntityBuilderImpl(0), entityResolver, templatesByName)
        templatesByName[template.name] = template
    }

    fun loadEntity(node: JsonNode) {
        val name = getName(node)
        val entityBuilder = entitiesByName[name]?:throw RuntimeJsonMappingException("Cannot find entity builder with name $name")
        EntityTemplate(name, node, entityBuilder, entityResolver, templatesByName)
    }

    fun  getThingType(thing: JsonNode): Int? {
        val thingType = thing["thing"] ?: return entityThingType
        return when (thingType.textValue()) {
            "entity" -> entityThingType
            "entityTemplate" -> templateThingType
            else -> throw RuntimeJsonMappingException("Unexpected thing type " + thingType.textValue())
        }
    }

    //  First pass: Determine names to IDs
    for (i in 0..jsonTree.size()) {
        val node = jsonTree[i] ?: continue
        val thingType = getThingType(node)
        if (thingType == entityThingType) {
            val nodeName = getName(node)
            val entity = EntityBuilderImpl(entityGenerator.newEntityId())
            entities.add(entity)
            entitiesByName[nodeName] = entity
        }
    }

    //  Second pass:
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
            return entities.map { entity -> command { currentFrame: TFrame, nextFrame -> nextFrame.addEntity(entity.build()) } }.toTypedArray()
        }
    } }
}
