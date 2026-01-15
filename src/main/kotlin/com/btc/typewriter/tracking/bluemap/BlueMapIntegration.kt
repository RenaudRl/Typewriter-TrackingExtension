package com.btc.typewriter.tracking.bluemap

import com.btc.typewriter.tracking.entries.artifact.TrackingArtifactEntry.TrackingPoint
import com.flowpowered.math.vector.Vector3d
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.BlueMapMap
import de.bluecolored.bluemap.api.markers.LineMarker
import de.bluecolored.bluemap.api.markers.MarkerSet
import de.bluecolored.bluemap.api.markers.POIMarker
import de.bluecolored.bluemap.api.math.Color
import de.bluecolored.bluemap.api.math.Line
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.util.UUID

object BlueMapIntegration {
    
    private const val MARKER_SET_ID_PREFIX = "tracking_"
    private val LOGGER = LoggerFactory.getLogger(BlueMapIntegration::class.java)
    
    /**
     * Shows a tracking session on BlueMap with optional points and path display.
     */
    fun showSession(
        playerId: UUID,
        playerName: String,
        sessionId: String,
        points: List<TrackingPoint>,
        showPoints: Boolean,
        showPath: Boolean
    ) {
        LOGGER.info("[BlueMap] showSession called: player={}, session={}, points={}, showPoints={}, showPath={}", 
            playerName, sessionId, points.size, showPoints, showPath)
        
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            LOGGER.warn("[BlueMap] BlueMap plugin is NOT enabled!")
            return
        }
        if (!showPoints && !showPath) {
            LOGGER.info("[BlueMap] Both showPoints and showPath are false, nothing to display")
            return
        }
        if (points.size < 2 && showPath) {
            LOGGER.warn("[BlueMap] Not enough points to draw path: {} points", points.size)
        }
        
        val apiOptional = BlueMapAPI.getInstance()
        if (!apiOptional.isPresent) {
            LOGGER.error("[BlueMap] BlueMapAPI.getInstance() returned empty! API not available.")
            return
        }
        
        val api = apiOptional.get()
        LOGGER.info("[BlueMap] BlueMapAPI available. BlueMap version: {}", api.blueMapVersion)
        
        runCatching {
            val setLabel = "Tracking: $playerName"
            val setId = "${MARKER_SET_ID_PREFIX}${sessionId}"
            
            // Generate a consistent color for this player
            val color = colorFromUUID(playerId)
            LOGGER.debug("[BlueMap] Using color: r={}, g={}, b={}", color.red, color.green, color.blue)
            
            // Group points by world and sort by timestamp
            val pointsByWorld = points.sortedBy { it.timestamp }.groupBy { it.world }
            LOGGER.info("[BlueMap] Points grouped by {} worlds: {}", pointsByWorld.keys.size, pointsByWorld.keys)
            
            var totalMarkersAdded = 0
            var totalPathsAdded = 0
            
            pointsByWorld.forEach { (worldName, worldPoints) ->
                LOGGER.info("[BlueMap] Processing world '{}' with {} points", worldName, worldPoints.size)
                
                // Get the Bukkit world
                val bukkitWorld = Bukkit.getWorld(worldName)
                if (bukkitWorld == null) {
                    LOGGER.error("[BlueMap] Bukkit world '{}' not found! Available worlds: {}", 
                        worldName, Bukkit.getWorlds().map { it.name })
                    return@forEach
                }
                
                // Get BlueMap world for this Bukkit world
                val blueMapWorld = api.getWorld(bukkitWorld)
                if (!blueMapWorld.isPresent) {
                    LOGGER.error("[BlueMap] BlueMap world not found for Bukkit world '{}'. Available BlueMap worlds: {}", 
                        worldName, api.worlds.map { it.id })
                    return@forEach
                }
                
                // Get all maps for this world
                val worldMaps = blueMapWorld.get().maps
                if (worldMaps.isEmpty()) {
                    LOGGER.error("[BlueMap] No BlueMap maps found for world '{}'. BlueMap world ID: {}", 
                        worldName, blueMapWorld.get().id)
                    return@forEach
                }
                
                LOGGER.info("[BlueMap] Found {} BlueMap maps for world '{}': {}", 
                    worldMaps.size, worldName, worldMaps.map { it.id })
                
                // Create or get marker set for each map
                worldMaps.forEach { map ->
                    LOGGER.debug("[BlueMap] Processing map '{}'", map.id)
                    
                    val markerSet = map.markerSets.computeIfAbsent(setId) {
                        LOGGER.info("[BlueMap] Creating new MarkerSet '{}' for map '{}'", setId, map.id)
                        MarkerSet.builder()
                            .label(setLabel)
                            .toggleable(true)
                            .defaultHidden(false)
                            .build()
                    }
                    
                    // Show individual points if enabled
                    if (showPoints) {
                        worldPoints.forEachIndexed { index, p ->
                            val position = Vector3d(p.x, p.y, p.z)
                            val marker = POIMarker.builder()
                                .label("$playerName #${index + 1}")
                                .position(position)
                                .maxDistance(200.0)
                                .build()
                            markerSet.markers["point_$index"] = marker
                            totalMarkersAdded++
                        }
                        LOGGER.info("[BlueMap] Added {} POI markers to map '{}'", worldPoints.size, map.id)
                    }
                    
                    // Show connected path if enabled and we have enough points
                    if (showPath && worldPoints.size >= 2) {
                        LOGGER.info("[BlueMap] Building line with {} points for map '{}'", worldPoints.size, map.id)
                        
                        try {
                            val linePoints = worldPoints.map { p -> Vector3d(p.x, p.y, p.z) }
                            LOGGER.debug("[BlueMap] Line points: first={}, last={}", 
                                linePoints.firstOrNull(), linePoints.lastOrNull())
                            
                            val line = Line(linePoints)
                            LOGGER.debug("[BlueMap] Line created with {} points, min={}, max={}", 
                                line.pointCount, line.min, line.max)
                            
                            val lineMarker = LineMarker.builder()
                                .label("$playerName path")
                                .line(line)
                                .lineColor(color)
                                .lineWidth(3)
                                .depthTestEnabled(false)
                                .build()
                            
                            val markerId = "path"
                            markerSet.markers[markerId] = lineMarker
                            totalPathsAdded++
                            
                            LOGGER.info("[BlueMap] Successfully added LineMarker '{}' to MarkerSet '{}' on map '{}'", 
                                markerId, setId, map.id)
                            LOGGER.info("[BlueMap] LineMarker details: label='{}', lineWidth={}, pointCount={}", 
                                lineMarker.label, lineMarker.lineWidth, lineMarker.line.pointCount)
                            
                            // Verify marker was added
                            val addedMarker = markerSet.markers[markerId]
                            if (addedMarker != null) {
                                LOGGER.info("[BlueMap] Verified: marker '{}' exists in MarkerSet", markerId)
                            } else {
                                LOGGER.error("[BlueMap] ERROR: marker '{}' was NOT added to MarkerSet!", markerId)
                            }
                            
                        } catch (e: Exception) {
                            LOGGER.error("[BlueMap] Exception while creating LineMarker", e)
                        }
                    }
                }
            }
            
            LOGGER.info("[BlueMap] Session display complete: {} POI markers, {} path lines added", 
                totalMarkersAdded, totalPathsAdded)
            
        }.onFailure { e -> 
            LOGGER.error("[BlueMap] Failed to show BlueMap session", e)
        }
    }
    
    /**
     * Hides a tracking session from BlueMap.
     */
    fun hideSession(sessionId: String) {
        LOGGER.info("[BlueMap] hideSession called for session: {}", sessionId)
        
        if (!Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            LOGGER.debug("[BlueMap] BlueMap not enabled")
            return
        }
        
        BlueMapAPI.getInstance().ifPresent { api ->
            runCatching {
                val setId = "${MARKER_SET_ID_PREFIX}${sessionId}"
                var removed = 0
                api.maps.forEach { map ->
                    if (map.markerSets.remove(setId) != null) {
                        removed++
                        LOGGER.debug("[BlueMap] Removed marker set '{}' from map '{}'", setId, map.id)
                    }
                }
                LOGGER.info("[BlueMap] Removed marker set from {} maps", removed)
            }.onFailure { e -> 
                LOGGER.error("[BlueMap] Failed to hide BlueMap session", e)
            }
        }
    }
    
    /**
     * Generates a consistent color from a UUID for player identification.
     */
    private fun colorFromUUID(uuid: UUID): Color {
        val hash = uuid.hashCode()
        val r = ((hash shr 16) and 0xFF)
        val g = ((hash shr 8) and 0xFF)
        val b = (hash and 0xFF)
        // Ensure brightness is reasonable
        val minBrightness = 100
        return Color(
            maxOf(r, minBrightness),
            maxOf(g, minBrightness),
            maxOf(b, minBrightness),
            1.0f
        )
    }
}
