package com.yourname.spawnselector.managers;

import com.yourname.spawnselector.SpawnSelector;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerDataManager {
    
    private final SpawnSelector plugin;
    private File playerDataFile;
    private FileConfiguration playerDataConfig;
    private final Set<UUID> playersWhoChoseSpawn;
    
    public PlayerDataManager(SpawnSelector plugin) {
        this.plugin = plugin;
        this.playersWhoChoseSpawn = new HashSet<>();
        setupPlayerDataFile();
    }
    
    private void setupPlayerDataFile() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建玩家数据文件: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }
    
    public void loadPlayerData() {
        if (playerDataConfig.contains("players")) {
            for (String uuidString : playerDataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    playersWhoChoseSpawn.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID格式: " + uuidString);
                }
            }
        }
        plugin.getLogger().info("已加载 " + playersWhoChoseSpawn.size() + " 个玩家的出生点选择数据");
    }
    
    public void savePlayerData() {
        // 清除旧数据
        playerDataConfig.set("players", null);
        
        // 保存新数据
        for (UUID uuid : playersWhoChoseSpawn) {
            playerDataConfig.set("players." + uuid.toString() + ".chosen", true);
        }
        
        try {
            playerDataConfig.save(playerDataFile);
            plugin.getLogger().info("已保存 " + playersWhoChoseSpawn.size() + " 个玩家的出生点选择数据");
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存玩家数据文件: " + e.getMessage());
        }
    }
    
    public boolean hasPlayerChosenSpawn(Player player) {
        return playersWhoChoseSpawn.contains(player.getUniqueId());
    }
    
    public boolean hasPlayerChosenSpawn(UUID uuid) {
        return playersWhoChoseSpawn.contains(uuid);
    }
    
    public void setPlayerChosenSpawn(Player player, boolean chosen) {
        if (chosen) {
            playersWhoChoseSpawn.add(player.getUniqueId());
        } else {
            playersWhoChoseSpawn.remove(player.getUniqueId());
        }
        
        // 立即保存到文件
        savePlayerData();
    }
    
    public boolean resetPlayerChoice(String playerName) {
        // 查找玩家UUID
        for (String uuidString : playerDataConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                // 这里简化处理，实际应用中可能需要通过玩家名查找UUID
                // 为了简单，我们直接通过玩家名匹配（注意：这不完全准确）
                if (playerDataConfig.contains("players." + uuidString + ".name")) {
                    String storedName = playerDataConfig.getString("players." + uuidString + ".name");
                    if (playerName.equalsIgnoreCase(storedName)) {
                        playersWhoChoseSpawn.remove(uuid);
                        savePlayerData();
                        return true;
                    }
                }
            } catch (IllegalArgumentException e) {
                // 跳过无效UUID
            }
        }
        return false;
    }
    
    public void savePlayerName(Player player) {
        playerDataConfig.set("players." + player.getUniqueId() + ".name", player.getName());
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存玩家名称: " + e.getMessage());
        }
    }
}
