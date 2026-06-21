package org.mz.mzdkplayer.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.mz.mzdkplayer.data.model.NFSConnection
import java.util.UUID

class NFSConnectionRepository(context: Context) : BaseConnectionRepository<NFSConnection>(
    context, "nfs_connections_prefs", object : TypeToken<List<NFSConnection>>() {}.type
) {
    override fun getId(connection: NFSConnection) = connection.id
    override fun tag() = "NfsRepo"

    override fun sanitize(connection: NFSConnection) = NFSConnection(
        id = connection.id ?: UUID.randomUUID().toString(),
        name = connection.name ?: "未命名连接",
        serverAddress = connection.serverAddress ?: "未知IP",
        shareName = connection.shareName ?: "未知路径"
    )
}
