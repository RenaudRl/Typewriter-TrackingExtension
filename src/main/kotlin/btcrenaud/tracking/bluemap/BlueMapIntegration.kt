package btcrenaud.tracking.bluemap

import btcrenaud.tracking.entries.artifact.TrackingArtifactEntry.TrackingPoint
import com.flowpowered.math.vector.Vector3d
import com.typewritermc.core.extension.annotations.Singleton
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.markers.LineMarker
import de.bluecolored.bluemap.api.markers.MarkerSet
import de.bluecolored.bluemap.api.markers.POIMarker
import de.bluecolored.bluemap.api.math.Color
import de.bluecolored.bluemap.api.math.Line
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.util.UUID

@Singleton
class BlueMapIntegration private constructor() {

    companion object {
        private const val MARKER_SET_ID_PREFIX = "tracking_"
        private val LOGGER = LoggerFactory.getLogger(BlueMapIntegration::class.java)
    }

    fun showSession(
        playerId: UUID,
        playerName: String,
        sessionId: String,
        points: List<TrackingPoint>,
        showPoints: Boolean,
        showPath: Boolean
    ) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) return
        if (!showPoints && !showPath) return

        val apiOptional = BlueMapAPI.getInstance()
        if (!apiOptional.isPresent) return

        val api = apiOptional.get()

        runCatching {
            val setLabel = "Tracking: $playerName"
            val setId = "${MARKER_SET_ID_PREFIX}${sessionId}"
            val color = colorFromUUID(playerId)

            val pointsByWorld = points.sortedBy { it.timestamp }.groupBy { it.world }

            pointsByWorld.forEach { (worldName, worldPoints) ->
                val bukkitWorld = Bukkit.getWorld(worldName) ?: return@forEach
                val blueMapWorld = api.getWorld(bukkitWorld)
                if (!blueMapWorld.isPresent) return@forEach

                val worldMaps = blueMapWorld.get().maps
                if (worldMaps.isEmpty()) return@forEach

                worldMaps.forEach { map ->
                    val markerSet = map.markerSets.computeIfAbsent(setId) {
                        MarkerSet.builder()
                            .label(setLabel)
                            .toggleable(true)
                            .defaultHidden(false)
                            .build()
                    }

                    if (showPoints) {
                        worldPoints.forEachIndexed { index, p ->
                            val position = Vector3d(p.x, p.y, p.z)
                            val marker = POIMarker.builder()
                                .label("$playerName #${index + 1}")
                                .position(position)
                                .maxDistance(200.0)
                                .build()
                            markerSet.markers["point_$index"] = marker
                        }
                    }

                    if (showPath && worldPoints.size >= 2) {
                        try {
                            val linePoints = worldPoints.map { p -> Vector3d(p.x, p.y, p.z) }
                            val line = Line(linePoints)
                            val lineMarker = LineMarker.builder()
                                .label("$playerName path")
                                .line(line)
                                .lineColor(color)
                                .lineWidth(3)
                                .depthTestEnabled(false)
                                .build()
                            markerSet.markers["path"] = lineMarker
                        } catch (e: Exception) {
                            LOGGER.error("[BlueMap] Exception while creating LineMarker", e)
                        }
                    }
                }
            }
        }.onFailure { e ->
            LOGGER.error("[BlueMap] Failed to show BlueMap session", e)
        }
    }

    fun hideSession(sessionId: String) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) return

        BlueMapAPI.getInstance().ifPresent { api ->
            runCatching {
                val setId = "${MARKER_SET_ID_PREFIX}${sessionId}"
                var removed = 0
                api.maps.forEach { map ->
                    if (map.markerSets.remove(setId) != null) {
                        removed++
                    }
                }
                if (removed > 0) LOGGER.debug("[BlueMap] Removed marker set from {} maps", removed)
            }.onFailure { e ->
                LOGGER.error("[BlueMap] Failed to hide BlueMap session", e)
            }
        }
    }

    private fun colorFromUUID(uuid: UUID): Color {
        val hash = uuid.hashCode()
        val r = ((hash shr 16) and 0xFF)
        val g = ((hash shr 8) and 0xFF)
        val b = (hash and 0xFF)
        val minBrightness = 100
        return Color(
            maxOf(r, minBrightness),
            maxOf(g, minBrightness),
            maxOf(b, minBrightness),
            1.0f
        )
    }
}
