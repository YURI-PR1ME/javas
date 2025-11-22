package com.example.apocolyps;

import org.bukkit.plugin.java.JavaPlugin;

public class ApocolypsPlugin extends JavaPlugin {
    private ApoManager manager;
    
    @Override
    public void onEnable() {
        this.manager = new ApoManager(this);
        
        // 注册命令
        this.getCommand("apocontrol").setExecutor(new ApoCommandExecutor(manager));
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ApoListener(manager, this), this);
        
        // 启动定时任务
        manager.startTasks();
        
        getLogger().info("§a天启奴隶主插件已加载！");
    }
    
    @Override
    public void onDisable() {
        if (manager != null) {
            manager.disableAll();
        }
        getLogger().info("§c天启奴隶主插件已卸载！");
    }
}
