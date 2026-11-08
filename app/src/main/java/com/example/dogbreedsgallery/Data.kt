package com.example.dogbreedsgallery

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import retrofit2.http.Query as HttpQuery

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

@Dao
abstract class BreedDao {
    @Query("SELECT * FROM breeds ORDER BY name")
    abstract fun observeAll(): Flow<List<BreedEntity>>

    @Query("SELECT * FROM breeds WHERE isFavorite = 1 ORDER BY name")
    abstract fun observeFavorites(): Flow<List<BreedEntity>>

    @Query("SELECT * FROM breeds WHERE id = :id")
    abstract fun observeById(id: Int): Flow<BreedEntity?>

    @Query("SELECT id FROM breeds WHERE isFavorite = 1")
    abstract suspend fun favoriteIds(): List<Int>

    @Upsert
    abstract suspend fun upsertAll(breeds: List<BreedEntity>)

    @Query("UPDATE breeds SET isFavorite = :isFavorite WHERE id = :id")
    abstract suspend fun setFavorite(id: Int, isFavorite: Boolean)

    /** Обновляет кэш данными из сети, не затирая флаг «избранное». */
    @Transaction
    open suspend fun upsertKeepingFavorites(breeds: List<BreedEntity>) {
        val favorites = favoriteIds().toSet()
        upsertAll(breeds.map { it.copy(isFavorite = it.id in favorites) })
    }
}

@Database(entities = [BreedEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun breedDao(): BreedDao
}

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

// ======================= Retrofit =======================

interface DogApi {
    @GET("breeds")
    suspend fun getBreeds(): List<BreedDto>

    @GET("images/search")
    suspend fun getImages(
        @HttpQuery("breed_ids") breedId: Int,
        @HttpQuery("limit") limit: Int = 20
    ): List<ImageDto>
}