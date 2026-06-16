package btcrenaud.tracking

import btcrenaud.tracking.commands.TrackingCommandExecutor
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton

@Singleton
object TrackingExtension : Initializable {

    override suspend fun initialize() {
        TrackingCommandExecutor.register()
    }

    override suspend fun shutdown() {
        // All @Singleton services are automatically shut down by Koin.
    }
}
