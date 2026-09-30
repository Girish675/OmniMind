package com.omnimind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "model_id")
    val modelId: String? = null,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)
