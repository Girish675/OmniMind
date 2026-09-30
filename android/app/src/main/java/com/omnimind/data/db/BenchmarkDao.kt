package com.omnimind.data.db

import androidx.room.*
import com.omnimind.data.entity.BenchmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmarks ORDER BY timestamp DESC")
    fun getAllBenchmarks(): Flow<List<BenchmarkEntity>>

    @Query("SELECT * FROM benchmarks WHERE model_id = :modelId ORDER BY timestamp DESC")
    fun getBenchmarksForModel(modelId: String): Flow<List<BenchmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmark(benchmark: BenchmarkEntity)

    @Query("DELETE FROM benchmarks WHERE id = :id")
    suspend fun deleteBenchmark(id: String)
}
