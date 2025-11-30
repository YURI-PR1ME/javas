package com.yourname.orionboss;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class ExecutionDragon {

    private final OrionBossPlugin plugin;
    private final Player target;
    private final float speed;
    private final double damage;
    private EnderDragon dragon;
    private BukkitRunnable movementTask;
    private Vector direction;
    private Location initialLocation;
    private boolean hasHitPlayer = false;

    public ExecutionDragon(OrionBossPlugin plugin, Player target, float speed, double damage) {
        this.plugin = plugin;
        this.target = target;
        this.speed = speed;
        this.damage = damage;
    }

    public void spawn(Location spawnLocation) {
        World world = spawnLocation.getWorld();
        if (world == null) return;

        this.initialLocation = spawnLocation.clone();
        
        // 生成末影龙
        dragon = (EnderDragon) world.spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);
        
        // 计算初始方向
        calculateDirection();
        
        // 设置龙属性
        setupDragonProperties();
        
        // 开始直线飞行
        startLinearMovement();
        
        // 开始碰撞检测
        startCollisionDetection();
        
        // 开始自动清理检测
        startAutoCleanup();
        
        // 视觉效果
        playSpawnEffects();
    }

    private void calculateDirection() {
        if (target == null || !target.isOnline()) {
            // 如果没有目标，朝随机水平方向飞行
            double randomAngle = Math.random() * 2 * Math.PI;
            this.direction = new Vector(Math.cos(randomAngle), 0, Math.sin(randomAngle)).normalize();
            return;
        }
        
        // 计算从龙的位置到目标位置的方向向量
        Location targetLoc = target.getLocation();
        this.direction = targetLoc.toVector().subtract(initialLocation.toVector());
        
        // 归一化并设置速度
        this.direction.normalize();
        
        // 保持水平飞行（Y轴速度为0）
        this.direction.setY(0);
    }

    private void setupDragonProperties() {
        if (dragon == null) return;
        
        dragon.setCustomName("§4§lExecution Dragon");
        dragon.setCustomNameVisible(true);
        dragon.setAI(false); // 禁用AI，我们手动控制移动
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        
        // 设置龙的生命值和伤害抗性
        dragon.setHealth(100.0);
    }

    private void startLinearMovement() {
        if (dragon == null || direction == null) return;
        
        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                    cancel();
                    return;
                }
                
                Location currentLoc = dragon.getLocation();
                
                // 应用直线移动
                Vector velocity = direction.clone().multiply(speed);
                dragon.setVelocity(velocity);
                
                // 强制设置龙的朝向为移动方向
                dragon.teleport(currentLoc.setDirection(direction));
                
                // 飞行粒子效果
                dragon.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, 
                    currentLoc, 3, 0.5, 0.5, 0.5, 0.1);
                    
                // 检查是否应该消失（高度低于0）
                if (currentLoc.getY() <= 0) {
                    removeDragon();
                    cancel();
                }
            }
        };
        
        movementTask.runTaskTimer(plugin, 0L, 1L); // 每tick更新
    }

    private void startCollisionDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                    cancel();
                    return;
                }
                
                // 检查与目标玩家的距离
                if (target != null && target.isOnline() && !target.isDead()) {
                    double distance = dragon.getLocation().distance(target.getLocation());
                    if (distance < 6.0 && !hasHitPlayer) { // 增加碰撞距离，因为龙体积大
                        applyDamage();
                        hasHitPlayer = true; // 只造成一次伤害
                    }
                }
                
                // 检查是否应该消失（高度低于0）
                if (dragon.getLocation().getY() <= 0) {
                    removeDragon();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 每5tick检查一次
    }

    private void applyDamage() {
        if (dragon == null || target.isDead() || !target.isOnline()) return;
        
        // 造成伤害
        target.damage(damage, dragon);
        
        // 击退效果
        Vector knockback = target.getLocation().toVector()
            .subtract(dragon.getLocation().toVector())
            .normalize()
            .multiply(2.0)
            .setY(0.5);
        target.setVelocity(knockback);
        
        // 伤害特效
        target.getWorld().playSound(target.getLocation(), 
            org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
        target.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, 
            target.getLocation(), 20, 1, 1, 1);
        
        target.sendMessage("§4§lExecution Dragon slammed into you!");
    }

    private void startAutoCleanup() {
        new BukkitRunnable() {
            private int ticksLived = 0;
            private final int maxLifetime = 20 * 15; // 15秒最大生存时间
            
            @Override
            public void run() {
                ticksLived++;
                
                // 超时清理
                if (ticksLived >= maxLifetime) {
                    removeDragon();
                    cancel();
                    return;
                }
                
                // 高度低于0清理
                if (dragon != null && dragon.getLocation().getY() <= 0) {
                    removeDragon();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void removeDragon() {
        if (dragon != null && dragon.isValid()) {
            // 消失特效
            playDeathEffects();
            dragon.remove();
        }
        
        if (movementTask != null) {
            movementTask.cancel();
        }
    }

    private void playSpawnEffects() {
        if (dragon == null) return;
        
        Location loc = dragon.getLocation();
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.6f);
        loc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, loc, 50, 2, 2, 2);
        loc.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, loc, 30, 1, 1, 1);
    }

    private void playDeathEffects() {
        if (dragon == null) return;
        
        Location loc = dragon.getLocation();
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f);
        loc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 10, 1, 1, 1);
    }

    public UUID getDragonUUID() {
        return dragon != null ? dragon.getUniqueId() : null;
    }

    public boolean isAlive() {
        return dragon != null && dragon.isValid() && !dragon.isDead();
    }
}
