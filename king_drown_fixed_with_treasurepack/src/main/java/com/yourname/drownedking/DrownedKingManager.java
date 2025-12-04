package com.yourname.drownedking;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DrownedKingManager {
    
    private final DrownedKingPlugin plugin;
    private final Map<UUID, DrownedKingBoss> activeBosses = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> bossAITasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shockCounters = new ConcurrentHashMap<>();
    private final Map<UUID, World> bossWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, List<Trident>> activeTridents = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> frenzyTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> frenzySummonCounters = new ConcurrentHashMap<>();
    
    public DrownedKingManager(DrownedKingPlugin plugin) {
        this.plugin = plugin;
        loadActiveBosses();
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
    }
    
    // 新增方法：获取三叉戟狂欢节是否破坏方块的配置
    public boolean isTridentFrenzyBlockDamageEnabled() {
        return plugin.getConfig().getBoolean("trident_frenzy_block_damage", true);
    }
    
    // 新增方法：获取配置值用于命令反馈
    public boolean getTridentFrenzyBlockDamage() {
        return isTridentFrenzyBlockDamageEnabled();
    }
    
    // 新增方法：设置配置值
    public void setTridentFrenzyBlockDamage(boolean enabled) {
        plugin.getConfig().set("trident_frenzy_block_damage", enabled);
        plugin.saveConfig();
    }
    
    public boolean spawnDrownedKing(Player spawner, Location location) {
        try {
            // 创建Boss实体
            Drowned boss = (Drowned) location.getWorld().spawnEntity(location, EntityType.DROWNED);
            
            // 设置Boss属性
            setupBossAttributes(boss);
            
            // 设置Boss装备
            setupBossEquipment(boss);
            
            // 创建Boss数据
            UUID bossId = UUID.randomUUID();
            DrownedKingBoss bossData = new DrownedKingBoss(
                bossId, 
                boss.getUniqueId(), 
                location, 
                spawner != null ? spawner.getName() : "CONSOLE"
            );
            
            // 为所有在线玩家添加Boss血条
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossData.addPlayerToBossBar(player);
            }
            
            // 存储Boss数据
            activeBosses.put(bossId, bossData);
            bossWorlds.put(bossId, location.getWorld());
            activeTridents.put(bossId, new ArrayList<>());
            frenzySummonCounters.put(bossId, 0);
            
            // 设置Boss元数据
            boss.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "drowned_king_boss"),
                PersistentDataType.STRING,
                bossId.toString()
            );
            
            // 开始AI任务
            startBossAI(boss, bossId);
            
            // 改变天气为雷暴
            World world = location.getWorld();
            world.setStorm(true);
            world.setThundering(true);
            
            // 广播消息
            String message = plugin.getConfig().getString("messages.spawn", 
                "§4⚡ 溺尸王 §c从深渊中苏醒§4! 所有玩家小心!");
            Bukkit.broadcastMessage(message);
            
            // 播放音效
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
            }
            
            saveBoss(bossData);
             // ============ 开始播放第一阶段BGM ============
        // 注意：这行代码必须在 return true; 之前
        if (plugin.getBgmPlayer() != null) {
            plugin.getBgmPlayer().playBGMForAll(DrownedBGMPlayer.BossPhase.DROWNED_NORMAL);
        }
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("生成溺尸王时出错: " + e.getMessage());
            return false;
        }
    }
    
    private void setupBossAttributes(Drowned boss) {
        boss.setCustomName("§4溺尸王 §c(深渊主宰)");
        boss.setCustomNameVisible(true);
        
        // 基础属性
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        boss.setHealth(80.0);
        boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(15.0);
        boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
        boss.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(50.0);
        
        // 药水效果
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 1));
        
        boss.setCanPickupItems(false);
        boss.setPersistent(true);
    }
    
    private void setupBossEquipment(Drowned boss) {
        // 三叉戟 - 使用大型三叉戟
        ItemStack largeTrident = createLargeTrident();
        boss.getEquipment().setItemInMainHand(largeTrident);
        boss.getEquipment().setItemInMainHandDropChance(0.0f);
        
        // 装备
        boss.getEquipment().setHelmet(createProtection4NetheriteHelmet());
        boss.getEquipment().setChestplate(createProtection4NetheriteChestplate());
        boss.getEquipment().setLeggings(createProtection4NetheriteLeggings());
        boss.getEquipment().setBoots(createProtection4NetheriteBoots());
        
        // 装备掉落率设为0
        boss.getEquipment().setHelmetDropChance(0.0f);
        boss.getEquipment().setChestplateDropChance(0.0f);
        boss.getEquipment().setLeggingsDropChance(0.0f);
        boss.getEquipment().setBootsDropChance(0.0f);
    }
    
    private ItemStack createLargeTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        meta.setDisplayName("§b深渊三叉戟");
        
        // 附魔 - 大型三叉戟有引雷、忠诚和穿刺
        meta.addEnchant(Enchantment.CHANNELING, 3, true);
        meta.addEnchant(Enchantment.IMPALING, 5, true);
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        
        // 添加Lore
        List<String> lore = new ArrayList<>();
        lore.add("§7溺尸王的专属武器");
        lore.add("§c能够召唤雷电攻击敌人");
        lore.add("§6大型三叉戟 - 引雷+忠诚+穿刺");
        meta.setLore(lore);
        
        trident.setItemMeta(meta);
        return trident;
    }
    
    private ItemStack createSmallTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        meta.setDisplayName("§7穿刺三叉戟");
        
        // 附魔 - 小型三叉戟只有穿刺
        meta.addEnchant(Enchantment.IMPALING, 3, true);
        
        // 添加Lore
        List<String> lore = new ArrayList<>();
        lore.add("§7小型穿刺三叉戟");
        lore.add("§c只有穿刺附魔");
        meta.setLore(lore);
        
        trident.setItemMeta(meta);
        return trident;
    }
    
    private ItemStack createProtection4NetheriteHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5深渊头盔");
        helmet.setItemMeta(meta);
        return helmet;
    }
    
    private ItemStack createProtection4NetheriteChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5深渊胸甲");
        chestplate.setItemMeta(meta);
        return chestplate;
    }
    
    private ItemStack createProtection4NetheriteLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5深渊护腿");
        leggings.setItemMeta(meta);
        return leggings;
    }
    
    private ItemStack createProtection4NetheriteBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5深渊靴子");
        boots.setItemMeta(meta);
        return boots;
    }
    
    private void addProtectionEnchantment(ItemMeta meta, int level) {
        try {
            meta.addEnchant(Enchantment.PROTECTION, level, true);
        } catch (Exception e) {
            plugin.getLogger().warning("添加保护附魔时出错: " + e.getMessage());
        }
    }
    
    private void startBossAI(Drowned boss, UUID bossId) {
        BukkitRunnable aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    this.cancel();
                    bossAITasks.remove(bossId);
                    shockCounters.remove(bossId);
                    
                    // 清理所有活跃的三叉戟
                    List<Trident> tridents = activeTridents.remove(bossId);
                    if (tridents != null) {
                        for (Trident trident : tridents) {
                            if (trident.isValid() && !trident.isDead()) {
                                trident.remove();
                            }
                        }
                    }
                    
                    // 清理狂欢节任务
                    BukkitRunnable frenzyTask = frenzyTasks.remove(bossId);
                    if (frenzyTask != null) {
                        frenzyTask.cancel();
                    }
                    
                    frenzySummonCounters.remove(bossId);
                    
                    return;
                }
                
                // 更新Boss血条
                DrownedKingBoss bossData = activeBosses.get(bossId);
                if (bossData != null) {
                    double healthPercent = boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue();
                    bossData.updateBossBar(healthPercent);
                    
                    // 检查电涌攻击计数是否达到6次 - 无论血量多少都触发
                    if (bossData.getSurgeAttackCount() >= 6 && !bossData.isInTridentFrenzy()) {
                        // 触发三叉戟狂欢节
                        tridentFrenzy(boss, bossId);
                        // 重置电涌计数
                        bossData.resetSurgeAttackCount();
                    }
                    
                    // 血量后半段更频繁释放技能
                    if (healthPercent <= 0.5) {
                        // 血量后半段更频繁释放技能
                        if (Math.random() < 0.08) { // 8%几率施放技能（血量后半段）
                            castRandomAbility(boss, bossId);
                        }
                    } else {
                        // 血量前半段正常频率
                        if (Math.random() < 0.03) { // 3%几率施放技能
                            castRandomAbility(boss, bossId);
                        }
                    }
                }
                
                // 攻击附近所有玩家
                attackNearbyPlayers(boss, bossId);
                
                // 电涌攻击 - 靠近的玩家会受到电击
                handleSurgeAttack(boss, bossId);
                
                // 传送机制 - 如果离所有玩家都很远
                if (shouldTeleport(boss)) {
                    teleportToRandomPlayer(boss);
                }
                
                // 清理无效的三叉戟
                cleanUpInvalidTridents(bossId);
            }
        };
        
        bossAITasks.put(bossId, aiTask);
        shockCounters.put(bossId, 0);
        aiTask.runTaskTimer(plugin, 0L, 20L); // 每秒执行一次
    }
    
    private void attackNearbyPlayers(Drowned boss, UUID bossId) {
        Collection<Player> nearbyPlayersCollection = boss.getLocation().getNearbyPlayers(30);
        List<Player> nearbyPlayers = new ArrayList<>(nearbyPlayersCollection);
        
        // 过滤掉旁观模式和创造模式的玩家
        nearbyPlayers.removeIf(player -> 
            player.getGameMode() == GameMode.SPECTATOR || 
            player.getGameMode() == GameMode.CREATIVE
        );
        
        for (Player player : nearbyPlayers) {
            if (player.isDead() || !player.isOnline()) continue;
            
            // 设置目标（会攻击最近的一个玩家）
            if (boss.getTarget() == null || !boss.getTarget().equals(player)) {
                // 选择最近的玩家作为主要目标
                if (boss.getTarget() == null || 
                    boss.getLocation().distance(player.getLocation()) < 
                    boss.getLocation().distance(boss.getTarget().getLocation())) {
                    boss.setTarget(player);
                }
            }
            
            // 远程攻击 - 如果玩家距离较远且持有三叉戟
            if (boss.getEquipment().getItemInMainHand().getType() == Material.TRIDENT) {
                double distance = boss.getLocation().distance(player.getLocation());
                if (distance > 8 && distance < 25 && Math.random() < 0.4) {
                    // 同时发射两种三叉戟
                    Vector direction = player.getLocation().add(0, 1, 0).toVector()
                        .subtract(boss.getLocation().toVector()).normalize();
                    
                    // 发射大型三叉戟（有引雷、忠诚、穿刺）
                    Trident largeTrident = boss.launchProjectile(Trident.class, direction);
                    largeTrident.setVelocity(direction.multiply(1.8));
                    
                    // 标记这个三叉戟属于Boss和类型
                    largeTrident.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "boss_trident"),
                        PersistentDataType.STRING,
                        boss.getUniqueId().toString()
                    );
                    largeTrident.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "trident_type"),
                        PersistentDataType.STRING,
                        "large"
                    );
                    
                    // 发射小型三叉戟（只有穿刺）
                    Trident smallTrident = boss.launchProjectile(Trident.class, direction);
                    smallTrident.setVelocity(direction.multiply(1.8));
                    
                    // 标记这个三叉戟属于Boss和类型
                    smallTrident.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "boss_trident"),
                        PersistentDataType.STRING,
                        boss.getUniqueId().toString()
                    );
                    smallTrident.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "trident_type"),
                        PersistentDataType.STRING,
                        "small"
                    );
                    
                    // 添加到活跃列表，用于狂欢节引爆
                    List<Trident> tridents = activeTridents.get(bossId);
                    if (tridents != null) {
                        tridents.add(smallTrident);
                    }
                }
            }
        }
    }
    
    private void handleSurgeAttack(Drowned boss, UUID bossId) {
        // 电涌攻击 - 靠近的玩家会受到电击
        Collection<Player> veryClosePlayers = boss.getLocation().getNearbyPlayers(3);
        
        // 过滤掉旁观模式和创造模式的玩家
        veryClosePlayers.removeIf(player -> 
            player.getGameMode() == GameMode.SPECTATOR || 
            player.getGameMode() == GameMode.CREATIVE
        );
        
        for (Player player : veryClosePlayers) {
            if (Math.random() < 0.2) { // 20%几率电击
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.damage(5.0, boss);
                player.sendMessage("§c⚡ 你被溺尸王的电涌击中!");
                
                // 增加电涌攻击计数
                DrownedKingBoss bossData = activeBosses.get(bossId);
                if (bossData != null) {
                    bossData.incrementSurgeAttackCount();
                    // 调试信息
                    boss.sendMessage("§e[DEBUG] 电涌计数: " + bossData.getSurgeAttackCount());
                }
            }
        }
    }
    
    private void castRandomAbility(Drowned boss, UUID bossId) {
        Random random = new Random();
        int ability = random.nextInt(3); // 0-2 只有3个普通技能
        
        switch (ability) {
            case 0:
                lightningStrike(boss, bossId);
                break;
            case 1:
                summonMinions(boss, bossId);
                break;
            case 2:
                shockwave(boss);
                break;
            // 三叉戟狂欢节现在由电涌计数触发
        }
    }
    
    private void lightningStrike(Drowned boss, UUID bossId) {
        Collection<Player> nearbyPlayersCollection = boss.getLocation().getNearbyPlayers(20);
        List<Player> nearbyPlayers = new ArrayList<>(nearbyPlayersCollection);
        
        // 过滤掉旁观模式和创造模式的玩家
        nearbyPlayers.removeIf(player -> 
            player.getGameMode() == GameMode.SPECTATOR || 
            player.getGameMode() == GameMode.CREATIVE
        );
        
        int strikes = 3 + new Random().nextInt(3); // 3-5次雷击
        
        for (int i = 0; i < strikes; i++) {
            if (nearbyPlayers.isEmpty()) break;
            
            Player target = nearbyPlayers.get(new Random().nextInt(nearbyPlayers.size()));
            target.getWorld().strikeLightningEffect(target.getLocation());
            
            // 计算伤害 14-25点随机伤害
            double damage = 14 + (Math.random() * 11); // 14到25之间的随机数
            target.damage(damage, boss);
            
            target.sendMessage("§c⚡ 你被溺尸王的雷电击中! 受到 " + String.format("%.1f", damage) + " 点伤害!");
            
            // 增加电涌攻击计数 - 雷电攻击也计入
            DrownedKingBoss bossData = activeBosses.get(bossId);
            if (bossData != null) {
                bossData.incrementSurgeAttackCount();
                // 调试信息
                boss.sendMessage("§e[DEBUG] 雷电攻击，电涌计数: " + bossData.getSurgeAttackCount());
            }
        }
        
        Bukkit.broadcastMessage("§4溺尸王 §c召唤了雷电风暴!");
    }
    
    private void summonMinions(Drowned boss, UUID bossId) {
        Location bossLoc = boss.getLocation();
        int minionCount = 3 + new Random().nextInt(3); // 3-5个随从
        
        for (int i = 0; i < minionCount; i++) {
            // 修改：优先在溺尸王附近且同高度生成
            Location spawnLoc = findSpawnLocationNearBoss(bossLoc, 2, 6);
            if (spawnLoc != null) {
                Drowned minion = (Drowned) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.DROWNED);
                minion.setCustomName("§7溺尸守卫");
                minion.setCustomNameVisible(true);
                minion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
                minion.setHealth(40.0);
                minion.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(6.0);
                
                // 给随从装备小型三叉戟
                ItemStack smallTrident = createSmallTrident();
                minion.getEquipment().setItemInMainHand(smallTrident);
                minion.getEquipment().setItemInMainHandDropChance(0.0f);
                
                // 设置随从目标为Boss的目标
                if (boss.getTarget() != null) {
                    minion.setTarget(boss.getTarget());
                }
                
                // 标记随从属于Boss
                minion.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "minion_of"),
                    PersistentDataType.STRING,
                    bossId.toString()
                );
            }
        }
        
        Bukkit.broadcastMessage("§4溺尸王 §c召唤了溺尸守卫!");
    }
    
    // 新增方法：在Boss附近且同高度寻找生成位置
    private Location findSpawnLocationNearBoss(Location bossLoc, int minDistance, int maxDistance) {
        Random random = new Random();
        World world = bossLoc.getWorld();
        double bossY = bossLoc.getY();
        
        // 尝试在Boss附近且同高度生成
        for (int i = 0; i < 15; i++) { // 增加尝试次数
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            
            double x = bossLoc.getX() + Math.cos(angle) * distance;
            double z = bossLoc.getZ() + Math.sin(angle) * distance;
            
            // 保持与Boss相同的高度
            Location testLocation = new Location(world, x, bossY, z);
            
            // 检查该位置是否安全
            if (isSafeLocation(testLocation)) {
                return testLocation;
            }
        }
        
        // 如果同高度找不到安全位置，则在Boss附近寻找任何安全位置
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            
            double x = bossLoc.getX() + Math.cos(angle) * distance;
            double z = bossLoc.getZ() + Math.sin(angle) * distance;
            
            Location testLocation = new Location(world, x, bossLoc.getY(), z);
            Location safeLocation = findSafeLocation(testLocation);
            
            if (safeLocation != null) {
                return safeLocation;
            }
        }
        
        return null;
    }
    
    // 新增方法：检查位置是否安全（与Boss同高度）
    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        // 检查当前位置是否安全（非固体且非液体）
        Location currentLoc = new Location(world, x + 0.5, y, z + 0.5);
        if (currentLoc.getBlock().getType().isSolid() || currentLoc.getBlock().isLiquid()) {
            return false;
        }
        
        // 检查上方一格是否安全
        Location aboveLoc = currentLoc.clone().add(0, 1, 0);
        if (aboveLoc.getBlock().getType().isSolid() || aboveLoc.getBlock().isLiquid()) {
            return false;
        }
        
        // 检查下方一格是否是固体
        Location belowLoc = currentLoc.clone().add(0, -1, 0);
        if (!belowLoc.getBlock().getType().isSolid()) {
            return false;
        }
        
        return true;
    }
    
    private void shockwave(Drowned boss) {
        Location bossLoc = boss.getLocation();
        
        // 击退效果
        Collection<Player> nearbyPlayersCollection = bossLoc.getNearbyPlayers(8);
        List<Player> nearbyPlayers = new ArrayList<>(nearbyPlayersCollection);
        
        // 过滤掉旁观模式和创造模式的玩家
        nearbyPlayers.removeIf(player -> 
            player.getGameMode() == GameMode.SPECTATOR || 
            player.getGameMode() == GameMode.CREATIVE
        );
        
        for (Player player : nearbyPlayers) {
            Vector direction = player.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
            direction.setY(0.3); // 轻微向上击飞
            player.setVelocity(direction.multiply(2.5));
            
            // 计算伤害 14-25点随机伤害
            double damage = 14 + (Math.random() * 11); // 14到25之间的随机数
            player.damage(damage, boss);
            
            player.sendMessage("§c💥 你被溺尸王的冲击波击中! 受到 " + String.format("%.1f", damage) + " 点伤害!");
        }
        
        // 粒子效果
        bossLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, bossLoc, 10);
        
        Bukkit.broadcastMessage("§4溺尸王 §c释放了冲击波!");
    }
    
    private void tridentFrenzy(Drowned boss, UUID bossId) {
        DrownedKingBoss bossData = activeBosses.get(bossId);
        if (bossData == null || bossData.isInTridentFrenzy()) {
            return;
        }
        
        // 设置狂欢节状态
        bossData.setInTridentFrenzy(true);
       // 在 tridentFrenzy 方法开头，设置狂欢节状态后添加：
// 切换到狂欢节BGM
if (plugin.getBgmPlayer() != null) {
    plugin.getBgmPlayer().updateBossPhase(DrownedBGMPlayer.BossPhase.DROWNED_FRENZY);
} 
        Bukkit.broadcastMessage("§4⚡ 溺尸王 §c开始了三叉戟狂欢节! 这将持续到它死亡!");
        
        Location bossLoc = boss.getLocation();
        
        // 第一阶段：快速投掷多个小型三叉戟
        for (int i = 0; i < 15; i++) { // 增加到15个小型三叉戟
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!boss.isValid() || boss.isDead()) return;
                
                // 向随机方向投掷小型三叉戟
                Vector direction = new Vector(
                    (Math.random() - 0.5) * 2,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * 2
                ).normalize();
                
                Trident trident = boss.launchProjectile(Trident.class, direction);
                trident.setVelocity(direction.multiply(2.0)); // 高速三叉戟
                
                // 标记这个三叉戟属于狂欢节和小型
                trident.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "frenzy_trident"),
                    PersistentDataType.BYTE,
                    (byte) 1
                );
                
                // 标记这个三叉戟属于Boss和类型
                trident.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "boss_trident"),
                    PersistentDataType.STRING,
                    boss.getUniqueId().toString()
                );
                trident.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "trident_type"),
                    PersistentDataType.STRING,
                    "small"
                );
                
                // 添加到活跃三叉戟列表
                List<Trident> tridents = activeTridents.get(bossId);
                if (tridents != null) {
                    tridents.add(trident);
                }
                
            }, i * 2L); // 错开的三叉戟投掷
        }
        
        // 启动持续引爆任务 - 每10秒引爆一次地上的小型三叉戟
        BukkitRunnable frenzyTask = new BukkitRunnable() {
            private int counter = 0;
            
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    this.cancel();
                    frenzyTasks.remove(bossId);
                    return;
                }
                
                // 每10秒引爆一次地上的小型三叉戟
                explodeGroundTridents(boss, bossId, 25.0); // 增加引爆半径
                
                // 每20秒召唤一次小弟
                counter++;
                if (counter % 2 == 0) { // 每两次执行，即20秒
                    summonMinions(boss, bossId);
                    Bukkit.broadcastMessage("§4溺尸王 §c在狂欢节中召唤了更多守卫!");
                }
                
                // 更新Boss血条显示狂欢节状态
                if (bossData != null) {
                    bossData.getBossBar().setTitle("§4⚡ 溺尸王 §c(狂欢节状态)");
                }
            }
        };
        
        frenzyTasks.put(bossId, frenzyTask);
        frenzyTask.runTaskTimer(plugin, 100L, 100L); // 5秒后开始，每5秒执行一次
    }
    
    private void explodeGroundTridents(Drowned boss, UUID bossId, double radius) {
        List<Trident> tridents = activeTridents.get(bossId);
        if (tridents == null || tridents.isEmpty()) return;
        
        Location bossLoc = boss.getLocation();
        int explodedCount = 0;
        
        // 收集半径内插在地上的小型三叉戟
        List<Trident> tridentsToExplode = new ArrayList<>();
        for (Trident trident : tridents) {
            if (trident.isValid() && !trident.isDead() && 
                trident.getLocation().distance(bossLoc) <= radius &&
                trident.isOnGround() && // 只引爆插在地上的三叉戟
                "small".equals(trident.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "trident_type"),
                    PersistentDataType.STRING))) {
                tridentsToExplode.add(trident);
            }
        }
        
        // 获取配置：是否破坏方块
        boolean blockDamage = isTridentFrenzyBlockDamageEnabled();
        
        // 引爆三叉戟
        for (Trident trident : tridentsToExplode) {
            Location tridentLoc = trident.getLocation();
            
            // 大型爆炸效果 - 使用配置决定是否破坏方块
            tridentLoc.getWorld().createExplosion(
                tridentLoc.getX(), tridentLoc.getY(), tridentLoc.getZ(),
                5.0f, // 爆炸威力
                blockDamage, // 使用配置决定是否破坏方块
                true, // 是否产生火焰  
                boss // 爆炸来源
            );
            
            // 对爆炸范围内的玩家造成14-25点随机伤害
            Collection<Player> explosionPlayers = tridentLoc.getNearbyPlayers(8);
            for (Player player : explosionPlayers) {
                double distance = player.getLocation().distance(tridentLoc);
                if (distance <= 8) {
                    // 距离越近伤害越高
                    double damageMultiplier = 1.0 - (distance / 8.0);
                    double damage = 14 + (Math.random() * 11); // 14到25之间的随机数
                    damage *= damageMultiplier;
                    
                    player.damage(damage, boss);
                    player.sendMessage("§c💥 你被三叉戟连锁爆炸击中! 受到 " + String.format("%.1f", damage) + " 点伤害!");
                }
            }
            
            // 多重闪电效果
            for (int i = 0; i < 3; i++) {
                Location lightningLoc = tridentLoc.clone().add(
                    (Math.random() - 0.5) * 8,
                    0,
                    (Math.random() - 0.5) * 8
                );
                lightningLoc.setY(tridentLoc.getWorld().getHighestBlockYAt(lightningLoc));
                lightningLoc.getWorld().strikeLightningEffect(lightningLoc);
            }
            
            // 从列表中移除
            tridents.remove(trident);
            trident.remove();
            explodedCount++;
        }
        
        if (explodedCount > 0) {
            String message = "§4⚡ 溺尸王 §c的狂欢节引爆了 " + explodedCount + " 个地上的三叉戟!";
            if (!blockDamage) {
                message += " §7(地形保护已启用)";
            }
            Bukkit.broadcastMessage(message);
        }
    }
    
    private void cleanUpInvalidTridents(UUID bossId) {
        List<Trident> tridents = activeTridents.get(bossId);
        if (tridents != null) {
            tridents.removeIf(trident -> !trident.isValid() || trident.isDead());
        }
    }
    
    private boolean shouldTeleport(Drowned boss) {
        Collection<Player> nearbyPlayersCollection = boss.getLocation().getNearbyPlayers(25);
        List<Player> nearbyPlayers = new ArrayList<>(nearbyPlayersCollection);
        
        // 过滤掉旁观模式和创造模式的玩家
        nearbyPlayers.removeIf(player -> 
            player.getGameMode() == GameMode.SPECTATOR || 
            player.getGameMode() == GameMode.CREATIVE
        );
        
        return nearbyPlayers.isEmpty(); // 如果没有玩家在25格内，则传送
    }
    
   private void teleportToRandomPlayer(Drowned boss) {
    List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
    
    // 过滤掉旁观模式和创造模式的玩家
    onlinePlayers.removeIf(player -> 
        player.getGameMode() == GameMode.SPECTATOR || 
        player.getGameMode() == GameMode.CREATIVE
    );
    
    if (onlinePlayers.isEmpty()) return;
    
    Player target = onlinePlayers.get(new Random().nextInt(onlinePlayers.size()));
    Location newLocation = findSpawnLocationAtPlayerLevel(target.getLocation(), 10, 20);
    
    if (newLocation != null) {
        boss.teleport(newLocation);
        Bukkit.broadcastMessage("§4溺尸王 §c传送到了 " + target.getName() + " 附近!");
    } else {
        // 如果找不到同高度位置，使用备用方法
        Location backupLocation = findSpawnLocation(target.getLocation(), 10, 20);
        if (backupLocation != null) {
            boss.teleport(backupLocation);
            Bukkit.broadcastMessage("§4溺尸王 §c传送到了 " + target.getName() + " 附近!");
        }
    }
} 
    private Location findSpawnLocation(Location center, int minDistance, int maxDistance) {
        Random random = new Random();
        
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            
            Location testLocation = new Location(center.getWorld(), x, center.getY(), z);
            Location safeLocation = findSafeLocation(testLocation);
            
            if (safeLocation != null) {
                return safeLocation;
            }
        }
        
        return null;
    }
   // 新增方法：优先在与玩家相同高度寻找位置
private Location findSpawnLocationAtPlayerLevel(Location center, int minDistance, int maxDistance) {
    Random random = new Random();
    World world = center.getWorld();
    double playerY = center.getY();
    
    // 尝试在玩家同一高度附近寻找位置
    for (int i = 0; i < 15; i++) { // 增加尝试次数
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        
        // 保持与玩家相近的高度
        Location testLocation = new Location(world, x, playerY, z);
        
        // 检查该位置是否安全
        if (isSafeLocationAtHeight(testLocation, playerY)) {
            return testLocation;
        }
    }
    
    // 如果同一高度找不到，尝试在玩家高度±3格范围内寻找
    for (int i = 0; i < 10; i++) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        
        // 在玩家高度附近随机偏移
        double yOffset = (random.nextDouble() - 0.5) * 6; // -3到+3的偏移
        Location testLocation = new Location(world, x, playerY + yOffset, z);
        
        if (isSafeLocation(testLocation)) {
            return testLocation;
        }
    }
    
    return null;
}

// 新增方法：检查特定高度位置是否安全
private boolean isSafeLocationAtHeight(Location location, double targetY) {
    World world = location.getWorld();
    int x = location.getBlockX();
    int z = location.getBlockZ();
    
    // 设置目标高度
    Location testLocation = new Location(world, x + 0.5, targetY, z + 0.5);
    
    // 检查当前位置是否安全（非固体且非液体）
    if (testLocation.getBlock().getType().isSolid() || testLocation.getBlock().isLiquid()) {
        return false;
    }
    
    // 检查上方一格是否安全
    Location aboveLoc = testLocation.clone().add(0, 1, 0);
    if (aboveLoc.getBlock().getType().isSolid() || aboveLoc.getBlock().isLiquid()) {
        return false;
    }
    
    // 检查下方一格是否是固体
    Location belowLoc = testLocation.clone().add(0, -1, 0);
    if (!belowLoc.getBlock().getType().isSolid()) {
        return false;
    }
    
    return true;
} 
    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        int y = world.getHighestBlockYAt(x, z);
        Location testLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
        
        if (testLocation.getBlock().getType().isSolid() || testLocation.getBlock().isLiquid()) {
            return null;
        }
        
        Location below = testLocation.clone().add(0, -1, 0);
        if (!below.getBlock().getType().isSolid()) {
            return null;
        }
        
        return testLocation;
    }
    
    // 事件处理方法
    public void handleBossDeath(Drowned boss) {
        String bossIdStr = boss.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "drowned_king_boss"),
            PersistentDataType.STRING
        );
        
        if (bossIdStr == null) return;
        
        UUID bossId = UUID.fromString(bossIdStr);
        DrownedKingBoss bossData = activeBosses.get(bossId);
        
        if (bossData != null) {
            bossData.complete(false);
            
            // 恢复天气
            stopRainForBoss(bossId);
            
            // 清理AI任务
            cleanupBossAI(bossId);
            
            // 清理所有活跃的三叉戟
            List<Trident> tridents = activeTridents.remove(bossId);
            if (tridents != null) {
                for (Trident trident : tridents) {
                    if (trident.isValid() && !trident.isDead()) {
                        trident.remove();
                    }
                }
            }
            
            // 清理狂欢节任务
            BukkitRunnable frenzyTask = frenzyTasks.remove(bossId);
            if (frenzyTask != null) {
                frenzyTask.cancel();
            }
            
            frenzySummonCounters.remove(bossId);
            
            // 广播消息
            String message = plugin.getConfig().getString("messages.death", 
                "§4溺尸王 §c已被击败! 世界恢复了平静。");
            Bukkit.broadcastMessage(message);
            // 停止BGM
if (plugin.getBgmPlayer() != null) {
    plugin.getBgmPlayer().stopAllBGM();
}
            // 给予奖励
            giveRewards(boss);
            
            updateBoss(bossData);
            activeBosses.remove(bossId);
        }
    }
    
    // 修改：处理玩家死亡（正确识别不死图腾）
    public void handlePlayerDeath(Player player, Drowned boss, boolean isRealDeath) {
        String bossIdStr = boss.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "drowned_king_boss"),
            PersistentDataType.STRING
        );
        
        if (bossIdStr == null) return;
        
        UUID bossId = UUID.fromString(bossIdStr);
        DrownedKingBoss bossData = activeBosses.get(bossId);
        
        if (bossData != null && isRealDeath) {
            // 增加玩家死亡计数
            bossData.incrementPlayerDeathCount(player.getUniqueId());
            
            // 增加总击杀数
            bossData.incrementPlayersKilled();
            
            // 广播击杀消息
            String message = plugin.getConfig().getString("messages.player_killed", 
                "§4溺尸王 §c击杀了 " + player.getName() + "!");
            Bukkit.broadcastMessage(message);
            
            // 检查是否需要退场（同一玩家死亡2次）
            if (bossData.shouldRetreatAfterPlayerDeath(player.getUniqueId())) {
                handleBossRetreatAfterKill(boss, bossId, player.getName());
            } else {
                // 第一次死亡，只发送嘲笑消息
                sendTauntMessage(boss, player.getName());
            }
            
            updateBoss(bossData);
        }
    }
    
    // 新增：处理守卫杀死玩家
    public void handleMinionKillPlayer(Player player, Drowned minion) {
        String bossIdStr = minion.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "minion_of"),
            PersistentDataType.STRING
        );
        
        if (bossIdStr == null) return;
        
        UUID bossId = UUID.fromString(bossIdStr);
        DrownedKingBoss bossData = activeBosses.get(bossId);
        
        if (bossData != null) {
            // 增加玩家死亡计数
            bossData.incrementPlayerDeathCount(player.getUniqueId());
            
            // 增加总击杀数
            bossData.incrementPlayersKilled();
            
            // 广播击杀消息
            String message = plugin.getConfig().getString("messages.player_killed_by_minion", 
                "§7溺尸守卫 §c在溺尸王的命令下击杀了 " + player.getName() + "!");
            Bukkit.broadcastMessage(message);
            
            // 检查是否需要退场（同一玩家死亡2次）
            if (bossData.shouldRetreatAfterPlayerDeath(player.getUniqueId())) {
                Drowned boss = getBossEntity(bossId);
                if (boss != null) {
                    handleBossRetreatAfterKill(boss, bossId, player.getName());
                }
            } else {
                // 第一次死亡，只发送嘲笑消息
                Drowned boss = getBossEntity(bossId);
                if (boss != null) {
                    sendTauntMessage(boss, player.getName());
                }
            }
            
            updateBoss(bossData);
        }
    }
    
    // 新增：处理三叉戟杀死玩家
    public void handleTridentKillPlayer(Player player, Trident trident) {
        String bossIdStr = trident.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "boss_trident"),
            PersistentDataType.STRING
        );
        
        if (bossIdStr == null) return;
        
        UUID bossId = UUID.fromString(bossIdStr);
        DrownedKingBoss bossData = activeBosses.get(bossId);
        
        if (bossData != null) {
            // 增加玩家死亡计数
            bossData.incrementPlayerDeathCount(player.getUniqueId());
            
            // 增加总击杀数
            bossData.incrementPlayersKilled();
            
            // 广播击杀消息
            String tridentType = trident.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "trident_type"),
                PersistentDataType.STRING
            );
            
            String message;
            if ("large".equals(tridentType)) {
                message = plugin.getConfig().getString("messages.player_killed_by_large_trident", 
                    "§4溺尸王 §c用深渊三叉戟击杀了 " + player.getName() + "!");
            } else {
                message = plugin.getConfig().getString("messages.player_killed_by_small_trident", 
                    "§4溺尸王 §c用穿刺三叉戟击杀了 " + player.getName() + "!");
            }
            Bukkit.broadcastMessage(message);
            
            // 检查是否需要退场（同一玩家死亡2次）
            if (bossData.shouldRetreatAfterPlayerDeath(player.getUniqueId())) {
                Drowned boss = getBossEntity(bossId);
                if (boss != null) {
                    handleBossRetreatAfterKill(boss, bossId, player.getName());
                }
            } else {
                // 第一次死亡，只发送嘲笑消息
                Drowned boss = getBossEntity(bossId);
                if (boss != null) {
                    sendTauntMessage(boss, player.getName());
                }
            }
            
            updateBoss(bossData);
        }
    }
    
    // 新增：获取Boss实体
    private Drowned getBossEntity(UUID bossId) {
        DrownedKingBoss bossData = activeBosses.get(bossId);
        if (bossData == null) return null;
        
        Entity entity = Bukkit.getEntity(bossData.getEntityId());
        if (entity instanceof Drowned) {
            return (Drowned) entity;
        }
        return null;
    }
    
    // 新增：发送嘲笑消息（不退场）
    private void sendTauntMessage(Drowned boss, String playerName) {
        // 嘲笑消息列表
        List<String> tauntMessages = Arrays.asList(
            "§4溺尸王 §c大笑着: §f\"渺小的" + playerName + "，这就是挑战深渊主宰的下场!\"",
            "§4溺尸王 §c嘲讽道: §f\"" + playerName + "，你的力量在深渊面前不堪一击!\"",
            "§4溺尸王 §c轻蔑地说: §f\"又一个不自量力的挑战者，" + playerName + "，你的灵魂将永沉海底!\"",
            "§4溺尸王 §c狂笑道: §f\"" + playerName + "，你的失败只会让我更加强大!\"",
            "§4溺尸王 §c嗤笑道: §f\"这就是所谓的勇士吗，" + playerName + "？太让我失望了!\""
        );
        
        Random random = new Random();
        String tauntMessage = tauntMessages.get(random.nextInt(tauntMessages.size()));
        
        // 广播嘲笑消息
        Bukkit.broadcastMessage(tauntMessage);
        
        // 播放嘲笑音效
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
    }
    
    // 修改：Boss杀死玩家后退场（只在同一玩家死亡2次时触发）
    private void handleBossRetreatAfterKill(Drowned boss, UUID bossId, String playerName) {
        // 终极嘲笑消息列表
        List<String> finalTauntMessages = Arrays.asList(
            "§4溺尸王 §c狂笑着: §f\"" + playerName + "，你已经死了两次! 深渊不再对你感兴趣!\"",
            "§4溺尸王 §c轻蔑地说: §f\"连死两次，" + playerName + "，你连作为猎物的资格都没有了!\"",
            "§4溺尸王 §c嘲讽道: §f\"" + playerName + "，你的无能让我感到无聊! 深渊不欢迎弱者!\"",
            "§4溺尸王 §c嗤笑道: §f\"两次死亡，" + playerName + "？你连让我认真的资格都没有!\"",
            "§4溺尸王 §c大笑着: §f\"" + playerName + "，你的灵魂已经腐朽! 不值得我再浪费时间!\""
        );
        
        Random random = new Random();
        String finalTauntMessage = finalTauntMessages.get(random.nextInt(finalTauntMessages.size()));
        
        // 广播终极嘲笑消息
        Bukkit.broadcastMessage(finalTauntMessage);
        Bukkit.broadcastMessage("§4⚡ 溺尸王 §c对重复的杀戮感到厌倦，准备退回深渊...");
        
        // 播放嘲笑音效
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
        
        // 延迟退场
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 播放退场特效
            Location bossLoc = boss.getLocation();
            bossLoc.getWorld().spawnParticle(Particle.EXPLOSION, bossLoc, 5);
            bossLoc.getWorld().spawnParticle(Particle.CLOUD, bossLoc, 20);
            
            // 播放退场音效
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            }
            
            // 移除Boss
            forceRemoveBoss(boss, bossId, "§4溺尸王 §c在对 " + playerName + " 的嘲笑声中退回了深渊...");
        }, 60L); // 3秒后退场
    }
    
    // 新增方法：强制移除Boss
    private void forceRemoveBoss(Drowned boss, UUID bossId, String message) {
        DrownedKingBoss bossData = activeBosses.get(bossId);
        
        if (bossData != null) {
            bossData.complete(false);
            
            // 恢复天气
            stopRainForBoss(bossId);
            
            // 清理AI任务
            cleanupBossAI(bossId);
            
            // 清理所有活跃的三叉戟
            List<Trident> tridents = activeTridents.remove(bossId);
            if (tridents != null) {
                for (Trident trident : tridents) {
                    if (trident.isValid() && !trident.isDead()) {
                        trident.remove();
                    }
                }
            }
            
            // 清理狂欢节任务
            BukkitRunnable frenzyTask = frenzyTasks.remove(bossId);
            if (frenzyTask != null) {
                frenzyTask.cancel();
            }
            
            frenzySummonCounters.remove(bossId);
            
            // 广播退场消息
            if (message != null) {
                Bukkit.broadcastMessage(message);
            }
            // 同样在 forceRemoveBoss 方法中，广播退场消息后添加：
// 停止BGM
if (plugin.getBgmPlayer() != null) {
    plugin.getBgmPlayer().stopAllBGM();
}
            // 移除Boss实体
            if (boss.isValid() && !boss.isDead()) {
                boss.remove();
            }
            
            updateBoss(bossData);
            activeBosses.remove(bossId);
        }
    }
    
    // 新增：处理守卫发射的三叉戟
    public void handleMinionTrident(Trident trident, Drowned minion) {
        String bossIdStr = minion.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "minion_of"),
            PersistentDataType.STRING
        );
        
        if (bossIdStr == null) return;
        
        UUID bossId = UUID.fromString(bossIdStr);
        
        // 标记这个三叉戟属于Boss和类型
        trident.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "boss_trident"),
            PersistentDataType.STRING,
            bossId.toString()
        );
        trident.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "trident_type"),
            PersistentDataType.STRING,
            "small"
        );
        trident.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "minion_trident"),
            PersistentDataType.BYTE,
            (byte) 1
        );
        
        // 添加到活跃三叉戟列表
        List<Trident> tridents = activeTridents.get(bossId);
        if (tridents != null) {
            tridents.add(trident);
        }
    }
    
   // 在 handleBossDeath 方法中找到 giveRewards 调用，替换为：
private void giveRewards(Drowned boss) {
    Location deathLocation = boss.getLocation();
    
    // 掉落大量经验
    deathLocation.getWorld().spawn(deathLocation, org.bukkit.entity.ExperienceOrb.class)
        .setExperience(200);
    
    // 掉落溺尸王宝藏袋
    plugin.createDrownedTreasureBag(deathLocation);
    
    // 额外掉落物
    deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.TRIDENT, 1));
    deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.NETHERITE_INGOT, 8));
    deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.DIAMOND, 25));
    deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.PRISMARINE_SHARD, 16));
    deathLocation.getWorld().dropItemNaturally(deathLocation, new ItemStack(Material.HEART_OF_THE_SEA, 1));
} 
    private void stopRainForBoss(UUID bossId) {
        World world = bossWorlds.remove(bossId);
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }
    
    private void cleanupBossAI(UUID bossId) {
        BukkitRunnable aiTask = bossAITasks.remove(bossId);
        if (aiTask != null) {
            aiTask.cancel();
        }
        shockCounters.remove(bossId);
        
        // 清理狂欢节任务
        BukkitRunnable frenzyTask = frenzyTasks.remove(bossId);
        if (frenzyTask != null) {
            frenzyTask.cancel();
        }
    }
    
    // 数据保存和加载
    private void loadActiveBosses() {
        FileConfiguration config = plugin.getDataConfig();
        
        if (!config.contains("active_bosses")) return;
        
        for (String bossIdStr : config.getConfigurationSection("active_bosses").getKeys(false)) {
            // 这里可以加载未完成的Boss
            // 由于Boss实体不会持久化，通常不需要加载
        }
    }
    
    // 修改：保存和加载玩家死亡计数
    private void saveBoss(DrownedKingBoss boss) {
        FileConfiguration config = plugin.getDataConfig();
        String path = "active_bosses." + boss.getBossId().toString();
        
        config.set(path + ".entity_id", boss.getEntityId().toString());
        config.set(path + ".spawn_location", boss.getSpawnLocation());
        config.set(path + ".spawned_by", boss.getSpawnedBy());
        config.set(path + ".spawn_time", boss.getSpawnTime());
        config.set(path + ".active", boss.isActive());
        config.set(path + ".completed", boss.isCompleted());
        config.set(path + ".success", boss.isSuccess());
        config.set(path + ".players_killed", boss.getPlayersKilled());
        config.set(path + ".surge_attack_count", boss.getSurgeAttackCount());
        config.set(path + ".in_trident_frenzy", boss.isInTridentFrenzy());
        
        // 保存玩家死亡计数
        Map<UUID, Integer> deathCounts = boss.getPlayerDeathCounts();
        for (Map.Entry<UUID, Integer> entry : deathCounts.entrySet()) {
            config.set(path + ".player_death_counts." + entry.getKey().toString(), entry.getValue());
        }
        
        plugin.saveData();
    }
    
    private void updateBoss(DrownedKingBoss boss) {
        saveBoss(boss);
    }
    
    public void saveAllBosses() {
        for (DrownedKingBoss boss : activeBosses.values()) {
            saveBoss(boss);
        }
    }
    
    public void checkActiveBosses() {
        Iterator<Map.Entry<UUID, DrownedKingBoss>> iterator = activeBosses.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, DrownedKingBoss> entry = iterator.next();
            DrownedKingBoss boss = entry.getValue();
            
            if (boss.isCompleted()) {
                iterator.remove();
                continue;
            }
            
            // 检查Boss实体是否还存在
            Entity entity = Bukkit.getEntity(boss.getEntityId());
            if (entity == null || entity.isDead()) {
                boss.complete(false);
                stopRainForBoss(boss.getBossId());
                cleanupBossAI(boss.getBossId());
                
                // 清理三叉戟
                List<Trident> tridents = activeTridents.remove(boss.getBossId());
                if (tridents != null) {
                    for (Trident trident : tridents) {
                        if (trident.isValid() && !trident.isDead()) {
                            trident.remove();
                        }
                    }
                }
                
                updateBoss(boss);
                iterator.remove();
            }
        }
    }
    
    public Map<UUID, DrownedKingBoss> getActiveBosses() {
        return activeBosses;
    }
    
    // 新玩家加入时添加到Boss血条
    public void addPlayerToAllBossBars(Player player) {
        for (DrownedKingBoss boss : activeBosses.values()) {
            boss.addPlayerToBossBar(player);
        }
    }
}
