package app.untilparking.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 위경도. */
data class GeoPoint(val latitude: Double, val longitude: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

/** 대원 거리(m). 도시 안 거리라 구면 근사로 충분하다. */
fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}
