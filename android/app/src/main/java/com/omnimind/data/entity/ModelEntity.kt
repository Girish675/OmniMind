package com.omnimind.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    val sha256: String? = null,
    val architecture: String? = null,
    @ColumnInfo(name = "parameter_count")
    val parameterCount: Long? = null,
    val quantization: String? = null,
    @ColumnInfo(name = "context_length")
    val contextLength: Int? = null,
    val author: String? = null,
    val license: String? = null,
    @ColumnInfo(name = "download_source")
    val downloadSource: String? = null,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    val status: String = "ready"
)
