package com.btc.typewriter.tracking

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.SimpleCommandMap
import org.bukkit.command.defaults.BukkitCommand
import org.slf4j.LoggerFactory

object CommandRegistry {
    private val logger = LoggerFactory.getLogger("TrackingCommands")
    private var commandMap: CommandMap? = null
    private val registeredCommands = mutableListOf<String>()

    fun register(name: String, executor: (org.bukkit.command.CommandSender, Array<out String>) -> Unit) {
        val map = getCommandMap() ?: return
        
        val command = object : BukkitCommand(name) {
            override fun execute(sender: org.bukkit.command.CommandSender, label: String, args: Array<out String>): Boolean {
                executor(sender, args)
                return true
            }
            
            override fun tabComplete(sender: org.bukkit.command.CommandSender, alias: String, args: Array<out String>): MutableList<String> {
                // Return empty list to allow manual handling or default behavior
                // A better implementation would allow passing a TabCompleter or similar
                return mutableListOf()
            }
        }
        
        map.register("tracking", command)
        registeredCommands.add(name)
        logger.info("Registered command: /$name")
    }
    
    // overload to support tab completion
    fun register(name: String, executor: (org.bukkit.command.CommandSender, Array<out String>) -> Unit, tabCompleter: (org.bukkit.command.CommandSender, Array<out String>) -> List<String>) {
        val map = getCommandMap() ?: return
        
        val command = object : BukkitCommand(name) {
            override fun execute(sender: org.bukkit.command.CommandSender, label: String, args: Array<out String>): Boolean {
                executor(sender, args)
                return true
            }
            
            override fun tabComplete(sender: org.bukkit.command.CommandSender, alias: String, args: Array<out String>): MutableList<String> {
                return tabCompleter(sender, args).toMutableList()
            }
        }
        
        map.register("tracking", command)
        registeredCommands.add(name)
        logger.info("Registered command: /$name")
    }

    fun unregister(name: String) {
        val map = getCommandMap() ?: return
        
        try {
            if (map is SimpleCommandMap) {
                val knownCommandsField = SimpleCommandMap::class.java.getDeclaredField("knownCommands")
                knownCommandsField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val knownCommands = knownCommandsField.get(map) as MutableMap<String, Command>
                knownCommands.remove(name)
                knownCommands.remove("tracking:$name")
                registeredCommands.remove(name)
                logger.info("Unregistered command: /$name")
            }
        } catch (e: Exception) {
            logger.error("Failed to unregister command: /$name", e)
        }
    }

    fun unregisterAll() {
        val map = getCommandMap() ?: return
        
        registeredCommands.forEach { name ->
            try {
                if (map is SimpleCommandMap) {
                    val knownCommandsField = SimpleCommandMap::class.java.getDeclaredField("knownCommands")
                    knownCommandsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val knownCommands = knownCommandsField.get(map) as MutableMap<String, Command>
                    knownCommands.remove(name)
                    knownCommands.remove("tracking:$name")
                    logger.info("Unregistered command: /$name")
                }
            } catch (e: Exception) {
                logger.error("Failed to unregister command: /$name", e)
            }
        }
        
        registeredCommands.clear()
    }

    private fun getCommandMap(): CommandMap? {
        if (commandMap != null) return commandMap
        return try {
            val server = Bukkit.getServer()
            val field = server.javaClass.getDeclaredField("commandMap")
            field.isAccessible = true
            commandMap = field.get(server) as? CommandMap
            commandMap
        } catch (e: Exception) {
            logger.error("Failed to get CommandMap via reflection", e)
            null
        }
    }
}
