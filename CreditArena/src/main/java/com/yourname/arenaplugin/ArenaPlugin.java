package com.yourname.arenaplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ArenaPlugin extends JavaPlugin {
    
    private static ArenaPlugin instance;
    private ArenaManager arenaManager;
    private BetManager betManager;
    private ArenaListener arenaListener;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 检查依赖插件
        if (Bukkit.getPluginManager().getPlugin("CreditPlugin") == null) {
            getLogger().severe("CreditPlugin 未找到！本插件需要 CreditPlugin 才能运行。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 加载配置
        saveDefaultConfig();
        
        // 初始化管理器
        this.arenaManager = new ArenaManager();
        this.betManager = new BetManager();
        this.arenaListener = new ArenaListener();
        
        // 注册命令
        this.getCommand("arena").setExecutor(new ArenaCommand(this));
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(arenaListener, this);
        
        // 启动任务
        startTasks();
        
        getLogger().info("地下擂台系统已启用!");
    }
    
    @Override
    public void onDisable() {
        // 保存数据
        if (betManager != null) {
            betManager.saveAllBets();
        }
        getLogger().info("地下擂台系统已禁用!");
    }
    
    public static ArenaPlugin getInstance() {
        return instance;
    }
    
    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public BetManager getBetManager() {
        return betManager;
    }
    
    public ArenaListener getArenaListener() {
        return arenaListener;
    }
    
    private void startTasks() {
        // 每 tick 检查边界和选手选择
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (arenaManager != null) {
                arenaManager.checkBoundaries();
                arenaManager.checkPlayerSelection();
            }
        }, 0L, 1L); // 每 tick 检查一次
    }
}
