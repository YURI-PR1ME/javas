package com.yourname.creditdebitnote;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

public class CreditDebitNote extends JavaPlugin {
    
    private static CreditDebitNote instance;
    private DebitNoteManager debitNoteManager;
    private Plugin creditPlugin;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 检查主插件是否存在
        creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
        if (creditPlugin == null) {
            getLogger().severe("CreditPlugin 未找到！本插件依赖 CreditPlugin 运行。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化管理器
        this.debitNoteManager = new DebitNoteManager();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new DebitNoteListener(), this);
        
        // 注册命令
        this.getCommand("debit").setExecutor(new DebitNoteCommand(debitNoteManager));
        
        // 注册合成配方
        debitNoteManager.registerRecipes();
        
        getLogger().info("信用点借记单系统已启用!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("信用点借记单系统已禁用!");
    }
    
    public static CreditDebitNote getInstance() {
        return instance;
    }
    
    public DebitNoteManager getDebitNoteManager() {
        return debitNoteManager;
    }
    
    public Plugin getCreditPlugin() {
        return creditPlugin;
    }
}
