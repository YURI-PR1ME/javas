package com.yourname.lifestealsword;

import org.bukkit.plugin.java.JavaPlugin;

public class LifeStealSwordPlugin extends JavaPlugin {
    
    private static LifeStealSwordPlugin instance;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new LifeStealListener(), this);
        
        // 注册命令 - 使用无参构造函数
        getCommand("lifestealsword").setExecutor(new LifeStealCommand());
        
        getLogger().info("§a生命窃取剑插件已启用!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("§c生命窃取剑插件已禁用!");
    }
    
    public static LifeStealSwordPlugin getInstance() {
        return instance;
    }
}
