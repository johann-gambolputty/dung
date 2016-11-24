package org.dung

import java.time.Duration

fun main(args: Array<String>) {
    runMud({ world -> DesperateMeasures(world) })
}

class DesperateMeasures(world: MudWorld) : MudCore(world) {

    val location_start = createEntity("Bedroom", """
Your bedroom, night-time. Light shines around the edges of a tiny door, set in the north wall, that you have never noticed before.
""")

    val location_start_tunnel = createEntity("Tunnel", """
A long and winding tunnel, stretching from your bedroom to some unknown location
""")
    init { linkLocations(location_start, location_start_tunnel, ::north, ::south) }

    val location_castle_entrance = createEntity("Castle Entrance", """
A dismal castle, swept by rain from low, tattered clouds. Its walls are made from thick dark stone. Firelight flickers
fitfully through arrow slits.
""")
    val castleGuard = createEntity("Bob", "A tall, strong guard from the castle barracks. He's well-trained and well-equipped") {
        set(locationTrait, location_castle_entrance)
                .repeatAtRandom(Duration.ofSeconds(2), Duration.ofSeconds(20), entityCommand({ entity, currentFrame, nextFrame -> println("${entity.get(nameTrait)} picks his nose")}))
    }
    init { linkLocations(location_start_tunnel, location_castle_entrance, ::north, ::south) }
}
