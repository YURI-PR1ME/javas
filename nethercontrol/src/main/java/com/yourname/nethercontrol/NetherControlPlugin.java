// NetherControlPlugin.java
package com.yourname.nethercontrol;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class NetherControlPlugin extends JavaPlugin {
    
    private static NetherControlPlugin instance;
    private NetherControlManager controlManager;
    private FileConfiguration config;
    private File configFile;
    private CreditIntegration creditIntegration;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置
        saveDefaultConfig();
        config = getConfig();
        
        // 初始化管理器
        this.controlManager = new NetherControlManager();
        
        // 初始化信用点集成
        this.creditIntegration = new CreditIntegration();
        
        // 注册命令
        this.getCommand("nethercontrol").setExecutor(new NetherControlCommand());
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PortalListener(), this);
        getServer().getPluginManager().registerEvents(new StarItemListener(), this);
        
        // 启动信用点检查任务
        startCreditCheckTask();
        
        getLogger().info("地狱门控制系统已启用!");
        getLogger().info("当前状态: " + (controlManager.isUnlocked() ? "已解锁" : "已封锁"));
        
        // 检查Credit插件是否可用
        if (creditIntegration.isCreditAvailable()) {
            getLogger().info("✅ 已检测到信用点系统，集成功能已启用");
        } else {
            getLogger().info("⚠ 未检测到信用点系统，集成功能已禁用");
        }
    }
    
    @Override
    public void onDisable() {
        controlManager.saveConfig();
        getLogger().info("地狱门控制系统已禁用!");
    }
    
    public static NetherControlPlugin getInstance() {
        return instance;
    }
    
    public NetherControlManager getControlManager() {
        return controlManager;
    }
    
    public CreditIntegration getCreditIntegration() {
        return creditIntegration;
    }
    
    public void reloadPluginConfig() {
        reloadConfig();
        config = getConfig();
        controlManager.loadConfig();
    }
    
    private void startCreditCheckTask() {
        // 每2秒检查一次玩家信用点状态
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            creditIntegration.checkAllPlayers();
        }, 0L, 40L); // 2秒 = 40 tick
    }
}
