package org.mz.mzdkplayer.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * 连接配置 Repository 泛型基类。
 *
 * 封装了基于 SharedPreferences + Gson 的通用 CRUD 逻辑，
 * 子类只需提供 prefs 名、列表 Type、以及 [getId] / [sanitize] 两个抽象方法即可。
 *
 * @param context  Context
 * @param prefsName SharedPreferences 文件名
 * @param type Gson 反序列化用的 List<T> 类型
 */
abstract class BaseConnectionRepository<T>(
    context: Context,
    prefsName: String,
    private val type: Type
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CONNECTIONS = "connections"
    }

    /** 返回连接的唯一 ID（可能为 null） */
    protected abstract fun getId(connection: T): String?

    /** 对加载/保存的连接做空值保护，返回安全实例 */
    protected abstract fun sanitize(connection: T): T

    /** 日志 tag */
    protected abstract fun tag(): String

    // -------------------------------------------------------------------

    fun saveConnections(connections: List<T>) {
        prefs.edit { putString(KEY_CONNECTIONS, gson.toJson(connections)) }
    }

    fun getConnections(): List<T> {
        val json = prefs.getString(KEY_CONNECTIONS, null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val loaded = gson.fromJson<List<T>>(json, type) ?: emptyList()
            loaded.map { sanitize(it) }
        } catch (e: Exception) {
            Log.e(tag(), "解析连接列表失败", e)
            emptyList()
        }
    }

    fun addConnection(connection: T) {
        val current = getConnections().toMutableList()
        val safe = sanitize(connection)
        val id = getId(safe)
        if (id != null && current.any { getId(it) == id }) {
            // 已存在相同 ID 的连接, 不覆盖 (更新应使用 updateConnection)
            Log.w(tag(), "尝试添加已存在的连接 ID: $id, 已忽略")
            return
        }
        current.add(safe)
        saveConnections(current)
    }

    fun deleteConnection(id: String) {
        saveConnections(getConnections().filter { getId(it) != id })
    }

    fun getConnectionById(id: String): T? {
        return getConnections().find { getId(it) == id }
    }

    fun updateConnection(connection: T) {
        val current = getConnections().toMutableList()
        val safe = sanitize(connection)
        val id = getId(safe)
        val index = if (id != null) current.indexOfFirst { getId(it) == id } else -1
        if (index >= 0) {
            current[index] = safe
            saveConnections(current)
        } else {
            Log.w(tag(), "尝试更新一个不存在的连接 ID: $id")
        }
    }
}
