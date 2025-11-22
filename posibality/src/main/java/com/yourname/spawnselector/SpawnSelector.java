package com.yourname.spawnselector;

import com.yourname.spawnselector.commands.SpawnCommand;
import com.yourname.spawnselector.listeners.PlayerListener;
import com.yourname.spawnselector.managers.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SpawnSelector extends JavaPlugin {
    
    private static SpawnSelector instance;
    private ConfigManager configManager;
    private SpawnManager spawnManager;
    private PlayerStateManager playerStateManager;
    private PlayerDataManager playerDataManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.spawnManager = new SpawnManager(this);
        this.playerStateManager = new PlayerStateManager();
        this.playerDataManager = new PlayerDataManager(this);
        
        // Load configuration and data
        configManager.loadConfig();
        spawnManager.loadSpawnPoints();
        playerDataManager.loadPlayerData();
        
        // Register commands
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        
        // Register choose start command
        getCommand("choosestart").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("§c只有玩家可以使用此命令！"));
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length == 0) {
                // Show selection GUI again
                PlayerListener listener = new PlayerListener(this);
                listener.showSpawnSelection(player);
                return true;
            }
            
            String spawnId = args[0];
            PlayerListener listener = new PlayerListener(this);
            listener.handleSpawnSelection(player, spawnId);
            return true;
        });
        
        // Register reset command
        getCommand("spawnreset").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("spawnselector.admin")) {
                sender.sendMessage(Component.text("§c你没有权限使用此命令！"));
                return true;
            }
            
            if (args.length == 0) {
                sender.sendMessage(Component.text("§c用法: /spawnreset <玩家名>"));
                return true;
            }
            
            String playerName = args[0];
            if (playerDataManager.resetPlayerChoice(playerName)) {
                sender.sendMessage(Component.text("§a已重置玩家 " + playerName + " 的出生点选择"));
            } else {
                sender.sendMessage(Component.text("§c未找到玩家 " + playerName + " 的数据"));
            }
            return true;
        });
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        getLogger().info("SpawnSelector has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // Save player data when plugin disables
        playerDataManager.savePlayerData();
        getLogger().info("SpawnSelector has been disabled!");
    }
    
    public static SpawnSelector getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
    
    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}
