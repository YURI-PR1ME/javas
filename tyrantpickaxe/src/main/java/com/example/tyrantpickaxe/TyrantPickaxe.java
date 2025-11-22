package com.example.tyrantpickaxe;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class TyrantPickaxe extends JavaPlugin {

    // 用于识别镐子的唯一key
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        // 初始化 Key
        itemKey = new NamespacedKey(this, "tyrant_pickaxe");

        // 注册指令
        this.getCommand("givetp").setExecutor(new GetPickaxeCommand(this));

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PickaxeListener(this), this);

        getLogger().info("TyrantPickaxe 插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TyrantPickaxe 插件已禁用!");
    }

    /**
     * 获取用于识别物品的 NamespacedKey
     * @return 物品的 Key
     */
    public NamespacedKey getItemKey() {
        return itemKey;
    }
}
