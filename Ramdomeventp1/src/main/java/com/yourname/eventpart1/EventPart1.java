// EventPart1.java
package com.yourname.eventpart1;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public class EventPart1 extends JavaPlugin {
    
    private static EventPart1 instance;
    private EventManager eventManager;
    private Logger logger;
    
    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化事件管理器
        this.eventManager = new EventManager();
        
        // 注册命令
        this.getCommand("event").setExecutor(new EventCommand());
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new EventListener(), this);
        
        // 启动定时任务
        startTasks();
        
        logger.info("事件系统 Part1 已启用!");
    }
    
    @Override
    public void onDisable() {
        eventManager.saveAllData();
        logger.info("事件系统 Part1 已禁用!");
    }
    
    public static EventPart1 getInstance() {
        return instance;
    }
    
    public EventManager getEventManager() {
        return eventManager;
    }
    
    private void startTasks() {
        // 每10秒检查一次游戏时间（用于资源税检测）
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            eventManager.checkResourceTax();
        }, 0L, 200L); // 10秒 = 200 tick
        
        // 每10秒检查一次社会净化演习状态
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            eventManager.checkSocialPurification();
        }, 0L, 200L);
    }
}
