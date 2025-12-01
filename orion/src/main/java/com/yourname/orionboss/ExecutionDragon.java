package com.yourname.orionboss;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private final double damage;
    private EnderDragon dragon;
    private BukkitRunnable behaviorTask;
    private BukkitRunnable sonicAttackTask;
    private boolean hasHitPlayer = false;
    private int attackCooldown = 0;
    
    // 将 orbitCenter 提升为类成员变量
    private Location orbitCenter;
    private double orbitRadius = 12.0;
    private double orbitHeight = 8.0;
    private double orbitSpeed = 0.05;
    private double angle = 0;

    public ExecutionDragon(OrionBossPlugin plugin, Player target, double damage) {
        this.plugin = plugin;
        this.target = target;
        this.damage = damage;
    }

    public void spawn(Location spawnLocation) {
        World world = spawnLocation.getWorld();
        if (world == null) return;

        // 生成末影龙
        dragon = (EnderDragon) world.spawnEntity(spawnLocation, EntityType.ENDER_DRAGON);
        
        // 初始化轨道中心
        if (target != null && target.isOnline()) {
            orbitCenter = target.getLocation().clone();
            orbitCenter.setY(orbitCenter.getY() + orbitHeight);
        } else {
            orbitCenter = spawnLocation.clone();
        }
        
        // 设置龙属性
        setupDragonProperties();
        
        // 开始AI行为
        startDragonBehavior();
        
        // 开始音波攻击
        startSonicAttack();
        
        // 开始碰撞检测
        startCollisionDetection();
        
        // 开始自动清理检测
        startAutoCleanup();
        
        // 视觉效果
        playSpawnEffects();
    }

    private void setupDragonProperties() {
        if (dragon == null) return;
        
        dragon.setCustomName("§4§lExecution Dragon");
        dragon.setCustomNameVisible(true);
        // 启用AI，让龙自然移动
        dragon.setAI(true);
        
        // 设置龙的生命值
        dragon.setHealth(100.0);
        
        // 设置龙的阶段为盘旋，让它自由飞行
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
    }

    private void startDragonBehavior() {
        behaviorTask = new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                    cancel();
                    return;
                }
                
                // 更新盘旋中心点
                if (target != null && target.isOnline() && !target.isDead()) {
                    if (orbitCenter == null) {
                        orbitCenter = target.getLocation().clone();
                        orbitCenter.setY(orbitCenter.getY() + orbitHeight);
                    } else {
                        // 平滑跟随玩家位置
                        Location targetLoc = target.getLocation();
                        orbitCenter.setX(orbitCenter.getX() * 0.9 + targetLoc.getX() * 0.1);
                        orbitCenter.setZ(orbitCenter.getZ() * 0.9 + targetLoc.getZ() * 0.1);
                        orbitCenter.setY(targetLoc.getY() + orbitHeight);
                    }
                    
                    // 计算盘旋位置
                    angle += orbitSpeed;
                    double x = orbitCenter.getX() + Math.cos(angle) * orbitRadius;
                    double z = orbitCenter.getZ() + Math.sin(angle) * orbitRadius;
                    double y = orbitCenter.getY() + Math.sin(angle * 2) * 2; // 上下波动
                    
                    Location targetLocation = new Location(orbitCenter.getWorld(), x, y, z);
                    
                    // 计算朝向
                    Vector direction = targetLocation.toVector().subtract(dragon.getLocation().toVector()).normalize();
                    
                    // 平滑移动
                    Vector currentVelocity = dragon.getVelocity();
                    Vector desiredVelocity = direction.multiply(0.8);
                    Vector newVelocity = currentVelocity.multiply(0.7).add(desiredVelocity.multiply(0.3));
                    
                    dragon.setVelocity(newVelocity);
                    
                    // 设置朝向
                    dragon.teleport(dragon.getLocation().setDirection(direction));
                    
                    // 盘旋粒子效果
                    if (ticks % 5 == 0) {
                        dragon.getWorld().spawnParticle(Particle.DRAGON_BREATH, 
                            dragon.getLocation(), 3, 1, 1, 1, 0.05);
                    }
                }
                
                ticks++;
            }
        };
        
        behaviorTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void startSonicAttack() {
        sonicAttackTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead() || !dragon.isValid()) {
                    cancel();
                    return;
                }
                
                if (target != null && target.isOnline() && !target.isDead()) {
                    // 每3秒执行一次音波攻击
                    if (attackCooldown <= 0) {
                        performSonicAttack();
                        attackCooldown = 60; // 3秒冷却 (20 ticks = 1秒)
                    } else {
                        attackCooldown--;
                    }
                }
            }
        };
        
        sonicAttackTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void performSonicAttack() {
        if (dragon == null || target == null || !target.isOnline()) return;
        
        Location dragonLoc = dragon.getLocation();
        Location targetLoc = target.getLocation();
        
        // 计算方向向量
        Vector direction = targetLoc.toVector().subtract(dragonLoc.toVector()).normalize();
        
        // 播放音波音效
        dragonLoc.getWorld().playSound(dragonLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
        dragonLoc.getWorld().playSound(dragonLoc, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.9f);
        
        // 音波粒子效果
        new BukkitRunnable() {
            private double distance = 0;
            private final double maxDistance = 20.0;
            private final double step = 1.0;
            
            @Override
            public void run() {
                if (distance > maxDistance) {
                    cancel();
                    return;
                }
                
                // 计算当前音波位置
                Vector currentDirection = direction.clone().multiply(distance);
                Location currentLoc = dragonLoc.clone().add(currentDirection);
                
                // 生成音波粒子
                currentLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, currentLoc, 2, 0.5, 0.5, 0.5, 0);
                currentLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, currentLoc, 1, 0.3, 0.3, 0.3, 0.1);
                
                // 检查是否击中玩家
                if (currentLoc.distance(targetLoc) < 2.5) {
                    applySonicDamage();
                    cancel();
                    return;
                }
                
                distance += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // 龙攻击前的准备特效
        dragon.getWorld().spawnParticle(Particle.SCULK_CHARGE, dragonLoc, 10, 1, 1, 1, 0.5);
        dragon.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, dragonLoc, 5, 0.5, 0.5, 0.5, 0.1);
    }

    private void applySonicDamage() {
        if (target.isDead() || !target.isOnline()) return;
        
        // 造成音波伤害
        target.damage(10.0, dragon);
        
        // 音波击退效果
        Vector knockback = target.getLocation().toVector()
            .subtract(dragon.getLocation().toVector())
            .normalize()
            .multiply(1.5)
            .setY(0.3);
        target.setVelocity(knockback);
        
        // 音波命中特效
        target.getWorld().playSound(target.getLocation(), 
            Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);
        target.getWorld().spawnParticle(Particle.SONIC_BOOM, 
            target.getLocation(), 10, 1, 1, 1);
        target.getWorld().spawnParticle(Particle.SCULK_SOUL, 
            target.getLocation(), 15, 1, 1, 1, 0.2);
        
        target.sendMessage("§4§lExecution Dragon's sonic attack hits you!");
        
        // 屏幕震动效果
        target.sendTitle("", "§c💥 SONIC BOOM!", 5, 10, 5);
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
                    if (distance < 6.0 && !hasHitPlayer) {
                        applyCollisionDamage();
                        hasHitPlayer = true;
                    }
                }
                
                // 检查是否应该消失（高度低于0或超出范围）
                Location loc = dragon.getLocation();
                if (loc.getY() <= 0 || (orbitCenter != null && loc.distance(orbitCenter) > 50)) {
                    removeDragon();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 每10tick检查一次
    }

    private void applyCollisionDamage() {
        if (dragon == null || target.isDead() || !target.isOnline()) return;
        
        // 造成碰撞伤害
        target.damage(damage, dragon);
        
        // 碰撞击退效果
        Vector knockback = target.getLocation().toVector()
            .subtract(dragon.getLocation().toVector())
            .normalize()
            .multiply(2.5)
            .setY(1.0);
        target.setVelocity(knockback);
        
        // 碰撞特效
        target.getWorld().playSound(target.getLocation(), 
            Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, 
            target.getLocation(), 20, 1, 1, 1);
        target.getWorld().spawnParticle(Particle.EXPLOSION, 
            target.getLocation(), 5, 2, 2, 2);
        
        target.sendMessage("§4§lExecution Dragon slammed into you!");
        
        // 播放碰撞特效
        dragon.getWorld().playSound(dragon.getLocation(), 
            Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
    }

    private void startAutoCleanup() {
        new BukkitRunnable() {
            private int ticksLived = 0;
            private final int maxLifetime = 20 * 30; // 30秒最大生存时间
            
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
                
                // 如果目标玩家死亡或离线，清理
                if (target == null || !target.isOnline() || target.isDead()) {
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
        
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        
        if (sonicAttackTask != null) {
            sonicAttackTask.cancel();
        }
        
        // 清理轨道中心引用
        orbitCenter = null;
    }

    private void playSpawnEffects() {
        if (dragon == null) return;
        
        Location loc = dragon.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.6f);
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 50, 3, 3, 3);
        loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 30, 2, 2, 2);
        
        // 闪电特效
        loc.getWorld().strikeLightningEffect(loc);
    }

    private void playDeathEffects() {
        if (dragon == null) return;
        
        Location loc = dragon.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.5f, 0.8f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 20, 2, 2, 2);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 1, 1, 1);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 10, 1, 1, 1);
    }

    public UUID getDragonUUID() {
        return dragon != null ? dragon.getUniqueId() : null;
    }

    public boolean isAlive() {
        return dragon != null && dragon.isValid() && !dragon.isDead();
    }
}
