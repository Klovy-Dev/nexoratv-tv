package fr.nexoratv.tv.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// --------------------------------------------------------------- Entités

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,            // "M3U_URL" | "XTREAM"
    val m3uUrl: String?,
    val epgUrl: String?,
    val host: String?,
    val username: String?,
    val password: String?,
    val xtreamOutput: String,    // "TS" | "M3U8"
    val activationMac: String?,
    val createdAt: Long,
    val position: Int = 0,
)

@Entity(tableName = "favorites", primaryKeys = ["sourceId", "channelId"])
data class FavoriteEntity(
    val sourceId: String,
    val channelId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "watch_history")
data class WatchEntity(
    @PrimaryKey val key: String,          // "$sourceId::$channelId"
    val sourceId: String,
    val channelId: String,
    val name: String,
    val logo: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

// ------------------------------------------------------------------- DAO

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY position, createdAt")
    fun all(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY position, createdAt")
    suspend fun allOnce(): List<SourceEntity>

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT channelId FROM favorites WHERE sourceId = :sourceId")
    fun idsFor(sourceId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND channelId = :channelId")
    suspend fun remove(sourceId: String, channelId: String)
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(sourceId: String, limit: Int = 20): Flow<List<WatchEntity>>

    @Query("SELECT * FROM watch_history WHERE key = :key")
    suspend fun get(key: String): WatchEntity?

    @Upsert
    suspend fun upsert(entity: WatchEntity)
}

// -------------------------------------------------------------- Database

@Database(
    entities = [SourceEntity::class, FavoriteEntity::class, WatchEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun favorites(): FavoriteDao
    abstract fun watch(): WatchDao
}
