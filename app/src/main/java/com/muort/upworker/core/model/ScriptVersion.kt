package com.muort.upworker.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "script_versions")
data class ScriptVersion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountEmail: String,
    val scriptName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAutoSave: Boolean = false,  // 区分自动保存和手动保存
    val description: String? = null   // 版本描述
)
