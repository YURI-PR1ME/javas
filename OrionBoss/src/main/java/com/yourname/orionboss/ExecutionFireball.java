package com.yourname.orionboss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class ExecutionFireball {

    private final OrionBossPlugin plugin;
    private final Player target;
    private final float speed;
    private final double damage;
    private Fireball fireball;
    private BukkitRunnable movementTask;
    private Vector direction;
    private boolean hasHitPlayer = false;

    public ExecutionFireball(OrionBossPlugin plugin, Player target, float speed, double damage) {
        this.plugin = plugin;
        this.target = target;
        this.speed = speed;
        this.damage = damage;
    }

    public void spawn(Location spawnLocation) {
        World world = spawnLocation.getWorld();
        if (world == null) return;

        // 生成巨型火焰弹
        fireball = world.spawn(spawnLocation, Fireball.class);
        
        // 设置火焰弹属性
        setupFireballProperties();
        
        // 计算初始方向
        calculateDirection(spawnLocation);
        
        // 开始直线飞行
        startLinearMovement();
        
        // 开始碰撞检测
        startCollisionDetection();
        
        // 开始自动清理检测
        startAutoCleanup();
        
        // 视觉效果
        playSpawnEffects();
    }

    private void setupFireballProperties() {
        if (fireball == null) return;
        
        fireball.setCustomName("§4§lExecution Fireball");
        fireball.setCustomNameVisible(true);
        fireball.setYield(0f); // 禁用爆炸，我们手动处理伤害
        fireball.setIsIncendiary(false);
        fireball.setGravity(false); // 禁用重力，直线飞行
        
        // 设置火焰弹大小（通过视觉效果模拟）
        fireball.setVisualFire(true);
    }

    private void calculateDirection(Location spawnLocation) {
        if (target == null || !target.isOnline()) {
            // 如果没有目标，朝随机水平方向飞行
            double randomAngle = Math.random() * 2 * Math.PI;
            this.direction = new Vector(Math.cos(randomAngle), 0, Math.sin(randomAngle)).normalize();
            return;
        }
        
        // 计算从火焰弹位置到目标位置的方向向量
        Location targetLoc = target.getLocation();
        this.direction = targetLoc.toVector().subtract(spawnLocation.toVector());
        
        // 归一化
        this.direction.normalize();
        
        // 保持水平飞行（Y轴速度为0）
        this.direction.setY(0);
    }

    private void startLinearMovement() {
        if (fireball == null || direction == null) return;
        
        // 初始速度
        fireball.setVelocity(direction.multiply(speed));
        
        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (fireball == null || fireball.isDead() || !fireball.isValid()) {
                    cancel();
                    return;
                }
                
                Location currentLoc = fireball.getLocation();
                
                // 持续应用直线移动，确保不会偏离方向
                Vector currentVelocity = fireball.getVelocity();
                Vector desiredVelocity = direction.clone().multiply(speed);
                
                // 平滑转向到目标方向
                Vector newVelocity = currentVelocity.multiply(0.8).add(desiredVelocity.multiply(0.2)).normalize().multiply(speed);
                fireball.setVelocity(newVelocity);
                
                // 飞行粒子效果
                fireball.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, 
                    currentLoc, 5, 0.5, 0.5, 0.5, 0.1);
                fireball.getWorld().spawnParticle(org.bukkit.Particle.FLAME, 
                    currentLoc, 10, 0.5, 0.5, 0.5, 0.05);
                    
                // 检查是否应该消失（高度低于0）
                if (currentLoc.getY() <= 0) {
                    removeFireball();
                    cancel();
                }
            }
        };
        
        movementTask.runTaskTimer(plugin, 0L, 5L); // 每5tick更新一次，减少性能消耗
    }

    private void startCollisionDetection() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fireball == null || fireball.isDead() || !fireball.isValid()) {
                    cancel();
                    return;
                }
                
                // 检查与目标玩家的距离
                if (target != null && target.isOnline() && !target.isDead()) {
                    double distance = fireball.getLocation().distance(target.getLocation());
                    if (distance < 3.0 && !hasHitPlayer) { // 碰撞距离
                        applyDamage();
                        hasHitPlayer = true; // 只造成一次伤害
                        removeFireball(); // 命中后消失
                        cancel();
                    }
                }
                
                // 检查是否应该消失（高度低于0）
                if (fireball.getLocation().getY() <= 0) {
                    removeFireball();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 每5tick检查一次
    }

    private void applyDamage() {
        if (fireball == null || target.isDead() || !target.isOnline()) return;
        
        // 造成伤害
        target.damage(damage, fireball);
        
        // 击退效果
        Vector knockback = target.getLocation().toVector()
            .subtract(fireball.getLocation().toVector())
            .normalize()
            .multiply(2.0)
            .setY(0.5);
        target.setVelocity(knockback);
        
        // 伤害特效
        target.getWorld().playSound(target.getLocation(), 
            org.bukkit.Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);
        target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, 
            target.getLocation(), 5, 1, 1, 1);
        
        target.sendMessage("§4§lExecution Fireball slammed into you!");
    }

    private void startAutoCleanup() {
        new BukkitRunnable() {
            private int ticksLived = 0;
            private final int maxLifetime = 20 * 10; // 10秒最大生存时间
            
            @Override
            public void run() {
                ticksLived++;
                
                // 超时清理
                if (ticksLived >= maxLifetime) {
                    removeFireball();
                    cancel();
                    return;
                }
                
                // 高度低于0清理
                if (fireball != null && fireball.getLocation().getY() <= 0) {
                    removeFireball();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void removeFireball() {
        if (fireball != null && fireball.isValid()) {
            // 消失特效
            playDeathEffects();
            fireball.remove();
        }
        
        if (movementTask != null) {
            movementTask.cancel();
        }
    }

    private void playSpawnEffects() {
        if (fireball == null) return;
        
        Location loc = fireball.getLocation();
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_SHOOT, 3.0f, 0.6f);
        loc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc, 30, 1, 1, 1);
        loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc, 20, 1, 1, 1);
    }

    private void playDeathEffects() {
        if (fireball == null) return;
        
        Location loc = fireball.getLocation();
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        loc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 5, 1, 1, 1);
    }

    public UUID getFireballUUID() {
        return fireball != null ? fireball.getUniqueId() : null;
    }

    public boolean isAlive() {
        return fireball != null && fireball.isValid() && !fireball.isDead();
    }
}
