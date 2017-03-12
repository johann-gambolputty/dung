package org.dung.mud


import org.dung.createWorldInitializerFromJson
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import java.io.InputStreamReader

fun main(args: Array<String>) {

    val result = InputStreamReader(DesperateMeasures::class.java.getResourceAsStream("desperateMeasures.kt")).use { reader ->
        KotlinJsr223JvmLocalScriptEngineFactory().scriptEngine.eval(reader)
    }

    println(result)
    val world = UpdatingMudWorld()
    val initializer = createWorldInitializerFromJson<MudWorldFrame>(DesperateMeasures::class.java.getResource("desperateMeasures.json"), { traitName -> mudTraitTypes.findTraitType(traitName) }, world)
    runMud(world, initializer)
}
class DesperateMeasures()
