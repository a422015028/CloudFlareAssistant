package com.muort.upworker.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2, 
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            // 移除fallbackToDestructiveMigration以保护用户数据
            // 如果迁移失败会抛出异常而不是删除数据
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }
    
    @Provides
    @Singleton
    fun provideWebDavConfigDao(database: AppDatabase): WebDavConfigDao {
        return database.webDavConfigDao()
    }
    
    @Provides
    @Singleton
    fun provideZoneDao(database: AppDatabase): ZoneDao {
        return database.zoneDao()
    }
    
    @Provides
    @Singleton
    fun provideScriptVersionDao(database: AppDatabase): ScriptVersionDao {
        return database.scriptVersionDao()
    }
}
