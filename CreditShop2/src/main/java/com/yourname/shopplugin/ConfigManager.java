package com.yourname.shopplugin;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    public void reloadConfig() {
        // 配置重载逻辑已在主类中实现
    }
    
    public FileConfiguration getConfig() {
        return ShopPlugin.getInstance().getConfig();
    }
}
