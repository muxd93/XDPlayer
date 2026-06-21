package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import org.mz.mzdkplayer.data.model.WebDavConnection
import org.mz.mzdkplayer.data.repository.WebDavConnectionRepository

class WebDavListViewModel(application: Application) : BaseConnectionListViewModel<WebDavConnection>(application) {

    override val repository = WebDavConnectionRepository(application)

    init {
        loadConnections()
    }

    override fun getId(connection: WebDavConnection) = connection.id

    override fun hasDuplicateConnection(connections: List<WebDavConnection>, newConnection: WebDavConnection): Boolean {
        return connections.any { existing ->
            existing.id == newConnection.id ||
                    (existing.baseUrl == newConnection.baseUrl &&
                            existing.username == newConnection.username)
        }
    }
}
