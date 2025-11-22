package com.yourname.tyrantboss;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class TyrantBossPlugin extends JavaPlugin implements Listener {

    private TyrantBossManager bossManager;
    private final Map<UUID, TyrantBoss> activeBosses = new HashMap<>();
    private final Map<UUID, TyrantGhostBoss> activeGhostBosses = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private FileConfiguration treasureConfig;
    private File treasureFile;
    private TreasureManager treasureManager;

    @Override
    public void onEnable() {
        bossManager = new TyrantBossManager(this);
        treasureManager = new TreasureManager(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(bossManager, this);
        
        // 注册宝藏袋监听器
        getServer().getPluginManager().registerEvents(new TreasureBagListener(this, treasureManager), this);
        
        // 加载宝藏袋配置
        loadTreasureConfig();
        
        getLogger().info("TyrantBossPlugin 已启用!");
    }

    @Override
    public void onDisable() {
        for (TyrantBoss boss : activeBosses.values()) {
            boss.cleanup();
        }
        activeBosses.clear();
        
        for (TyrantGhostBoss ghostBoss : activeGhostBosses.values()) {
            ghostBoss.cleanup();
        }
        activeGhostBosses.clear();
        
        bossBars.clear();
        getLogger().info("TyrantBossPlugin 已禁用!");
    }

    // 加载宝藏袋配置
    private void loadTreasureConfig() {
        treasureFile = new File(getDataFolder(), "treasure.yml");
        if (!treasureFile.exists()) {
            saveResource("treasure.yml", false);
        }
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("宝藏袋配置已加载!");
    }

    // 重新加载宝藏袋配置
    public void reloadTreasureConfig() {
        treasureConfig = YamlConfiguration.loadConfiguration(treasureFile);
        getLogger().info("宝藏袋配置已重新加载!");
    }

    // 保存宝藏袋配置 - 只保留这一个方法
    public void saveTreasureConfig() {
        try {
            treasureConfig.save(treasureFile);
            getLogger().info("宝藏袋配置已保存!");
        } catch (Exception e) {
            getLogger().severe("保存宝藏袋配置时出错: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spawntyrant")) {
            // 检查是否是控制台或拥有特殊权限
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("tyrantboss.console")) {
                sender.sendMessage("§c这个指令只能通过召唤物品使用!");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令!");
                return true;
            }

            Player player = (Player) sender;
            
            Location spawnLocation = player.getLocation();
            spawnTyrantBoss(spawnLocation);
            player.sendMessage("§6暴君Boss 已生成! 准备战斗!");
            
            return true;
        } else if (command.getName().equalsIgnoreCase("tyrantreload")) {
            if (!sender.hasPermission("tyrantboss.reload")) {
                sender.sendMessage("§c你没有权限重新加载配置!");
                return true;
            }
            
            reloadTreasureConfig();
            sender.sendMessage("§a暴君插件配置已重新加载!");
            return true;
        } else if (command.getName().equalsIgnoreCase("tyrantaddtreasure")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以使用此命令!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("tyrantboss.addtreasure")) {
                player.sendMessage("§c你没有权限添加宝藏袋物品!");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§c用法: /tyrantaddtreasure <奖励ID> <概率>");
                player.sendMessage("§e示例: /tyrantaddtreasure my_sword 25.0");
                return true;
            }
            
            String rewardId = args[0];
            double chance;
            
            try {
                chance = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c概率必须是数字!");
                return true;
            }
            
            if (chance < 0 || chance > 100) {
                player.sendMessage("§c概率必须在0-100之间!");
                return true;
            }
            
            // 添加物品到宝藏袋
            boolean success = treasureManager.addItemToTreasure(player, rewardId, chance);
            if (success) {
                player.sendMessage("§a成功将手中物品添加到宝藏袋奖励中，ID: " + rewardId);
            } else {
                player.sendMessage("§c添加失败，请确保手中有物品!");
            }
            
            return true;
        } else if (command.getName().equalsIgnoreCase("tyrantlisttreasure")) {
            if (!sender.hasPermission("tyrantboss.listtreasure")) {
                sender.sendMessage("§c你没有权限查看宝藏袋物品!");
                return true;
            }
            
            treasureManager.listTreasureItems(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("tyrantremovetreasure")) {
            if (!sender.hasPermission("tyrantboss.removetreasure")) {
                sender.sendMessage("§c你没有权限移除宝藏袋物品!");
                return true;
            }
            
            if (args.length < 1) {
                sender.sendMessage("§c用法: /tyrantremovetreasure <奖励ID>");
                return true;
            }
            
            String rewardId = args[0];
            boolean success = treasureManager.removeTreasureItem(rewardId);
            
            if (success) {
                sender.sendMessage("§a成功移除宝藏袋物品: " + rewardId);
            } else {
                sender.sendMessage("§c未找到指定的宝藏袋物品: " + rewardId);
            }
            
            return true;
        }
        
        return false;
    }

    public void spawnTyrantBoss(Location location) {
        WitherSkeleton boss = (WitherSkeleton) location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
        setupBossAttributes(boss);
        
        TyrantBoss tyrantBoss = new TyrantBoss(boss, this);
        activeBosses.put(boss.getUniqueId(), tyrantBoss);
        
        // 创建Boss血条
        createBossBar(boss);
        
        // 开始Boss行为
        tyrantBoss.startBossBehavior();
    }

    private void setupBossAttributes(WitherSkeleton boss) {
        boss.setCustomName("§6§l暴君 §c§lTyrant");
        boss.setCustomNameVisible(true);
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        
        // 设置属性 - 减少近战攻击力但增加血量
        Objects.requireNonNull(boss.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(150.0); // 增加血量
        boss.setHealth(150.0);
        Objects.requireNonNull(boss.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(15.0); // 减少近战伤害
        Objects.requireNonNull(boss.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.35);
        Objects.requireNonNull(boss.getAttribute(Attribute.FOLLOW_RANGE)).setBaseValue(50.0);
        
        // 装备下界合金甲
        equipNetheriteArmor(boss);
        
        // 给予镐子作为武器
        boss.getEquipment().setItemInMainHand(createTyrantPickaxe());
        boss.getEquipment().setItemInMainHandDropChance(0.0f);
        
        // 添加免疫摔落伤害的效果
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
    }

    private void equipNetheriteArmor(WitherSkeleton boss) {
        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        
        // 设置装备不掉落
        boss.getEquipment().setHelmetDropChance(0.0f);
        boss.getEquipment().setChestplateDropChance(0.0f);
        boss.getEquipment().setLeggingsDropChance(0.0f);
        boss.getEquipment().setBootsDropChance(0.0f);
    }

    private ItemStack createTyrantPickaxe() {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6暴君之镐");
            meta.setLore(Arrays.asList(
                "§7暴君的专属武器",
                "§7每五次攻击引发爆炸",
                "§7蕴含着毁灭性的力量"
            ));
            meta.setUnbreakable(true);
            pickaxe.setItemMeta(meta);
        }
        
        return pickaxe;
    }

    private void createBossBar(WitherSkeleton boss) {
        BossBar bossBar = Bukkit.createBossBar(
            "§6§l暴君 §c§lTyrant §7- §c❤ " + (int)boss.getHealth() + "/" + (int)boss.getMaxHealth(),
            BarColor.RED,
            BarStyle.SEGMENTED_12 // 使用12段血条显示更精确
        );
        
        bossBar.setProgress(boss.getHealth() / boss.getMaxHealth());
        
        // 向所有在线玩家显示血条
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        
        bossBars.put(boss.getUniqueId(), bossBar);
    }

    private void createGhostBossBar(Ghast ghost) {
        BossBar bossBar = Bukkit.createBossBar(
            "§5§l暴君残魂 §d§lTyrant Ghost §7- §c❤ " + (int)ghost.getHealth() + "/" + (int)ghost.getMaxHealth(),
            BarColor.PURPLE,
            BarStyle.SEGMENTED_12
        );
        
        bossBar.setProgress(ghost.getHealth() / ghost.getMaxHealth());
        
        // 向所有在线玩家显示血条
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        
        bossBars.put(ghost.getUniqueId(), bossBar);
    }

    private void updateBossBar(WitherSkeleton boss) {
        BossBar bossBar = bossBars.get(boss.getUniqueId());
        if (bossBar != null) {
            String phaseText = getPhaseText(boss);
            String rageText = activeBosses.get(boss.getUniqueId()).isEnraged() ? " §4§l[暴怒]§7" : "";
            String invulText = activeBosses.get(boss.getUniqueId()).isInvulnerable() ? " §b§l[无敌]§7" : "";
            
            bossBar.setTitle("§6§l暴君 §c§lTyrant §7- §c❤ " + 
                (int)boss.getHealth() + "/" + (int)boss.getMaxHealth() + 
                " §7[" + phaseText + "]" + rageText + invulText);
            bossBar.setProgress(boss.getHealth() / boss.getMaxHealth());
        }
    }

    private void updateGhostBossBar(Ghast ghost) {
        BossBar bossBar = bossBars.get(ghost.getUniqueId());
        if (bossBar != null) {
            TyrantGhostBoss ghostBoss = activeGhostBosses.get(ghost.getUniqueId());
            String phaseText = "§5§l残魂阶段";
            
            bossBar.setTitle("§5§l暴君残魂 §d§lTyrant Ghost §7- §c❤ " + 
                (int)ghost.getHealth() + "/" + (int)ghost.getMaxHealth() + 
                " §7[" + phaseText + "]");
            bossBar.setProgress(ghost.getHealth() / ghost.getMaxHealth());
        }
    }

    private String getPhaseText(WitherSkeleton boss) {
        double healthPercent = boss.getHealth() / boss.getMaxHealth();
        if (healthPercent <= 0.1) return "§4§l终焉阶段";
        if (healthPercent <= 0.3) return "§c§l狂暴阶段";
        return "§6§l常规阶段";
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (activeBosses.containsKey(event.getEntity().getUniqueId())) {
            WitherSkeleton boss = (WitherSkeleton) event.getEntity();
            TyrantBoss tyrantBoss = activeBosses.get(boss.getUniqueId());
            
            if (tyrantBoss != null) {
                // 检查无敌状态
                if (tyrantBoss.isInvulnerable()) {
                    event.setCancelled(true);
                    return;
                }
                
                tyrantBoss.onDamage(event);
                updateBossBar(boss);
                
                // 记录对Boss的伤害
                if (event.getDamager() instanceof Player) {
                    tyrantBoss.recordPlayerDamage((Player) event.getDamager());
                }
            }
        } else if (activeGhostBosses.containsKey(event.getEntity().getUniqueId())) {
            Ghast ghost = (Ghast) event.getEntity();
            TyrantGhostBoss ghostBoss = activeGhostBosses.get(ghost.getUniqueId());
            
            if (ghostBoss != null) {
                ghostBoss.onDamage(event);
                updateGhostBossBar(ghost);
            }
        }
    }

    // 新增：处理暴君摔落伤害
    @EventHandler
    public void onBossFallDamage(EntityDamageEvent event) {
        if (activeBosses.containsKey(event.getEntity().getUniqueId())) {
            // 如果是摔落伤害，取消它
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
            }
        }
    }

    // 新增：保护宝藏袋不被烧毁
    @EventHandler
    public void onTreasureBagDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item) {
            Item item = (Item) event.getEntity();
            ItemStack itemStack = item.getItemStack();
            
            // 检查是否是宝藏袋
            if (itemStack.getType() == Material.BUNDLE && itemStack.hasItemMeta()) {
                ItemMeta meta = itemStack.getItemMeta();
                if (meta.hasDisplayName() && meta.getDisplayName().equals("§6§l暴君宝藏袋")) {
                    // 取消所有可能破坏宝藏袋的伤害
                    if (event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                        event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
                        event.getCause() == EntityDamageEvent.DamageCause.LAVA ||
                        event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                        event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (activeBosses.containsKey(event.getEntity().getUniqueId())) {
            WitherSkeleton boss = (WitherSkeleton) event.getEntity();
            TyrantBoss tyrantBoss = activeBosses.remove(boss.getUniqueId());
            
            if (tyrantBoss != null) {
                tyrantBoss.cleanup();
            }
            
            BossBar bossBar = bossBars.remove(boss.getUniqueId());
            if (bossBar != null) {
                bossBar.removeAll();
            }
            
            // 清除掉落物
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // 生成暴君残魂
            spawnTyrantGhost(boss.getLocation());
            
            // 广播消息
            Bukkit.broadcastMessage("§6§l暴君已被击败! 但它的残魂依然存在! §5小心暴君残魂的轰炸和龙息!");
        } else if (activeGhostBosses.containsKey(event.getEntity().getUniqueId())) {
            Ghast ghost = (Ghast) event.getEntity();
            TyrantGhostBoss ghostBoss = activeGhostBosses.remove(ghost.getUniqueId());
            
            if (ghostBoss != null) {
                ghostBoss.cleanup();
            }
            
            BossBar bossBar = bossBars.remove(ghost.getUniqueId());
            if (bossBar != null) {
                bossBar.removeAll();
            }
            
            // 清除掉落物
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // 生成暴君宝藏袋
            createTreasureBag(ghost.getLocation());
            
            // 广播最终胜利消息
            Bukkit.broadcastMessage("§6§l暴君残魂已被彻底消灭! §e荣耀归于勇者们!");
            Bukkit.broadcastMessage("§6§l暴君宝藏袋已掉落! §e快去收集战利品吧!");
        }
    }

    // 新增：生成暴君残魂
    private void spawnTyrantGhost(Location location) {
        Ghast ghost = (Ghast) location.getWorld().spawnEntity(location, EntityType.GHAST);
        setupGhostAttributes(ghost);
        
        TyrantGhostBoss ghostBoss = new TyrantGhostBoss(ghost, this);
        activeGhostBosses.put(ghost.getUniqueId(), ghostBoss);
        
        // 创建Boss血条
        createGhostBossBar(ghost);
        
        // 开始Boss行为
        ghostBoss.startBossBehavior();
    }

    private void setupGhostAttributes(Ghast ghost) {
        ghost.setCustomName("§5§l暴君残魂 §d§lTyrant Ghost");
        ghost.setCustomNameVisible(true);
        ghost.setPersistent(true);
        ghost.setRemoveWhenFarAway(false);
        
        // 设置属性 - 80血量，无护甲
        Objects.requireNonNull(ghost.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(80.0);
        ghost.setHealth(80.0);
        
        // 免疫摔落伤害
        ghost.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
    }

    // 新增：创建暴君宝藏袋
    private void createTreasureBag(Location location) {
        // 创建宝藏袋物品
        ItemStack treasureBag = new ItemStack(Material.BUNDLE);
        ItemMeta meta = treasureBag.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6§l暴君宝藏袋");
            meta.setLore(Arrays.asList(
                "§7暴君掉落的珍贵战利品",
                "§7蕴含着强大的力量",
                "",
                "§e右键点击打开"
            ));
            
            // 添加附魔光效
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            
            treasureBag.setItemMeta(meta);
        }
        
        // 掉落宝藏袋
        Item item = location.getWorld().dropItemNaturally(location, treasureBag);
        item.setCustomName("§6§l暴君宝藏袋");
        item.setCustomNameVisible(true);
        item.setGlowing(true);
        item.setUnlimitedLifetime(true); // 防止消失
        item.setInvulnerable(true); // 设置无敌，防止被破坏
        
        // 添加粒子效果
        location.getWorld().spawnParticle(Particle.FLAME, location, 50, 1, 1, 1);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        
        // 设置宝藏袋被捡起时的行为
        setupTreasureBagPickup(item);
    }

    // 新增：设置宝藏袋被捡起时的行为
    private void setupTreasureBagPickup(Item treasureItem) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!treasureItem.isValid() || treasureItem.isDead()) {
                    cancel();
                    return;
                }
                
                // 持续粒子效果
                Location loc = treasureItem.getLocation();
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.2, 0.2);
                
                // 检查是否有玩家在附近
                for (Player player : loc.getWorld().getPlayers()) {
                    if (player.getLocation().distance(loc) <= 3) {
                        player.sendActionBar("§6附近有暴君宝藏袋!");
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getEntity();
            if (fireball.getShooter() instanceof WitherSkeleton) {
                WitherSkeleton shooter = (WitherSkeleton) fireball.getShooter();
                if (activeBosses.containsKey(shooter.getUniqueId())) {
                    Location hitLocation = event.getEntity().getLocation();
                    hitLocation.getBlock().setType(Material.LAVA);
                    
                    // 创建爆炸效果
                    createMagmaExplosion(hitLocation);
                }
            } else if (fireball.getShooter() instanceof Ghast) {
                Ghast shooter = (Ghast) fireball.getShooter();
                if (activeGhostBosses.containsKey(shooter.getUniqueId())) {
                    // 暴君残魂的火球不生成岩浆，但爆炸威力更大
                    Location hitLocation = event.getEntity().getLocation();
                    hitLocation.getWorld().createExplosion(hitLocation, 4.0f, true, true, shooter);
                }
            }
        }
    }

    @EventHandler
    public void onWitherSkullHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof WitherSkull) {
            WitherSkull skull = (WitherSkull) event.getEntity();
            if (skull.getShooter() instanceof WitherSkeleton) {
                WitherSkeleton shooter = (WitherSkeleton) skull.getShooter();
                if (activeBosses.containsKey(shooter.getUniqueId())) {
                    // 检查是否是爆炸骷髅头
                    if (skull.isCharged()) {
                        Location hitLocation = event.getEntity().getLocation();
                        // 创建凋零爆炸效果，但不伤害暴君自己
                        hitLocation.getWorld().createExplosion(hitLocation, 3.0f, true, true, shooter); // 将暴君设为爆炸源，这样爆炸不会伤害自己
                        hitLocation.getWorld().spawnParticle(Particle.SMOKE, hitLocation, 30, 1, 1, 1);
                    }
                }
            }
        }
    }

    // 新增：处理龙息攻击
    @EventHandler
    public void onDragonBreathHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof DragonFireball) {
            DragonFireball dragonFireball = (DragonFireball) event.getEntity();
            if (dragonFireball.getShooter() instanceof Ghast) {
                Ghast shooter = (Ghast) dragonFireball.getShooter();
                if (activeGhostBosses.containsKey(shooter.getUniqueId())) {
                    Location hitLocation = event.getEntity().getLocation();
                    
                    // 创建龙息区域效果
                    createDragonBreathArea(hitLocation);
                }
            }
        }
    }

    private void createDragonBreathArea(Location location) {
        // 创建龙息云
        AreaEffectCloud cloud = location.getWorld().spawn(location, AreaEffectCloud.class);
        cloud.setRadius(5.0f);
        cloud.setRadiusOnUse(-0.5f);
        cloud.setRadiusPerTick(-0.01f);
        cloud.setDuration(200); // 10秒
        cloud.setParticle(Particle.DRAGON_BREATH);
        cloud.setColor(Color.PURPLE);
        
        // 添加效果
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1), true);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 1), true);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1), true);
        
        // 音效和粒子
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
        location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 50, 2, 2, 2);
    }

    private void createMagmaExplosion(Location location) {
        location.getWorld().spawnParticle(Particle.LAVA, location, 50, 2, 2, 2);
        location.getWorld().spawnParticle(Particle.FLAME, location, 30, 2, 2, 2);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
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

    public TyrantBossManager getBossManager() {
        return bossManager;
    }

    public Map<UUID, TyrantBoss> getActiveBosses() {
        return activeBosses;
    }
    
    public Map<UUID, TyrantGhostBoss> getActiveGhostBosses() {
        return activeGhostBosses;
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
}
