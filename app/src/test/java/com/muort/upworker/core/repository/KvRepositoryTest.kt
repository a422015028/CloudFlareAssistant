package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import retrofit2.Response

@ExperimentalCoroutinesApi
class KvRepositoryTest {
    
    @Mock
    private lateinit var api: CloudFlareApi
    
    private lateinit var repository: KvRepository
    
    private val testAccount = Account(
        id = 1,
        accountId = "test-account-id",
        email = "test@example.com",
        token = "test-token",
        zoneId = "test-zone",
        isDefault = true
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = KvRepository(api)
    }
    
    @Test
    fun `listNamespaces returns success with namespaces`() = runTest {
        // Given
        val namespaces = listOf(
            KvNamespace(id = "ns1", title = "Namespace 1"),
            KvNamespace(id = "ns2", title = "Namespace 2")
        )
        val response = CloudFlareResponse(
            success = true,
            result = namespaces,
            errors = emptyList()
        )
        `when`(api.listKvNamespaces(anyString(), anyString()))
            .thenReturn(Response.success(response))
        
        // When
        val result = repository.listNamespaces(testAccount)
        
        // Then
        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.size)
    }
    
    @Test
    fun `createNamespace returns success with new namespace`() = runTest {
        // Given
        val newNamespace = KvNamespace(id = "ns-new", title = "New Namespace")
        val response = CloudFlareResponse(
            success = true,
            result = newNamespace,
            errors = emptyList()
        )
        `when`(api.createKvNamespace(anyString(), anyString(), any()))
            .thenReturn(Response.success(response))
        
        // When
        val result = repository.createNamespace(testAccount, "New Namespace")
        
        // Then
        assertTrue(result is Resource.Success)
        assertEquals("New Namespace", (result as Resource.Success).data.title)
    }
    
    @Test
    fun `deleteNamespace returns success`() = runTest {
        // Given
        val response = CloudFlareResponse<Unit>(
            success = true,
            result = null,
            errors = emptyList()
        )
        `when`(api.deleteKvNamespace(anyString(), anyString(), anyString()))
            .thenReturn(Response.success(response))
        
        // When
        val result = repository.deleteNamespace(testAccount, "ns1")
        
        // Then
        assertTrue(result is Resource.Success)
    }
    
    @Test
    fun `listKeys returns success with keys`() = runTest {
        // Given
        val keys = listOf(
            KvKey(name = "key1", metadata = null),
            KvKey(name = "key2", metadata = "meta")
        )
        val response = CloudFlareResponse(
            success = true,
            result = keys,
            errors = emptyList()
        )
        `when`(api.listKvKeys(anyString(), anyString(), anyString()))
            .thenReturn(Response.success(response))
        
        // When
        val result = repository.listKeys(testAccount, "ns1")
        
        // Then
        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.size)
    }
    
    @Test
    fun `putValue returns success`() = runTest {
        // Given
        val response = CloudFlareResponse<Unit>(
            success = true,
            result = null,
            errors = emptyList()
        )
        `when`(api.putKvValue(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Response.success(response))
        
        // When
        val result = repository.putValue(testAccount, "ns1", "key1", "value1")
        
        // Then
        assertTrue(result is Resource.Success)
    }
    
    @Test
    fun `getValue returns success with value`() = runTest {
        // Given
        val value = "test-value"
        `when`(api.getKvValue(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Response.success(value))
        
        // When
        val result = repository.getValue(testAccount, "ns1", "key1")
        
        // Then
        assertTrue(result is Resource.Success)
        assertEquals(value, (result as Resource.Success).data)
    }
    
    @Test
    fun `listNamespaces returns error on API failure`() = runTest {
        // Given
        val errorResponse = CloudFlareResponse<List<KvNamespace>>(
            success = false,
            result = null,
            errors = listOf(CloudFlareError(code = 1000, message = "API Error"))
        )
        `when`(api.listKvNamespaces(anyString(), anyString()))
            .thenReturn(Response.success(errorResponse))
        
        // When
        val result = repository.listNamespaces(testAccount)
        
        // Then
        assertTrue(result is Resource.Error)
        assertTrue((result as Resource.Error).message.contains("API Error"))
    }
}
