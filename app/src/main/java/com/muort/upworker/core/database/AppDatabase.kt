package com.muort.upworker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.R2BackupConfig
import com.muort.upworker.core.model.LocalBackupConfig
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.model.ScriptVersion
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.model.CatalogTemplate
import com.muort.upworker.core.model.CatalogFavorite

@Database(
    entities = [Account::class, WebDavConfig::class, R2BackupConfig::class, LocalBackupConfig::class, Zone::class, ScriptVersion::class, CatalogSource::class, CatalogTemplate::class, CatalogFavorite::class],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun r2BackupConfigDao(): R2BackupConfigDao
    abstract fun localBackupConfigDao(): LocalBackupConfigDao
    abstract fun zoneDao(): ZoneDao
    abstract fun scriptVersionDao(): ScriptVersionDao
    abstract fun catalogDao(): CatalogDao
    
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
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create R2 backup config table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS r2_backup_config (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        bucketName TEXT NOT NULL,
                        backupPath TEXT NOT NULL,
                        autoBackup INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Global API Key authentication fields to accounts table
                db.execSQL("ALTER TABLE accounts ADD COLUMN email TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN globalApiKey TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN authType TEXT NOT NULL DEFAULT 'TOKEN'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 zones 表增加名称服务器与套餐字段
                db.execSQL("ALTER TABLE zones ADD COLUMN nameServers TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE zones ADD COLUMN plan TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 webdav_config 增加备份密码字段
                db.execSQL("ALTER TABLE webdav_config ADD COLUMN backupPassword TEXT DEFAULT NULL")
                // 为 r2_backup_config 增加备份密码字段
                db.execSQL("ALTER TABLE r2_backup_config ADD COLUMN backupPassword TEXT DEFAULT NULL")
                // 创建本地备份配置表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_backup_config (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        directoryUri TEXT DEFAULT NULL,
                        autoBackup INTEGER NOT NULL DEFAULT 0,
                        backupPassword TEXT DEFAULT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Catalog 数据源表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS catalog_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        name TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        lastSynced INTEGER,
                        lastStatus TEXT NOT NULL DEFAULT 'idle',
                        lastError TEXT,
                        etag TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 模板表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS catalog_templates (
                        localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId TEXT NOT NULL,
                        sourceId INTEGER NOT NULL,
                        sourceName TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        version TEXT NOT NULL,
                        type TEXT NOT NULL,
                        authorName TEXT,
                        authorUrl TEXT,
                        tags TEXT,
                        icon TEXT,
                        homepage TEXT,
                        readmeUrl TEXT,
                        sourceKind TEXT,
                        sourceUrl TEXT,
                        workerSourceKind TEXT,
                        workerSourceUrl TEXT,
                        pagesSourceKind TEXT,
                        pagesSourceUrl TEXT,
                        bindingsJson TEXT,
                        envJson TEXT,
                        routes TEXT,
                        crons TEXT,
                        compatibilityDate TEXT,
                        compatibilityFlags TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_templates_sourceId ON catalog_templates(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_templates_type ON catalog_templates(type)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_templates_templateId ON catalog_templates(templateId)")

                // 收藏表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS catalog_favorites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_favorites_templateId ON catalog_favorites(templateId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 catalog_templates 表新增字段：mainModule、workerMainModule、assetsJson
                db.execSQL("ALTER TABLE catalog_templates ADD COLUMN mainModule TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE catalog_templates ADD COLUMN workerMainModule TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE catalog_templates ADD COLUMN assetsJson TEXT DEFAULT NULL")
            }
        }
    }
}
