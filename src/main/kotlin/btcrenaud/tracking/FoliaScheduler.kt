package btcrenaud.tracking

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

object FoliaScheduler {
    private val plugin: Plugin by lazy {
        Bukkit.getPluginManager().getPlugin("Typewriter")
            ?: Bukkit.getPluginManager().getPlugin("TypeWriter")
            ?: throw IllegalStateException("TypeWriter plugin not found")
    }
    private val folia: Boolean by lazy { detectFolia() }
    fun isFolia(): Boolean = folia
    private fun detectFolia(): Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); true
    } catch (_: ClassNotFoundException) { false }
      catch (_: Throwable) { false }

    interface TaskHandle { fun cancel(); val isCancelled: Boolean }
    private class BukkitHandle(private val task: BukkitTask) : TaskHandle {
        override fun cancel() = task.cancel()
        override val isCancelled get() = task.isCancelled
    }
    private class FoliaHandle(private val task: io.papermc.paper.threadedregions.scheduler.ScheduledTask) : TaskHandle {
        override fun cancel() { task.cancel() }
        override val isCancelled get() = task.isCancelled
    }

    fun runTask(block: () -> Unit): TaskHandle = if (folia) {
        FoliaHandle(Bukkit.getGlobalRegionScheduler().run(plugin) { _ -> block() })
    } else { BukkitHandle(Bukkit.getScheduler().runTask(plugin, block)) }

    fun runSync(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) { block(); return }
        if (folia) {
            val future = java.util.concurrent.CompletableFuture<Unit>()
            Bukkit.getGlobalRegionScheduler().execute(plugin) {
                try { block(); future.complete(Unit) }
                catch (t: Throwable) { future.completeExceptionally(t) }
            }; future.join()
        } else {
            val future = java.util.concurrent.CompletableFuture<Unit>()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try { block(); future.complete(Unit) }
                catch (t: Throwable) { future.completeExceptionally(t) }
            }); future.join()
        }
    }

    fun runAtFixedRate(initialDelayTicks: Long, periodTicks: Long, block: () -> Unit): TaskHandle = if (folia) {
        FoliaHandle(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, java.util.function.Consumer { block() }, initialDelayTicks.coerceAtLeast(1), periodTicks.coerceAtLeast(1)))
    } else { BukkitHandle(Bukkit.getScheduler().runTaskTimer(plugin, block, initialDelayTicks, periodTicks)) }

    fun runAsync(block: () -> Unit): TaskHandle = if (folia) {
        FoliaHandle(Bukkit.getAsyncScheduler().runNow(plugin) { _ -> block() })
    } else { BukkitHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, block)) }
}

enum class ServerPlatform(val displayName: String) { PAPER("Paper"), FOLIA("Folia"), AS_PAPER("Advanced Slime Paper") }
val serverPlatform: ServerPlatform by lazy {
    when {
        isClassPresent("com.infernalsuite.asp.api.AdvancedSlimePaperAPI") -> ServerPlatform.AS_PAPER
        isClassPresent("io.papermc.paper.threadedregions.RegionizedServer") -> ServerPlatform.FOLIA
        else -> ServerPlatform.PAPER
    }
}
private fun isClassPresent(className: String): Boolean = try { Class.forName(className); true } catch (_: ClassNotFoundException) { false }
