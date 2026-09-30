package com.omnimind.data.repository

import com.omnimind.data.db.BenchmarkDao
import com.omnimind.data.entity.BenchmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BenchmarkRepository(
    private val benchmarkDao: BenchmarkDao
) {
    fun getAllBenchmarks(): Flow<List<BenchmarkEntity>> =
        benchmarkDao.getAllBenchmarks()

    fun getBenchmarksForModel(modelId: String): Flow<List<BenchmarkEntity>> =
        benchmarkDao.getBenchmarksForModel(modelId)

    suspend fun recordBenchmark(benchmark: BenchmarkEntity) = withContext(Dispatchers.IO) {
        benchmarkDao.insertBenchmark(benchmark)
    }

    suspend fun deleteBenchmark(id: String) = withContext(Dispatchers.IO) {
        benchmarkDao.deleteBenchmark(id)
    }
}
