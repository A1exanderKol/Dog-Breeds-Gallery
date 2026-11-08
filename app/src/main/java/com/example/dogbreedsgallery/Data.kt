package com.example.dogbreedsgallery

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "breeds")
data class BreedEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val temperament: String,
    val origin: String?,
    val lifeSpan: String,
    val description: String?,
    val imageUrl: String,
    val isFavorite: Boolean
)

data class ImageDto(val id: String?, val url: String?)

data class BreedDto(
    val id: Int,
    val name: String,
    val temperament: String?,
    val origin: String?,
    @Json(name = "life_span") val lifeSpan: String?,
    val description: String?,
    @Json(name = "bred_for") val bredFor: String?,
    @Json(name = "reference_image_id") val referenceImageId: String?,
    val image: ImageDto?
)

fun BreedDto.toEntity() = BreedEntity(
    id = id,
    name = name,
    temperament = temperament.orEmpty(),
    origin = origin?.takeIf { it.isNotBlank() },
    lifeSpan = lifeSpan.orEmpty(),
    description = description?.takeIf { it.isNotBlank() } ?: bredFor?.takeIf { it.isNotBlank() },
    imageUrl = image?.url
        ?: referenceImageId?.let { "https://cdn2.thedogapi.com/images/$it.jpg" }
        ?: "",
    isFavorite = false
)