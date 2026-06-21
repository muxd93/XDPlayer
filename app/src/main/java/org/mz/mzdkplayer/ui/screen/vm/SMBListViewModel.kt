package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import org.mz.mzdkplayer.data.model.SMBConnection
import org.mz.mzdkplayer.data.repository.SMBConnectionRepository

class SMBListViewModel(application: Application) : BaseConnectionListViewModel<SMBConnection>(application) {

    override val repository = SMBConnectionRepository(application)

    init {
        loadConnections()
    }

    override fun getId(connection: SMBConnection) = connection.id

    override fun hasDuplicateConnection(connections: List<SMBConnection>, newConnection: SMBConnection): Boolean {
        return connections.any { existing ->
            existing.id == newConnection.id ||
                    (existing.ip == newConnection.ip &&
                            existing.username == newConnection.username &&
                            existing.shareName == newConnection.shareName &&
                            existing.password == newConnection.password)
        }
    }
}
