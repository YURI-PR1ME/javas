package com.example.godsexecutioner;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class ExecutionerListener implements Listener {

    private final GodsExecutionerPlugin plugin;
    private final Map<UUID, Long> chargeCooldowns = new HashMap<>();
    private final Map<UUID, Long> witherSkullCooldowns = new HashMap<>();
    private final Map<UUID, Long> gravityFieldCooldowns = new HashMap<>();
    
    // 存储引力场信息：玩家UUID -> 拉取的实体列表
    private final Map<UUID, List<LivingEntity>> gravityFields = new HashMap<>();
    
    // 存储被减少生命上限的实体：实体UUID -> 原始最大生命值
    private final Map<UUID, Double> reducedHealthEntities = new HashMap<>();
    
    // 冷却时间（毫秒）
    private static final long CHARGE_COOLDOWN = 3000; // 3秒
    private static final long WITHER_SKULL_COOLDOWN = 5000; // 5秒
    private static final long GRAVITY_FIELD_COOLDOWN = 10000; // 10秒

    public ExecutionerListener(GodsExecutionerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理玩家交互事件（右键/左键）
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 检查是否手持神之执行者
        if (!ExecutionerManager.isGodsExecutioner(item, plugin)) {
            return;
        }
        
        // 检查权限
        if (!player.hasPermission("godsexecutioner.use")) {
            player.sendMessage(ChatColor.RED + "你没有权限使用神之执行者！");
            return;
        }
        
        Action action = event.getAction();
        boolean isSneaking = player.isSneaking();
        
        if (isSneaking) {
            // 潜行状态下的技能
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                handleWitherSkull(player);
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                handleGravityField(player);
            }
        } else {
            // 非潜行状态下的技能
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                handleCharge(player);
            }
        }
    }

    /**
     * 右键技能：冲锋
     */
    private void handleCharge(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // 检查冷却
        if (chargeCooldowns.containsKey(playerId)) {
            long cooldownEnd = chargeCooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remaining = (cooldownEnd - now) / 1000 + 1;
                player.sendMessage(ChatColor.YELLOW + "冲锋技能冷却中... (" + remaining + "秒)");
                return;
            }
        }
        
        // 播放音效和粒子
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, 
            player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        
        // 获取玩家朝向
        Vector direction = player.getEyeLocation().getDirection().normalize();
        
        // 增加冲锋速度和距离
        double speed = 3.5; // 增加速度
        Vector velocity = direction.multiply(speed);
        
        // 给玩家一个向上的速度分量，避免贴地飞行
        velocity.setY(velocity.getY() + 0.3);
        
        player.setVelocity(velocity);
        
        // 开始检测冲锋路径上的实体
        new BukkitRunnable() {
            private int ticks = 0;
            private final int MAX_TICKS = 60; // 增加持续时间为3秒 (60 ticks)
            private final List<UUID> hitEntities = new ArrayList<>();
            private Location lastLocation = player.getLocation();
            
            @Override
            public void run() {
                ticks++;
                
                // 检查玩家是否还在空中或移动
                if (ticks > MAX_TICKS || player.isOnGround() || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                
                // 检测玩家当前位置周围的实体（碰撞检测）
                Location currentLocation = player.getLocation();
                
                // 计算玩家移动的方向和距离
                Vector movement = currentLocation.toVector().subtract(lastLocation.toVector());
                double movementDistance = movement.length();
                
                if (movementDistance > 0) {
                    // 检测玩家周围3格范围内的实体
                    for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
                        if (entity instanceof LivingEntity && 
                            !entity.equals(player) && 
                            !hitEntities.contains(entity.getUniqueId())) {
                            
                            LivingEntity target = (LivingEntity) entity;
                            
                            // 检查实体是否在玩家的路径上
                            if (isEntityInPath(lastLocation, currentLocation, target)) {
                                hitEntities.add(target.getUniqueId());
                                
                                // 在目标位置产生爆炸
                                Location explosionLoc = target.getLocation();
                                player.getWorld().createExplosion(explosionLoc, 2.5f, false, false);
                                
                                // 播放效果
                                player.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                                player.getWorld().spawnParticle(Particle.EXPLOSION, explosionLoc, 25, 0.5, 0.5, 0.5, 0.1);
                                
                                // 对目标造成伤害
                                double damage = 10.0; // 增加基础伤害
                                
                                // 对亡灵生物额外伤害
                                if (isUndead(target)) {
                                    damage += 40.0; // +40点亡灵伤害
                                    player.sendMessage(ChatColor.GOLD + "§l神罚! 对亡灵额外伤害!");
                                }
                                
                                target.damage(damage, player);
                                
                                // 击退目标
                                Vector knockback = target.getLocation().toVector()
                                    .subtract(player.getLocation().toVector())
                                    .normalize()
                                    .multiply(2.0); // 增加击退力度
                                target.setVelocity(knockback);
                                
                                // 短暂眩晕效果
                                target.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOWNESS, 20, 2 // 1秒缓慢III
                                ));
                            }
                        }
                    }
                }
                
                lastLocation = currentLocation;
                
                // 显示轨迹粒子
                for (int i = 0; i < 5; i++) {
                    Location particleLoc = player.getLocation().add(
                        (Math.random() - 0.5) * 3,
                        Math.random() * 2,
                        (Math.random() - 0.5) * 3
                    );
                    player.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0, 0, 0, 0);
                }
            }
            
            /**
             * 检查实体是否在路径上
             */
            private boolean isEntityInPath(Location start, Location end, Entity entity) {
                // 计算路径的中间点
                Vector path = end.toVector().subtract(start.toVector());
                double pathLength = path.length();
                
                if (pathLength == 0) return false;
                
                Vector pathDirection = path.normalize();
                
                // 计算实体到路径的最短距离
                Vector entityToStart = entity.getLocation().toVector().subtract(start.toVector());
                double projectionLength = entityToStart.dot(pathDirection);
                
                // 如果投影在路径范围之外
                if (projectionLength < 0 || projectionLength > pathLength) {
                    return false;
                }
                
                // 计算投影点
                Vector projection = pathDirection.multiply(projectionLength);
                Vector closestPoint = start.toVector().add(projection);
                
                // 计算实体到投影点的距离
                double distance = closestPoint.distance(entity.getLocation().toVector());
                
                // 如果距离小于2格，认为在路径上
                return distance < 2.0;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        
        // 设置冷却
        chargeCooldowns.put(playerId, now + CHARGE_COOLDOWN);
        player.sendMessage(ChatColor.GOLD + "§l冲锋! 向敌人发起致命冲击!");
    }

    /**
     * 潜行+左键：发射凋零骷髅头
     */
    private void handleWitherSkull(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // 检查冷却
        if (witherSkullCooldowns.containsKey(playerId)) {
            long cooldownEnd = witherSkullCooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remaining = (cooldownEnd - now) / 1000 + 1;
                player.sendMessage(ChatColor.YELLOW + "凋零骷髅头冷却中... (" + remaining + "秒)");
                return;
            }
        }
        
        // 创建凋零骷髅头
        Location spawnLoc = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        
        WitherSkull skull = player.getWorld().spawn(spawnLoc, WitherSkull.class);
        skull.setShooter(player);
        skull.setDirection(direction);
        skull.setCharged(true); // 蓝色凋零骷髅头
        skull.setVelocity(direction.multiply(1.5));
        
        // 添加自定义标签，标记为神之执行者发射的
        skull.setMetadata("gods_executioner_skull", 
            new FixedMetadataValue(plugin, true));
        
        // 播放效果
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spawnLoc, 10, 0.1, 0.1, 0.1, 0.05);
        
        // 设置冷却
        witherSkullCooldowns.put(playerId, now + WITHER_SKULL_COOLDOWN);
        player.sendMessage(ChatColor.DARK_GRAY + "凋零骷髅头已发射!");
        
        // 监听凋零骷髅头命中
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!skull.isValid()) {
                    this.cancel();
                }
                
                // 显示轨迹粒子
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, 
                    skull.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * 凋零骷髅头命中事件
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof WitherSkull)) {
            return;
        }
        
        WitherSkull skull = (WitherSkull) event.getEntity();
        
        // 检查是否由神之执行者发射
        if (!skull.hasMetadata("gods_executioner_skull")) {
            return;
        }
        
        // 在命中点产生大爆炸
        Location hitLoc = skull.getLocation();
        skull.getWorld().createExplosion(hitLoc, 3.0f, true, false);
        
        // 对附近的亡灵生物造成额外伤害
        for (Entity entity : skull.getNearbyEntities(5, 3, 5)) {
            if (entity instanceof LivingEntity && !entity.equals(skull.getShooter())) {
                LivingEntity target = (LivingEntity) entity;
                
                double damage = 15.0;
                if (isUndead(target)) {
                    damage += 40.0; // 对亡灵额外伤害
                }
                
                target.damage(damage, (Player) skull.getShooter());
                
                // 添加凋零效果
                target.addPotionEffect(new PotionEffect(
                    PotionEffectType.WITHER, 100, 2 // 5秒凋零效果
                ));
            }
        }
        
        // 播放效果
        skull.getWorld().playSound(hitLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.5f);
        
        // 清除凋零骷髅头
        skull.remove();
    }

    /**
     * 潜行+右键：引力场
     */
    private void handleGravityField(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // 检查冷却
        if (gravityFieldCooldowns.containsKey(playerId)) {
            long cooldownEnd = gravityFieldCooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remaining = (cooldownEnd - now) / 1000 + 1;
                player.sendMessage(ChatColor.YELLOW + "引力场冷却中... (" + remaining + "秒)");
                return;
            }
        }
        
        // 播放启动效果
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, 
            player.getLocation().add(0, 1, 0), 100, 2, 2, 2, 0.1);
        
        // 获取附近的实体（10格范围内）
        List<LivingEntity> pulledEntities = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                LivingEntity target = (LivingEntity) entity;
                
                // 计算拉向玩家的力
                Vector direction = player.getLocation().toVector()
                    .subtract(target.getLocation().toVector())
                    .normalize()
                    .multiply(0.8);
                
                // 施加拉力
                target.setVelocity(direction);
                
                pulledEntities.add(target);
                
                // 显示粒子效果
                Location particleLoc = target.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.DRAGON_BREATH, 
                    particleLoc, 10, 0.3, 0.3, 0.3, 0.02);
            }
        }
        
        // 存储引力场信息
        gravityFields.put(playerId, pulledEntities);
        
        // 设置冷却
        gravityFieldCooldowns.put(playerId, now + GRAVITY_FIELD_COOLDOWN);
        player.sendMessage(ChatColor.DARK_PURPLE + "§l引力场生成! 正在拉取附近的敌人...");
        
        // 3秒后执行回复和减益效果
        new BukkitRunnable() {
            @Override
            public void run() {
                // 获取存储的实体列表
                List<LivingEntity> entities = gravityFields.get(playerId);
                if (entities == null || entities.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "引力场结束，没有拉取到敌人。");
                    return;
                }
                
                int entityCount = 0;
                
                // 对每个被拉取的实体施加效果
                for (LivingEntity entity : entities) {
                    if (entity != null && !entity.isDead()) {
                        entityCount++;
                        
                        // 失明效果
                        entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.BLINDNESS, 60, 0 // 3秒失明
                        ));
                        
                        // 记录原始最大生命值，并减少最大生命上限
                        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
                        if (maxHealthAttr != null) {
                            double originalMaxHealth = maxHealthAttr.getBaseValue();
                            
                            // 保存原始最大生命值
                            reducedHealthEntities.put(entity.getUniqueId(), originalMaxHealth);
                            
                            // 减少5点最大生命值
                            double newMaxHealth = Math.max(1, originalMaxHealth - 5);
                            maxHealthAttr.setBaseValue(newMaxHealth);
                            
                            // 如果当前生命值超过新上限，调整为上限
                            if (entity.getHealth() > newMaxHealth) {
                                entity.setHealth(newMaxHealth);
                            }
                            
                            // 通知玩家（如果是玩家）
                            if (entity instanceof Player) {
                                Player targetPlayer = (Player) entity;
                                targetPlayer.sendMessage(ChatColor.RED + "你的最大生命值被减少了5点！将在30秒后恢复。");
                            }
                            
                            // 30秒后恢复最大生命值
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (entity.isValid() && !entity.isDead()) {
                                        Double storedHealth = reducedHealthEntities.get(entity.getUniqueId());
                                        if (storedHealth != null) {
                                            maxHealthAttr.setBaseValue(storedHealth);
                                            reducedHealthEntities.remove(entity.getUniqueId());
                                            
                                            // 通知恢复
                                            if (entity instanceof Player) {
                                                Player targetPlayer = (Player) entity;
                                                targetPlayer.sendMessage(ChatColor.GREEN + "你的最大生命值已恢复！");
                                                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                                            }
                                        }
                                    } else {
                                        // 实体已死亡或无效，清理数据
                                        reducedHealthEntities.remove(entity.getUniqueId());
                                    }
                                }
                            }.runTaskLater(plugin, 20L * 30); // 30秒后
                        }
                        
                        // 播放效果
                        entity.getWorld().playSound(entity.getLocation(), 
                            Sound.ENTITY_WITHER_HURT, 0.8f, 1.2f);
                        entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, 
                            entity.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                    }
                }
                
                // 玩家回复生命值（每个敌人回复3点）
                double healAmount = entityCount * 3.0;
                double newHealth = Math.min(player.getHealth() + healAmount, 
                    player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.setHealth(newHealth);
                
                // 播放回复效果
                player.getWorld().playSound(player.getLocation(), 
                    Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.HEART, 
                    player.getLocation().add(0, 2, 0), entityCount * 5, 1, 1, 1, 0.1);
                
                player.sendMessage(ChatColor.GREEN + "§l引力场结束! 吸收了 " + entityCount + 
                    " 个敌人的生命，回复了 " + healAmount + " 点生命值!");
                
                // 清理引力场数据
                gravityFields.remove(playerId);
            }
        }.runTaskLater(plugin, 60L); // 3秒后执行 (20 ticks = 1秒)
    }

    /**
     * 处理实体伤害事件（实现亡灵额外伤害）
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 检查攻击者是否为玩家
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        
        // 检查是否使用神之执行者
        if (!ExecutionerManager.isGodsExecutioner(weapon, plugin)) {
            return;
        }
        
        // 检查目标是否为亡灵生物
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getEntity();
            
            if (isUndead(target)) {
                // 对亡灵生物额外伤害
                double originalDamage = event.getDamage();
                double newDamage = originalDamage + 40.0;
                event.setDamage(newDamage);
                
                // 显示特殊效果
                target.getWorld().playSound(target.getLocation(), 
                    Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 0.8f);
                
                // 神圣粒子效果
                for (int i = 0; i < 10; i++) {
                    target.getWorld().spawnParticle(Particle.END_ROD, 
                        target.getLocation().add(
                            (Math.random() - 0.5) * 2,
                            Math.random() * 2,
                            (Math.random() - 0.5) * 2
                        ), 1, 0, 0, 0, 0.01);
                }
            }
        }
    }

    /**
     * 判断实体是否为亡灵生物
     */
    private boolean isUndead(Entity entity) {
        // 检查实体类型
        EntityType type = entity.getType();
        
        // 判断是否为亡灵生物
        switch (type) {
            case ZOMBIE:
            case SKELETON:
            case WITHER_SKELETON:
            case STRAY:
            case HUSK:
            case DROWNED:
            case PHANTOM:
            case WITHER:
            case PIGLIN_BRUTE:
            case SKELETON_HORSE:
            case ZOMBIE_HORSE:
            case ZOMBIE_VILLAGER:
            case ZOMBIFIED_PIGLIN:
                return true;
            default:
                return false;
        }
    }

    /**
     * 玩家退出时清理数据
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        chargeCooldowns.remove(playerId);
        witherSkullCooldowns.remove(playerId);
        gravityFieldCooldowns.remove(playerId);
        gravityFields.remove(playerId);
        
        // 如果玩家是被减少生命上限的实体，恢复其生命上限
        if (reducedHealthEntities.containsKey(playerId)) {
            Player player = event.getPlayer();
            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                Double originalHealth = reducedHealthEntities.get(playerId);
                if (originalHealth != null) {
                    maxHealthAttr.setBaseValue(originalHealth);
                }
            }
            reducedHealthEntities.remove(playerId);
        }
    }
    
    /**
     * 实体死亡时清理数据
     */
    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        // 如果死亡的实体是被减少生命上限的，清理数据
        reducedHealthEntities.remove(event.getEntity().getUniqueId());
    }
}
