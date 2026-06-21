package org.mz.mzdkplayer.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import org.mz.mzdkplayer.data.model.SMBConnection

class SMBConnectionRepository(context: Context) : BaseConnectionRepository<SMBConnection>(
    context, "smb_connections_prefs", object : TypeToken<List<SMBConnection>>() {}.type
) {
    override fun getId(connection: SMBConnection) = connection.id
    override fun tag() = "SMBConnectionRepository"

    override fun sanitize(connection: SMBConnection) = SMBConnection(
        id = connection.id,
        name = connection.name ?: "未命名连接",
        ip = connection.ip ?: "未知IP",
        username = connection.username ?: "未知用户",
        password = connection.password ?: "",
        shareName = connection.shareName ?: "未知路径"
    )
}
