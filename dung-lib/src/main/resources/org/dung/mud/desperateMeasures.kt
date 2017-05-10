package org.dung.mud

class Mud : MudWorldBuilder() {

    val templateActor = entityTemplate {
        health set 10
        gender set genders.thing
        damageMonitor()
        affordanceAttackTrait()
        inspectable()
    }
    val basicSword = entityTemplate {
    }
    //  TODO parameterized templates
    val templateCastleGuard = entityTemplate {
        templateActor()
        description { "A guard called ${this[name]}" }
        inventory()
        //randomFlavourNoiseTrait < flavourNoise("hello")
    }
    val l0: LocationId = location {
        description set
            """
            |You are in your bedroom.
            |It is night-time.
            |A low moon sends a shaft of moonlight stretching through a gap in the curtain
            |By its light, a strange glittering outline of a door has appeared, where there was once only bare wall
            """
        startingLocation set true
        goNorth set l1
        templateCastleGuard {
            name set "Bob"
            gender set genders.male
        }
    }
    val l1: LocationId = location {
        description {
            """
            |You are in a long, low tunnel.
            |Wan moonlight filters from around the edges of a hidden door, that leads back to your room.
            |The opposite direction is shrouded in darkness.
            """
        }

        goNorth set l2
    }
    val l2: LocationId = location {
        description {
            """
            |A dark tunnel. A faint glimmer of light twinkles at either end.
            """
        }
        goNorth set l3
    }
    val l3: LocationId = location {
        description {
            """
            |A dark tunnel. A faint glimmer of light twinkles at either end.
            """
        }
        goNorth set l2
        goSouth set l4
    }
    val templateCrystalCreature = entityTemplate { }
    val templateCrystalSnake = entityTemplate {
        templateCrystalCreature()
    }
    val l4: LocationId = location {
        description {
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