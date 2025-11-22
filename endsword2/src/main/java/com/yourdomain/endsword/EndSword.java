package com.yourdomain.endsword;

import org.bukkit.plugin.java.JavaPlugin;

public final class EndSword extends JavaPlugin {

    private ItemManager itemManager;

    @Override
    public void onEnable() {
        // 1. 初始化物品管理器
        this.itemManager = new ItemManager(this);
        itemManager.init(); // 创建物品模板
        itemManager.createRecipe(); // 注册合成配方

        // 2. 注册事件监听器
        getServer().getPluginManager().registerEvents(new AbilityListener(this, itemManager), this);

        // 3. 启动被动 BUFF 任务
        // 每 20 tick (1秒) 检查一次
        new BuffTask(itemManager).runTaskTimer(this, 0L, 20L);

        getLogger().info("EndSword 插件已启动!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EndSword 插件已关闭!");
    }
}
