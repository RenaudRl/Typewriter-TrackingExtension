package com.btc.typewriter.tracking.entries.artifact

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import com.typewritermc.engine.paper.entry.AssetManager
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Entry(
    name = "tracking_artifact",
    description = "Stores player tracking sessions and points",
    color = Colors.BLUE,
    icon = "mdi:map-marker-path"
)
@Tags("btc", "tracking", "storage", "artifact")
class TrackingArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique artifact identifier for file storage")
    override val artifactId: String = "tracking_storage",
) : ArtifactEntry, Listener {

    companion object {
        private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
        private val gson: Gson
            get() = get(Gson::class.java, named("dataSerializer"))
        private val assetManager: AssetManager
            get() = get(AssetManager::class.java)
        private val states = ConcurrentHashMap<String, ArtifactState>()
        private val saveTaskIds = ConcurrentHashMap<String, Int>()
        private val LOGGER = LoggerFactory.getLogger(TrackingArtifactEntry::class.java)
    }

    private data class ArtifactState(
        val data: ConcurrentHashMap<UUID, PlayerTrackingData> = ConcurrentHashMap(),
        var dirty: Boolean = false,
        val nextSessionId: AtomicInteger = AtomicInteger(1)
    )

    data class PlayerTrackingData(
        val sessions: MutableList<TrackingSession> = mutableListOf()
    )

    data class TrackingSession(
        val sessionId: String = "1",
        val startTime: Long = System.currentTimeMillis(),
        var endTime: Long = 0,
        val points: MutableList<TrackingPoint> = mutableListOf()
    )

    data class TrackingPoint(
        val x: Double,
        val y: Double,
        val z: Double,
        val world: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        loadData()
        LOGGER.info("TrackingArtifactEntry initialized with id={}, artifactId={}", id, artifactId)
    }

    private fun getState(): ArtifactState {
        return states.computeIfAbsent(id) { ArtifactState() }
    }

    fun getPlayerData(playerId: UUID): PlayerTrackingData {
        return getState().data.computeIfAbsent(playerId) { PlayerTrackingData() }
    }

    fun addPoint(playerId: UUID, sessionId: String, point: TrackingPoint) {
        val data = getPlayerData(playerId)
        val session = data.sessions.find { it.sessionId == sessionId }
        if (session != null) {
            session.points.add(point)
            LOGGER.debug("Added point to session {} for player {}, total points: {}", sessionId, playerId, session.points.size)
            markDirty()
        } else {
            LOGGER.warn("Could not find session {} for player {}", sessionId, playerId)
        }
    }

    fun startSession(playerId: UUID): TrackingSession {
        val state = getState()
        val sessionNumber = state.nextSessionId.getAndIncrement()
        val session = TrackingSession(sessionId = sessionNumber.toString())
        getPlayerData(playerId).sessions.add(session)
        LOGGER.info("Started tracking session {} for player {}", session.sessionId, playerId)
        markDirty()
        return session
    }

    fun endSession(playerId: UUID, sessionId: String) {
        val data = getPlayerData(playerId)
        val session = data.sessions.find { it.sessionId == sessionId }
        if (session != null) {
            session.endTime = System.currentTimeMillis()
            LOGGER.info("Ended tracking session {} for player {} with {} points", sessionId, playerId, session.points.size)
            markDirty()
        }
    }

    private fun markDirty() {
        getState().dirty = true
        scheduleSave()
    }

    private fun scheduleSave() {
        if (saveTaskIds.containsKey(id)) return
        val taskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
            saveTaskIds.remove(id)
            save()
        }, 20L * 5).taskId // Save after 5 seconds delay
        saveTaskIds[id] = taskId
    }

    private fun save(force: Boolean = false) {
        val state = states[id]
        if (state == null) {
            LOGGER.warn("Cannot save tracking artifact {}: state is null", id)
            return
        }
        if (!force && !state.dirty) {
            LOGGER.debug("Skipping save for {} - not dirty", id)
            return
        }

        runCatching {
            val json = JsonObject()
            val playersJson = JsonObject()

            var totalPoints = 0
            state.data.forEach { (uuid, playerData) ->
                totalPoints += playerData.sessions.sumOf { it.points.size }
                playersJson.add(uuid.toString(), gson.toJsonTree(playerData))
            }

            // Save the next session ID counter so it persists across restarts
            json.addProperty("nextSessionId", state.nextSessionId.get())
            json.add("players", playersJson)
            
            val jsonString = gson.toJson(json)
            
            LOGGER.debug("Saving tracking artifact {} (artifactId={}) with {} players, {} total points, nextSessionId={}",
                id, artifactId, state.data.size, totalPoints, state.nextSessionId.get())
            
            runBlocking {
                assetManager.storeStringAsset(this@TrackingArtifactEntry, jsonString)
            }
            state.dirty = false
            LOGGER.info("Successfully saved tracking artifact {} with {} players and {} points", id, state.data.size, totalPoints)
        }.onFailure { error ->
            LOGGER.error("Failed to save tracking artifact $id (artifactId=$artifactId)", error)
        }
    }

    private fun loadData() {
        runCatching {
            LOGGER.info("Loading tracking artifact id={}, artifactId={}", id, artifactId)
            val content = runBlocking { assetManager.fetchStringAsset(this@TrackingArtifactEntry) }
            if (content.isNullOrBlank()) {
                LOGGER.info("No existing data for tracking artifact {} - starting fresh", artifactId)
                return
            }

            val json = gson.fromJson(content, JsonObject::class.java) ?: return
            
            val state = getState()
            
            // Load the next session ID counter
            if (json.has("nextSessionId")) {
                val nextId = json.get("nextSessionId").asInt
                state.nextSessionId.set(nextId)
                LOGGER.debug("Loaded nextSessionId: {}", nextId)
            }
            
            val playersJson = json.getAsJsonObject("players") ?: return

            var loadedPlayers = 0
            var loadedPoints = 0
            var maxSessionId = 0
            
            playersJson.entrySet().forEach { (uuidStr, playerJson) ->
                runCatching {
                    val uuid = UUID.fromString(uuidStr)
                    val playerData = gson.fromJson(playerJson, PlayerTrackingData::class.java)
                    // Ensure mutable lists are initialized (defensive deserialization)
                    val safePlayerData = PlayerTrackingData(
                        sessions = playerData.sessions.map { session ->
                            // Track the highest session ID
                            session.sessionId.toIntOrNull()?.let { 
                                if (it > maxSessionId) maxSessionId = it 
                            }
                            TrackingSession(
                                sessionId = session.sessionId,
                                startTime = session.startTime,
                                endTime = session.endTime,
                                points = session.points.toMutableList()
                            )
                        }.toMutableList()
                    )
                    state.data[uuid] = safePlayerData
                    loadedPlayers++
                    loadedPoints += safePlayerData.sessions.sumOf { it.points.size }
                }.onFailure { error ->
                    LOGGER.warn("Failed to load player tracking data for $uuidStr", error)
                }
            }
            
            // If nextSessionId wasn't saved, derive it from the highest existing session ID
            if (!json.has("nextSessionId") && maxSessionId > 0) {
                state.nextSessionId.set(maxSessionId + 1)
                LOGGER.info("Derived nextSessionId from data: {}", maxSessionId + 1)
            }
            
            LOGGER.info("Loaded tracking artifact {} with {} players and {} total points (nextSessionId={})", 
                artifactId, loadedPlayers, loadedPoints, state.nextSessionId.get())
        }.onFailure { error ->
            LOGGER.warn("Failed to load tracking artifact {} (artifactId={})", id, artifactId, error)
        }
    }

    @EventHandler
    fun onUnload(event: TypewriterUnloadEvent) {
        LOGGER.info("Unloading tracking artifact {}, forcing save...", id)
        HandlerList.unregisterAll(this)
        // Cancel any pending save tasks
        saveTaskIds[id]?.let {
            Bukkit.getScheduler().cancelTask(it)
            LOGGER.debug("Cancelled pending save task for {}", id)
        }
        saveTaskIds.remove(id)
        // Force immediate save
        save(force = true)
        states.remove(id)
    }
}
