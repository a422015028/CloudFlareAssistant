package com.muort.upworker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.WebDavConfig

@Database(
    entities = [Account::class, WebDavConfig::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun webDavConfigDao(): WebDavConfigDao
    
    companion object {
        const val DATABASE_NAME = "cloudflare_assistant_db"
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if accounts table exists before altering
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='accounts'")
                if (cursor.moveToFirst()) {
                    // Add R2 credential columns only if table exists
                    db.execSQL("ALTER TABLE accounts ADD COLUMN r2AccessKeyId TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE accounts ADD COLUMN r2SecretAccessKey TEXT DEFAULT NULL")
                }
                cursor.close()
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create WebDAV config table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS webdav_config (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        backupPath TEXT NOT NULL,
                        autoBackup INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
