package com.yourname.tyrantboss;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TyrantGhostBoss {

    private final Ghast ghost;
    private final TyrantBossPlugin plugin;
    private BukkitRunnable behaviorTask;
    
    // 技能冷却
    private long lastFireballTime = 0;
    private long lastDragonBreathTime = 0;
    private long lastBombardmentTime = 0;
    private static final long FIREBALL_COOLDOWN = 4000; // 4秒
    private static final long DRAGON_BREATH_COOLDOWN = 8000; // 8秒
    private static final long BOMBARDMENT_COOLDOWN = 15000; // 15秒

    public TyrantGhostBoss(Ghast ghost, TyrantBossPlugin plugin) {
        this.ghost = ghost;
        this.plugin = plugin;
    }

    public void startBossBehavior() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!ghost.isValid() || ghost.isDead()) {
                    cancel();
                    return;
                }

                performRandomAbility();
                updateBossEffects();
            }
        };
        behaviorTask.runTaskTimer(plugin, 0L, 20L);
    
    // === 新增：播放残魂阶段BGM ===
    if (plugin.getBgmPlayer() != null) {
        plugin.getBgmPlayer().updateBossPhase(TyrantBGMPlayer.BossPhase.GHOST_PHASE);
    }}
    private void performRandomAbility() {
        long currentTime = System.currentTimeMillis();
        Player target = findNearestPlayer();

        if (target == null) return;

        // 随机选择技能
        int random = new Random().nextInt(100);

        if (random < 40 && currentTime - lastFireballTime > FIREBALL_COOLDOWN) {
            useFireballAbility(target);
        } else if (random < 70 && currentTime - lastDragonBreathTime > DRAGON_BREATH_COOLDOWN) {
            useDragonBreathAbility(target);
        } else if (random < 90 && currentTime - lastBombardmentTime > BOMBARDMENT_COOLDOWN) {
            useBombardmentAbility();
        }
    }

    private void useFireballAbility(Player target) {
        lastFireballTime = System.currentTimeMillis();
        
        // 恶魂本身会发射火球，我们只需要设置目标
        Vector direction = target.getLocation().add(0, 1, 0).subtract(ghost.getLocation()).toVector().normalize();
        
        // 发射火球
        Fireball fireball = ghost.launchProjectile(Fireball.class);
        fireball.setDirection(direction);
        fireball.setYield(4.0f); // 更大的爆炸威力
        fireball.setIsIncendiary(true);
        fireball.setCustomName("TyrantGhostFireball");
        
        // 音效和粒子
        ghost.getWorld().playSound(ghost.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.8f);
        ghost.getWorld().spawnParticle(Particle.FLAME, ghost.getLocation(), 20, 1, 1, 1);
        
        // 提示信息
        target.sendMessage("§5暴君残魂向你发射了强力火球!");
    }

    private void useDragonBreathAbility(Player target) {
        lastDragonBreathTime = System.currentTimeMillis();
        
        Location eyeLocation = ghost.getEyeLocation();
        Vector direction = target.getLocation().add(0, 1, 0).subtract(eyeLocation).toVector().normalize();
        
        // 发射龙息火球
        DragonFireball dragonFireball = ghost.launchProjectile(DragonFireball.class);
        dragonFireball.setDirection(direction);
        dragonFireball.setCustomName("TyrantGhostDragonBreath");
        
        // 音效和粒子
        ghost.getWorld().playSound(ghost.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 2.0f, 0.7f);
        ghost.getWorld().spawnParticle(Particle.DRAGON_BREATH, eyeLocation, 30, 1, 1, 1);
        
        // 提示信息
        target.sendMessage("§5暴君残魂向你吐出了致命龙息!");
    }

    private void useBombardmentAbility() {
        lastBombardmentTime = System.currentTimeMillis();
        
        Location ghostLocation = ghost.getLocation();
        
        // 广播消息
        Bukkit.broadcastMessage("§5§l暴君残魂开始轰炸! 寻找掩护!");
        
        // 开始轰炸
        new BukkitRunnable() {
            private int ticks = 0;
            private final int duration = 60; // 3秒

            @Override
            public void run() {
                if (ticks >= duration || ghost.isDead()) {
                    cancel();
                    return;
                }

                spawnBombardment(ghostLocation);
                ticks += 10; // 每0.5秒一次轰炸
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void spawnBombardment(Location center) {
        int fireballCount = 8;
        double radius = 20.0;
        
        Random random = new Random();
        
        for (int i = 0; i < fireballCount; i++) {
            // 随机分布在圆形区域内
            double distance = radius * Math.sqrt(random.nextDouble());
            double angle = 2 * Math.PI * random.nextDouble();
            
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            double y = center.getY() + 30 + random.nextDouble() * 10;
            
            Location spawnLoc = new Location(center.getWorld(), x, y, z);
            
            // 发射火球
            Fireball fireball = center.getWorld().spawn(spawnLoc, Fireball.class);
            fireball.setDirection(new Vector(0, -1, 0));
            fireball.setYield(3.0f);
            fireball.setIsIncendiary(true);
            fireball.setCustomName("TyrantGhostBombardment");
            fireball.setShooter(ghost);
            
            // 粒子效果
            center.getWorld().spawnParticle(Particle.SMOKE, spawnLoc, 5, 0.5, 0.5, 0.5);
        }
        
        // 轰炸音效
        center.getWorld().playSound(center, Sound.ENTITY_GHAST_WARN, 3.0f, 0.5f);
    }

    public void onDamage(EntityDamageByEntityEvent event) {
        // 可以在这里添加暴君残魂受到伤害时的特殊行为
    }

    private void updateBossEffects() {
        // 持续免疫摔落伤害
        if (!ghost.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            ghost.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
        }
        
        // 添加一些视觉效果
        ghost.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, ghost.getLocation(), 5, 2, 2, 2);
    }

    public Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : ghost.getWorld().getPlayers()) {
            if (!isValidTarget(player)) continue;
            
            double distance = player.getLocation().distance(ghost.getLocation());
            if (distance < nearestDistance && distance <= 50) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    // 新增：检查玩家是否为有效目标
    private boolean isValidTarget(Player player) {
        return player != null && 
               player.isOnline() && 
               !player.isDead() && 
               player.getGameMode() == GameMode.SURVIVAL;
    }

    public void cleanup() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        // === 新增：停止BGM ===
    if (plugin != null && plugin.getBgmPlayer() != null) {
        plugin.getBgmPlayer().stopAllBGM();
    }
    }

    public Ghast getGhost() {
        return ghost;
    }
    
    public TyrantBossPlugin getPlugin() {
        return plugin;
    }
}
