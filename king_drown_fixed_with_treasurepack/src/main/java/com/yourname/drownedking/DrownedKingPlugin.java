package com.yourname.drownedking;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

public class DrownedKingPlugin extends JavaPlugin {
    
    private static DrownedKingPlugin instance;
    private DrownedKingManager drownedKingManager;
    private DrownedTreasureManager treasureManager;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private FileConfiguration treasureConfig;
    private File treasureFile;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置并添加新选项
        saveDefaultConfig();
        setupDataFile();
        
        // 加载宝藏袋配置
        loadTreasureConfig();
        
        // 确保新配置项存在
        FileConfiguration config = getConfig();
        if (!config.contains("trident_frenzy_block_damage")) {
            config.set("trident_frenzy_block_damage", true); // 默认开启方块破坏
            saveConfig();
        }
        
        // 确保消息配置存在
        if (!config.contains("messages.player_killed_by_minion")) {
            config.set("messages.player_killed_by_minion", "§7溺尸守卫 §c在溺尸王的命令下击杀了 {player}!");
        }
        if (!config.contains("messages.player_killed_by_large_trident")) {
            config.set("messages.player_killed_by_large_trident", "§4溺尸王 §c用深渊三叉戟击杀了 {player}!");
        }
        if (!config.contains("messages.player_killed_by_small_trident")) {
            config.set("messages.player_killed_by_small_trident", "§4溺尸王 §c用穿刺三叉戟击杀了 {player}!");
        }
        saveConfig();
        
        this.drownedKingManager = new DrownedKingManager(this);
        this.treasureManager = new DrownedTreasureManager(this);
        
        try {
            this.getCommand("drownedking").setExecutor(new DrownedKingCommand(this));
        } catch (Exception e) {
            getLogger().warning("Failed to register command: " + e.getMessage());
        }
        
        try {
            Bukkit.getPluginManager().registerEvents(new DrownedKingListener(this), this);
            // 注册宝藏袋监听器
            Bukkit.getPluginManager().registerEvents(new DrownedTreasureBagListener(this, treasureManager), this);
        } catch (Exception e) {
            getLogger().warning("Failed to register listeners: " + e.getMessage());
        }
        
        startBossTask();
        
        getLogger().info("溺尸王Boss插件已启用!");
    }
    
    @Override
    public void onDisable() {
        if (drownedKingManager != null) {
            drownedKingManager.saveAllBosses();
        }
        getLogger().info("溺尸王Boss插件已禁用!");
    }
    
    public static DrownedKingPlugin getInstance() {
        return instance;
    }
    
    public DrownedKingManager getDrownedKingManager() {
        return drownedKingManager;
    }
    
    public DrownedTreasureManager getTreasureManager() {
        return treasureManager;
    }
    
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "bosses.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "创建数据文件时出错", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    // 加载宝藏袋配置
    private void loadTreasureConfig() {
        treasureFile = new File(getDataFolder(), "drowned_treasure.yml");
        if (!treasureFile.exists()) {
            saveResource("drowned_treasure.yml", false);
        }
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("溺尸王宝藏袋配置已加载!");
    }
    
    // 重新加载宝藏袋配置
    public void reloadTreasureConfig() {
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("溺尸王宝藏袋配置已重新加载!");
    }
    
    // 保存宝藏袋配置
    public void saveTreasureConfig() {
        try {
            treasureConfig.save(treasureFile);
            getLogger().info("溺尸王宝藏袋配置已保存!");
        } catch (Exception e) {
            getLogger().severe("保存溺尸王宝藏袋配置时出错: " + e.getMessage());
        }
    }
    
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "保存数据文件时出错", e);
        }
    }
    
    public YamlConfiguration getDataConfig() {
        return dataConfig;
    }
    
    public FileConfiguration getTreasureConfig() {
        return treasureConfig;
    }
    
    public File getTreasureFile() {
        return treasureFile;
    }
    
    private void startBossTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (drownedKingManager != null) {
                drownedKingManager.checkActiveBosses();
            }
        }, 0L, 100L);
    }
    
    // 创建溺尸王宝藏袋
    public void createDrownedTreasureBag(Location location) {
        // 创建宝藏袋物品
        ItemStack treasureBag = new ItemStack(Material.BUNDLE);
        ItemMeta meta = treasureBag.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§b§l溺尸王宝藏袋");
            meta.setLore(Arrays.asList(
                "§7溺尸王掉落的珍贵战利品",
                "§7蕴含着海洋的力量",
                "",
                "§b右键点击打开"
            ));
            
            // 添加附魔光效
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            
            treasureBag.setItemMeta(meta);
        }
        
        // 掉落宝藏袋
        Item item = location.getWorld().dropItemNaturally(location, treasureBag);
        item.setCustomName("§b§l溺尸王宝藏袋");
        item.setCustomNameVisible(true);
        item.setGlowing(true);
        item.setUnlimitedLifetime(true); // 防止消失
        item.setInvulnerable(true); // 设置无敌，防止被破坏
        
        // 添加粒子效果 - 修复：使用正确的粒子名称
        location.getWorld().spawnParticle(Particle.BUBBLE, location, 50, 1, 1, 1);
        location.getWorld().spawnParticle(Particle.END_ROD, location, 30, 1, 1, 1);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        
        // 设置宝藏袋被捡起时的行为
        setupTreasureBagPickup(item);
    }
    
    // 设置宝藏袋被捡起时的行为
    private void setupTreasureBagPickup(Item treasureItem) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!treasureItem.isValid() || treasureItem.isDead()) {
                    cancel();
                    return;
                }
                
                // 持续粒子效果
                Location loc = treasureItem.getLocation();
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.2, 0.2);
                loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 2, 0.2, 0.2, 0.2);
                
                // 检查是否有玩家在附近
                for (org.bukkit.entity.Player player : loc.getWorld().getPlayers()) {
                    if (player.getLocation().distance(loc) <= 3) {
                        player.sendActionBar("§b附近有溺尸王宝藏袋!");
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }
}
