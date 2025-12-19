package com.example.godsexecutioner;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class GodsExecutionerPlugin extends JavaPlugin {

    private static GodsExecutionerPlugin instance;
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        instance = this;
        itemKey = new NamespacedKey(this, "gods_executioner");

        // 注册命令
        getCommand("getexecutioner").setExecutor(new GiveExecutionerCommand(this));

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new ExecutionerListener(this), this);

        getLogger().info("§6神之执行者插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§6神之执行者插件已禁用!");
    }

    public static GodsExecutionerPlugin getInstance() {
        return instance;
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }
}
