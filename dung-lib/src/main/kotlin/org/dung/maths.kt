package org.dung

fun <T : Comparable<T>> clamp(value: T, min: T, max: T): T =
        if (value < min) min else if (value > max) max else value

data class Vector3d(val x: Float, val y: Float, val z: Float) {
    operator fun plus(vec: Vector3d) = Vector3d(x + vec.x, y + vec.y, z + vec.z)
    operator fun minus(vec: Vector3d) = Vector3d(x - vec.x, y - vec.y, z - vec.z)
    operator fun times(vec: Vector3d) = Vector3d(x * vec.x, y * vec.y, z * vec.z)
    operator fun times(scalar: Float) = Vector3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3d(x / scalar, y / scalar, z / scalar)
}