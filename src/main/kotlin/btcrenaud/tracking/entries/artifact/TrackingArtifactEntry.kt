package btcrenaud.tracking.entries.artifact

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import com.typewritermc.engine.paper.entry.AssetManager
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.engine.paper.events.TypewriterUnloadEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
@Singleton
class TrackingArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique artifact identifier for file storage")
    override val artifactId: String = "tracking_storage",
) : ArtifactEntry, Listener {

    companion object {
        private const val MAX_POINTS_PER_SESSION = 10_000
        private const val SAVE_DELAY_MS = 5_000L
        private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
        private val gson: Gson
            get() = get(Gson::class.java, named("dataSerializer"))
        private val assetManager: AssetManager
            get() = get(AssetManager::class.java)
        private val states = ConcurrentHashMap<String, ArtifactState>()
        private val LOGGER = LoggerFactory.getLogger(TrackingArtifactEntry::class.java)
        private var pendingSaveJob: Job? = null
    }

    private data class ArtifactState(
        val data: ConcurrentHashMap<UUID, PlayerTrackingData> = ConcurrentHashMap(),
        @Volatile var dirty: Boolean = false,
        val nextSessionId: AtomicInteger = AtomicInteger(1)
    )

    data class PlayerTrackingData(
        val sessions: List<TrackingSession> = emptyList()
    )

    data class TrackingSession(
        val sessionId: String = "1",
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long = 0,
        val points: List<TrackingPoint> = emptyList()
    )

    data class TrackingPoint(
        val x: Double,
        val y: Double,
        val z: Double,
        val world: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Debounce save is managed in companion object (KSP: entries must be stateless)

    init {
        registerEvents()
        loadData()
        LOGGER.info("TrackingArtifactEntry initialized with id={}, artifactId={}", id, artifactId)
    }

    private fun registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    private fun getState(): ArtifactState {
        return states.computeIfAbsent(id) { ArtifactState() }
    }

    fun getPlayerData(playerId: UUID): PlayerTrackingData {
        return getState().data.computeIfAbsent(playerId) { PlayerTrackingData() }
    }

    fun addPoint(playerId: UUID, sessionId: String, point: TrackingPoint) {
        val state = getState()
        val playerData = state.data[playerId] ?: return

        val sessionIndex = playerData.sessions.indexOfFirst { it.sessionId == sessionId }
        if (sessionIndex < 0) {
            LOGGER.warn("Could not find session {} for player {}", sessionId, playerId)
            return
        }

        val session = playerData.sessions[sessionIndex]
        // Enforce max points per session (10000)
        if (session.points.size >= MAX_POINTS_PER_SESSION) return

        val updatedSession = session.copy(
            points = session.points + point
        )
        val updatedSessions = playerData.sessions.toMutableList().apply {
            set(sessionIndex, updatedSession)
        }
        state.data[playerId] = playerData.copy(sessions = updatedSessions)

        markDirty()
    }

    fun startSession(playerId: UUID): TrackingSession {
        val state = getState()
        val sessionNumber = state.nextSessionId.getAndIncrement()
        val session = TrackingSession(sessionId = sessionNumber.toString())
        val playerData = getPlayerData(playerId)
        state.data[playerId] = playerData.copy(
            sessions = playerData.sessions + session
        )
        LOGGER.info("Started tracking session {} for player {}", session.sessionId, playerId)
        markDirty()
        return session
    }

    fun endSession(playerId: UUID, sessionId: String) {
        val state = getState()
        val playerData = state.data[playerId] ?: return

        val sessionIndex = playerData.sessions.indexOfFirst { it.sessionId == sessionId }
        if (sessionIndex < 0) return

        val session = playerData.sessions[sessionIndex]
        val updatedSession = session.copy(endTime = System.currentTimeMillis())
        val updatedSessions = playerData.sessions.toMutableList().apply {
            set(sessionIndex, updatedSession)
        }
        state.data[playerId] = playerData.copy(sessions = updatedSessions)

        LOGGER.info("Ended tracking session {} for player {} with {} points", sessionId, playerId, updatedSession.points.size)
        markDirty()
    }

    private fun markDirty() {
        getState().dirty = true
        scheduleSave()
    }

    private fun scheduleSave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = CoroutineScope(Dispatchers.Default).launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    private fun save(force: Boolean = false) {
        val state = states[id] ?: return
        if (!force && !state.dirty) return

        runCatching {
            val json = JsonObject()
            val playersJson = JsonObject()

            var totalPoints = 0
            state.data.forEach { (uuid, playerData) ->
                totalPoints += playerData.sessions.sumOf { it.points.size }
                playersJson.add(uuid.toString(), gson.toJsonTree(playerData))
            }

            json.addProperty("nextSessionId", state.nextSessionId.get())
            json.add("players", playersJson)

            val jsonString = gson.toJson(json)

            LOGGER.debug("Saving tracking artifact {} (artifactId={}) with {} players, {} total points",
                id, artifactId, state.data.size, totalPoints)

            CoroutineScope(Dispatchers.IO).launch {
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
            val content = runBlocking(Dispatchers.IO) {
                assetManager.fetchStringAsset(this@TrackingArtifactEntry)
            }
            if (content.isNullOrBlank()) {
                LOGGER.info("No existing data for tracking artifact {} - starting fresh", artifactId)
                return
            }

            val json = gson.fromJson(content, JsonObject::class.java) ?: return
            val state = getState()

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
                    val safePlayerData = PlayerTrackingData(
                        sessions = playerData.sessions.map { session ->
                            session.sessionId.toIntOrNull()?.let {
                                if (it > maxSessionId) maxSessionId = it
                            }
                            TrackingSession(
                                sessionId = session.sessionId,
                                startTime = session.startTime,
                                endTime = session.endTime,
                                points = session.points.toList()
                            )
                        }
                    )
                    state.data[uuid] = safePlayerData
                    loadedPlayers++
                    loadedPoints += safePlayerData.sessions.sumOf { it.points.size }
                }.onFailure { error ->
                    LOGGER.warn("Failed to load player tracking data for $uuidStr", error)
                }
            }

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
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        save(force = true)
        states.remove(id)
    }
}
