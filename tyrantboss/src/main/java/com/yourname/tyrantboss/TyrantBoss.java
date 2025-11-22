package com.yourname.tyrantboss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TyrantBoss {

    private final WitherSkeleton boss;
    private final TyrantBossPlugin plugin;
    private int attackCount = 0;
    private boolean isInSecondPhase = false;
    private boolean isInFinalPhase = false;
    private boolean isUsingUltimate = false;
    private boolean isEnraged = false; // 新增：暴怒状态
    private boolean isInvulnerable = false; // 新增：无敌状态
    private final Set<UUID> attackedPlayers = new HashSet<>();
    private BukkitRunnable behaviorTask;
    private final Map<UUID, Integer> playerAttackCounts = new HashMap<>();
    
    // 攻击间隔计数器
    private int attackIntervalCounter = 0;
    private static final int SKULL_ATTACK_INTERVAL_PHASE1 = 8; // 一阶段每8次攻击发射骷髅头
    private static final int SKULL_ATTACK_INTERVAL_PHASE2 = 4; // 二阶段每4次攻击发射骷髅头
    
    // 暴怒机制
    private long lastSuccessfulAttackTime = 0;
    private static final long RAGE_COOLDOWN = 5000; // 5秒未攻击触发暴怒
    private Player currentTarget = null;
    
    // 骷髅头追踪器
    private final Map<UUID, WitherSkullTracker> activeTrackers = new HashMap<>();

    // 技能冷却 - 修改：二阶段传送冷却时间更短
    private long lastTeleportTime = 0;
    private long lastFireballTime = 0;
    private long lastSummonTime = 0;
    private long lastRapidDisplacementTime = 0;
    private static final long TELEPORT_COOLDOWN_PHASE1 = 10000; // 一阶段10秒
    private static final long TELEPORT_COOLDOWN_PHASE2 = 5000;  // 二阶段5秒，更频繁
    private static final long FIREBALL_COOLDOWN = 8000;
    private static final long SUMMON_COOLDOWN = 15000;
    private static final long RAPID_DISPLACEMENT_COOLDOWN_NORMAL = 15000;
    private static final long RAPID_DISPLACEMENT_COOLDOWN_PHASE2 = 20000;

    public TyrantBoss(WitherSkeleton boss, TyrantBossPlugin plugin) {
        this.boss = boss;
        this.plugin = plugin;
        this.lastSuccessfulAttackTime = System.currentTimeMillis();
        
        // 添加免疫摔落伤害的效果
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
    }

    public void startBossBehavior() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                checkPhaseTransition();
                checkRageCondition();
                performRandomAbility();
                updateBossEffects();
            }
        };
        behaviorTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void checkPhaseTransition() {
        double healthPercent = boss.getHealth() / boss.getMaxHealth();
        
        if (!isInSecondPhase && healthPercent <= 0.3) {
            enterSecondPhase();
        }
        
        if (!isInFinalPhase && healthPercent <= 0.1) {
            enterFinalPhase();
        }
    }

    // 新增：检查暴怒条件
    private void checkRageCondition() {
        if (isUsingUltimate || isEnraged || isInvulnerable) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSuccessfulAttackTime > RAGE_COOLDOWN) {
            triggerEnrage();
        }
    }

    // 新增：触发暴怒
    private void triggerEnrage() {
        isEnraged = true;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);
        boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation(), 50, 3, 3, 3);
        
        Bukkit.broadcastMessage("§4§l暴君暴怒了! 5秒内未攻击到目标，释放全方位毁灭攻击!");
        
        // 向所有方向发射可破坏方块的凋零骷髅头
        useOmnidirectionalSkullAttack();
        
        // 瞬移到玩家背后进行暴击
        Player target = findNearestPlayer();
        if (target != null) {
            useRageTeleportAndCrit(target);
        }
        
        // 5秒后解除暴怒状态
        new BukkitRunnable() {
            @Override
            public void run() {
                isEnraged = false;
                lastSuccessfulAttackTime = System.currentTimeMillis(); // 重置计时器
            }
        }.runTaskLater(plugin, 100L); // 5秒后
    }

    // 新增：全方位骷髅头攻击
    private void useOmnidirectionalSkullAttack() {
        Location bossLocation = boss.getLocation();
        
        // 8个方向发射可破坏方块的爆炸骷髅头
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            Vector direction = new Vector(Math.cos(angle), 0.1, Math.sin(angle)).normalize();
            
            // 修复：使用自定义方法发射凋零骷髅头，避免伤害暴君自己
            launchWitherSkull(direction, true);
        }
        
        boss.getWorld().playSound(bossLocation, Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.8f);
    }

    // 新增：安全发射凋零骷髅头的方法
    private void launchWitherSkull(Vector direction, boolean isCharged) {
        Location eyeLocation = boss.getEyeLocation();
        WitherSkull skull = boss.getWorld().spawn(eyeLocation, WitherSkull.class);
        
        // 设置骷髅头属性
        skull.setDirection(direction);
        skull.setCharged(isCharged);
        skull.setShooter(boss);
        skull.setVelocity(direction.multiply(1.5));
        
        // 添加追踪器
        addSkullTracker(skull);
    }

    // 新增：暴怒传送和暴击
    private void useRageTeleportAndCrit(Player target) {
        Location behind = calculatePositionBehind(target);
        
        // 传送效果
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 30);
        boss.teleport(behind);
        boss.getWorld().spawnParticle(Particle.FLAME, behind, 30);
        
        // 暴击攻击（双倍伤害）
        double originalDamage = Objects.requireNonNull(boss.getAttribute(Attribute.ATTACK_DAMAGE)).getValue();
        double critDamage = originalDamage * 2;
        
        target.damage(critDamage, boss);
        target.sendTitle("§4§l暴君暴击!", "§c你受到了双倍伤害!", 10, 40, 10);
        
        // 击退效果
        Vector knockback = target.getLocation().getDirection().multiply(-2).setY(1);
        target.setVelocity(knockback);
        
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
    }

    private void enterSecondPhase() {
        isInSecondPhase = true;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 100, 3, 3, 3);
        
        // 增加移动速度和攻击力
        Objects.requireNonNull(boss.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.5);
        
        Bukkit.broadcastMessage("§c§l暴君进入狂暴阶段! 机动性提升! 传送和骷髅头攻击更加频繁!");
    }

    private void enterFinalPhase() {
        isInFinalPhase = true;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.8f);
        boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, boss.getLocation(), 200, 4, 4, 4);
        
        // 使用终极技能
        useUltimateAbility();
        
        Bukkit.broadcastMessage("§4§l暴君进入终焉阶段! 释放最终毁灭技能!");
    }

    private void performRandomAbility() {
        if (isUsingUltimate || isEnraged || isInvulnerable) return;

        long currentTime = System.currentTimeMillis();
        Player target = findNearestPlayer();

        if (target == null) return;

        // 随机选择技能
        int random = new Random().nextInt(100);

        // 修改：二阶段传送技能使用更短的冷却时间
        long teleportCooldown = isInSecondPhase ? TELEPORT_COOLDOWN_PHASE2 : TELEPORT_COOLDOWN_PHASE1;
        
        // 修改：二阶段传送技能触发概率更高
        int teleportChance = isInSecondPhase ? 40 : 25; // 二阶段40%概率，一阶段25%概率
        
        if (random < teleportChance && currentTime - lastTeleportTime > teleportCooldown) {
            useTeleportAbility(target);
        } else if (random < 50 && currentTime - lastFireballTime > FIREBALL_COOLDOWN) {
            useFireballAbility(target);
        } else if (random < 75 && currentTime - lastSummonTime > SUMMON_COOLDOWN) {
            useSummonAbility();
        } else if (isInSecondPhase && random < 90) {
            // 二阶段快速位移技能
            long rapidDisplacementCooldown = isInSecondPhase ? 
                RAPID_DISPLACEMENT_COOLDOWN_PHASE2 : RAPID_DISPLACEMENT_COOLDOWN_NORMAL;
            
            if (currentTime - lastRapidDisplacementTime > rapidDisplacementCooldown) {
                useRapidDisplacementAbility();
            }
        }
    }

    private void useTeleportAbility(Player target) {
        lastTeleportTime = System.currentTimeMillis();
        
        Location behindPlayer = calculatePositionBehind(target);
        
        // 传送效果
        boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 50);
        boss.teleport(behindPlayer);
        boss.getWorld().spawnParticle(Particle.PORTAL, behindPlayer, 50);
        
        // 击飞玩家
        target.setVelocity(new Vector(0, 1.5, 0));
        target.sendTitle("§c§l暴君闪现!", "§e你被击飞了!", 10, 40, 10);
        
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        // 修复：传送拉近后的爆炸攻击不会伤害暴君自己
        triggerSafeExplosion(behindPlayer);
    }

    // 新增：安全的爆炸方法，不会伤害暴君自己
    private void triggerSafeExplosion(Location location) {
        // 创建爆炸但不伤害暴君自己
        boss.getWorld().createExplosion(location, 2.0f, false, false, boss);
        
        boss.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 1, 1, 1);
        boss.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
        
        // 对周围玩家造成额外伤害
        for (Player player : getNearbyPlayers(3)) {
            // 只对有效目标造成伤害
            if (isValidTarget(player)) {
                player.damage(8.0, boss);
                player.sendMessage("§c暴君传送后引发了爆炸!");
            }
        }
    }

    private void useFireballAbility(Player target) {
        lastFireballTime = System.currentTimeMillis();
        
        Location eyeLocation = boss.getEyeLocation();
        Vector direction = target.getLocation().subtract(eyeLocation).toVector().normalize();
        
        // 修复：使用安全方法发射火球
        launchSafeFireball(direction);
        
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0f, 1.0f);
    }

    // 新增：安全发射火球的方法
    private void launchSafeFireball(Vector direction) {
        Location eyeLocation = boss.getEyeLocation();
        Fireball fireball = boss.getWorld().spawn(eyeLocation, Fireball.class);
        
        fireball.setDirection(direction);
        fireball.setYield(2.0f);
        fireball.setIsIncendiary(true);
        fireball.setCustomName("TyrantFireball");
        fireball.setShooter(boss);
    }

    // 修改：去除蛮兵，改为骷髅和凋零骷髅
    private void useSummonAbility() {
        lastSummonTime = System.currentTimeMillis();
        
        int skeletonCount = 6 + new Random().nextInt(4); // 6-9个骷髅
        int witherSkeletonCount = 4 + new Random().nextInt(3); // 4-6个凋零骷髅
        
        for (int i = 0; i < skeletonCount; i++) {
            Location spawnLoc = findSpawnLocationAroundBoss(5);
            Skeleton skeleton = (Skeleton) boss.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
            skeleton.setCustomName("§7暴君的骷髅射手");
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
        }
        
        for (int i = 0; i < witherSkeletonCount; i++) {
            Location spawnLoc = findSpawnLocationAroundBoss(5);
            WitherSkeleton witherSkeleton = (WitherSkeleton) boss.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            witherSkeleton.setCustomName("§8暴君的凋零护卫");
            witherSkeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            witherSkeleton.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            witherSkeleton.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        }
        
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.8f);
        Bukkit.broadcastMessage("§6暴君召唤了骷髅军团! 小心骷髅射手和凋零护卫!");
    }

    private void useRapidDisplacementAbility() {
        // 更新快速位移冷却时间
        lastRapidDisplacementTime = System.currentTimeMillis();
        
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        if (nearbyPlayers.isEmpty()) return;

        new BukkitRunnable() {
            private int attacksLeft = 3 * nearbyPlayers.size();
            private int currentPlayerIndex = 0;

            @Override
            public void run() {
                if (attacksLeft <= 0 || nearbyPlayers.isEmpty() || boss.isDead()) {
                    cancel();
                    return;
                }

                Player target = nearbyPlayers.get(currentPlayerIndex);
                if (!target.isOnline() || target.isDead() || !isValidTarget(target)) {
                    currentPlayerIndex = (currentPlayerIndex + 1) % nearbyPlayers.size();
                    return;
                }

                // 传送到玩家背后
                Location behind = calculatePositionBehind(target);
                boss.teleport(behind);
                
                // 攻击玩家
                boss.attack(target);
                
                // 粒子效果
                boss.getWorld().spawnParticle(Particle.CRIT, target.getLocation(), 10);
                
                attacksLeft--;
                currentPlayerIndex = (currentPlayerIndex + 1) % nearbyPlayers.size();
                
                if (attacksLeft <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // 增强：发射追踪凋零骷髅头攻击
    private void useWitherSkullAttack(Player target) {
        Location eyeLocation = boss.getEyeLocation();
        
        // 发射3个爆炸骷髅头和1个不爆炸骷髅头
        for (int i = 0; i < 4; i++) {
            boolean isCharged = (i < 3); // 前3个是爆炸的，最后1个不爆炸
            
            // 计算正确的方向（指向玩家）
            Vector direction;
            if (target != null) {
                direction = target.getLocation().add(0, 1, 0).subtract(eyeLocation).toVector().normalize();
                
                // 添加轻微散射
                if (i > 0) {
                    double spreadAmount = 0.2;
                    direction.add(new Vector(
                        (Math.random() - 0.5) * spreadAmount,
                        (Math.random() - 0.5) * spreadAmount,
                        (Math.random() - 0.5) * spreadAmount
                    )).normalize();
                }
            } else {
                // 如果没有目标，使用默认方向
                direction = boss.getLocation().getDirection();
            }
            
            // 修复：使用安全方法发射凋零骷髅头
            launchWitherSkull(direction, isCharged);
            
            // 粒子效果
            if (isCharged) {
                boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eyeLocation, 5, 0.5, 0.5, 0.5);
            } else {
                boss.getWorld().spawnParticle(Particle.SMOKE, eyeLocation, 5, 0.5, 0.5, 0.5);
            }
        }
        
        // 音效
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.8f);
        
        // 提示信息
        if (target != null) {
            target.sendMessage("§4暴君向你发射了追踪凋零骷髅头!");
        }
    }

    // 新增：添加骷髅头追踪器
    private void addSkullTracker(WitherSkull skull) {
        WitherSkullTracker tracker = new WitherSkullTracker(skull, this);
        activeTrackers.put(skull.getUniqueId(), tracker);
        tracker.startTracking();
    }

    // 新增：移除骷髅头追踪器
    public void removeSkullTracker(UUID skullId) {
        activeTrackers.remove(skullId);
    }

    private void useUltimateAbility() {
        isUsingUltimate = true;
        isInvulnerable = true; // 开启无敌状态
        
        Location bossLocation = boss.getLocation();
        
        // 广播无敌状态
        Bukkit.broadcastMessage("§4§l暴君释放终极技能，进入无敌状态!");
        
        // 清除上方方块
        clearBlocksAboveBoss(bossLocation);
        
        // 召唤恶魂火弹雨 - 改进为覆盖范围的均匀轰炸
        new BukkitRunnable() {
            private int ticks = 0;
            private final int duration = 200; // 5秒

            @Override
            public void run() {
                if (ticks >= duration || boss.isDead()) {
                    isUsingUltimate = false;
                    isInvulnerable = false; // 关闭无敌状态
                    Bukkit.broadcastMessage("§6§l暴君的无敌状态结束!");
                    cancel();
                    return;
                }

                spawnGhastFireballRain(bossLocation);
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void clearBlocksAboveBoss(Location location) {
        int radius = 15;
        int height = 20;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= height; y++) {
                    Location blockLoc = location.clone().add(x, y, z);
                    if (blockLoc.getBlock().getType() != Material.BEDROCK && 
                        blockLoc.getBlock().getType() != Material.AIR) {
                        blockLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
        
        boss.getWorld().playSound(location, Sound.ENTITY_WITHER_BREAK_BLOCK, 3.0f, 0.8f);
    }

    // 改进：均匀轰炸整个区域，而不仅仅是圆上的点
    private void spawnGhastFireballRain(Location center) {
        int fireballCount = 20; // 增加火球数量以获得更好的覆盖
        double radius = 15.0; // 扩大轰炸半径
        
        Random random = new Random();
        
        for (int i = 0; i < fireballCount; i++) {
            // 使用均匀分布在整个圆形区域内生成火球
            double distance = radius * Math.sqrt(random.nextDouble()); // 均匀分布在圆形区域内
            double angle = 2 * Math.PI * random.nextDouble();
            
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            double y = center.getY() + 25 + random.nextDouble() * 10; // 增加高度范围
            
            Location spawnLoc = new Location(center.getWorld(), x, y, z);
            
            // 修复：使用安全方法发射火球
            Fireball fireball = center.getWorld().spawn(spawnLoc, Fireball.class);
            fireball.setDirection(new Vector(0, -1, 0));
            fireball.setYield(4.0f); // 增加爆炸威力
            fireball.setIsIncendiary(true);
            fireball.setCustomName("TyrantGhastFireball");
            fireball.setShooter(boss); // 设置发射者为暴君，这样爆炸不会伤害暴君自己
            
            // 添加粒子效果显示轰炸位置
            center.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 5, 0.5, 0.5, 0.5);
        }
        
        // 播放轰炸音效
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 3.0f, 0.5f);
    }

    public void onDamage(EntityDamageByEntityEvent event) {
        // 检查无敌状态
        if (isInvulnerable) {
            event.setCancelled(true);
            return;
        }
        
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            
            // 只记录有效目标的伤害
            if (isValidTarget(damager)) {
                // 近战攻击计数 - 减少攻击频率（5次触发爆炸）
                if (event.getCause().toString().contains("ENTITY_ATTACK")) {
                    attackCount++;
                    
                    if (attackCount >= 5) { // 从3次增加到5次
                        triggerExplosion();
                        attackCount = 0;
                    }
                    
                    // 一阶段和二阶段都会发射骷髅头，但频率不同
                    attackIntervalCounter++;
                    
                    int skullInterval = isInSecondPhase ? SKULL_ATTACK_INTERVAL_PHASE2 : SKULL_ATTACK_INTERVAL_PHASE1;
                    
                    if (attackIntervalCounter >= skullInterval) {
                        Player target = findNearestPlayer();
                        if (target != null) {
                            useWitherSkullAttack(target);
                        }
                        attackIntervalCounter = 0;
                    }
                }
            }
        }
    }

    // 新增：检查玩家是否为有效目标
    private boolean isValidTarget(Player player) {
        return player != null && 
               player.isOnline() && 
               !player.isDead() && 
               player.getGameMode() == GameMode.SURVIVAL;
    }

    // 新增：记录玩家造成的伤害
    public void recordPlayerDamage(Player player) {
        if (isValidTarget(player)) {
            lastSuccessfulAttackTime = System.currentTimeMillis(); // 重置暴怒计时器
            currentTarget = player; // 更新当前目标
        }
    }

    private void triggerExplosion() {
        Location loc = boss.getLocation();
        
        // 修复：爆炸不会伤害暴君自己，将暴君设为爆炸源
        boss.getWorld().createExplosion(loc, 3.0f, false, false, boss);
        
        boss.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 2, 2, 2);
        boss.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        
        // 对周围玩家造成额外伤害
        for (Player player : getNearbyPlayers(5)) {
            // 只对有效目标造成伤害
            if (isValidTarget(player)) {
                player.damage(10.0, boss);
                player.sendMessage("§c暴君的镐子引发了爆炸!");
            }
        }
    }

    private void updateBossEffects() {
        if (isInSecondPhase) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2));
        }
        
        if (isInFinalPhase) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 2));
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 5, 1, 1, 1);
        }
        
        if (isEnraged) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2));
            boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation(), 10, 1, 1, 1);
        }
        
        // 持续免疫摔落伤害
        if (!boss.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
        }
        
        // 无敌状态特效
        if (isInvulnerable) {
            boss.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, boss.getLocation(), 20, 2, 2, 2);
            boss.getWorld().spawnParticle(Particle.END_ROD, boss.getLocation(), 10, 1, 1, 1);
        }
    }

    public Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : boss.getWorld().getPlayers()) {
            if (!isValidTarget(player)) continue;
            
            double distance = player.getLocation().distance(boss.getLocation());
            if (distance < nearestDistance && distance <= 50) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    public List<Player> getNearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (isValidTarget(player) && player.getLocation().distance(boss.getLocation()) <= radius) {
                players.add(player);
            }
        }
        return players;
    }

    private Location calculatePositionBehind(Entity target) {
        Vector direction = target.getLocation().getDirection();
        Vector behind = direction.multiply(-2).normalize();
        return target.getLocation().add(behind).add(0, 1, 0);
    }

    private Location findSpawnLocationAroundBoss(double radius) {
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = boss.getLocation().getX() + radius * Math.cos(angle);
        double z = boss.getLocation().getZ() + radius * Math.sin(angle);
        
        Location spawnLoc = new Location(boss.getWorld(), x, boss.getLocation().getY(), z);
        return spawnLoc;
    }

    public void cleanup() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        
        // 清理所有追踪器
        for (WitherSkullTracker tracker : activeTrackers.values()) {
            tracker.cancel();
        }
        activeTrackers.clear();
        
        // 确保无敌状态被清除
        isInvulnerable = false;
    }

    public WitherSkeleton getBoss() {
        return boss;
    }
    
    public boolean isEnraged() {
        return isEnraged;
    }
    
    public boolean isInvulnerable() {
        return isInvulnerable;
    }
    
    public TyrantBossPlugin getPlugin() {
        return plugin;
    }
}
