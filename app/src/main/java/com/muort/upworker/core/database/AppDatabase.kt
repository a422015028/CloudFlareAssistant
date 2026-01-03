package com.muort.upworker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.model.ScriptVersion

@Database(
    entities = [Account::class, WebDavConfig::class, Zone::class, ScriptVersion::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun zoneDao(): ZoneDao
    abstract fun scriptVersionDao(): ScriptVersionDao
    
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
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create zones table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS zones (
                        id TEXT PRIMARY KEY NOT NULL,
                        accountId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        status TEXT NOT NULL,
                        type TEXT,
                        paused INTEGER NOT NULL DEFAULT 0,
                        isSelected INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create index on accountId for better query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_zones_accountId ON zones(accountId)")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create script versions table for editor history
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS script_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountEmail TEXT NOT NULL,
                        scriptName TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isAutoSave INTEGER NOT NULL DEFAULT 0,
                        description TEXT
                    )
                """.trimIndent())
                
                // Create indexes for better query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_script_versions_account_script ON script_versions(accountEmail, scriptName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_script_versions_timestamp ON script_versions(timestamp)")
            }
        }
    }
}
