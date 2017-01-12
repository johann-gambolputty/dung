package org.dung.mud

import org.dung.createWorldInitializerFromJson


fun main(args: Array<String>) {


    //runMud({ world -> DesperateMeasures(world) })
    runMud(createWorldInitializerFromJson(DesperateMeasures::class.java.getResource("desperateMeasures.json"), { traitName -> mudTraitTypes.findTraitType(traitName) }))
}
class DesperateMeasures()
