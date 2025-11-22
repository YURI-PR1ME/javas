package com.yourname.spawnselector.managers;

import com.yourname.spawnselector.SpawnSelector;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    private final SpawnSelector plugin;
    private FileConfiguration config;
    
    public ConfigManager(SpawnSelector plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        
        // Set default values if they don't exist
        config.addDefault("messages.welcome", "&a请选择你的出生点！");
        config.addDefault("messages.already-chosen", "&c你已经选择过出生点了！");
        config.addDefault("messages.spawn-selected", "&a你已选择出生点: &e%spawn%");
        config.addDefault("messages.no-permission", "&c你没有权限使用此命令！");
        config.addDefault("messages.spawn-added", "&a成功添加出生点: &e%spawn%");
        config.addDefault("messages.spawn-removed", "&a成功移除出生点: &e%spawn%");
        config.addDefault("messages.spawn-not-found", "&c出生点未找到！");
        config.addDefault("messages.invalid-usage", "&c用法不正确！");
        
        config.addDefault("settings.freeze-on-join", true);
        config.addDefault("settings.allow-movement-before-choice", false);
        config.addDefault("settings.auto-kick-if-no-choice", false);
        config.addDefault("settings.choice-timeout-seconds", 300);
        
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }
    
    public String getMessage(String path) {
        return config.getString("messages." + path, "&cMessage not found: " + path)
                .replace('&', '§');
    }
    
    public boolean getBoolean(String path) {
        return config.getBoolean("settings." + path, false);
    }
    
    public int getInt(String path) {
        return config.getInt("settings." + path, 0);
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
}
