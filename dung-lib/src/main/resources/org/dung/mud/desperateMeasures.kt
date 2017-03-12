package org.dung.mud


val mud = buildMud {
    val templateActor = entityTemplate {
        healthTrait < 10
        genderTrait < genders.thing
        +damageMonitorTrait
        +affordanceAttackTrait
        +affordanceInspectTrait
    }
    val basicSword = entityTemplate {
    }
    val templateCastleGuard = entityTemplate {
        +templateActor
        descriptionTrait < "A guard called #{entity.name}"
        +inventoryTrait
        //randomFlavourNoiseTrait < flavourNoise("hello")
    }
    class Locations {
        val l0: LocationId = location {
            +templateCastleGuard.derive { nameTrait < "Bob"; genderTrait < genders.male }
            linksTo(l1)
        }
        val l1: LocationId = location {
            linksTo(l0)
        }
    }
}
mud