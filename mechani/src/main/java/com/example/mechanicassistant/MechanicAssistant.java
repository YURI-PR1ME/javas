package com.example.mechanicassistant;

import org.bukkit.plugin.java.JavaPlugin;

public class MechanicAssistant extends JavaPlugin {
    private static MechanicAssistant instance;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new com.example.mechanicassistant.items.GlueWand(), this);
        getServer().getPluginManager().registerEvents(new com.example.mechanicassistant.items.StructureWrench(), this);
        getServer().getPluginManager().registerEvents(new com.example.mechanicassistant.items.CompressedMachineItem(), this);
        
        // 注册命令
        getCommand("givewrench").setExecutor(new MechanicCommandExecutor());
        getCommand("giveglue").setExecutor(new MechanicCommandExecutor());
        getCommand("givemachine").setExecutor(new MechanicCommandExecutor());
        
        getLogger().info("机械动力插件已加载！");
        getLogger().info("使用方法：");
        getLogger().info("1. 使用强力胶选择区域");
        getLogger().info("2. 使用扳手保存结构");
        getLogger().info("3. 使用压缩机械放置结构");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("机械动力插件已卸载！");
    }
    
    public static MechanicAssistant getInstance() {
        return instance;
    }
}
