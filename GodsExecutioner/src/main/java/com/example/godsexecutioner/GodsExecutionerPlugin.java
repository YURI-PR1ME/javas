package com.example.godsexecutioner;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class GodsExecutionerPlugin extends JavaPlugin {

    private static GodsExecutionerPlugin instance;
    private NamespacedKey itemKey;
    private ExecutionerListener listener;
    private CraftingManager craftingManager;

    @Override
    public void onEnable() {
        instance = this;
        itemKey = new NamespacedKey(this, "gods_executioner");

        // 注册命令
        getCommand("getexecutioner").setExecutor(new GiveExecutionerCommand(this));

        // 注册事件监听器
        listener = new ExecutionerListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        
        // 初始化并注册合成管理器
        craftingManager = new CraftingManager(this);
        craftingManager.registerRecipe();
        
        // 注册合成事件监听器
        getServer().getPluginManager().registerEvents(new CraftingListener(this, craftingManager), this);

        getLogger().info("§6神之执行者插件已启用!");
        getLogger().info("§6合成配方: 暴君之镐 + 太平洋之风 + 龙蛋");
    }

    @Override
    public void onDisable() {
        // 恢复所有被减少生命值的实体
        if (listener != null) {
            listener.restoreAllMaxHealth();
        }
        
        // 取消注册合成配方
        if (craftingManager != null) {
            craftingManager.unregisterRecipe();
        }
        
        getLogger().info("§6神之执行者插件已禁用!");
    }

    public static GodsExecutionerPlugin getInstance() {
        return instance;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }
}
