package btcrenaud.tracking.service

import btcrenaud.tracking.bluemap.BlueMapIntegration
import btcrenaud.tracking.entries.artifact.TrackingArtifactEntry
import btcrenaud.tracking.entries.manifest.TrackingManifestEntry
import com.github.retrooper.packetevents.protocol.particle.Particle
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import com.typewritermc.engine.paper.extensions.packetevents.sendPacketTo
import btcrenaud.tracking.FoliaScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.koin.java.KoinJavaComponent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class TrackingService : Initializable, Listener {

    private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
    private val blueMapIntegration: BlueMapIntegration by lazy {
        KoinJavaComponent.get(BlueMapIntegration::class.java)
    }

    private val activeSessions = ConcurrentHashMap<UUID, String>()
    private val activeInspections = ConcurrentHashMap<UUID, InspectionSession>()
    private val lastRecordTime = ConcurrentHashMap<UUID, Long>()

    private var trackingTask: FoliaScheduler.TaskHandle? = null
    private var particleTask: FoliaScheduler.TaskHandle? = null
    private var animationCycle = 0

    // Debounce save
    private var pendingSaveJob: Job? = null

    // Cached manifest (refreshed on interval)
    private var cachedManifest: TrackingManifestEntry? = null
    private var manifestCacheTime = 0L

    companion object {
        private const val PARTICLE_ANIMATION_DURATION = 50
        private const val MANIFEST_CACHE_TTL_MS = 5_000L
    }

    data class InspectionSession(
        val targetPlayerId: UUID,
        val sessionId: String,
        val points: List<TrackingArtifactEntry.TrackingPoint>
    )

    override suspend fun initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        startTrackingTask()
        startParticleTask()
    }

    override suspend fun shutdown() {
        trackingTask?.cancel()
        trackingTask = null
        particleTask?.cancel()
        particleTask = null
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        HandlerList.unregisterAll(this)
        activeSessions.clear()
        activeInspections.clear()
        lastRecordTime.clear()
    }

    private fun getManifest(): TrackingManifestEntry? {
        val now = System.currentTimeMillis()
        if (cachedManifest != null && now - manifestCacheTime < MANIFEST_CACHE_TTL_MS) {
            return cachedManifest
        }
        cachedManifest = Query(TrackingManifestEntry::class).find().firstOrNull()
        manifestCacheTime = now
        return cachedManifest
    }

    private fun startTrackingTask() {
        trackingTask = FoliaScheduler.runAtFixedRate(20L, 20L) {
            recordTick()
        }
    }

    private fun startParticleTask() {
        particleTask = FoliaScheduler.runAtFixedRate(1L, 1L) {
            particleTick()
        }
    }

    private fun recordTick() {
        val manifest = getManifest() ?: return
        if (!manifest.trackingEnabled) return

        val now = System.currentTimeMillis()
        val intervalMs = manifest.pointIntervalSeconds * 1000L

        Bukkit.getOnlinePlayers().forEach { player ->
            val sessionId = activeSessions[player.uniqueId] ?: return@forEach
            val lastTime = lastRecordTime[player.uniqueId] ?: 0L

            if (now - lastTime >= intervalMs) {
                recordPoint(player, sessionId, manifest)
                lastRecordTime[player.uniqueId] = now
            }
        }
    }

    private fun particleTick() {
        val manifest = getManifest() ?: return

        animationCycle++
        if (animationCycle > PARTICLE_ANIMATION_DURATION) {
            animationCycle = 0
        }

        activeInspections.forEach { (adminId, session) ->
            val admin = Bukkit.getPlayer(adminId) ?: return@forEach
            renderAnimatedPath(admin, session)
        }
    }

    private fun recordPoint(player: Player, sessionId: String, manifest: TrackingManifestEntry) {
        val storage = manifest.storage.get() ?: return
        val loc = player.location
        val point = TrackingArtifactEntry.TrackingPoint(
            x = loc.x,
            y = loc.y,
            z = loc.z,
            world = loc.world.name,
            timestamp = System.currentTimeMillis()
        )

        storage.addPoint(player.uniqueId, sessionId, point)
    }

    private fun renderAnimatedPath(admin: Player, session: InspectionSession) {
        val points = session.points.filter { it.world == admin.world.name }
        if (points.size < 2) return

        val progress = animationCycle.toDouble() / PARTICLE_ANIMATION_DURATION
        val easedProgress = easeInOutQuad(progress)

        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]

            val distSq = (start.x - admin.location.x).let { dx ->
                (start.z - admin.location.z).let { dz -> dx * dx + dz * dz }
            }
            if (distSq > 900) continue

            for (offset in 0..1) {
                val percentage = (easedProgress + offset * 0.1) % 1.0
                val x = lerp(start.x, end.x, percentage)
                val y = lerp(start.y, end.y, percentage)
                val z = lerp(start.z, end.z, percentage)

                val color = colorFromIndex(i)

                val packet = WrapperPlayServerParticle(
                    Particle(ParticleTypes.DUST, ParticleDustData(1f, color.toPacketColor())),
                    true,
                    Vector3d(x, y, z),
                    Vector3f.zero(),
                    0f,
                    1
                )
                packet sendPacketTo admin
            }
        }
    }

    private fun lerp(start: Double, end: Double, t: Double): Double {
        return start + (end - start) * t
    }

    private fun easeInOutQuad(t: Double): Double {
        return if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t
    }

    private fun colorFromIndex(index: Int): Color {
        val hue = (index * 37) % 360
        return Color.fromRGB(
            java.awt.Color.HSBtoRGB(hue / 360f, 0.8f, 1f) and 0xFFFFFF
        )
    }

    private fun Color.toPacketColor(): com.github.retrooper.packetevents.protocol.color.Color {
        return com.github.retrooper.packetevents.protocol.color.Color(red, green, blue)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val manifest = getManifest() ?: return
        if (!manifest.trackingEnabled) return

        val storage = manifest.storage.get() ?: return
        val session = storage.startSession(event.player.uniqueId)
        activeSessions[event.player.uniqueId] = session.sessionId
        lastRecordTime[event.player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val sessionId = activeSessions.remove(event.player.uniqueId) ?: return
        lastRecordTime.remove(event.player.uniqueId)
        val manifest = getManifest() ?: return
        val storage = manifest.storage.get() ?: return
        storage.endSession(event.player.uniqueId, sessionId)
    }

    fun startInspection(admin: Player, targetName: String, sessionId: String): Boolean {
        val target = Bukkit.getOfflinePlayer(targetName)

        val manifest = getManifest() ?: return false
        val storage = manifest.storage.get() ?: return false

        val data = storage.getPlayerData(target.uniqueId)
        val session = data.sessions.find { it.sessionId == sessionId } ?: return false

        val inspection = InspectionSession(target.uniqueId, sessionId, session.points)
        activeInspections[admin.uniqueId] = inspection

        if (manifest.blueMapEnabled) {
            blueMapIntegration.showSession(
                target.uniqueId,
                targetName,
                sessionId,
                session.points,
                manifest.showBlueMapPoints,
                manifest.showBlueMapPath
            )
        }

        return true
    }

    fun stopInspection(admin: Player) {
        val session = activeInspections.remove(admin.uniqueId) ?: return
        blueMapIntegration.hideSession(session.sessionId)
    }

    fun refreshInspection(admin: Player) {
        val inspection = activeInspections[admin.uniqueId] ?: return
        val manifest = getManifest() ?: return
        val target = Bukkit.getOfflinePlayer(inspection.targetPlayerId)

        blueMapIntegration.hideSession(inspection.sessionId)

        if (manifest.blueMapEnabled) {
            blueMapIntegration.showSession(
                inspection.targetPlayerId,
                target.name ?: inspection.targetPlayerId.toString(),
                inspection.sessionId,
                inspection.points,
                manifest.showBlueMapPoints,
                manifest.showBlueMapPath
            )
        }
    }

    fun refreshAllInspections() {
        val manifest = getManifest() ?: return

        activeInspections.forEach { (adminId, inspection) ->
            val admin = Bukkit.getPlayer(adminId) ?: return@forEach
            val target = Bukkit.getOfflinePlayer(inspection.targetPlayerId)

            blueMapIntegration.hideSession(inspection.sessionId)

            if (manifest.blueMapEnabled) {
                blueMapIntegration.showSession(
                    inspection.targetPlayerId,
                    target.name ?: inspection.targetPlayerId.toString(),
                    inspection.sessionId,
                    inspection.points,
                    manifest.showBlueMapPoints,
                    manifest.showBlueMapPath
                )
            }
        }
    }

    fun getPlayerSessions(targetName: String): List<String> {
        val target = Bukkit.getOfflinePlayer(targetName)
        val manifest = getManifest() ?: return emptyList()
        val storage = manifest.storage.get() ?: return emptyList()
        return storage.getPlayerData(target.uniqueId).sessions.map { it.sessionId }
    }
}
