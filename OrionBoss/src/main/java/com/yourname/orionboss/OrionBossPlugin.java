package com.yourname.orionboss;

import org.bukkit.Location;
import org.bukkit.entity.Wither;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class OrionBossPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, OrionBoss> activeBosses = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private FileConfiguration treasureConfig;
    private File treasureFile;
    private TreasureManager treasureManager;
    
    // 使徒相关字段
    private ApostleBoss activeApostle = null;
    
    // BGM 管理器
    private BGMPlayer bgmPlayer;

    @Override
    public void onEnable() {
        treasureManager = new TreasureManager(this);
        bgmPlayer = new BGMPlayer(this);  // 初始化BGM管理器
        
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new OrionBossListener(this), this);
        getServer().getPluginManager().registerEvents(new TreasureBagListener(this, treasureManager), this);
        getServer().getPluginManager().registerEvents(new PlayerBGMListener(this), this);
        
        loadTreasureConfig();
        
        getLogger().info("OrionBossPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        for (OrionBoss boss : activeBosses.values()) {
            boss.cleanup();
        }
        activeBosses.clear();
        bossBars.clear();
        
        // 清理使徒
        if (activeApostle != null) {
            activeApostle.cleanup();
            activeApostle = null;
        }
        
        // 清理BGM
        if (bgmPlayer != null) {
            bgmPlayer.cleanup();
        }
        
        getLogger().info("OrionBossPlugin has been disabled!");
    }

    private void loadTreasureConfig() {
        treasureFile = new File(getDataFolder(), "treasure.yml");
        if (!treasureFile.exists()) {
            saveResource("treasure.yml", false);
        }
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("Treasure configuration loaded!");
    }

    public void reloadTreasureConfig() {
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("Treasure configuration reloaded!");
    }

    public void saveTreasureConfig() {
        try {
            treasureConfig.save(treasureFile);
            getLogger().info("Treasure configuration saved!");
        } catch (Exception e) {
            getLogger().severe("Error saving treasure configuration: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spawnorion")) {
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("orionboss.console")) {
                sender.sendMessage("§cThis command can only be used via summon item!");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }

            Player player = (Player) sender;
            
            if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.THE_END) {
                player.sendMessage("§cOrion can only be summoned in The End!");
                return true;
            }
            
            Location spawnLocation = player.getLocation();
            spawnOrionBoss(spawnLocation);
            player.sendMessage("§6§lOrion Boss has been summoned! Prepare for battle!");
            
            return true;
        } else if (command.getName().equalsIgnoreCase("orionreload")) {
            if (!sender.hasPermission("orionboss.reload")) {
                sender.sendMessage("§cYou don't have permission to reload configuration!");
                return true;
            }
            
            reloadTreasureConfig();
            sender.sendMessage("§aOrion plugin configuration reloaded!");
            return true;
        } else if (command.getName().equalsIgnoreCase("orionaddtreasure")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("orionboss.addtreasure")) {
                player.sendMessage("§cYou don't have permission to add treasure bag items!");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§cUsage: /orionaddtreasure <rewardId> <chance>");
                player.sendMessage("§eExample: /orionaddtreasure my_sword 25.0");
                return true;
            }
            
            String rewardId = args[0];
            double chance;
            
            try {
                chance = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cChance must be a number!");
                return true;
            }
            
            if (chance < 0 || chance > 100) {
                player.sendMessage("§cChance must be between 0-100!");
                return true;
            }
            
            boolean success = treasureManager.addItemToTreasure(player, rewardId, chance);
            if (success) {
                player.sendMessage("§aSuccessfully added item to treasure bag with ID: " + rewardId);
            } else {
                player.sendMessage("§cFailed to add item, make sure you're holding an item!");
            }
            
            return true;
        } else if (command.getName().equalsIgnoreCase("orionlisttreasure")) {
            if (!sender.hasPermission("orionboss.listtreasure")) {
                sender.sendMessage("§cYou don't have permission to view treasure bag items!");
                return true;
            }
            
            treasureManager.listTreasureItems(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("orionremovetreasure")) {
            if (!sender.hasPermission("orionboss.removetreasure")) {
                sender.sendMessage("§cYou don't have permission to remove treasure bag items!");
                return true;
            }
            
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /orionremovetreasure <rewardId>");
                return true;
            }
            
            String rewardId = args[0];
            boolean success = treasureManager.removeTreasureItem(rewardId);
            
            if (success) {
                sender.sendMessage("§aSuccessfully removed treasure bag item: " + rewardId);
            } else {
                sender.sendMessage("§cTreasure bag item not found: " + rewardId);
            }
            
            return true;
        }
        
        return false;
    }

    public void spawnOrionBoss(Location location) {
        // 先清理已存在的Boss
        if (!activeBosses.isEmpty()) {
            for (OrionBoss existingBoss : activeBosses.values()) {
                existingBoss.cleanup();
                removeBossBar(existingBoss.getBoss().getUniqueId());
            }
            activeBosses.clear();
        }
        
        Wither boss = (Wither) location.getWorld().spawnEntity(location, EntityType.WITHER);
        setupBossAttributes(boss);
        
        OrionBoss orionBoss = new OrionBoss(boss, this);
        activeBosses.put(boss.getUniqueId(), orionBoss);
        
        createBossBar(boss);
        orionBoss.startBossBehavior();
        
        // 启动BGM
        startBossBGM();
        
        // Broadcast message
        org.bukkit.Bukkit.broadcastMessage("§6§lORION §e§lTHE HUNTER §6§lhas been summoned in The End!");
        org.bukkit.Bukkit.broadcastMessage("§cPrepare for an epic battle!");
    }

    // 使徒相关方法
    public void summonApostle(Location location) {
        if (activeApostle != null) {
            activeApostle.cleanup();
        }
        
        activeApostle = new ApostleBoss(location, this);
        activeApostle.startFight();
        
        // 切换为使徒BGM
        if (bgmPlayer != null) {
            bgmPlayer.updateBossPhase(BGMPlayer.BossPhase.APOSTLE);
        }
        
        // 注册使徒监听器
        getServer().getPluginManager().registerEvents(new ApostleListener(this), this);
    }

    public void onApostleDeath() {
    if (activeApostle != null) {
        activeApostle.cleanup();
        activeApostle = null;
    }
    
    // 确保BGM完全停止
    if (bgmPlayer != null) {
        bgmPlayer.stopAllBGM();
    }
    
    // 短暂延迟后恢复Orion战斗和BGM
    org.bukkit.Bukkit.getScheduler().runTaskLater(this, () -> {
        // 恢复Orion战斗
        for (OrionBoss orionBoss : activeBosses.values()) {
            orionBoss.returnFromRetreat();
        }
        
        // 切换回Orion第二阶段BGM
        if (bgmPlayer != null) {
            bgmPlayer.updateBossPhase(BGMPlayer.BossPhase.ORION_RAGE);
        }
        
        // 广播消息
        org.bukkit.Bukkit.broadcastMessage("§6§lThe Apostle falls! Orion returns to finish the battle!");
    }, 20L); // 延迟1秒确保BGM完全停止
}
// 在OrionBossPlugin类中添加这个方法
public ApostleBoss getActiveApostle() {
    return activeApostle;
}
    // BossBar显示/隐藏方法
    public void hideBossBarFromAllPlayers() {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.setVisible(false);
        }
    }

    public void showBossBarToAllPlayers() {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.setVisible(true);
        }
    }

    private void setupBossAttributes(Wither boss) {
        boss.setCustomName("§6§l猎户座 §e§lORION");
        boss.setCustomNameVisible(true);
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        
        Objects.requireNonNull(boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).setBaseValue(500.0);
        boss.setHealth(500.0);
        
        // 添加保护效果
        boss.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.RESISTANCE, 
            Integer.MAX_VALUE, 
            2, 
            false, 
            false
        ));
    }

    private void createBossBar(Wither boss) {
        // 确保移除旧的BossBar
        removeBossBar(boss.getUniqueId());
        
        BossBar bossBar = org.bukkit.Bukkit.createBossBar(
            "§6§l猎户座 ORION §7- §c❤ " + (int)boss.getHealth() + "/" + (int)boss.getMaxHealth(),
            org.bukkit.boss.BarColor.PURPLE,
            org.bukkit.boss.BarStyle.SEGMENTED_12
        );
        
        bossBar.setProgress(boss.getHealth() / boss.getMaxHealth());
        bossBar.setVisible(true);
        
        // 添加所有在线玩家
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        
        bossBars.put(boss.getUniqueId(), bossBar);
    }

    public void updateBossBar(Wither boss) {
        BossBar bossBar = bossBars.get(boss.getUniqueId());
        if (bossBar != null) {
            bossBar.setTitle("§6§l猎户座 ORION §7- §c❤ " + 
                (int)boss.getHealth() + "/" + (int)boss.getMaxHealth());
            bossBar.setProgress(boss.getHealth() / boss.getMaxHealth());
        }
    }

    public void removeBossBar(UUID bossId) {
        BossBar bossBar = bossBars.remove(bossId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    public void createTreasureBag(Location location) {
        ItemStack treasureBag = new ItemStack(Material.BUNDLE);
        ItemMeta meta = treasureBag.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§l猎户座宝藏袋");
            meta.setLore(Arrays.asList(
                "§7猎户座掉落的珍贵战利品",
                "§7蕴含着星辰的力量",
                "",
                "§e右键点击打开"
            ));
            
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            
            treasureBag.setItemMeta(meta);
        }
        
        org.bukkit.entity.Item item = location.getWorld().dropItemNaturally(location, treasureBag);
        item.setCustomName("§6§l猎户座宝藏袋");
        item.setCustomNameVisible(true);
        item.setGlowing(true);
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        
        location.getWorld().spawnParticle(org.bukkit.Particle.FLAME, location, 50, 1, 1, 1);
        location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
    }

    public void showBossBarToPlayer(Player player) {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.addPlayer(player);
        }
    }

    public void hideBossBarFromPlayer(Player player) {
        for (BossBar bossBar : bossBars.values()) {
            bossBar.removePlayer(player);
        }
    }

    public Map<UUID, OrionBoss> getActiveBosses() {
        return activeBosses;
    }
    
    public FileConfiguration getTreasureConfig() {
        return treasureConfig;
    }
    
    public File getTreasureFile() {
        return treasureFile;
    }
    
    public TreasureManager getTreasureManager() {
        return treasureManager;
    }
    
    // BGM相关方法
    public BGMPlayer getBgmPlayer() {
        return bgmPlayer;
    }
    
    public void startBossBGM() {
        if (bgmPlayer != null) {
            bgmPlayer.playBGMForAll(BGMPlayer.BossPhase.ORION_NORMAL);
        }
    }
    
    public void stopBossBGM() {
        if (bgmPlayer != null) {
            bgmPlayer.stopAllBGM();
        }
    }
}
