package org

data class EventType(val name: String)

open class EventData(val eventId: Int, val eventType: EventType)
