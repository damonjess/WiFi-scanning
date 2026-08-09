package com.damon.wifiaudit.data

import androidx.room.Embedded
import androidx.room.Relation

data class LocationWithSightings(
    @Embedded val location: LocationFix,
    @Relation(parentColumn = "id", entityColumn = "locationId")
    val wifiSightings: List<WifiSighting>,
    @Relation(parentColumn = "id", entityColumn = "locationId")
    val bleSightings: List<BleSighting>
)
