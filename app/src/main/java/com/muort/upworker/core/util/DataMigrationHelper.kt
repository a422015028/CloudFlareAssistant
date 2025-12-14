package com.muort.upworker.core.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.model.Account
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy account data structure for migration
 */
private data class LegacyAccount(
    val a: String,  // name
    val b: String,  // accountId
    val c: String,  // token
    val d: String?  // zoneId
)

/**
 * Migrates data from old SharedPreferences-based storage to new Room database
 */
@Singleton
class DataMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val gson: Gson
) {
    
    private val legacyPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("cloudflare_accounts", Context.MODE_PRIVATE)
    }
    
    private val upworkerPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("upworker_prefs", Context.MODE_PRIVATE)
    }
    
    private val migrationPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("migration_status", Context.MODE_PRIVATE)
    }
    
    /**
     * Check if migration has been completed
     */
    fun isMigrationCompleted(): Boolean {
        return migrationPrefs.getBoolean(MIGRATION_COMPLETED_KEY, false)
    }
    
    /**
     * Perform migration from legacy storage to Room database
     */
    suspend fun migrateDataIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        if (isMigrationCompleted()) {
            Timber.d("Migration already completed, skipping")
            return@withContext MigrationResult.AlreadyCompleted
        }
        
        try {
            val migratedCount = migrateAccounts()
            markMigrationCompleted()
            
            Timber.i("Migration completed successfully. Migrated $migratedCount accounts")
            MigrationResult.Success(migratedCount)
        } catch (e: Exception) {
            Timber.e(e, "Migration failed")
            MigrationResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Migrate accounts from SharedPreferences to Room
     */
    private suspend fun migrateAccounts(): Int {
        val accountsJson = legacyPrefs.getString("accounts", null)
        if (accountsJson.isNullOrEmpty()) {
            Timber.d("No legacy accounts found")
            return 0
        }
        
        val type = object : TypeToken<List<LegacyAccount>>() {}.type
        val legacyAccounts: List<LegacyAccount> = try {
            gson.fromJson(accountsJson, type)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse legacy accounts")
            return 0
        }
        
        if (legacyAccounts.isEmpty()) {
            return 0
        }
        
        // Get default account name from upworker_prefs
        val defaultAccountName = upworkerPrefs.getString("selected_account", null)
        
        val newAccounts = legacyAccounts.mapIndexed { index, legacy ->
            Account(
                name = legacy.a,
                accountId = legacy.b,
                token = legacy.c,
                zoneId = legacy.d?.takeIf { it.isNotBlank() },
                isDefault = (legacy.a == defaultAccountName) || (index == 0 && defaultAccountName == null),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        
        // Insert all accounts into Room
        accountDao.insertAccounts(newAccounts)
        
        Timber.d("Migrated ${newAccounts.size} accounts to Room database")
        return newAccounts.size
    }
    
    /**
     * Mark migration as completed
     */
    private fun markMigrationCompleted() {
        migrationPrefs.edit()
            .putBoolean(MIGRATION_COMPLETED_KEY, true)
            .putLong(MIGRATION_TIMESTAMP_KEY, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Reset migration status (for testing purposes)
     */
    fun resetMigrationStatus() {
        migrationPrefs.edit().clear().apply()
        Timber.d("Migration status reset")
    }
    
    companion object {
        private const val MIGRATION_COMPLETED_KEY = "migration_completed"
        private const val MIGRATION_TIMESTAMP_KEY = "migration_timestamp"
    }
}

sealed class MigrationResult {
    data class Success(val migratedCount: Int) : MigrationResult()
    data class Failed(val error: String) : MigrationResult()
    object AlreadyCompleted : MigrationResult()
}
