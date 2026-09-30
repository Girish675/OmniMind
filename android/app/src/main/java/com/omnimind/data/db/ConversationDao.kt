package com.omnimind.data.db

import androidx.room.*
import com.omnimind.data.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE is_deleted = 0 ORDER BY updated_at DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET is_deleted = 1 WHERE id = :id")
    suspend fun softDeleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun hardDeleteConversation(id: String)

    @Query("UPDATE conversations SET title = :title, updated_at = :timestamp WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updated_at = :timestamp WHERE id = :id")
    suspend fun touchConversation(id: String, timestamp: Long = System.currentTimeMillis())
}
