package org.dung.mud
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.InputStreamReader

class SimpleMud : MudWorldBuilder() {

    val templatePlayer = entityTemplate {
        health set 10
        gender set genders.male
        damageMonitor()
        inspectable()
        affordanceAttackTrait()
    }

    val templateActor = entityTemplate {
        health set 10
        gender set genders.thing
        damageMonitor()
        affordanceAttackTrait()
        inspectable()
    }

    val templateCrystalActor = entityTemplate {
        inherit(templateActor)
    }

    val templateLevel1Actor = entityTemplate {
        xpValueTrait set 1
    }
    val templateLevel10Actor = entityTemplate {
        xpValueTrait set 1000
    }

    val templateWeapon = entityTemplate { }
    val templateArmour = entityTemplate { }
    val templateShield = entityTemplate { }

    val templatePlainSword = templateWeapon {
    }

    val templatePlateMailArmour = templateArmour {
    }

    val templateCastleBlackGuard = templateActor {
        inherit(templateLevel10Actor)
        gender set genders.male
        skill set 100
        crit set 5
        health set 150
        name set "Guard"
        aliases set arrayOf("GUARD")
        description set "Castle Black guard"
    }

    val start: LocationId = location {
        description {
            """Something has woken you up.
            |Your room is quiet and dark.
            |No lights illuminate the night outside, although a pale moonlight glows through gaps in the curtains.
            |You look around to see what woke you, and your eyes fall on a faint twinkling shape etched on the far wall.
            |On closer inspection, tiny sparks are racing around on the wall's surface, tracing out the shape of a small
            |rectangle. As your tentative fingers reach out to touch it, the wall cracks open along the tracery of
            |glowing lines, and a whole section falls back into blackness and disappears.
            """
        }
        startingLocation set true
        goNorth set start_tunnel0
    }
    val start_tunnel_no_way_back: LocationId = location {
        description set
                """You reach the end of the tunnel where the glowing door led back to your bedroom, but it has
                disappeared. You're only option is to return the way you came.
                """
        goNorth set start_tunnel1
    }
    val start_tunnel0: LocationId = location {
        description set
            """You crawl through the opening. The faint moonlight from your room allows you to see the rough outlines of
            |tunnel extending off into blackness in front of you.
            """
        goSouth set start
        goNorth set start_tunnel1
    }
    val start_tunnel1: LocationId = location {
        description set
                """The tunnel is as dark as the blackest starless night. The glowing door back to your bedroom has
                |disappeared behind a slight curve. Faint sounds of water trickling around rocks reach your ears. You
                |make your way forward by trailing your hand along the slick rocky wall to your left.
                """
        goSouth set start_tunnel_no_way_back
        goNorth set start_tunnel_end
    }
    val start_tunnel_end: LocationId = location {
        description set
                """Turning a corner, you finally emerge from the darkness into a cavern lit by a pale splash of milky
                |moonlight.
                |There's a jumble of rocks and whitish sand strewn around an entrance to the cavern that faces onto a
                |featureless plain. The wan moonlight feebly illuminates a flat expanse, broken by towering shards of
                |mountains that jut from the ground like splinters from flesh.
                """
        goSouth set start_tunnel1
        goNorth set crystal_plain_0
    }
    val start_tunnel_end_return: LocationId = location {
        description set
                """You have returned to the cavern leading to the tunnel from home. Behind you stretches the glinting
                |expanse of the crystalline plain
                """
        goSouth set start_tunnel1
        goNorth set crystal_plain_0

    }


    val templateItem = entityTemplate {
    }

    val templateCrystalSnakeScales = templateItem {
        inherit(templateItem)
        name set "Crystal Snake Scales"
        aliases set arrayOf("SCALES")
        description set "Moldy snake scales. They may hold some value as they are shot through with fractured shards of a milky white crystal"
        worth set copper(2)
    }

    val templateCrystalSnake = templateCrystalActor {
        inherit(templateLevel1Actor)
        health set 5
        name set "Crystal Snake"
        aliases set arrayOf("SNAKE")
        description set "A small vicious looking snake whose body has been corrupted with shards of opaque white crystal"
        inventory set Inventory(arrayOf())
    }

    val crystalPlainTemplate = entityTemplate {
        locationClimate set temperateClimate
    }

    val crystal_plain_0: LocationId = location {
        inherit(crystalPlainTemplate)
        description set
                """You stand on the featureless plain. To the south is the cavern entrance that bought you to this place.
                |The ground is a hard layer of milky crystal. Swimming in its depths, you can see shapeless shadows.
                |Peering closer, you can just about make out arms, legs, heads.
                """
        +templateCrystalSnake
        goNorth set crystal_plain_2
        goEast set crystal_plain_1
        goSouth set start_tunnel_end_return
    }

    val crystal_plain_1: LocationId = location {
        inherit(crystalPlainTemplate)
        description set
                """You come to an abrupt edge. Peering over the edge, a swirl of cloud or fog obscures your view of the
                |ground below. It's a long way down.
                """
        +templateCrystalSnake
        +templateCrystalSnake
        goWest set crystal_plain_0
    }

    val crystal_plain_2: LocationId = location {
        inherit(crystalPlainTemplate)
        description set
                """Your wanderings have taken you to the base of one of the huge mountains that jut like frozen water
                |spouts from the surface of the plain. The surface of the mountain is glassy, and its edges razor-sharp.
                |Climbing this would be suicidal.
                |Nearby is a short tunnel leading under the mountain.
                """
        goSouth set crystal_plain_0
    }

    val time_portal: LocationId = location {
        description set
                """The tunnel winds its way under the crystal berg. Dim light filtering through the glassy walls illuminates]
                |your way.
                """
    }
}

//  Hate
//  Resurrection
//  Weather
//  Day/night cycle
//  Look
//  Levels
//  Equipment
//  Special attacks
//  Drop trait

fun loadMud(resourceName: String): MudWorldBuilder {
    return InputStreamReader(DesperateMeasures::class.java.getResourceAsStream(resourceName)).use { reader ->
        KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine.eval(reader)
    } as MudWorldBuilder;
}

fun main(args: Array<String>) {

    val world = UpdatingMudWorld()
    //loadMud("desperateMeasures.kt").build(world)
    SimpleMud().build(world)
//    val initializer = createWorldInitializerFromJson<MudWorldFrame>(DesperateMeasures::class.java.getResource("desperateMeasures.json"), { traitName -> mudTraitTypes.findTraitType(traitName) as TraitTypeT<*> }, world)
    runMud(world, { world -> MudWorldInitializer(world)})
}
class DesperateMeasures()
