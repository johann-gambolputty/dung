package org.dung

fun main(args: Array<String>) {
    runMud({ world -> DesperateMeasures(world) })
}

class DesperateMeasures(world: MudWorld) : MudCore(world) {

    fun castleGuard(id: Int, name: String) =
            EntityImpl(id).modify()
                    .set(nameTrait, name)
                    .set(descriptionTrait, "A tall, strong guard from the castle barracks. He's well-trained and well-equipped")

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
    init { linkLocations(location_start_tunnel, location_castle_entrance, ::north, ::south) }
}
