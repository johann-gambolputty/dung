package org.dung

fun main(args: Array<String>) {
    runMud(DesperateMeasures())
}

class DesperateMeasures() : MudCore() {

    fun castleGuard(name: String) =
            EntityImpl()
                    .set(nameTrait, name)

    val location_start = createLocation("Bedroom", """
Your bedroom, night-time. Light shines around the edges of a tiny door, set in the north wall, that you have never noticed before.
""", {
        +EntityImpl(0).modify()
        +EntityImpl(0).modify()
    })

    val location_start_tunnel = createLocation("Tunnel", """
A long and winding tunnel, stretching from your bedroom to some unknown location
""")
    init { linkLocations(location_start, location_start_tunnel, ::north, ::south) }

    val location_castle_entrance = createLocation("Castle Entrance", """
A dismal castle, swept by rain from low, tattered clouds. Its walls are made from thick dark stone. Firelight flickers
fitfully through arrow slits.
""")
    init { linkLocations(location_start_tunnel, location_castle_entrance, ::north, ::south) }
}
