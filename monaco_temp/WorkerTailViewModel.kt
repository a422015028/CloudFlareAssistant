package jiamin.chen.orangecloud.ui.workers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
import jiamin.chen.orangecloud.core.system.TailNotifier
import jiamin.chen.orangecloud.data.model.TailTraceItem
import jiamin.chen.orangecloud.data.model.tailDisplayText
import jiamin.chen.orangecloud.data.repository.AccountStore
import jiamin.chen.orangecloud.data.repository.WorkerTailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TailConnState {
    data object Idle : TailConnState
    data object Connecting : TailConnState
    data object Connected : TailConnState
    data class Disconnected(val reason: String?) : TailConnState
}

data class TailLogLine(
    val id: Long,
    val timestampMs: Long,
    val level: String,   // event | log | info | warn | error | debug | exception
    val text: String,
)

/**
 * Workers 实时日志编排（对应 iOS WorkerTailViewModel）：
 * 创建 tail session → 连 WebSocket → 消费事件流；断线自动重建一次，退出销毁 tail。
 * Live Updates 通知（Android 16 实况通知）留作后续增量。
 */
@HiltViewModel
class WorkerTailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountStore: AccountStore,
    private val tailRepository: WorkerTailRepository,
    private val tailNotifier: TailNotifier,
    authRepository: AuthRepository,
) : ViewModel() {

    val scriptName: String = checkNotNull(savedStateHandle["scriptName"])
    private val accountId: String? = accountStore.selectedAccountId.value
    val missingScope: Boolean = !authRepository.hasScope(Scopes.WORKERS_TAIL_READ)

    private val _state = MutableStateFlow<TailConnState>(TailConnState.Idle)
    val state: StateFlow<TailConnState> = _state.asStateFlow()

    private val _lines = MutableStateFlow<List<TailLogLine>>(emptyList())
    val lines: StateFlow<List<TailLogLine>> = _lines.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var collectJob: Job? = null
    private var tailId: String? = null
    private var userStopped = false
    private var didReconnect = false
    private var lineId = 0L
    private var eventCount = 0
    private var lastNotifyMs = 0L

    init {
        if (!missingScope && accountId != null) start()
    }

    fun start() {
        viewModelScope.launch {
            teardown()
            userStopped = false
            didReconnect = false
            connect()
        }
    }

    fun stop() {
        viewModelScope.launch {
            userStopped = true
            teardown()
            tailNotifier.cancel()
            _state.value = TailConnState.Idle
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun togglePause() {
        _paused.update { !it }
    }

    private suspend fun connect() {
        if (accountId == null) {
            _state.value = TailConnState.Disconnected(null)
            return
        }
        _state.value = TailConnState.Connecting
        try {
            val session = tailRepository.createTail(accountId, scriptName)
            tailId = session.id
            val socket = tailRepository.makeSocket(session)
            _state.value = TailConnState.Connected
            tailNotifier.update(scriptName, eventCount, "", connected = true)
            collectJob = viewModelScope.launch {
                try {
                    socket.events().collect { handle(it) }
                    streamEnded(null)
                } catch (e: Exception) {
                    streamEnded(e)
                }
            }
        } catch (e: Exception) {
            _state.value = TailConnState.Disconnected(e.message)
        }
    }

    /** 流结束：用户主动停止则忽略；否则自动重建一次，再失败转断开态。 */
    private fun streamEnded(error: Throwable?) {
        if (userStopped) return
        if (!didReconnect) {
            didReconnect = true
            viewModelScope.launch {
                teardown()
                connect()
            }
        } else {
            _state.value = TailConnState.Disconnected(error?.message)
            tailNotifier.update(scriptName, eventCount, "", connected = false)
        }
    }

    private suspend fun teardown() {
        collectJob?.cancel()
        collectJob = null
        val id = tailId
        val acct = accountId
        tailId = null
        if (id != null && acct != null) {
            runCatching { tailRepository.deleteTail(acct, scriptName, id) }
        }
    }

    private fun handle(item: TailTraceItem) {
        if (_paused.value) return
        val eventMs = item.eventTimestamp ?: System.currentTimeMillis()
        val newLines = buildList {
            val request = item.event?.request
            val cron = item.event?.cron
            if (request != null) {
                add(line(eventMs, "event", "${request.method ?: "GET"} ${request.url.orEmpty()} → ${item.outcome ?: "?"}"))
            } else if (cron != null) {
                add(line(eventMs, "event", "cron $cron → ${item.outcome ?: "?"}"))
            }
            item.logs?.forEach { log ->
                val text = log.message?.joinToString(" ") { it.tailDisplayText() }.orEmpty()
                add(line(log.timestamp ?: eventMs, log.level, text))
            }
            item.exceptions?.forEach { ex ->
                add(line(ex.timestamp ?: eventMs, "exception", listOfNotNull(ex.name, ex.message).joinToString(": ")))
            }
        }
        if (newLines.isEmpty()) return
        _lines.update { current ->
            val all = current + newLines
            if (all.size > MAX_LINES) all.takeLast(MAX_LINES) else all
        }
        eventCount += newLines.size
        val now = System.currentTimeMillis()
        if (now - lastNotifyMs > 1000) {   // 节流：最多每秒更新一次通知
            lastNotifyMs = now
            tailNotifier.update(scriptName, eventCount, newLines.last().text, connected = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tailNotifier.cancel()
    }

    private fun line(timestampMs: Long, level: String, text: String) =
        TailLogLine(id = lineId++, timestampMs = timestampMs, level = level, text = text)

    companion object {
        private const val MAX_LINES = 1000
    }
}
