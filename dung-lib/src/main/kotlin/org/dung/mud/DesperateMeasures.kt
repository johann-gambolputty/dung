package org.dung.mud

import org.dung.EntityBuilderImpl
import org.dung.EntityImpl
import org.dung.createWorldInitializerFromJson


fun main(args: Array<String>) {
//    val msg = EntityBuilderImpl(0)
//            .set(nameTrait, "Bob")
//            .set(genderTrait, genders.male)
//            .build()
//            .processText("#{entity.name} stands by. #{entity.gender.pronoun} looks bored")
    val world = UpdatingMudWorld()
    val initializer = createWorldInitializerFromJson<MudWorldFrame>(DesperateMeasures::class.java.getResource("desperateMeasures.json"), { traitName -> mudTraitTypes.findTraitType(traitName) }, world)
    runMud(world, initializer)
}
class DesperateMeasures()
