package com.btc.typewriter.tracking.commands

import com.btc.typewriter.tracking.CommandRegistry
import com.btc.typewriter.tracking.TrackingExtension
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TrackingCommand {

    init {
        CommandRegistry.register("tracking", ::execute, ::tabComplete)
    }
    
    fun unregister() {
        CommandRegistry.unregister("tracking")
    }
    
    private fun execute(sender: CommandSender, args: Array<out String>) {
        val service = TrackingExtension.service
        if (service == null) {
            sender.sendMessage("Tracking service is not available.")
            return
        }
        
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return
        }
        
        if (!sender.hasPermission("typewriter.tracking.inspect")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.isEmpty()) {
            // Toggle off
            service.stopInspection(sender)
            sender.sendMessage("Tracking inspection disabled.")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("Usage: /tracking <player> <session_id> or /tracking to disable")
            return
        }
        
        val targetName = args[0]
        val sessionId = args[1]
        
        val success = service.startInspection(sender, targetName, sessionId)
        if (success) {
            sender.sendMessage("Now inspecting session $sessionId for $targetName.")
        } else {
            sender.sendMessage("Could not find session $sessionId for player $targetName.")
        }
    }
    
    private fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val service = TrackingExtension.service ?: return emptyList()
        
        if (!sender.hasPermission("typewriter.tracking.inspect")) return emptyList()
        
        return when (args.size) {
            1 -> {
                Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                val targetName = args[0]
                service.getPlayerSessions(targetName)
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
            else -> emptyList()
        }
    }
}
