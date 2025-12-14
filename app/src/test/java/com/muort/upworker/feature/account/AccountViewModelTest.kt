package com.muort.upworker.feature.account

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.UiState
import com.muort.upworker.core.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class AccountViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var accountRepository: AccountRepository
    
    private lateinit var viewModel: AccountViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = AccountViewModel(accountRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `initial state is Loading`() = runTest {
        // Then
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Loading)
    }
    
    @Test
    fun `loadAccounts updates uiState to Success with accounts`() = runTest {
        // Given
        val testAccounts = listOf(
            Account(
                id = 1,
                accountId = "acc1",
                email = "test@example.com",
                token = "token1",
                zoneId = "zone1",
                isDefault = true
            )
        )
        `when`(accountRepository.getAllAccounts())
            .thenReturn(flowOf(Resource.Success(testAccounts)))
        
        // When
        viewModel.loadAccounts()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(1, (state as UiState.Success).data.size)
    }
    
    @Test
    fun `loadAccounts updates uiState to Empty when no accounts`() = runTest {
        // Given
        `when`(accountRepository.getAllAccounts())
            .thenReturn(flowOf(Resource.Success(emptyList())))
        
        // When
        viewModel.loadAccounts()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Empty)
    }
    
    @Test
    fun `addAccount calls repository and reloads accounts`() = runTest {
        // Given
        val newAccount = Account(
            accountId = "acc1",
            email = "new@example.com",
            token = "token1",
            zoneId = "zone1",
            isDefault = false
        )
        `when`(accountRepository.addAccount(newAccount))
            .thenReturn(Resource.Success(Unit))
        `when`(accountRepository.getAllAccounts())
            .thenReturn(flowOf(Resource.Success(listOf(newAccount))))
        
        // When
        viewModel.addAccount(newAccount)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify(accountRepository).addAccount(newAccount)
    }
    
    @Test
    fun `deleteAccount removes account and reloads list`() = runTest {
        // Given
        val accountToDelete = Account(
            id = 1,
            accountId = "acc1",
            email = "delete@example.com",
            token = "token1",
            zoneId = "zone1",
            isDefault = false
        )
        `when`(accountRepository.deleteAccount(accountToDelete))
            .thenReturn(Resource.Success(Unit))
        `when`(accountRepository.getAllAccounts())
            .thenReturn(flowOf(Resource.Success(emptyList())))
        
        // When
        viewModel.deleteAccount(accountToDelete)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify(accountRepository).deleteAccount(accountToDelete)
    }
    
    @Test
    fun `setDefaultAccount updates default status`() = runTest {
        // Given
        val accountId = 1L
        `when`(accountRepository.setDefaultAccount(accountId))
            .thenReturn(Resource.Success(Unit))
        `when`(accountRepository.getAllAccounts())
            .thenReturn(flowOf(Resource.Success(emptyList())))
        
        // When
        viewModel.setDefaultAccount(accountId)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify(accountRepository).setDefaultAccount(accountId)
    }
}
