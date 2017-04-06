package org.dung.mud
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.InputStreamReader

class SimpleMud : MudWorldBuilder() {

    val templateActor = entityTemplate {
        healthTrait eq 10
        genderTrait eq genders.thing
        damageMonitorTrait()
        affordanceAttackTrait()
        affordanceInspectTrait()
    }

    val templateCrystalActor = entityTemplate {
        templateActor()
    }

    val templateLevel1Actor = entityTemplate {
        xpValueTrait eq 1
    }

    val templateCrystalSnake = templateCrystalActor {
        templateLevel1Actor()
        healthTrait eq 5
        nameTrait eq "Crystal Snake"
        aliasesTrait eq arrayOf("SNAKE")
        descriptionTrait eq "A small vicious looking snake whose body has been corrupted with shards of opaque white crystal"
        inventoryTrait eq Inventory(arrayOf())
    }


    val start: LocationId = location {
        descriptionTrait {
            """Something has woken you up.
            |Your room is quiet and dark.
            |No lights illuminate the night outside, although a pale moonlight glows through gaps in the curtains.
            |You look around to see what woke you, and your eyes fall on a faint twinkling shape etched on the far wall.
            |On closer inspection, tiny sparks are racing around on the wall's surface, tracing out the shape of a small
            |rectangle. As your tentative fingers reach out to touch it, the wall cracks open along the tracery of
            |glowing lines, and a whole section falls back into blackness and disappears.
            |
            """
        }
        startingLocation eq true
        affordanceGoNorthTrait eq start_tunnel0
    }
    val start_tunnel0: LocationId = location {
        descriptionTrait eq
            """You crawl through the opening. The faint moonlight from your room allows you to see the rough outlines of
            |tunnel extending off into blackness in front of you
            """
        affordanceGoSouthTrait eq start
        templateCrystalSnake()
    }
}

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
