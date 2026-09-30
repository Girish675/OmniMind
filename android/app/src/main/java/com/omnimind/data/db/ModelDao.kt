package com.omnimind.data.db

import androidx.room.*
import com.omnimind.data.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY is_default DESC, imported_at DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultModel(): ModelEntity?

    @Query("SELECT * FROM models WHERE file_path = :path LIMIT 1")
    suspend fun getModelByPath(path: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModelById(id: String)

    @Query("UPDATE models SET is_default = 0")
    suspend fun clearDefaultModels()

    @Transaction
    suspend fun setDefaultModel(id: String) {
        clearDefaultModels()
        markAsDefault(id)
    }

    @Query("UPDATE models SET is_default = 1 WHERE id = :id")
    suspend fun markAsDefault(id: String)

    @Query("UPDATE models SET last_used_at = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)

    @Query("UPDATE models SET name = :newName WHERE id = :id")
    suspend fun renameModel(id: String, newName: String)
}
