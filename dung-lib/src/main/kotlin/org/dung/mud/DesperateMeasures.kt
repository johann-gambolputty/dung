package org.dung.mud

import org.dung.EntityBuilder
import org.dung.setDefault

fun main(args: Array<String>) {
    runMud({ world -> DesperateMeasures(world) })
}

class DesperateMeasures(world: UpdatingMudWorld) : MudWorldInitializer(world) {

    fun club(location: Int?) = createEntity("Club", "A rough, gnarled branch") {
        this.setOrClearLocation(location)
    }
    fun sword(location: Int?) = createEntity("Sword", "A simple sword") {
        this.setOrClearLocation(location)
    }
    fun standardCreature(name: String, description: String, buildMore: EntityBuilder.()-> EntityBuilder = { this }) =
            createEntity(name, description) {
                this.setDefault(damageMonitorTrait).buildMore()
            }



    val location_start = createEntity("Bedroom", """
Your bedroom, night-time. Light shines around the edges of a tiny door, set in the north wall, that you have never noticed before.
""")

    val location_start_tunnel = createEntity("Tunnel", """
A long and winding tunnel, stretching from your bedroom to some unknown location
""")
    init {
        club(location_start_tunnel)
        linkLocations(location_start, location_start_tunnel, ::north, ::south)
    }

    val location_castle_entrance = createEntity("Castle Entrance", """
A dismal castle, swept by rain from low, tattered clouds. Its walls are made from thick dark stone. Firelight flickers
fitfully through arrow slits.
""")
    fun createCastleGuard(name: String, location: Int) = createEntity(name, "A tall, strong guard from the castle barracks. He's well-trained and well-equipped") {
        set(locationTrait, location)
        .randomFlavourNoise(world, arrayOf(
                { entity -> "${entity.get(nameTrait)} picks his nose and carefully inspects the result" },
                { entity -> "${entity.get(nameTrait)} hums a tune. hummmm." },
                { entity -> "${entity.get(nameTrait)} sighs in boredom" },
                { entity -> "${entity.get(nameTrait)} peers into the distance" },
                { entity -> "${entity.get(nameTrait)} stares at you with dull listless eyes" },
                { entity -> "${entity.get(nameTrait)} dreams of a world without castles" },
                { entity -> "${entity.get(nameTrait)} contemplates the futility of existence" }
        ))
        .randomlyChatty()
    }
    val bob = createCastleGuard("Bob", location_castle_entrance)
    val fred = createCastleGuard("Fred", location_castle_entrance)
    init { linkLocations(location_start_tunnel, location_castle_entrance, ::north, ::south) }
}
