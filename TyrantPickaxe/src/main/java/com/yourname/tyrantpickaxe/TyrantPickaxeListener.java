package com.yourname.tyrantpickaxe;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.GameMode;

import java.util.List;
import java.util.UUID;
import java.util.Random;
import java.util.stream.Collectors;

public class TyrantPickaxeListener implements Listener {

    private final TyrantPickaxePlugin plugin;
    private final TyrantPickaxeManager manager;
    private final Random random;

    public TyrantPickaxeListener(TyrantPickaxePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getPickaxeManager();
        this.random = new Random();
    }

    @EventHandler
    public void onPlayerHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack previousItem = player.getInventory().getItem(event.getPreviousSlot());
        
        // Stop ActionBar updates if switching away from Tyrant Pickaxe
        if (plugin.isTyrantPickaxe(previousItem) && !plugin.isTyrantPickaxe(newItem)) {
            manager.stopActionBarUpdates(player);
            player.removePotionEffect(PotionEffectType.HASTE);
        }
        
        // Start ActionBar updates and apply effects if switching to Tyrant Pickaxe
        if (plugin.isTyrantPickaxe(newItem)) {
            // Apply Haste X effect (level 10)
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.HASTE, 
                Integer.MAX_VALUE, // Infinite duration while holding
                49, // Level 10 (0-based index: 9 = level 10)
                true, // Ambient particles
                false // No icon
            ));
            
            // Start ActionBar updates
            manager.startActionBarUpdates(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !plugin.isTyrantPickaxe(item)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            
            if (player.isSneaking()) {
                // Rapid displacement ability
                handleRapidDisplacement(player);
            } else {
                // Single target teleport
                handleSingleTeleport(player);
            }
        }
    }

    private void handleSingleTeleport(Player player) {
        // Check cooldown
        if (manager.isOnTeleportCooldown(player)) {
            // Cooldown message is now shown in ActionBar, no need for chat message
            return;
        }

        // Find target entity using ray trace (player's crosshair)
        Entity target = findTargetByRayTrace(player, 30);
        
        if (target == null) {
            player.sendMessage("§cNo valid target found! Aim at a living entity within 30 blocks.");
            return;
        }

        // Create red particle effect at the starting location
        createRedParticleEffect(player.getLocation());
        
        // Teleport behind the target
        teleportBehindTarget(player, target);
        
        // Set initial cooldown
        manager.setTeleportCooldown(player, 3);
        
        // Schedule task to check for critical hit within 2 seconds
        new org.bukkit.scheduler.BukkitRunnable() {
            private final UUID playerId = player.getUniqueId();
            private final UUID targetId = target.getUniqueId();
            private boolean hitDetected = false;
            
            @Override
            public void run() {
                Player currentPlayer = plugin.getServer().getPlayer(playerId);
                Entity currentTarget = plugin.getServer().getEntity(targetId);
                
                if (currentPlayer != null && !hitDetected) {
                    // No critical hit detected within 2 seconds, cooldown remains
                    // Message is now shown in ActionBar, no need for chat message
                }
            }
        }.runTaskLater(plugin, 40L); // 2 seconds (40 ticks)
    }

    private void handleRapidDisplacement(Player player) {
        // Check cooldown
        if (manager.isOnRapidDisplacementCooldown(player)) {
            // Cooldown message is now shown in ActionBar, no need for chat message
            return;
        }

        // Find all living entities within 15 blocks，排除旁观和创造模式的玩家
        List<LivingEntity> targets = player.getWorld().getEntitiesByClass(LivingEntity.class).stream()
                .filter(entity -> !entity.getUniqueId().equals(player.getUniqueId()))
                .filter(entity -> entity.getLocation().distance(player.getLocation()) <= 15)
                .filter(entity -> {
                    if (entity instanceof Player) {
                        Player p = (Player) entity;
                        return p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
                    }
                    return true; // 生物都包括
                })
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            player.sendMessage("§cNo valid targets found within 15 blocks!");
            return;
        }

        player.sendMessage("§6Starting Rapid Displacement! Teleporting to " + targets.size() + " targets for 3 rounds...");
        manager.startRapidDisplacement(player, targets);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        
        if (!plugin.isTyrantPickaxe(weapon)) {
            return;
        }

        // 检查是否在快速位移状态中
        if (manager.isInRapidDisplacement(player) && event.getEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getEntity();
            manager.handleRapidDisplacementAttack(player, target);
            
            // 在快速位移状态下，不处理其他效果
            return;
        }

        // Check if this is a critical hit (player is falling)
        boolean isCritical = player.getFallDistance() > 0.0F && !player.isOnGround();
        
        if (isCritical) {
            // Check if within 2 seconds of teleport
            if (manager.getTeleportCooldownRemaining(player) > 1000) { // More than 1 second remaining
                manager.resetTeleportCooldown(player);
                // Set consecutive hit reward
                manager.setConsecutiveHitReward(player, true);
                
                // Create red particle effect on critical hit
                createRedParticleEffect(player.getLocation());
                
                // Send message about reward
                player.sendMessage("§6Consecutive Hit! Next attack will ignite targets and fire fireballs!");
            }

            // Handle kill counter for players
            if (event.getEntity() instanceof Player && event.getFinalDamage() >= ((Player) event.getEntity()).getHealth()) {
                manager.incrementKillCounter(player);
                
                // Check if negative effect should be triggered
                if (manager.shouldTriggerNegativeEffect(player)) {
                    triggerNegativeEffect(player);
                    manager.resetKillCounter(player);
                }
            }
        }
        
        // Check for consecutive hit reward
        if (manager.hasConsecutiveHitReward(player)) {
            // Apply fire effect to target
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.getEntity();
                target.setFireTicks(100); // 5 seconds of fire
                
                // Create fire particle effect
                createFireParticleEffect(target.getLocation());
            }
            
            // Shoot 5 fireballs forward
            manager.shootFireballs(player);
            
            // Remove the reward after use
            manager.removeConsecutiveHitReward(player);
            
            // Send message
            player.sendMessage("§eFire attack unleashed!");
        }
    }

    @EventHandler
    public void onEntityDamageByFireball(EntityDamageByEntityEvent event) {
        // 检查伤害是否来自火焰弹
        if (event.getDamager() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getDamager();
            
            // 检查是否是Tyrant Pickaxe的火焰弹
            if (fireball.getCustomName() != null && fireball.getCustomName().equals("TyrantFireball")) {
                // 计算15-20点的随机伤害
                double damage = 15 + random.nextDouble() * 5; // 15-20点伤害
                
                // 如果受伤者是发射者自己，伤害减半
                if (fireball.getShooter() instanceof Player && event.getEntity().equals(fireball.getShooter())) {
                    damage = damage / 2;
                    ((Player) fireball.getShooter()).sendMessage("§6You took reduced fireball damage!");
                }
                
                // 设置伤害
                event.setDamage(damage);
                
                // 创建爆炸粒子效果
                createExplosionParticleEffect(event.getEntity().getLocation());
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // 检查爆炸是否来自火焰弹
        if (event.getEntity() instanceof Fireball) {
            Fireball fireball = (Fireball) event.getEntity();
            
            // 检查是否是Tyrant Pickaxe的火焰弹
            if (fireball.getCustomName() != null && fireball.getCustomName().equals("TyrantFireball")) {
                // 取消原版爆炸破坏方块
                event.blockList().clear();
                
                // 创建自定义爆炸效果
                createCustomExplosionEffect(fireball.getLocation());
            }
        }
    }

    private Entity findTargetByRayTrace(Player player, double maxDistance) {
        // Use ray trace to find the entity the player is looking at
        RayTraceResult rayTrace = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            maxDistance,
            entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        
        if (rayTrace != null) {
            return rayTrace.getHitEntity();
        }
        
        return null;
    }

    private String getEntityName(Entity entity) {
        if (entity instanceof Player) {
            return ((Player) entity).getName();
        } else if (entity instanceof Monster) {
            return "monster";
        } else if (entity instanceof Animals) {
            return "animal";
        } else {
            return "entity";
        }
    }

    private void triggerNegativeEffect(Player player) {
        // Apply negative effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0)); // 3 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0)); // 3 seconds
        
        // Send message
        player.sendTitle("§c§lTYRANT'S CURSE", "§e\"Now, who is the Tyrant? HA Ha HA!\"", 10, 40, 10);
        player.sendMessage("§4The Tyrant's power overwhelms you!");
        
        // Create intense red particle effect for negative effect
        createIntenseRedParticleEffect(player.getLocation());
        
        // Play sound effect if possible
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.ENTITY_WITHER_DEATH;
            player.playSound(player.getLocation(), sound, 1.0f, 0.8f);
        } catch (Exception e) {
            // Sound might not be available in all versions
        }
    }

    private void teleportBehindTarget(Player player, Entity target) {
        // Calculate position behind the target (1 block behind, 1 block above)
        Vector direction = target.getLocation().getDirection();
        Vector behind = direction.multiply(-1).normalize();
        
        Location teleportLoc = target.getLocation().add(behind);
        teleportLoc.setY(teleportLoc.getY() + 1);
        
        // Make player face the target after teleportation
        Vector lookDirection = target.getLocation().toVector().subtract(teleportLoc.toVector()).normalize();
        teleportLoc.setDirection(lookDirection);
        
        // Create red particle effect at the target location
        createRedParticleEffect(teleportLoc);
        
        player.teleport(teleportLoc);
        
        // Create red particle effect at the player's new location
        createRedParticleEffect(player.getLocation());
    }

    private void createRedParticleEffect(Location location) {
        // Create red dust particle effect
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1.0f);
        
        // Spawn particles in a sphere pattern
        for (int i = 0; i < 20; i++) {
            double angle = 2 * Math.PI * i / 20;
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            
            for (int j = 0; j < 3; j++) {
                double y = j * 0.5;
                location.getWorld().spawnParticle(
                    Particle.DUST, 
                    location.getX() + x, 
                    location.getY() + y, 
                    location.getZ() + z, 
                    1, 
                    dustOptions
                );
            }
        }
        
        // Add some upward particle trail
        for (int i = 0; i < 10; i++) {
            location.getWorld().spawnParticle(
                Particle.DUST,
                location.getX(),
                location.getY() + i * 0.2,
                location.getZ(),
                3,
                0.2, 0.2, 0.2,
                dustOptions
            );
        }
    }

    private void createFireParticleEffect(Location location) {
        // Create flame particles
        for (int i = 0; i < 10; i++) {
            location.getWorld().spawnParticle(
                Particle.FLAME,
                location.getX() + (Math.random() - 0.5) * 2,
                location.getY() + Math.random() * 2,
                location.getZ() + (Math.random() - 0.5) * 2,
                5,
                0.1, 0.1, 0.1,
                0.05
            );
        }
        
        // 使用 SMOKE 替代 SMOKE_LARGE
        location.getWorld().spawnParticle(
            Particle.SMOKE,  // 修复：使用可用的 SMOKE 粒子
            location,
            5,
            0.5, 0.5, 0.5,
            0.1
        );
    }

    private void createExplosionParticleEffect(Location location) {
        // 创建爆炸粒子效果
        location.getWorld().spawnParticle(
            Particle.EXPLOSION,
            location,
            5,
            1.0, 1.0, 1.0,
            0.1
        );
        
        // 添加火焰粒子
        location.getWorld().spawnParticle(
            Particle.FLAME,
            location,
            20,
            2.0, 2.0, 2.0,
            0.1
        );
        
        // 添加烟雾粒子
        location.getWorld().spawnParticle(
            Particle.SMOKE,
            location,
            10,
            1.5, 1.5, 1.5,
            0.1
        );
    }

    private void createCustomExplosionEffect(Location location) {
        // 创建自定义爆炸效果
        location.getWorld().spawnParticle(
            Particle.EXPLOSION,
            location,
            1
        );
        
        // 添加冲击波效果
        for (int i = 0; i < 3; i++) {
            double radius = 2.0 + i * 0.5;
            for (int j = 0; j < 20; j++) {
                double angle = 2 * Math.PI * j / 20;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                
                location.getWorld().spawnParticle(
                    Particle.FLAME,
                    location.getX() + x,
                    location.getY(),
                    location.getZ() + z,
                    1,
                    0, 0, 0,
                    0.05
                );
            }
        }
    }

    private void createIntenseRedParticleEffect(Location location) {
        // Create more intense red particle effect for negative effects
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 2.0f);
        
        // Spawn a large amount of particles in a bigger sphere
        for (int i = 0; i < 50; i++) {
            double angle = 2 * Math.PI * i / 50;
            double x = Math.cos(angle) * 2.5;
            double z = Math.sin(angle) * 2.5;
            
            for (int j = 0; j < 5; j++) {
                double y = j * 0.5;
                location.getWorld().spawnParticle(
                    Particle.DUST, 
                    location.getX() + x, 
                    location.getY() + y, 
                    location.getZ() + z, 
                    2, 
                    dustOptions
                );
            }
        }
        
        // Add explosive particle effect
        location.getWorld().spawnParticle(
            Particle.FLAME,
            location,
            20,
            1.5, 1.5, 1.5,
            0.1
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Remove haste effect when player quits
        player.removePotionEffect(PotionEffectType.HASTE);
        
        // Stop ActionBar updates
        manager.stopActionBarUpdates(player);
        
        // Clean up manager data
        manager.resetKillCounter(player);
        manager.removeConsecutiveHitReward(player);
    }
}
