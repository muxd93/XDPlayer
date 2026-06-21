package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import org.mz.mzdkplayer.data.model.HTTPLinkConnection
import org.mz.mzdkplayer.data.repository.HTTPLinkConnectionRepository

class HTTPLinkListViewModel(application: Application) : BaseConnectionListViewModel<HTTPLinkConnection>(application) {

    override val repository = HTTPLinkConnectionRepository(application)

    init {
        loadConnections()
    }

    override fun getId(connection: HTTPLinkConnection) = connection.id

    override fun hasDuplicateConnection(connections: List<HTTPLinkConnection>, newConnection: HTTPLinkConnection): Boolean {
        return connections.any { existing ->
            existing.id == newConnection.id ||
                    (existing.serverAddress == newConnection.serverAddress &&
                            existing.shareName == newConnection.shareName)
        }
    }
}
