package btcrenaud.tracking.entries.manifest

import btcrenaud.tracking.entries.artifact.TrackingArtifactEntry
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

@Entry(
    name = "tracking_manifest",
    description = "Master configuration for Player Tracking",
    color = Colors.ORANGE,
    icon = "mdi:radar"
)
@Tags("btc", "tracking", "manifest", "config")
@Singleton
class TrackingManifestEntry(
    override val id: String = "",
    override val name: String = "",

    @Help("Artifact used to store tracking data")
    val storage: Ref<TrackingArtifactEntry> = emptyRef(),

    @Help("Track every X seconds")
    val pointIntervalSeconds: Long = 20,

    @Help("Enable BlueMap integration")
    val blueMapEnabled: Boolean = true,

    @Help("Show individual points on BlueMap")
    val showBlueMapPoints: Boolean = true,

    @Help("Show connected path lines on BlueMap")
    val showBlueMapPath: Boolean = true,

    @Help("Enable tracking for all players")
    val trackingEnabled: Boolean = true

) : ManifestEntry, Listener {

    companion object {
        private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
    }

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onUnload(event: TypewriterUnloadEvent) {
        HandlerList.unregisterAll(this)
    }
}
