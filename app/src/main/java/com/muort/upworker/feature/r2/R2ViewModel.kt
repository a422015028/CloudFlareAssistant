package com.muort.upworker.feature.r2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.R2Bucket
import com.muort.upworker.core.model.R2CustomDomain
import com.muort.upworker.core.model.R2Object
import com.muort.upworker.core.model.Resource
import java.io.File
import com.muort.upworker.core.repository.R2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class R2ViewModel @Inject constructor(
    private val r2Repository: R2Repository
) : ViewModel() {
    
    private val _buckets = MutableStateFlow<List<R2Bucket>>(emptyList())
    val buckets: StateFlow<List<R2Bucket>> = _buckets.asStateFlow()
    
    private val _selectedBucket = MutableStateFlow<R2Bucket?>(null)
    val selectedBucket: StateFlow<R2Bucket?> = _selectedBucket.asStateFlow()
    
    private val _objects = MutableStateFlow<List<R2Object>>(emptyList())
    val objects: StateFlow<List<R2Object>> = _objects.asStateFlow()
    
    private val _customDomains = MutableStateFlow<List<R2CustomDomain>>(emptyList())
    val customDomains: StateFlow<List<R2CustomDomain>> = _customDomains.asStateFlow()
    
    // 存储所有 bucket 的自定义域映射：bucketName -> List<R2CustomDomain>
    private val _allCustomDomains = MutableStateFlow<Map<String, List<R2CustomDomain>>>(emptyMap())
    val allCustomDomains: StateFlow<Map<String, List<R2CustomDomain>>> = _allCustomDomains.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _objectsLoadingState = MutableStateFlow(false)
    val objectsLoadingState: StateFlow<Boolean> = _objectsLoadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun loadBuckets(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.listBuckets(account)) {
                is Resource.Success -> {
                    _buckets.value = result.data
                    Timber.d("Loaded ${result.data.size} buckets")
                }
                is Resource.Error -> {
                    _message.emit("加载存储桶失败: ${result.message}")
                    Timber.e("Failed to load buckets: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createBucket(account: Account, name: String, location: String? = null) {
        if (name.isBlank()) {
            viewModelScope.launch {
                _message.emit("请输入存储桶名称")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.createBucket(account, name, location)) {
                is Resource.Success -> {
                    _message.emit("存储桶创建成功")
                    loadBuckets(account)
                }
                is Resource.Error -> {
                    _message.emit("创建存储桶失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteBucket(account: Account, bucketName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.deleteBucket(account, bucketName)) {
                is Resource.Success -> {
                    _message.emit("存储桶删除成功")
                    if (_selectedBucket.value?.name == bucketName) {
                        _selectedBucket.value = null
                        _objects.value = emptyList()
                    }
                    loadBuckets(account)
                }
                is Resource.Error -> {
                    _message.emit("删除存储桶失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun selectBucket(bucket: R2Bucket) {
        _selectedBucket.value = bucket
    }
    
    fun clearObjects() {
        _objects.value = emptyList()
    }
    
    fun loadObjects(account: Account, bucketName: String, prefix: String? = null) {
        viewModelScope.launch {
            // Clear previous objects immediately
            _objects.value = emptyList()
            _objectsLoadingState.value = true
            
            when (val result = r2Repository.listObjects(account, bucketName, prefix)) {
                is Resource.Success -> {
                    _objects.value = result.data.objects ?: emptyList()
                    Timber.d("Loaded ${result.data.objects?.size ?: 0} objects")
                }
                is Resource.Error -> {
                    _message.emit("加载对象列表失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _objectsLoadingState.value = false
        }
    }
    
    fun uploadObject(account: Account, bucketName: String, objectKey: String, file: File) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.uploadObject(account, bucketName, objectKey, file)) {
                is Resource.Success -> {
                    _message.emit("对象上传成功")
                    loadObjects(account, bucketName)
                }
                is Resource.Error -> {
                    _message.emit("上传对象失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun downloadObject(account: Account, bucketName: String, objectKey: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.downloadObject(account, bucketName, objectKey)) {
                is Resource.Success -> {
                    onResult(result.data)
                }
                is Resource.Error -> {
                    _message.emit("下载对象失败: ${result.message}")
                    onResult(null)
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteObject(account: Account, bucketName: String, objectKey: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.deleteObject(account, bucketName, objectKey)) {
                is Resource.Success -> {
                    _message.emit("对象删除成功")
                    loadObjects(account, bucketName)
                }
                is Resource.Error -> {
                    _message.emit("删除对象失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    // ==================== Custom Domains ====================
    
    fun loadCustomDomains(account: Account, bucketName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = r2Repository.listCustomDomains(account, bucketName)) {
                is Resource.Success -> {
                    _customDomains.value = result.data
                    // 更新所有自定义域映射
                    val currentMap = _allCustomDomains.value.toMutableMap()
                    currentMap[bucketName] = result.data
                    _allCustomDomains.value = currentMap
                    Timber.d("Loaded ${result.data.size} custom domains for bucket $bucketName")
                }
                is Resource.Error -> {
                    _message.emit("加载自定义域失败: ${result.message}")
                    Timber.e("Failed to load custom domains: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun createCustomDomain(account: Account, bucketName: String, domain: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.createCustomDomain(account, bucketName, domain)) {
                is Resource.Success -> {
                    _message.emit("自定义域添加成功")
                    loadCustomDomains(account, bucketName)
                }
                is Resource.Error -> {
                    _message.emit("添加自定义域失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteCustomDomain(account: Account, bucketName: String, domain: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = r2Repository.deleteCustomDomain(account, bucketName, domain)) {
                is Resource.Success -> {
                    _message.emit("自定义域删除成功")
                    loadCustomDomains(account, bucketName)
                }
                is Resource.Error -> {
                    _message.emit("删除自定义域失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
}
