package org.mz.mzdkplayer.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.mz.mzdkplayer.data.model.FTPConnection

class FTPConnectionRepository(context: Context) : BaseConnectionRepository<FTPConnection>(
    context, "ftp_connections_prefs", object : TypeToken<List<FTPConnection>>() {}.type
) {
    override fun getId(connection: FTPConnection) = connection.id
    override fun tag() = "FTPRepo"

    override fun sanitize(connection: FTPConnection) = FTPConnection(
        id = connection.id,
        name = connection.name ?: "未命名连接",
        ip = connection.ip ?: "未知IP",
        port = connection.port,
        username = connection.username ?: "未知用户",
        password = connection.password ?: "",
        shareName = connection.shareName ?: "未知路径"
    )
}
