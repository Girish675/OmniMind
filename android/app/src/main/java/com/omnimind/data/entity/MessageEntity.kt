package com.omnimind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id", "sequence_number"], name = "idx_messages_conversation")
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    val role: String, // "system", "user", "assistant"
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Int,
    @ColumnInfo(name = "is_partial")
    val isPartial: Boolean = false,
    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null,
    @ColumnInfo(name = "generation_stats_json")
    val generationStatsJson: String? = null
)
