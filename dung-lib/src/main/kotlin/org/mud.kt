package org

import rx.Observable
import rx.subjects.PublishSubject

val locationTrait = TraitKeyData("location", { 0 })
val nameTrait = TraitKeyData("name", { "Bob" })
val descriptionTrait = TraitKeyData("name", { "Description of Bob" })
val inventoryTrait = TraitKeyData("inventory", { arrayOf<EntityData>() })
val attackFocusTrait = TraitKeyData("attackFocus", { setOf<Int>() })
val holdingTrait = TraitKeyData("holding", { arrayOf<EntityData>() })
val attacksWhenTouched = TraitKeyData("attacksWhenTouched", { true })


val touchEventType = EventType("touched")
class TouchEventData(eventId: Int, idOfEntityWhoTouched: Int, idOfEntityWhoWasTouched: Int) : EventData(eventId, touchEventType)


fun sword(entityIdGenerator: ()->Int) =
        EntityData(entityIdGenerator())
                .addTrait(nameTrait, "Sword")
                .addTrait(descriptionTrait, "A rusty, blunt, generally useless hunk of junk")

fun EntityData.attacksWhenTouched() = addTrait(tickableTrait, { frame ->
    frame.events.reduce()
})

fun enemy(entityIdGenerator: ()->Int, locationId: Int, name: String): EntityData {
    val weapon = sword(entityIdGenerator)
    return EntityData(entityIdGenerator())
            .addTrait(locationTrait, locationId)
            .addTrait(nameTrait, name)
            .addTrait(inventoryTrait, arrayOf(weapon))
            .addTrait(holdingTrait, arrayOf(weapon))
            .addTrait(attacksWhenTouched, true)
            .addTrait(tickableTrait, { frame ->
                frame.events.reduce(trait(attackFocusTrait)?:setOf<Int>(), )
            })

}

