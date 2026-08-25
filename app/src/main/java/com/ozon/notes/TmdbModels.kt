package com.ozon.notes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse(
    val results: List<TmdbMovie>
)

@Serializable
data class TmdbMovie(
    val id: Int,
    val title: String? = null,
    val name: String? = null, // For TV shows
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val displayDate: String get() = releaseDate ?: firstAirDate ?: ""
}
