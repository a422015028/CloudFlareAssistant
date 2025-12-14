package com.muort.upworker.core.repository

import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class AccountRepositoryTest {
    
    @Mock
    private lateinit var accountDao: AccountDao
    
    private lateinit var repository: AccountRepository
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = AccountRepository(accountDao)
    }
    
    @Test
    fun `getAllAccounts returns success with accounts`() = runTest {
        // Given
        val testAccounts = listOf(
            Account(
                id = 1,
                accountId = "acc1",
                email = "test1@example.com",
                token = "token1",
                zoneId = "zone1",
                isDefault = true
            ),
            Account(
                id = 2,
                accountId = "acc2",
                email = "test2@example.com",
                token = "token2",
                zoneId = "zone2",
                isDefault = false
            )
        )
        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(testAccounts))
        
        // When
        val result = repository.getAllAccounts().first()
        
        // Then
        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.size)
        assertEquals("test1@example.com", result.data[0].email)
    }
    
    @Test
    fun `getAllAccounts returns empty list when no accounts`() = runTest {
        // Given
        `when`(accountDao.getAllAccounts()).thenReturn(flowOf(emptyList()))
        
        // When
        val result = repository.getAllAccounts().first()
        
        // Then
        assertTrue(result is Resource.Success)
        assertTrue((result as Resource.Success).data.isEmpty())
    }
    
    @Test
    fun `getDefaultAccount returns default account`() = runTest {
        // Given
        val defaultAccount = Account(
            id = 1,
            accountId = "acc1",
            email = "default@example.com",
            token = "token1",
            zoneId = "zone1",
            isDefault = true
        )
        `when`(accountDao.getDefaultAccount()).thenReturn(flowOf(defaultAccount))
        
        // When
        val result = repository.getDefaultAccount().first()
        
        // Then
        assertEquals(defaultAccount, result)
        assertTrue(result?.isDefault == true)
    }
    
    @Test
    fun `addAccount inserts account successfully`() = runTest {
        // Given
        val newAccount = Account(
            accountId = "acc3",
            email = "new@example.com",
            token = "token3",
            zoneId = "zone3",
            isDefault = false
        )
        
        // When
        val result = repository.addAccount(newAccount)
        
        // Then
        assertTrue(result is Resource.Success)
        verify(accountDao).insert(newAccount)
    }
    
    @Test
    fun `setDefaultAccount updates default status correctly`() = runTest {
        // Given
        val accountId = 5L
        
        // When
        val result = repository.setDefaultAccount(accountId)
        
        // Then
        assertTrue(result is Resource.Success)
        verify(accountDao).clearDefaultFlags()
        verify(accountDao).setDefaultAccount(accountId)
    }
    
    @Test
    fun `deleteAccount removes account successfully`() = runTest {
        // Given
        val accountToDelete = Account(
            id = 1,
            accountId = "acc1",
            email = "delete@example.com",
            token = "token1",
            zoneId = "zone1",
            isDefault = false
        )
        
        // When
        val result = repository.deleteAccount(accountToDelete)
        
        // Then
        assertTrue(result is Resource.Success)
        verify(accountDao).delete(accountToDelete)
    }
    
    @Test
    fun `getAccountCount returns correct count`() = runTest {
        // Given
        val expectedCount = 3
        `when`(accountDao.getAccountCount()).thenReturn(flowOf(expectedCount))
        
        // When
        val result = repository.getAccountCount().first()
        
        // Then
        assertEquals(expectedCount, result)
    }
}
