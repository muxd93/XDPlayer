package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mz.mzdkplayer.data.repository.BaseConnectionRepository

/**
 * 连接列表 ViewModel 泛型基类。
 *
 * 封装了连接列表的通用状态管理（connections / 选中状态 / 操作面板 / 长按），
 * 子类只需提供 [repository] 实例、[getId] 和 [hasDuplicateConnection] 即可。
 */
abstract class BaseConnectionListViewModel<T>(application: Application) :
    AndroidViewModel(application) {

    protected abstract val repository: BaseConnectionRepository<T>

    /** 从连接对象中取出唯一 ID（用于删除后清理选中状态） */
    protected abstract fun getId(connection: T): String?

    /** 检查新连接是否与现有连接重复 */
    abstract fun hasDuplicateConnection(connections: List<T>, newConnection: T): Boolean

    // -------------------------------------------------------------------
    // 状态流
    // -------------------------------------------------------------------

    private val _connections = MutableStateFlow<List<T>>(emptyList())
    val connections: StateFlow<List<T>> = _connections

    private val _isOPanelShow = MutableStateFlow(false)
    val isOPanelShow: StateFlow<Boolean> = _isOPanelShow

    private val _selectedConnection = MutableStateFlow<T?>(null)
    val selectedConnection: StateFlow<T?> = _selectedConnection

    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex = _selectedIndex.asStateFlow()

    private val _selectedId = MutableStateFlow("")
    val selectedId = _selectedId.asStateFlow()

    private val _isLongPressInProgress = MutableStateFlow(false)
    val isLongPressInProgress = _isLongPressInProgress.asStateFlow()

    // -------------------------------------------------------------------
    // 通用方法
    // -------------------------------------------------------------------

    fun loadConnections() {
        _connections.value = repository.getConnections()
    }

    fun addConnection(connection: T): Boolean {
        if (!hasDuplicateConnection(_connections.value, connection)) {
            repository.addConnection(connection)
            loadConnections()
            return true
        }
        return false
    }

    fun updateConnection(connection: T) {
        repository.updateConnection(connection)
        loadConnections()
    }

    fun deleteConnection(id: String) {
        // 记录被删除项在列表中的位置, 用于调整选中索引
        val deletedIndex = _connections.value.indexOfFirst { getId(it) == id }
        repository.deleteConnection(id)
        loadConnections()
        if (_selectedConnection.value?.let { getId(it) == id } == true) {
            _selectedConnection.value = null
        }
        if (_selectedId.value == id) {
            _selectedId.value = ""
            // 删除选中项时, 焦点停留在同位置的新项(或最后一项), 而非清空为 -1
            val newSize = _connections.value.size
            _selectedIndex.value = if (newSize == 0) -1 else minOf(deletedIndex.coerceAtLeast(0), newSize - 1)
        } else if (deletedIndex >= 0 && deletedIndex < _selectedIndex.value) {
            // 被删除的项在选中项之前, 选中索引前移
            _selectedIndex.value = _selectedIndex.value - 1
        }
        // 确保 selectedIndex 不越界
        val newSize = _connections.value.size
        if (_selectedIndex.value >= newSize) {
            _selectedIndex.value = newSize - 1
        }
    }

    fun selectConnection(connection: T) {
        _selectedConnection.value = connection
    }

    fun openOPlane() {
        _isOPanelShow.value = true
    }

    fun closeOPanel() {
        _isOPanelShow.value = false
    }

    fun setSelectedIndex(index: Int) {
        _selectedIndex.value = index
        // 同步 selectedId 和 selectedConnection, 避免三套状态不同步
        _connections.value.getOrNull(index)?.let { conn ->
            _selectedId.value = getId(conn) ?: ""
            _selectedConnection.value = conn
        }
    }

    fun setSelectedId(id: String?) {
        if (id != null) {
            _selectedId.value = id
            // 同步 selectedIndex 和 selectedConnection, 避免三套状态不同步
            val index = _connections.value.indexOfFirst { getId(it) == id }
            if (index >= 0) {
                _selectedIndex.value = index
                _selectedConnection.value = _connections.value[index]
            }
        }
    }

    fun setIsLongPressInProgress(isLongPressInProgress: Boolean) {
        _isLongPressInProgress.value = isLongPressInProgress
    }

    fun getConnectionById(id: String): T? {
        return repository.getConnectionById(id)
    }
}
