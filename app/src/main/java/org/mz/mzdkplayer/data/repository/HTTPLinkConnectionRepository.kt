package org.mz.mzdkplayer.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.mz.mzdkplayer.data.model.HTTPLinkConnection

class HTTPLinkConnectionRepository(context: Context) : BaseConnectionRepository<HTTPLinkConnection>(
    context, "http_link_connections_prefs", object : TypeToken<List<HTTPLinkConnection>>() {}.type
) {
    override fun getId(connection: HTTPLinkConnection) = connection.id
    override fun tag() = "HTTPLinkRepo"

    override fun sanitize(connection: HTTPLinkConnection) = HTTPLinkConnection(
        id = connection.id,
        name = connection.name ?: "未命名连接",
        serverAddress = connection.serverAddress ?: "未知地址",
        shareName = connection.shareName ?: "未知路径"
    )
}
