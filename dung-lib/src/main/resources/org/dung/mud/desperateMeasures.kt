package org.dung.mud

class Mud : MudWorldBuilder() {

    val templateActor = entityTemplate {
        healthTrait eq 10
        genderTrait eq genders.thing
        damageMonitorTrait()
        affordanceAttackTrait()
        affordanceInspectTrait()
    }
    val basicSword = entityTemplate {
    }
    //  TODO parameterized templates
    val templateCastleGuard = entityTemplate {
        templateActor()
        descriptionTrait { "A guard called ${this[nameTrait]}" }
        inventoryTrait()
        //randomFlavourNoiseTrait < flavourNoise("hello")
    }
    val l0: LocationId = location {
        descriptionTrait eq
            """
            |You are in your bedroom.
            |It is night-time.
            |A low moon sends a shaft of moonlight stretching through a gap in the curtain
            |By its light, a strange glittering outline of a door has appeared, where there was once only bare wall
            """
        startingLocation eq true
        affordanceGoNorthTrait eq l1
        templateCastleGuard {
            nameTrait eq "Bob"
            genderTrait eq genders.male
        }
    }
    val l1: LocationId = location {
        descriptionTrait {
            """
            |You are in a long, low tunnel.
            |Wan moonlight filters from around the edges of a hidden door, that leads back to your room.
            |The opposite direction is shrouded in darkness.
            """
        }

        affordanceGoNorthTrait eq l2
    }
    val l2: LocationId = location {
        descriptionTrait {
            """
            |A dark tunnel. A faint glimmer of light twinkles at either end.
            """
        }
        affordanceGoNorthTrait eq l3
    }
    val l3: LocationId = location {
        descriptionTrait {
            """
            |A dark tunnel. A faint glimmer of light twinkles at either end.
            """
        }
        affordanceGoNorthTrait eq l2
        affordanceGoSouthTrait eq l4
    }
    val templateCrystalCreature = entityTemplate { }
    val templateCrystalSnake = entityTemplate {
        templateCrystalCreature()
    }
    val l4: LocationId = location {
        descriptionTrait {
            """
            |The rocky opening to a dark tunnel that stretches its way back to your bedroom.
            |The tunnel opens out from the side of a mountain onto a blasted landscape.
            |The ground is baked, dry and dusty.
            |Mountains rear out of the plain like shards of shattered glass
            """
        }
        templateCrystalSnake()
    }
}
Mud()