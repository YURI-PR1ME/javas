package com.example.viralfire;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FireGenerator {
    private final ViralFirePlugin plugin;
    private final Random random = new Random();
    
    public FireGenerator(ViralFirePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void startFireGeneration() {
        // 每20 ticks (1秒) 执行一次火焰生成
        new BukkitRunnable() {
            @Override
            public void run() {
                generateFireAroundInfectedEntities();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private void generateFireAroundInfectedEntities() {
        Set<UUID> infectedEntities = plugin.getInfectedEntities();
        
        synchronized (infectedEntities) {
            int processed = 0;
            int maxProcessPerTick = 30; // 增加处理数量
            
            for (UUID entityId : infectedEntities) {
                if (processed >= maxProcessPerTick) break;
                
                Entity entity = plugin.getServer().getEntity(entityId);
                if (entity != null && entity.isValid() && entity instanceof LivingEntity) {
                    // 为所有被感染的实体生成火焰，包括玩家
                    generateFireAroundEntity((LivingEntity) entity);
                    processed++;
                }
            }
        }
    }
    
    private void generateFireAroundEntity(LivingEntity entity) {
        Location entityLoc = entity.getLocation();
        World world = entity.getWorld();
        
        // 重要修复：检查玩家是否持有零度石，如果有则跳过火焰生成
        // 但注意：即使玩家被感染，如果持有零度石也不会生成火焰
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (plugin.hasZeroStone(player)) {
                return; // 持有零度石的玩家周围不生成火焰
            }
        }
        
        int firesGenerated = 0;
        int maxFiresPerEntity = 8; // 增加最大火焰数量
        
        // 优化火焰生成算法 - 修复位置计算
        for (int x = -3; x <= 3; x++) { // 扩大范围
            for (int z = -3; z <= 3; z++) {
                for (int y = -1; y <= 2; y++) { // 增加Y轴检查范围
                    if (firesGenerated >= maxFiresPerEntity) break;
                    
                    // 提高火焰生成几率，特别是靠近实体的位置
                    int distance = Math.abs(x) + Math.abs(z);
                    int spawnChance;
                    
                    if (distance <= 1) {
                        spawnChance = 85; // 靠近实体位置高几率
                    } else if (distance <= 2) {
                        spawnChance = 65; // 中等距离中等几率
                    } else {
                        spawnChance = 40; // 较远距离较低几率
                    }
                    
                    if (random.nextInt(100) < spawnChance) {
                        // 修复：使用实体脚部位置作为基准
                        int blockX = entityLoc.getBlockX() + x;
                        int blockY = (int) Math.floor(entityLoc.getY()) + y;
                        int blockZ = entityLoc.getBlockZ() + z;
                        
                        Block targetBlock = world.getBlockAt(blockX, blockY, blockZ);
                        
                        // 检查目标方块是否可燃且未被感染
                        if (plugin.isFlammable(targetBlock) && 
                            !plugin.getInfectedBlocks().contains(targetBlock.getLocation())) {
                            
                            // 检查目标方块上方是否可以放置火焰
                            Block fireBlock = targetBlock.getRelative(0, 1, 0);
                            if (fireBlock.getType().isAir() && 
                                !plugin.getInfectedBlocks().contains(fireBlock.getLocation())) {
                                
                                // 直接生成火焰，不经过infectBlock（避免递归问题）
                                fireBlock.setType(Material.FIRE);
                                Location fireLoc = fireBlock.getLocation();
                                plugin.getInfectedBlocks().add(fireLoc);
                                // 新增：记录感染时间
                                plugin.getInfectedBlockTimes().put(fireLoc, System.currentTimeMillis());
                                
                                // 标记区块中的感染火焰
                                Chunk chunk = fireBlock.getChunk();
                                PersistentDataContainer pdc = chunk.getPersistentDataContainer();
                                String fireKey = "infected_" + (fireLoc.getBlockX() & 15) + "_" + 
                                                fireLoc.getBlockY() + "_" + (fireLoc.getBlockZ() & 15);
                                pdc.set(new NamespacedKey(plugin, fireKey), 
                                       org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                                
                                firesGenerated++;
                                
                                // 视觉和声音效果
                                world.spawnParticle(plugin.getCustomFireParticle(), 
                                    fireLoc.add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02);
                                if (firesGenerated == 1) { // 只在第一次生成时播放声音
                                    world.playSound(fireLoc, Sound.BLOCK_FIRE_AMBIENT, 0.4f, 0.9f);
                                }
                                
                                // 新增：灵魂火焰粒子效果可以感染生物
                                infectEntitiesFromParticles(fireLoc, 2.0);
                            }
                        }
                    }
                }
            }
        }
        
        // 确保脚下火焰生成更可靠 - 修复版本
        Block standingBlock = world.getBlockAt(
            entityLoc.getBlockX(),
            (int) Math.floor(entityLoc.getY()) - 1, // 实体站立的方块
            entityLoc.getBlockZ()
        );
        
        if (plugin.isFlammable(standingBlock) && 
            !plugin.getInfectedBlocks().contains(standingBlock.getLocation())) {
            
            Block fireBlock = standingBlock.getRelative(0, 1, 0);
            if (fireBlock.getType().isAir() && 
                !plugin.getInfectedBlocks().contains(fireBlock.getLocation())) {
                
                // 直接生成火焰
                fireBlock.setType(Material.FIRE);
                Location fireLoc = fireBlock.getLocation();
                plugin.getInfectedBlocks().add(fireLoc);
                // 新增：记录感染时间
                plugin.getInfectedBlockTimes().put(fireLoc, System.currentTimeMillis());
                
                // 标记区块中的感染火焰
                Chunk chunk = fireBlock.getChunk();
                PersistentDataContainer pdc = chunk.getPersistentDataContainer();
                String fireKey = "infected_" + (fireLoc.getBlockX() & 15) + "_" + 
                                fireLoc.getBlockY() + "_" + (fireLoc.getBlockZ() & 15);
                pdc.set(new NamespacedKey(plugin, fireKey), 
                       org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                
                // 视觉效果
                world.spawnParticle(plugin.getCustomFireParticle(), 
                    fireLoc.add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.03);
                
                // 新增：灵魂火焰粒子效果可以感染生物
                infectEntitiesFromParticles(fireLoc, 2.0);
            }
        }
        
        // 增强视觉效果 - 使用自定义粒子
        for (int i = 0; i < 8; i++) { // 增加粒子数量
            double offsetX = (random.nextDouble() - 0.5) * 4.0;
            double offsetZ = (random.nextDouble() - 0.5) * 4.0;
            double offsetY = random.nextDouble() * 2.0;
            Location particleLoc = entityLoc.clone().add(offsetX, offsetY, offsetZ);
            world.spawnParticle(plugin.getCustomFlameTrail(), particleLoc, 2, 0.1, 0.1, 0.1, 0.02);
            world.spawnParticle(plugin.getCustomFireParticle(), particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
            
            // 新增：灵魂火焰粒子效果可以感染生物
            if (random.nextInt(100) < 20) { // 20%几率感染
                infectEntitiesFromParticles(particleLoc, 1.5);
            }
        }
    }
    
    // 新增方法：从粒子效果感染生物
    private void infectEntitiesFromParticles(Location particleLoc, double radius) {
        World world = particleLoc.getWorld();
        
        for (Entity entity : world.getNearbyEntities(particleLoc, radius, radius, radius)) {
            if (entity instanceof LivingEntity && 
                !plugin.getInfectedEntities().contains(entity.getUniqueId())) {
                
                // 修复：使用射线检测替代 hasLineOfSight(Location)
                boolean hasLineOfSight = hasLineOfSightToLocation((LivingEntity) entity, particleLoc);
                
                if (hasLineOfSight || entity.getLocation().distance(particleLoc) <= 1.0) {
                    plugin.infectEntity(entity, null); // 来源为null，表示来自环境
                }
            }
        }
    }
    
    // 新增方法：检查实体是否能看到指定位置
    private boolean hasLineOfSightToLocation(LivingEntity entity, Location targetLoc) {
        Location eyeLoc = entity.getEyeLocation();
        org.bukkit.util.Vector direction = targetLoc.toVector().subtract(eyeLoc.toVector());
        
        // 使用射线检测
        org.bukkit.util.RayTraceResult result = entity.getWorld().rayTraceBlocks(
            eyeLoc, 
            direction, 
            eyeLoc.distance(targetLoc),
            org.bukkit.FluidCollisionMode.NEVER,
            true
        );
        
        // 如果没有碰撞到方块，说明有视线
        return result == null;
    }
}
