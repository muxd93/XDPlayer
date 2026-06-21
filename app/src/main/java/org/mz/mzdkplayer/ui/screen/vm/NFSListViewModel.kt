package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import org.mz.mzdkplayer.data.model.NFSConnection
import org.mz.mzdkplayer.data.repository.NFSConnectionRepository

class NFSListViewModel(application: Application) : BaseConnectionListViewModel<NFSConnection>(application) {

    override val repository = NFSConnectionRepository(application)

    init {
        loadConnections()
    }

    override fun getId(connection: NFSConnection) = connection.id

    override fun hasDuplicateConnection(connections: List<NFSConnection>, newConnection: NFSConnection): Boolean {
        return connections.any { existing ->
            existing.id == newConnection.id ||
                    (existing.serverAddress == newConnection.serverAddress)
        }
    }
}
