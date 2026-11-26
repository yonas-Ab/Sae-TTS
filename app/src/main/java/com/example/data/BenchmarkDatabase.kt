package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: String = "",
    val runType: String, // "SPEECH_TEST" or "COMPARISON"
    val presetName: String,
    val modelId: String,
    val modelDisplayName: String,
    val speakerId: Int,
    val speakerName: String,
    val text: String,
    val wordCount: Int,
    val charCount: Int,
    val threadCount: Int,
    val speed: Float,
    val supertonicSteps: Int?,
    val isColdInit: Boolean,
    val initTimeMs: Long,
    val timeToFirstAudioMs: Long,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long,
    val realTimeFactor: Double,
    val sampleRate: Int,
    val rssBeforeMb: Double?,
    val rssAfterMb: Double?,
    val rssDeltaMb: Double?,
    val wavFilePath: String?,
    val createdAtMs: Long,
)

@Entity(tableName = "subjective_ratings")
data class SubjectiveRatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: String,
    val modelId: String,
    val naturalness: Int,
    val expressiveness: Int,
    val characterIdentity: Int,
    val pronunciation: Int,
    val overallPreference: Int,
    val notes: String,
    val updatedAtMs: Long,
)

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmarks ORDER BY createdAtMs DESC")
    fun observeAllBenchmarks(): Flow<List<BenchmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(entity: BenchmarkEntity): Long

    @Query("DELETE FROM benchmarks WHERE id = :id")
    suspend fun deleteBenchmarkById(id: Long)

    @Query("DELETE FROM benchmarks")
    suspend fun clearAllBenchmarks()

    @Query("SELECT * FROM benchmarks ORDER BY createdAtMs DESC")
    suspend fun getAllBenchmarksOnce(): List<BenchmarkEntity>
}

@Dao
interface SubjectiveRatingDao {
    @Query("SELECT * FROM subjective_ratings ORDER BY updatedAtMs DESC")
    fun observeAllRatings(): Flow<List<SubjectiveRatingEntity>>

    @Query("SELECT * FROM subjective_ratings WHERE batchId = :batchId AND modelId = :modelId LIMIT 1")
    suspend fun findByBatchAndModel(batchId: String, modelId: String): SubjectiveRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(rating: SubjectiveRatingEntity): Long

    @Query("DELETE FROM subjective_ratings")
    suspend fun clearAllRatings()

    @Query("SELECT * FROM subjective_ratings ORDER BY updatedAtMs DESC")
    suspend fun getAllRatingsOnce(): List<SubjectiveRatingEntity>
}

@Database(
    entities = [BenchmarkEntity::class, SubjectiveRatingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BenchmarkDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun subjectiveRatingDao(): SubjectiveRatingDao

    companion object {
        @Volatile
        private var INSTANCE: BenchmarkDatabase? = null

        fun getInstance(context: Context): BenchmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BenchmarkDatabase::class.java,
                    "sae_tts_benchmark.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
