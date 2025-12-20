// [file name]: PacificWindPlugin.java
package com.yourname.pacificwind;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class PacificWindPlugin extends JavaPlugin {
    
    private static PacificWindPlugin instance;
    private PacificWindManager windManager;
    private File customConfigFile;
    private FileConfiguration customConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 创建数据文件夹
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 创建自定义配置文件
        customConfigFile = new File(getDataFolder(), "data.yml");
        if (!customConfigFile.exists()) {
            try {
                customConfigFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("创建数据文件时出错: " + e.getMessage());
            }
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);
        
        this.windManager = new PacificWindManager(this);
        
        // 加载击杀数据
        loadKillData();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PacificWindListener(this), this);
        
        // 注册命令
        getCommand("pacificwind").setExecutor(new PacificWindCommand(this));
        
        getLogger().info("§9太平洋之风插件已启用!");
        getLogger().info("暴君召唤状态: " + (isTyrantSummoned() ? "§c已召唤" : "§a未召唤"));
        getLogger().info("新功能: 蓄力下雨(CD5分钟) + 模式切换 + 引雷爆炸 + 急迫X + 击杀重置冷却");
    }
    
    @Override
    public void onDisable() {
        saveCustomConfig();
        saveKillData();
        
        // 取消所有正在运行的异步任务
        Bukkit.getScheduler().cancelTasks(this);
        
        getLogger().info("§9太平洋之风插件已禁用!");
    }
    
    public static PacificWindPlugin getInstance() {
        return instance;
    }
    
    public PacificWindManager getWindManager() {
        return windManager;
    }
    
    /**
     * 检查暴君是否已被召唤
     */
    public boolean isTyrantSummoned() {
        return customConfig.getBoolean("tyrant-summoned", false);
    }
    
    /**
     * 设置暴君召唤状态
     */
    public void setTyrantSummoned(boolean summoned) {
        customConfig.set("tyrant-summoned", summoned);
        saveCustomConfig();
        
        if (summoned) {
            getLogger().warning("暴君已被召唤，服务器将无法再次召唤暴君");
        }
    }
    
    /**
     * 重置暴君召唤状态（管理员命令）
     */
    public void resetTyrantSummoned() {
        customConfig.set("tyrant-summoned", false);
        saveCustomConfig();
        getLogger().info("暴君召唤状态已重置，现在可以重新召唤暴君");
    }
    
    /**
     * 保存击杀数据
     */
    private void saveKillData() {
        try {
            // 清除旧的击杀数据
            if (customConfig.contains("kill-counts")) {
                customConfig.set("kill-counts", null);
            }
            
            // 保存新的击杀数据
            for (Map.Entry<UUID, Integer> entry : windManager.getKillCounts().entrySet()) {
                customConfig.set("kill-counts." + entry.getKey().toString(), entry.getValue());
            }
            
            saveCustomConfig();
            getLogger().info("击杀数据已保存");
        } catch (Exception e) {
            getLogger().severe("保存击杀数据时出错: " + e.getMessage());
        }
    }
    
    /**
     * 加载击杀数据
     */
    private void loadKillData() {
        try {
            if (customConfig.contains("kill-counts")) {
                for (String key : customConfig.getConfigurationSection("kill-counts").getKeys(false)) {
                    UUID playerId = UUID.fromString(key);
                    int kills = customConfig.getInt("kill-counts." + key, 0);
                    windManager.getKillCounts().put(playerId, kills);
                }
                getLogger().info("击杀数据已加载: " + windManager.getKillCounts().size() + " 个玩家数据");
            }
        } catch (Exception e) {
            getLogger().severe("加载击杀数据时出错: " + e.getMessage());
        }
    }
    
    /**
     * 保存自定义配置
     */
    private void saveCustomConfig() {
        try {
            customConfig.save(customConfigFile);
        } catch (IOException e) {
            getLogger().severe("保存数据文件时出错: " + e.getMessage());
        }
    }
    
    // 添加一个获取击杀计数Map的方法（仅供PacificWindManager使用）
    public Map<UUID, Integer> getKillCounts() {
        // 这个方法应该只在PacificWindManager中使用
        // 由于PacificWindManager已经通过getter提供了访问，我们这里提供一个包装方法
        return windManager.getKillCounts();
    }
}
