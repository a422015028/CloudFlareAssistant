package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    fun getAllAccounts(): Flow<List<Account>>
    
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    suspend fun getAllAccountsSync(): List<Account>
    
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?
    
    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): Account?
    
    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    fun getDefaultAccountFlow(): Flow<Account?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<Account>)
    
    @Update
    suspend fun updateAccount(account: Account)
    
    @Delete
    suspend fun deleteAccount(account: Account)
    
    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
    
    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearAllDefaults()
    
    @Transaction
    suspend fun setDefaultAccount(accountId: Long) {
        clearAllDefaults()
        val account = getAccountById(accountId)
        account?.let {
            updateAccount(it.copy(isDefault = true, updatedAt = System.currentTimeMillis()))
        }
    }
    
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
    
    @Query("UPDATE accounts SET zoneId = :zoneId, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateAccountZoneId(accountId: Long, zoneId: String, updatedAt: Long = System.currentTimeMillis())
}
