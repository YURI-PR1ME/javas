package com.yourname.orionboss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class OrionBoss {

    private final Wither boss;
    private final OrionBossPlugin plugin;
    private BukkitRunnable behaviorTask;
    private int attackCounter = 0;
    private final Random random = new Random();
    
    
    // === 新增：连招和嘲讽管理器 ===
    private final OrionComboManager comboManager;
    private final TauntManager tauntManager;
    // 撤退相关字段
    private boolean hasRetreated = false;
    private boolean hasSummonedApostle = false;
    private double savedHealth = 0.0;
    private Location retreatLocation;
    
    // Skill cooldowns
    private long lastLavaAttack = 0;
    private long lastSkullAttack = 0;
    private long lastCloneAttack = 0;
    private long lastVoidAttack = 0;
    private long lastCrystalAttack = 0;
    private long lastRainAttack = 0;
    private long lastUltimateAttack = 0;
    
    // Cooldown constants
    private static final long LAVA_COOLDOWN = 3000;
    private static final long SKULL_COOLDOWN = 10000;
    private static final long CLONE_COOLDOWN = 10000;
    private static final long VOID_COOLDOWN = 45000;
    private static final long CRYSTAL_COOLDOWN = 30000;
    private static final long RAIN_COOLDOWN = 35000;
    private static final long ULTIMATE_COOLDOWN = 20000;
    private static final long EXECUTION_COOLDOWN = 15000;
    private long lastExecutionAttack = 0;
    
    // Track players for void attack
    private final Map<UUID, Location> playerOriginalLocations = new HashMap<>();
    private final Map<UUID, BukkitRunnable> voidTasks = new HashMap<>();
    
    // 骷髅头追踪器
    private final Map<UUID, WitherSkullTracker> activeTrackers = new HashMap<>();

    public OrionBoss(Wither boss, OrionBossPlugin plugin) {
        this.boss = boss;
        this.plugin = plugin;
        this.comboManager = new OrionComboManager(plugin);
    this.tauntManager = new TauntManager(plugin);
    }

    public void startBossBehavior() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 添加死亡检测和宝藏袋掉落
                if (!boss.isValid() || boss.isDead()) {
                    if (boss.isDead()) {
                        // Boss死亡时掉落宝藏袋
                        plugin.createTreasureBag(boss.getLocation());
                        // 广播死亡消息
                        Bukkit.broadcastMessage("§6§lORION §e§lTHE HUNTER §6§lhas been defeated!");
                        Bukkit.broadcastMessage("§aTreasure bag dropped at the death location!");
                    }
                    
                    // 清理Boss
                    cleanup();
                    plugin.getActiveBosses().remove(boss.getUniqueId());
                    plugin.removeBossBar(boss.getUniqueId());
                    cancel();
                    return;
                }

                // 检查撤退条件
                if (!hasRetreated && !hasSummonedApostle) {
                    checkRetreatCondition();
                }

                performComboAttack();
                updateBossEffects();
                plugin.updateBossBar(boss);
            }
        };
        behaviorTask.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

    private void checkRetreatCondition() {
        if (boss.getHealth() <= boss.getMaxHealth() * 0.3 && !hasRetreated && !hasSummonedApostle) {
            retreatAndSummonApostle();
        }
    }

    private void retreatAndSummonApostle() {
        // 保存状态
        savedHealth = boss.getHealth();
        retreatLocation = boss.getLocation().clone();
        hasRetreated = true;
        hasSummonedApostle = true;
        
        // 使Orion消失（无敌+隐身+静默）
        boss.setInvulnerable(true);
        boss.setInvisible(true);
        boss.setSilent(true);
        boss.setAI(false);
        boss.setGravity(false);
        
        // 传送到高空隐藏
        boss.teleport(boss.getLocation().add(0, 100, 0));
        
        // 隐藏BossBar
        plugin.hideBossBarFromAllPlayers();
        
        // 广播消息
        Bukkit.broadcastMessage("§6§lOrion retreats! His Apostle takes the field!");
        Bukkit.broadcastMessage("§cDefeat the Apostle to bring Orion back!");
        
        // 召唤使徒
        plugin.summonApostle(retreatLocation);
    }

    public void returnFromRetreat() {
        if (!hasRetreated) return;
        
        hasRetreated = false;
        
        // 恢复Orion
        boss.setInvulnerable(false);
        boss.setInvisible(false);
        boss.setSilent(false);
        boss.setAI(true);
        boss.setGravity(true);
        
        // 回到战场
        boss.teleport(retreatLocation);
        
        // 恢复保存的血量
        if (savedHealth > 0) {
            double newHealth = Math.min(savedHealth, boss.getMaxHealth());
            boss.setHealth(newHealth);
        }
        
        // 重新显示BossBar
        plugin.showBossBarToAllPlayers();
        
        // 广播消息
        Bukkit.broadcastMessage("§6§lOrion returns with renewed fury!");
        if (comboManager != null) {
        comboManager.resetComboCount();
    }
    }
// === 新增：获取管理器的方法 ===
public OrionComboManager getComboManager() {
    return comboManager;
}

public TauntManager getTauntManager() {
    return tauntManager;
}
// === 替换：新的连招攻击系统 ===
private void performComboAttack() {
    // 如果正在嘲讽，不执行攻击
    if (tauntManager.isTaunting()) {
        return;
    }
    
    Player target = findNearestPlayer();
    if (target == null) return;
    
    long currentTime = System.currentTimeMillis();
    double healthPercent = boss.getHealth() / boss.getMaxHealth();
    
    // 检查嘲讽触发
     tauntManager.checkTaunt(healthPercent);
    if (tauntManager.isTaunting()) {
        return; // 触发嘲讽，本次不攻击
    }
    // 获取下一个连招
    List<Integer> currentCombo = comboManager.getNextCombo(healthPercent);
    if (currentCombo.isEmpty()) {
        return;
    }
    
    // 获取连招中的下一个技能
    int skillId = comboManager.getNextSkillInCombo();
    if (skillId == 0) {
        return;
    }
    
    // 根据技能ID执行对应技能（需要检查冷却）
    switch (skillId) {
        case 1: // Lava Attack
            if (currentTime - lastLavaAttack > LAVA_COOLDOWN) {
                useLavaAttack(target);
                lastLavaAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 2: // Skull Attack
            if (currentTime - lastSkullAttack > SKULL_COOLDOWN) {
                useSkullAttack(target);
                lastSkullAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 3: // Clone Attack
            if (currentTime - lastCloneAttack > CLONE_COOLDOWN) {
                useCloneAttack();
                lastCloneAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 4: // Void Attack
            if (currentTime - lastVoidAttack > VOID_COOLDOWN) {
                useVoidAttack(target);
                lastVoidAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 5: // Crystal Attack
            if (currentTime - lastCrystalAttack > CRYSTAL_COOLDOWN) {
                useCrystalAttack(target);
                lastCrystalAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 6: // Rain Attack
            if (currentTime - lastRainAttack > RAIN_COOLDOWN) {
                useRainAttack(target);
                lastRainAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 7: // Ultimate Attack
            if (currentTime - lastUltimateAttack > ULTIMATE_COOLDOWN) {
                useUltimateAttack(target);
                lastUltimateAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case 8: // Execution Attack
            if (healthPercent < 0.5 && currentTime - lastExecutionAttack > EXECUTION_COOLDOWN) {
                useExecutionAttack(target);
                lastExecutionAttack = currentTime;
            } else {
                comboManager.resetCombo();
            }
            break;
            
        case -1: // 普通攻击（发射追踪骷髅头）
            // 注意：这里使用launchTrackingWitherSkull方法，但设置为非充能版
            launchTrackingWitherSkull(target.getLocation().toVector()
                .subtract(boss.getLocation().toVector()).normalize(), false);
            break;
    }
}
    private void useLavaAttack(Player target) {
        lastLavaAttack = System.currentTimeMillis();
        
        Location playerLoc = target.getLocation();
        Location lavaCenter = playerLoc.clone().subtract(0, 1, 0); // Below player
        
        // Create 2x2 lava area
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                Location lavaLoc = lavaCenter.clone().add(x, 0, z);
                if (lavaLoc.getBlock().getType().isSolid()) {
                    lavaLoc.getBlock().setType(Material.LAVA);
                }
            }
        }
        
        // Effects
        target.getWorld().playSound(playerLoc, Sound.BLOCK_LAVA_AMBIENT, 1.0f, 1.0f);
        target.getWorld().spawnParticle(Particle.LAVA, playerLoc, 20, 1, 0, 1);
        
        target.sendMessage("§cOrion turned the ground beneath you into lava!");
    }

    private void useSkullAttack(Player target) {
        lastSkullAttack = System.currentTimeMillis();
        
        // 技能启动特效
        Location bossLoc = boss.getLocation();
        boss.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.9f);
        boss.getWorld().spawnParticle(Particle.CLOUD, bossLoc, 20, 2, 2, 2);
        
        // 给目标玩家视觉警告
        if (target != null && target.isOnline()) {
            target.sendTitle("§4§lSKULL BARRAGE", "§cTracking and regular skulls incoming!", 10, 40, 10);
            target.spawnParticle(Particle.FLAME, target.getLocation(), 15, 1, 1, 1);
        }
        
        new BukkitRunnable() {
            private int rounds = 0;
            
            @Override
            public void run() {
                if (rounds >= 3 || boss.isDead()) {
                    cancel();
                    return;
                }
                
                // 检查目标是否仍然有效
                if (target == null || !target.isOnline() || target.isDead()) {
                    Player newTarget = findNearestPlayer();
                    if (newTarget == null) {
                        cancel();
                        return;
                    }
                    // 继续使用新目标
                }
                
                // Shoot 3 regular skulls and 3 tracking skulls per round
                for (int i = 0; i < 3; i++) {
                    // 常规非追踪头颅（保持原有逻辑）
                    Location eyeLocation = boss.getEyeLocation();
                    Vector direction = target.getLocation().add(0, 1, 0)
                            .subtract(eyeLocation).toVector().normalize();
                    
                    // Add some spread
                    direction.add(new Vector(
                        (random.nextDouble() - 0.5) * 0.3,
                        (random.nextDouble() - 0.5) * 0.3,
                        (random.nextDouble() - 0.5) * 0.3
                    )).normalize();
                    
                    WitherSkull skull = boss.launchProjectile(WitherSkull.class);
                    skull.setDirection(direction);
                    skull.setCharged(false);
                    
                    // 同时发射追踪头颅（仅在目标有效时）
                    if (target != null && target.isOnline() && !target.isDead()) {
                        launchTrackingWitherSkull(direction.clone(), false);
                    }
                }
                
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.8f);
                
                // 每轮攻击后显示提示信息
                if (rounds == 0) {
                    Bukkit.broadcastMessage("§4Orion is launching a barrage of wither skulls!");
                    if (target != null && target.isOnline()) {
                        target.sendMessage("§cBoth regular and tracking skulls are coming at you!");
                    }
                }
                
                rounds++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1 second between rounds
        
        if (target != null && target.isOnline()) {
            target.sendMessage("§4Orion is launching wither skulls at you!");
        }
    }

    // 专用方法：发射追踪凋零头颅（非充能版）
    private void launchTrackingWitherSkull(Vector direction, boolean isCharged) {
        if (boss == null || !boss.isValid() || boss.isDead()) {
            return;
        }
        
        Location eyeLocation = boss.getEyeLocation();
        
        // 添加一些随机散布，使追踪头颅的路径稍微不同
        direction.add(new Vector(
            (random.nextDouble() - 0.5) * 0.2,
            (random.nextDouble() - 0.5) * 0.2,
            (random.nextDouble() - 0.5) * 0.2
        )).normalize();
        
        try {
            WitherSkull skull = boss.getWorld().spawn(eyeLocation, WitherSkull.class);
            
            skull.setDirection(direction);
            skull.setCharged(isCharged);
            skull.setShooter(boss);
            skull.setVelocity(direction.multiply(1.5));
            
            // 添加追踪器
            addSkullTracker(skull);
            
            // 添加特效区分追踪头颅
            if (isCharged) {
                boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, 
                    eyeLocation, 5, 0.3, 0.3, 0.3);
            } else {
                boss.getWorld().spawnParticle(Particle.CLOUD, 
                    eyeLocation, 3, 0.2, 0.2, 0.2);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to launch tracking wither skull: " + e.getMessage());
        }
    }

    private void useCloneAttack() {
        lastCloneAttack = System.currentTimeMillis();
        
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        if (nearbyPlayers.isEmpty()) return;
        
        for (Player player : nearbyPlayers) {
            for (int i = 0; i < 3; i++) {
                spawnPlayerClone(player);
            }
        }
        
        Bukkit.broadcastMessage("§5§lOrion has summoned shadow clones of all players!");
    }

    // 修改为使用EntityUtils
    private void spawnPlayerClone(Player original) {
        Location spawnLoc = EntityUtils.findSpawnLocationAround(original.getLocation(), 5);
        Husk clone = EntityUtils.spawnPlayerClone(original, spawnLoc, "§8SHADOW OF ", plugin);
        
        // 添加爆炸攻击AI
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    cancel();
                    return;
                }
                
                Player target = findNearestPlayerTo(clone.getLocation());
                if (target != null && target.getLocation().distance(clone.getLocation()) < 3) {
                    // Create end crystal explosion
                    Location explosionLoc = clone.getLocation();
                    explosionLoc.getWorld().createExplosion(explosionLoc, 4.0f, true, true, clone);
                    target.damage(20.0, clone);
                    clone.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void useVoidAttack(Player target) {
        lastVoidAttack = System.currentTimeMillis();
        
        // Store original location
        Location originalLocation = target.getLocation().clone();
        Location bossOriginalLocation = boss.getLocation().clone();
        
        // Calculate void location (deep below in The End)
        Location voidLocation = new Location(
            target.getWorld(),
            target.getLocation().getX(),
            -70, // Deep in void
            target.getLocation().getZ()
        );
        
        // Teleport player and boss to void
        target.teleport(voidLocation);
        boss.teleport(voidLocation.add(0, 10, 0));
        
        playerOriginalLocations.put(target.getUniqueId(), originalLocation);
        
        target.sendTitle("§4§lVOID DROWNING", "§cYou've been cast into the void!", 10, 40, 10);
        
        // Schedule return after 1 second
        BukkitRunnable returnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isOnline() && !target.isDead()) {
                    target.teleport(originalLocation);
                    boss.teleport(bossOriginalLocation);
                    target.sendMessage("§6You've been pulled back from the void!");
                }
                playerOriginalLocations.remove(target.getUniqueId());
                voidTasks.remove(target.getUniqueId());
            }
        };
        
        voidTasks.put(target.getUniqueId(), returnTask);
        returnTask.runTaskLater(plugin, 60L); // 3 second
    }

    private void useCrystalAttack(Player target) {
        lastCrystalAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lOrion is preparing FINAL ANNIHILATION!");
        
        new BukkitRunnable() {
            private int crystalsPlaced = 0;
            private final int totalCrystals = 12;
            private final List<EnderCrystal> crystals = new ArrayList<>();
            
            @Override
            public void run() {
                if (crystalsPlaced >= totalCrystals || boss.isDead()) {
                    // Detonate all crystals
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (EnderCrystal crystal : crystals) {
                                if (crystal.isValid()) {
                                    crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true, boss);
                                    crystal.remove();
                                }
                            }
                        }
                    }.runTaskLater(plugin, 20L);
                    cancel();
                    return;
                }
                
                // Place crystal in random position around player
                double angle = 2 * Math.PI * crystalsPlaced / totalCrystals;
                double distance = 5 + random.nextDouble() * 5;
                double x = target.getLocation().getX() + distance * Math.cos(angle);
                double z = target.getLocation().getZ() + distance * Math.sin(angle);
                double y = EntityUtils.findGroundLevel(target.getWorld(), x, z);
                
                Location crystalLoc = new Location(target.getWorld(), x, y + 1, z);
                EnderCrystal crystal = target.getWorld().spawn(crystalLoc, EnderCrystal.class);
                crystals.add(crystal);
                
                crystalsPlaced++;
            }
        }.runTaskTimer(plugin, 0L, 5L); // Fast placement
    }

    private void useRainAttack(Player target) {
        lastRainAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lOrion calls upon the CELESTIAL WRATH!");
        
        Location center = target.getLocation();
        int duration = 100; // 5 seconds
        int attacksPerTick = 3;
        
        new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || boss.isDead()) {
                    // Spawn enhanced clones at the end
                    spawnEnhancedClones();
                    cancel();
                    return;
                }
                
                // Spawn various projectiles
                for (int i = 0; i < attacksPerTick; i++) {
                    spawnRandomProjectile(center);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnRandomProjectile(Location center) {
        double x = center.getX() + (random.nextDouble() - 0.5) * 20;
        double z = center.getZ() + (random.nextDouble() - 0.5) * 20;
        double y = center.getY() + 25;
        
        Location spawnLoc = new Location(center.getWorld(), x, y, z);
        
        int attackType = random.nextInt(5);
        switch (attackType) {
            case 0: // Arrow
                Arrow arrow = center.getWorld().spawn(spawnLoc, Arrow.class);
                arrow.setVelocity(new Vector(0, -2, 0));
                arrow.setShooter(boss);
                break;
            case 1: // Dragon Fireball
                DragonFireball fireball = center.getWorld().spawn(spawnLoc, DragonFireball.class);
                fireball.setVelocity(new Vector(0, -1, 0));
                fireball.setShooter(boss);
                break;
            case 2: // Small Fireball
                SmallFireball smallFireball = center.getWorld().spawn(spawnLoc, SmallFireball.class);
                smallFireball.setVelocity(new Vector(0, -1.5, 0));
                smallFireball.setShooter(boss);
                break;
            case 3: // Splash Potion - Instant Damage
                ThrownPotion damagePotion = center.getWorld().spawn(spawnLoc, ThrownPotion.class);
                damagePotion.setItem(new ItemStack(Material.SPLASH_POTION));
                damagePotion.setVelocity(new Vector(0, -1, 0));
                break;
            case 4: // Splash Potion - Poison
                ThrownPotion poisonPotion = center.getWorld().spawn(spawnLoc, ThrownPotion.class);
                poisonPotion.setItem(new ItemStack(Material.SPLASH_POTION));
                poisonPotion.setVelocity(new Vector(0, -1, 0));
                break;
        }
    }

    private void spawnEnhancedClones() {
        for (Player player : getNearbyPlayers(50)) {
            spawnEnhancedPlayerClone(player);
        }
    }

    private void spawnEnhancedPlayerClone(Player original) {
        Location spawnLoc = EntityUtils.findSpawnLocationAround(original.getLocation(), 3);
        Husk clone = EntityUtils.spawnEnhancedPlayerClone(original, spawnLoc, plugin);
        
        // 添加力量效果
        clone.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
        
        // Teleport AI
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    cancel();
                    return;
                }
                
                Player target = findNearestPlayerTo(clone.getLocation());
                if (target != null) {
                    if (target.getLocation().distance(clone.getLocation()) > 8) {
                        // Teleport closer to player
                        Location teleportLoc = EntityUtils.findSpawnLocationAround(target.getLocation(), 3);
                        clone.teleport(teleportLoc);
                    }
                    
                    // Attack with explosion
                    if (target.getLocation().distance(clone.getLocation()) < 4) {
                        Location explosionLoc = clone.getLocation();
                        explosionLoc.getWorld().createExplosion(explosionLoc, 5.0f, true, true, clone);
                        target.damage(25.0, clone);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Check every 2 seconds
    }

    private void useUltimateAttack(Player target) {
        lastUltimateAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lORION UNLEASHES HIS ULTIMATE POWER!");
        Bukkit.broadcastMessage("§6§lTHE VOID'S EMBRACE!");
        
        Location center = target.getLocation();
        
        // Phase 1: Replace air with lava in 4 block radius
        replaceAirWithLava(center, 4);
        
        // Phase 2: 发射20个追踪凋零头
        new BukkitRunnable() {
            @Override
            public void run() {
                launchMultipleTrackingSkulls(20);
            }
        }.runTaskLater(plugin, 40L); // 2秒后发射
        
        // Phase 3: After 3 seconds, replace lava with end crystals and explode
        new BukkitRunnable() {
            @Override
            public void run() {
                replaceLavaWithCrystals(center, 4);
                
                // Phase 4: Lightning strikes
                strikeLightningAround(center, 4);
            }
        }.runTaskLater(plugin, 60L); // 3秒后
    }

    private void launchMultipleTrackingSkulls(int count) {
        Bukkit.broadcastMessage("§4§lORION UNLEASHES TRACKING WITHER SKULLS!");
        
        Location bossLocation = boss.getLocation();
        List<Player> nearbyPlayers = getNearbyPlayers(50);
        
        if (nearbyPlayers.isEmpty()) return;
        
        // 播放特效
        boss.getWorld().playSound(bossLocation, Sound.ENTITY_WITHER_SHOOT, 3.0f, 0.6f);
        boss.getWorld().spawnParticle(Particle.CLOUD, bossLocation, 100, 3, 3, 3);
        
        // 使用正确的调度方式：分批次发射
        for (int i = 0; i < count; i++) {
            final int skullIndex = i;
            
            // 延迟发射，每5个一组，每组间隔5ticks
            int delay = (i / 5) * 5 + (i % 5);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (boss.isDead() || !boss.isValid()) {
                        cancel();
                        return;
                    }
                    
                    // 随机选择目标玩家或最近玩家
                    Player targetPlayer;
                    if (nearbyPlayers.size() > 1 && Math.random() > 0.7) {
                        // 70%概率选择随机玩家
                        targetPlayer = nearbyPlayers.get(new Random().nextInt(nearbyPlayers.size()));
                    } else {
                        // 30%概率选择最近玩家
                        targetPlayer = findNearestPlayer();
                    }
                    
                    if (targetPlayer == null || !targetPlayer.isOnline()) {
                        return;
                    }
                    
                    // 计算Boss周围的随机发射位置（半径3-8格）
                    double angle = Math.random() * 2 * Math.PI;
                    double radius = 3 + Math.random() * 5; // 3-8格半径
                    double heightOffset = 1 + Math.random() * 3; // 1-4格高度偏移
                    
                    // 计算相对于Boss的随机位置
                    Location spawnLocation = bossLocation.clone().add(
                        Math.cos(angle) * radius,
                        heightOffset,
                        Math.sin(angle) * radius
                    );
                    
                    // 确保生成位置是安全的（不是方块内部）
                    while (spawnLocation.getBlock().getType() != Material.AIR && 
                           spawnLocation.getBlock().getType() != Material.CAVE_AIR) {
                        spawnLocation.add(0, 1, 0);
                        if (spawnLocation.getY() - bossLocation.getY() > 10) {
                            // 如果太高，回到Boss位置附近
                            spawnLocation = bossLocation.clone().add(
                                Math.cos(angle) * radius,
                                heightOffset,
                                Math.sin(angle) * radius
                            );
                            break;
                        }
                    }
                    
                    // 计算发射方向（指向目标玩家，但稍微随机化）
                    Vector baseDirection = targetPlayer.getLocation()
                        .add(0, 1, 0)
                        .subtract(spawnLocation)
                        .toVector()
                        .normalize();
                    
                    // 添加随机散布，使每个头颅的路径都不同
                    double spread = 0.4;
                    Vector spreadVector = new Vector(
                        (Math.random() - 0.5) * spread,
                        (Math.random() - 0.5) * spread * 0.3,
                        (Math.random() - 0.5) * spread
                    );
                    
                    Vector finalDirection = baseDirection.add(spreadVector).normalize();
                    
                    try {
                        // 直接从计算出的位置生成追踪头颅
                        WitherSkull skull = boss.getWorld().spawn(spawnLocation, WitherSkull.class);
                        
                        skull.setDirection(finalDirection);
                        skull.setCharged(skullIndex % 3 == 0); // 每3个有一个是充能的
                        skull.setShooter(boss);
                        skull.setVelocity(finalDirection.multiply(1.2 + Math.random() * 0.3));
                        
                        // 添加追踪器
                        addSkullTracker(skull);
                        
                        // 添加特效区分追踪头颅
                        if (skullIndex % 3 == 0) {
                            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, 
                                spawnLocation, 3, 0.2, 0.2, 0.2);
                        } else {
                            boss.getWorld().spawnParticle(Particle.CLOUD, 
                                spawnLocation, 2, 0.1, 0.1, 0.1);
                        }
                        
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to launch tracking wither skull: " + e.getMessage());
                    }
                }
            }.runTaskLater(plugin, delay);
        }
        
        // 给所有玩家警告
        for (Player player : getNearbyPlayers(100)) {
            player.sendTitle("§4§lTRACKING SKULLS", "§c" + count + " skulls are chasing you!", 10, 60, 10);
            player.sendMessage("§4§lWARNING: Orion has launched " + count + " tracking wither skulls!");
        }
    }

    private void replaceAirWithLava(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.AIR) {
                        checkLoc.getBlock().setType(Material.LAVA);
                    }
                }
            }
        }
        
        center.getWorld().playSound(center, Sound.BLOCK_LAVA_AMBIENT, 2.0f, 0.8f);
    }

    private void useExecutionAttack(Player target) {
        lastExecutionAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lORION UNLEASHES EXECUTION! §c§lDRAGONS FROM ABYSS!");
        
        Location bossLoc = boss.getLocation();
        
        // 召唤3条龙从不同方向攻击
        summonExecutionDragon(target, bossLoc.clone().add(10, 8, 0)); // 右侧
        summonExecutionDragon(target, bossLoc.clone().add(-10, 8, 0)); // 左侧
        summonExecutionDragon(target, bossLoc.clone().add(0, 12, 10)); // 前方
        
        // 技能特效
        boss.getWorld().playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 3.0f, 0.7f);
        boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, bossLoc, 100, 5, 5, 5);
        
        // 对所有附近玩家显示标题
        for (Player player : getNearbyPlayers(50)) {
            player.sendTitle("§4§lEXECUTION", "§cDragons are coming!", 10, 40, 10);
        }
    }

    // 添加头颅追踪器方法
    private void addSkullTracker(WitherSkull skull) {
        WitherSkullTracker tracker = new WitherSkullTracker(skull, this);
        activeTrackers.put(skull.getUniqueId(), tracker);
        tracker.startTracking();
    }

    // 移除头颅追踪器方法
    public void removeSkullTracker(UUID skullId) {
        WitherSkullTracker tracker = activeTrackers.remove(skullId);
        if (tracker != null) {
            tracker.cancel();
        }
    }

    private void summonExecutionDragon(Player target, Location spawnLocation) {
        ExecutionDragon executionDragon = new ExecutionDragon(plugin, target, 40.0);
        executionDragon.spawn(spawnLocation);
        
        // 生成时的局部特效
        spawnLocation.getWorld().playSound(spawnLocation, 
            Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.8f);
        spawnLocation.getWorld().spawnParticle(Particle.PORTAL, 
            spawnLocation, 30, 2, 2, 2);
        spawnLocation.getWorld().spawnParticle(Particle.FLAME, 
            spawnLocation, 20, 1, 1, 1);
    }

    private void replaceLavaWithCrystals(Location center, int radius) {
        List<EnderCrystal> crystals = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.LAVA) {
                        checkLoc.getBlock().setType(Material.AIR);
                        Location crystalLoc = checkLoc.clone().add(0, 1, 0);
                        EnderCrystal crystal = center.getWorld().spawn(crystalLoc, EnderCrystal.class);
                        crystals.add(crystal);
                    }
                }
            }
        }
        
        // Explode all crystals after a brief moment
        new BukkitRunnable() {
            @Override
            public void run() {
                for (EnderCrystal crystal : crystals) {
                    if (crystal.isValid()) {
                        crystal.getWorld().createExplosion(crystal.getLocation(), 8.0f, true, true, boss);
                        crystal.remove();
                    }
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    private void strikeLightningAround(Location center, int radius) {
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location strikeLoc = new Location(center.getWorld(), x, center.getY(), z);
            
            center.getWorld().strikeLightning(strikeLoc);
        }
    }

    private void updateBossEffects() {
        // Constant boss effects
        boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 10, 2, 2, 2);
        
        // Health-based effects
        double healthPercent = boss.getHealth() / boss.getMaxHealth();
        if (healthPercent < 0.3) {
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 20, 3, 3, 3);
        }
    }

    private Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : boss.getWorld().getPlayers()) {
            if (!EntityUtils.isValidTarget(player)) continue;
            
            double distance = player.getLocation().distance(boss.getLocation());
            if (distance < nearestDistance && distance <= 50) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    private Player findNearestPlayerTo(Location location) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : location.getWorld().getPlayers()) {
            if (!EntityUtils.isValidTarget(player)) continue;
            
            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    private List<Player> getNearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (EntityUtils.isValidTarget(player) && player.getLocation().distance(boss.getLocation()) <= radius) {
                players.add(player);
            }
        }
        return players;
    }

    public void cleanup() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        
        // Cancel all void tasks
        for (BukkitRunnable task : voidTasks.values()) {
            task.cancel();
        }
        voidTasks.clear();
        playerOriginalLocations.clear();
        
        // 清理所有追踪头颅
        for (WitherSkullTracker tracker : activeTrackers.values()) {
            tracker.cancel();
        }
        activeTrackers.clear();
        // === 新增：清理连招和嘲讽管理器 ===
    if (tauntManager != null) {
        tauntManager.cleanup();
    }
    if (comboManager != null) {
        comboManager.resetCombo();
        comboManager.resetComboCount();
    }
    
        // 清理撤退相关字段
        hasRetreated = false;
        hasSummonedApostle = false;
        savedHealth = 0.0;
        retreatLocation = null;
    }

    public Wither getBoss() {
        return boss;
    }
    
    public OrionBossPlugin getPlugin() {
        return plugin;
    }
}
