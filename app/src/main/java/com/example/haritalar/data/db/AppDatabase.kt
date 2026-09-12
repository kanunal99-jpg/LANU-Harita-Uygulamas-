package com.example.haritalar.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_places")
data class FavoritePlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String, // "HOME", "WORK", "CUSTOM"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_places ORDER BY id ASC")
    fun getAllFavorites(): Flow<List<FavoritePlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoritePlace): Long

    @Delete
    suspend fun deleteFavorite(favorite: FavoritePlace)

    @Query("SELECT * FROM favorite_places WHERE category = :category LIMIT 1")
    suspend fun getFavoriteByCategory(category: String): FavoritePlace?
}

@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(item: SearchHistoryItem): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Entity(tableName = "cached_traffic_signals")
data class CachedTrafficSignalEntity(
    @PrimaryKey val id: Long, // OSM node id
    val latitude: Double,
    val longitude: Double,
    val crossing: String?,
    val direction: String?,
    val hasSound: Boolean,
    val hasVibration: Boolean,
    val hasArrow: Boolean,
    val reference: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface TrafficSignalDao {
    @Query("SELECT * FROM cached_traffic_signals WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getSignalsInBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<CachedTrafficSignalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<CachedTrafficSignalEntity>)

    @Query("SELECT COUNT(*) FROM cached_traffic_signals")
    suspend fun getCount(): Int

    @Query("DELETE FROM cached_traffic_signals WHERE cachedAt < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)

    @Query("SELECT * FROM cached_traffic_signals ORDER BY cachedAt DESC LIMIT 200")
    suspend fun getRecentSignals(): List<CachedTrafficSignalEntity>
}

@Database(
    entities = [FavoritePlace::class, SearchHistoryItem::class, CachedTrafficSignalEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun trafficSignalDao(): TrafficSignalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haritalar_nav.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
