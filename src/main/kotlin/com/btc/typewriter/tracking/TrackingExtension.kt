package com.btc.typewriter.tracking

import com.btc.typewriter.tracking.commands.TrackingCommand
import com.btc.typewriter.tracking.service.TrackingService
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton

@Singleton
class TrackingExtension : Initializable {
    
    companion object {
        // Direct references to avoid ClassLoader conflicts with Koin
        var service: TrackingService? = null
            private set
        var command: TrackingCommand? = null
            private set
    }
    
    override suspend fun initialize() {
        service = TrackingService()
        command = TrackingCommand()
    }

    override suspend fun shutdown() {
        command?.unregister()
        command = null
        service?.shutdown()
        service = null
    }
}
