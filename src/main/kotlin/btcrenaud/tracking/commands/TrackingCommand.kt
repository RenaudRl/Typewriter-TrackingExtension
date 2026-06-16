package btcrenaud.tracking.commands

import btcrenaud.tracking.service.TrackingService
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.koin.java.KoinJavaComponent

/**
 * `/tracking` command — inspect and list player tracking sessions.
 * Uses classic CommandExecutor (Typewriter command DSL is not exposed to extensions).
 */
object TrackingCommandExecutor : CommandExecutor, TabCompleter {

    private var registered = false

    fun register() {
        if (registered) return
        val commandMap = Bukkit.getCommandMap()
        val cmd = object : Command(
            "tracking",
            "Inspect or list player tracking sessions",
            "/tracking [inspect <player> [session] | sessions <player>]",
            emptyList()
        ) {
            override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
                return onCommand(sender, this, label, args)
            }
        }
        cmd.permission = "typewriter.tracking.inspect"
        commandMap.register("typewriter", cmd)
        registered = true
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (!player.hasPermission("typewriter.tracking.inspect")) {
            player.sendMessage("<red>You don't have permission.</red>")
            return true
        }

        if (args.isEmpty()) {
            stopInspection(player)
            return true
        }

        return when (args[0].lowercase()) {
            "inspect" -> handleInspect(player, args)
            "sessions" -> handleSessions(player, args)
            else -> {
                player.sendMessage("Usage: /tracking [inspect <player> [session] | sessions <player>]")
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        if (args.size == 1) {
            return listOf("inspect", "sessions").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("sessions", ignoreCase = true)) {
            return null // player name completion
        }
        if (args.size == 3 && args[0].equals("inspect", ignoreCase = true)) {
            // Could suggest sessions here, but we'd need to resolve player name first
            return emptyList()
        }
        return emptyList()
    }

    private fun stopInspection(player: Player) {
        val service: TrackingService = KoinJavaComponent.get(TrackingService::class.java)
        service.stopInspection(player)
        player.sendMessage("<gray>Tracking inspection disabled.</gray>")
    }

    private fun handleInspect(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("Usage: /tracking inspect <player> [session]")
            return true
        }

        val svc: TrackingService = KoinJavaComponent.get(TrackingService::class.java)
        val targetName = args[1]

        if (args.size >= 3) {
            val sessionId = args[2]
            val success = svc.startInspection(player, targetName, sessionId)
            if (success) {
                player.sendMessage("<green>Now inspecting session $sessionId for $targetName.</green>")
            } else {
                player.sendMessage("<red>Could not find session $sessionId for player $targetName.</red>")
            }
        } else {
            val sessions = svc.getPlayerSessions(targetName)
            if (sessions.isEmpty()) {
                player.sendMessage("<red>No tracking sessions found for player $targetName.</red>")
            } else {
                player.sendMessage("<green>Available sessions for $targetName:</green>")
                sessions.forEach { id ->
                    player.sendMessage("  <gray>-</gray> <yellow>$id</yellow>")
                }
            }
        }
        return true
    }

    private fun handleSessions(player: Player, args: Array<out String>): Boolean {
        if (args.size < 2) {
            player.sendMessage("Usage: /tracking sessions <player>")
            return true
        }

        val svc: TrackingService = KoinJavaComponent.get(TrackingService::class.java)
        val targetName = args[1]
        val sessions = svc.getPlayerSessions(targetName)
        if (sessions.isEmpty()) {
            player.sendMessage("<red>No tracking sessions found for player $targetName.</red>")
        } else {
            player.sendMessage("<green>Available sessions for $targetName:</green>")
            sessions.forEach { id ->
                player.sendMessage("  <gray>-</gray> <yellow>$id</yellow>")
            }
        }
        return true
    }
}
