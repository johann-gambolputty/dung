package org.dung.mud

import com.fasterxml.jackson.databind.JsonNode
import org.dung.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

enum class TimeOfDay {
    Sunrise,
    Morning,
    Afternoon,
    Sunset,
    Evening,
    Night
}

enum class PhaseOfMoon {

}

class CelestialState(val timeOfDay: TimeOfDay) {

}

class Weather(val celestialStateToDescriptions: (CelestialState)->Array<String>) {
    infix fun then(transitions: Array<out WeatherTransition>): WeatherGraph = WeatherGraph(this, transitions)
    infix fun transitionTo(weather: Weather): Pair<Weather, Weather> = this to weather

    fun description(state: CelestialState): String {
        val descriptions = celestialStateToDescriptions(state)
        return if (descriptions.isEmpty()) "" else descriptions[Random().nextInt(descriptions.size)]
    }
}

class WeatherGraph(val initial: Weather, val edges: Array<out WeatherTransition>)

class WeatherTransition(val from: Weather, val to:Weather, val probability: Double)

infix fun Pair<Weather, Weather>.withProbability(probability: Double): WeatherTransition = WeatherTransition(first, second, probability)

fun changeWeather(vararg transitions: WeatherTransition): Array<out WeatherTransition> = transitions

val nullWeather = Weather({_ -> arrayOf()})
val cloudlessWeather = Weather({ state -> when(state.timeOfDay) {
    TimeOfDay.Sunrise -> arrayOf(
            "The sun peeks over the horizon through a layer of haze, on an otherwise cloudless sky"
    )
    TimeOfDay.Morning -> arrayOf(
            "Golden morning sunlight brightens the day"
    )
    TimeOfDay.Afternoon -> arrayOf(
            "The sun blazes down from a perfect sea of blue"
    )
    TimeOfDay.Sunset -> arrayOf(
            "Long shadows spread from a sinking red sun",
            "A glowing sun sets in dappled shades of orange"
    )
    TimeOfDay.Evening -> arrayOf(
            ""
    )
    TimeOfDay.Night -> arrayOf(
    )
}})
val cloudyWeather = Weather({ state ->
    val x = "cloudy"
    when(state.timeOfDay) {
        TimeOfDay.Sunrise -> arrayOf(
                "TODO $x - sunrise"
        )
        TimeOfDay.Morning -> arrayOf(
                "TODO $x - morning"
        )
        TimeOfDay.Afternoon -> arrayOf(
                "TODO $x - afternoon"
        )
        TimeOfDay.Sunset -> arrayOf(
                "TODO $x - sunset"
        )
        TimeOfDay.Evening -> arrayOf(
                "TODO $x - evening"
        )
        TimeOfDay.Night -> arrayOf(
                "TODO $x - night"
        )
}})
val lightRain = Weather({ state ->
    val x = "light rain"
    when(state.timeOfDay) {
    TimeOfDay.Sunrise -> arrayOf(
            "TODO $x - sunrise"
    )
    TimeOfDay.Morning -> arrayOf(
            "TODO $x - morning"
    )
    TimeOfDay.Afternoon -> arrayOf(
            "TODO $x - afternoon"
    )
    TimeOfDay.Sunset -> arrayOf(
            "TODO $x - sunset"
    )
    TimeOfDay.Evening -> arrayOf(
            "TODO $x - evening"
    )
    TimeOfDay.Night -> arrayOf(
            "TODO $x - night"
    )
}})
val heavyRain = Weather({ state ->
    val x = "heavy rain"
    when(state.timeOfDay) {
        TimeOfDay.Sunrise -> arrayOf(
                "TODO $x - sunrise"
        )
        TimeOfDay.Morning -> arrayOf(
                "TODO $x - morning"
        )
        TimeOfDay.Afternoon -> arrayOf(
                "TODO $x - afternoon"
        )
        TimeOfDay.Sunset -> arrayOf(
                "TODO $x - sunset"
        )
        TimeOfDay.Evening -> arrayOf(
                "TODO $x - evening"
        )
        TimeOfDay.Night -> arrayOf(
                "TODO $x - night"
        )
}})
val storm = Weather({ state ->
    val x = "storm"
    when(state.timeOfDay) {
        TimeOfDay.Sunrise -> arrayOf(
                "TODO $x - sunrise"
        )
        TimeOfDay.Morning -> arrayOf(
                "TODO $x - morning"
        )
        TimeOfDay.Afternoon -> arrayOf(
                "TODO $x - afternoon"
        )
        TimeOfDay.Sunset -> arrayOf(
                "TODO $x - sunset"
        )
        TimeOfDay.Evening -> arrayOf(
                "TODO $x - evening"
        )
        TimeOfDay.Night -> arrayOf(
                "TODO $x - night"
        )
}})

val temperateClimate = cloudlessWeather then changeWeather(
        cloudlessWeather to cloudyWeather withProbability 20.0,
        cloudyWeather to lightRain withProbability 20.0,
        cloudyWeather to cloudlessWeather withProbability 20.0,
        lightRain to heavyRain withProbability 20.0,
        lightRain to cloudyWeather withProbability 20.0,
        heavyRain to storm withProbability 20.0,
        heavyRain to lightRain withProbability 20.0,
        storm to heavyRain withProbability 10.0,
        storm to cloudyWeather withProbability 10.0
)

class WeatherState(val currentWeather: Weather, val climate: WeatherGraph) : HasDescription {
    override val decriptionOrder: Int = 1

    private fun commandsToUpdateWeatherTo(entity: Entity, nextWeatherState: WeatherState): List<WorldCommand<MudWorldFrame>> {
        return listOf(
                entity.updateInNextFrame { _, _ -> set(weather, nextWeatherState) },
                { currentFrame, nextFrame -> nextFrame.addEntity(nextFrame.newEntity()
                        .echoEvent(nextWeatherState.description(currentFrame, entity), entity.id)
                        .build()
                )})
    }

    override fun description(currentFrame: MudWorldFrame, entity: Entity): String {
        val celestialState = CelestialState(TimeOfDay.Sunrise)
        return currentWeather.description(celestialState)
    }
}

val weather = mudTraitTypes.newTrait("weather", { -> WeatherState(nullWeather, temperateClimate) }, no_json())
val locationClimate = ProxyApplyTraitType<WeatherGraph>({ value ->
    set(weather, WeatherState(value.initial, value))
            .scheduleWeatherChanges()
})

private fun EntityBuilder.scheduleWeatherChanges(): EntityBuilder =
        repeatAtRandom(Duration.ofSeconds(1), Duration.ofSeconds(5), { entity, currentFrame, nextFrame ->
            nextFrame.updateEntity(entity.id, { updateWeather(entity, this, currentFrame, nextFrame) })
        })

fun updateWeather(location: Entity, locationBuilder: EntityBuilder, currentFrame: MudWorldFrame, nextFrame: WorldFrameBuilder<MudWorldFrame>): EntityBuilder {
    val weatherState = location.get(weather)?:return locationBuilder
    val p = rnd.nextDouble() * 100
    val newWeather = weatherState.climate.edges
            .filter { it.from == weatherState.currentWeather }
            .fold(weatherState.currentWeather to p, { acc, trans ->
                if (acc.second <= 0) acc else {
                    val newP = p - trans.probability
                    if (newP < 0) trans.to to 0.0 else acc
                }
            }).first
    if (newWeather == weatherState.currentWeather) {
        return locationBuilder
    }
    val newWeatherState = WeatherState(newWeather, weatherState.climate)
    //  TODO this should not generate an echo event - there should be a state watcher
    nextFrame.addEntity(nextFrame.newEntity().echoEvent(newWeatherState.description(currentFrame, location), location.id).build())
    return locationBuilder.set(weather, newWeatherState)
}