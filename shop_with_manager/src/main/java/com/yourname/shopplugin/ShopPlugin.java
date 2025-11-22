package com.yourname.shopplugin;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ShopPlugin extends JavaPlugin {
    
    private static ShopPlugin instance;
    private ShopManager shopManager;
    private ConfigManager configManager;
    private File shopsFile;
    private YamlConfiguration shopsConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化配置文件
        saveDefaultConfig();
        setupShopsFile();
        
        // 初始化管理器
        this.configManager = new ConfigManager();
        this.shopManager = new ShopManager();
        
        // 注册命令
        this.getCommand("shop").setExecutor(new ShopCommand());
        
        // 注册所有事件监听器
        getServer().getPluginManager().registerEvents(new ShopListener(), this);
        getServer().getPluginManager().registerEvents(new ShopGUI(), this);
        getServer().getPluginManager().registerEvents(new AdminGUI(), this);
        
        // 加载商店数据
        shopManager.loadShopData();
        
        // 注册合成配方
        registerRecipes();
        
        getLogger().info("信用点商店插件已启用!");
    }
    
    @Override
    public void onDisable() {
        shopManager.saveShopData();
        getLogger().info("信用点商店插件已禁用!");
    }
    
    public static ShopPlugin getInstance() {
        return instance;
    }
    
    public ShopManager getShopManager() {
        return shopManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    private void setupShopsFile() {
        shopsFile = new File(getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            saveResource("shops.yml", false);
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
    }
    
    public void saveShopsData() {
        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "保存商店数据时出错: " + e.getMessage());
        }
    }
    
    public YamlConfiguration getShopsConfig() {
        return shopsConfig;
    }
    
    public void reloadAllConfigs() {
        reloadConfig();
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        configManager.reloadConfig();
        shopManager.loadShopData();
    }
    
    private void registerRecipes() {
        // 注册商店终端合成配方
        registerShopTerminalRecipe();
    }
    
    private void registerShopTerminalRecipe() {
        ItemStack terminal = ShopTerminal.createShopTerminal();
        NamespacedKey key = new NamespacedKey(this, "shop_terminal");
        
        ShapedRecipe recipe = new ShapedRecipe(key, terminal);
        recipe.shape("DED", "EOE", "DED");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('O', Material.OBSIDIAN);
        
        Bukkit.addRecipe(recipe);
        getLogger().info("商店终端合成配方已注册");
    }
}
