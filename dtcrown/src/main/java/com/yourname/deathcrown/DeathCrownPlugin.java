package com.yourname.deathcrown;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathCrownPlugin extends JavaPlugin {
    
    private static DeathCrownPlugin instance;
    private DeathCrownManager crownManager;
    private Set<UUID> usedWorlds;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        this.crownManager = new DeathCrownManager(this);
        this.usedWorlds = new HashSet<>();
        
        // 加载已使用的世界数据
        loadUsedWorlds();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new DeathCrownListener(this), this);
        
        // 注册命令
        getCommand("deathcrown").setExecutor(new DeathCrownCommand(this));
        
        getLogger().info("§4死亡王冠插件已启用!");
    }
    
    @Override
    public void onDisable() {
        saveUsedWorlds();
        getLogger().info("§4死亡王冠插件已禁用!");
    }
    
    public static DeathCrownPlugin getInstance() {
        return instance;
    }
    
    public DeathCrownManager getCrownManager() {
        return crownManager;
    }
    
    public boolean isWorldUsed(UUID worldId) {
        return usedWorlds.contains(worldId);
    }
    
    public void markWorldAsUsed(UUID worldId) {
        usedWorlds.add(worldId);
        saveUsedWorlds();
    }
    
    public void resetWorldUsage(UUID worldId) {
        usedWorlds.remove(worldId);
        saveUsedWorlds();
    }
    
    public void resetAllWorldUsage() {
        usedWorlds.clear();
        saveUsedWorlds();
    }
    
    // 修改：将方法改为public
    public void loadUsedWorlds() {
        FileConfiguration config = getConfig();
        for (String worldIdStr : config.getStringList("used-worlds")) {
            try {
                usedWorlds.add(UUID.fromString(worldIdStr));
            } catch (IllegalArgumentException e) {
                getLogger().warning("无效的世界UUID: " + worldIdStr);
            }
        }
    }
    
    private void saveUsedWorlds() {
        FileConfiguration config = getConfig();
        java.util.List<String> worldIds = new java.util.ArrayList<>();
        for (UUID worldId : usedWorlds) {
            worldIds.add(worldId.toString());
        }
        config.set("used-worlds", worldIds);
        saveConfig();
    }
}
