package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import org.mz.mzdkplayer.data.model.FTPConnection
import org.mz.mzdkplayer.data.repository.FTPConnectionRepository

class FTPListViewModel(application: Application) : BaseConnectionListViewModel<FTPConnection>(application) {

    override val repository = FTPConnectionRepository(application)

    init {
        loadConnections()
    }

    override fun getId(connection: FTPConnection) = connection.id

    override fun hasDuplicateConnection(connections: List<FTPConnection>, newConnection: FTPConnection): Boolean {
        return connections.any { existing ->
            existing.id == newConnection.id ||
                    (existing.ip == newConnection.ip &&
                            existing.username == newConnection.username &&
                            existing.shareName == newConnection.shareName &&
                            existing.password == newConnection.password)
        }
    }
}
