package org.mz.mzdkplayer.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.mz.mzdkplayer.data.model.WebDavConnection

class WebDavConnectionRepository(context: Context) : BaseConnectionRepository<WebDavConnection>(
    context, "webdav_connections_prefs", object : TypeToken<List<WebDavConnection>>() {}.type
) {
    override fun getId(connection: WebDavConnection) = connection.id
    override fun tag() = "WebDavRepo"

    override fun sanitize(connection: WebDavConnection) = WebDavConnection(
        id = connection.id,
        name = connection.name ?: "未命名连接",
        baseUrl = connection.baseUrl ?: "未知地址",
        username = connection.username ?: "未知用户",
        password = connection.password ?: ""
    )
}
