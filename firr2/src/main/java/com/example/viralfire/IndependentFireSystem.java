package com.example.viralfire;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Random;

public class IndependentFireSystem {
    private final ViralFirePlugin plugin;
    private final Random random = new Random();
    
    // 兼容性粒子定义
    private final Particle RED_PARTICLE;
    private final boolean USE_DUST_OPTIONS;
    
    public IndependentFireSystem(ViralFirePlugin plugin) {
        this.plugin = plugin;
        
        // 检测可用的红色粒子效果
        Particle redParticle = Particle.FLAME; // 默认使用火焰粒子
        boolean useDustOptions = false;
        
        try {
            // 尝试使用REDSTONE粒子（较新版本）
            redParticle = Particle.valueOf("REDSTONE");
            useDustOptions = true;
        } catch (IllegalArgumentException e1) {
            try {
                // 尝试使用DUST粒子（其他版本）
                redParticle = Particle.valueOf("DUST");
                useDustOptions = true;
            } catch (IllegalArgumentException e2) {
                // 如果都不支持，使用FLAME粒子
                redParticle = Particle.FLAME;
                useDustOptions = false;
            }
        }
        
        RED_PARTICLE = redParticle;
        USE_DUST_OPTIONS = useDustOptions;
        
        plugin.getLogger().info("使用粒子效果: " + RED_PARTICLE.name() + ", 使用DustOptions: " + USE_DUST_OPTIONS);
    }
    
    public void startIndependentFireSystem() {
        // 启动独立病毒火传播任务
        new BukkitRunnable() {
            @Override
            public void run() {
                spreadViralFireIndependently();
            }
        }.runTaskTimer(plugin, 0L, 20L); // 每秒执行一次
        
        // 启动病毒火粒子效果任务
        new BukkitRunnable() {
            @Override
            public void run() {
                updateViralFireParticles();
            }
        }.runTaskTimer(plugin, 0L, 5L); // 每5tick执行一次
        
        // 启动实体接触感染检测
        new BukkitRunnable() {
            @Override
            public void run() {
                checkEntityContactWithViralFire();
            }
        }.runTaskTimer(plugin, 0L, 10L); // 每10tick执行一次
    }
    
    /**
     * 独立传播病毒火 - 不依赖感染者
     */
    private void spreadViralFireIndependently() {
        int processed = 0;
        int maxProcessPerTick = 40;
        
        synchronized (plugin.getInfectedBlocks()) {
            // 创建副本避免并发修改
            java.util.List<Location> infectedBlocksCopy = new java.util.ArrayList<>(plugin.getInfectedBlocks());
            
            for (Location fireLoc : infectedBlocksCopy) {
                if (processed >= maxProcessPerTick) break;
                
                // 检查区块是否加载
                if (!fireLoc.getWorld().isChunkLoaded(fireLoc.getBlockX() >> 4, fireLoc.getBlockZ() >> 4)) {
                    continue;
                }
                
                Block fireBlock = fireLoc.getBlock();
                
                // 如果火焰已经被熄灭，从感染列表中移除
                if (fireBlock.getType() != Material.FIRE) {
                    plugin.getInfectedBlocks().remove(fireLoc);
                    plugin.getInfectedBlockTimes().remove(fireLoc);
                    continue;
                }
                
                // 传播病毒火到周围的普通火焰
                spreadToNearbyFires(fireBlock);
                processed++;
                
                // 从病毒火传播到可燃方块
                spreadToFlammableBlocks(fireBlock);
            }
        }
    }
    
    /**
     * 病毒火感染周围的普通火焰
     */
    private void spreadToNearbyFires(Block viralFireBlock) {
        Location center = viralFireBlock.getLocation();
        World world = viralFireBlock.getWorld();
        
        int spreadRadius = 3;
        int maxFiresToInfect = 5;
        int firesInfected = 0;
        
        for (int x = -spreadRadius; x <= spreadRadius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -spreadRadius; z <= spreadRadius; z++) {
                    if (firesInfected >= maxFiresToInfect) break;
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block targetBlock = center.clone().add(x, y, z).getBlock();
                    
                    // 如果是普通火焰且未被感染
                    if (targetBlock.getType() == Material.FIRE && 
                        !plugin.getInfectedBlocks().contains(targetBlock.getLocation())) {
                        
                        // 根据距离决定感染几率
                        int distance = Math.abs(x) + Math.abs(z) + Math.abs(y);
                        int infectionChance;
                        
                        if (distance <= 1) {
                            infectionChance = 60; // 近距离高几率
                        } else if (distance <= 2) {
                            infectionChance = 40; // 中等距离中等几率
                        } else {
                            infectionChance = 25; // 远距离低几率
                        }
                        
                        if (random.nextInt(100) < infectionChance) {
                            infectFireBlock(targetBlock, null);
                            firesInfected++;
                            
                            // 视觉效果
                            Location targetLoc = targetBlock.getLocation();
                            spawnRedParticles(targetLoc.add(0.5, 0.5, 0.5), 8);
                            world.playSound(targetLoc, Sound.BLOCK_FIRE_AMBIENT, 0.3f, 0.8f);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 病毒火传播到可燃方块
     */
    private void spreadToFlammableBlocks(Block viralFireBlock) {
        Location center = viralFireBlock.getLocation();
        World world = viralFireBlock.getWorld();
        
        int spreadRadius = 2;
        int maxBlocksToInfect = 3;
        int blocksInfected = 0;
        
        for (int x = -spreadRadius; x <= spreadRadius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -spreadRadius; z <= spreadRadius; z++) {
                    if (blocksInfected >= maxBlocksToInfect) break;
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block targetBlock = center.clone().add(x, y, z).getBlock();
                    
                    // 检查目标方块是否可燃且未被感染
                    if (plugin.isFlammable(targetBlock) && 
                        !plugin.getInfectedBlocks().contains(targetBlock.getLocation()) &&
                        targetBlock.getType() != Material.FIRE) {
                        
                        // 检查目标方块上方是否可以放置火焰
                        Block fireBlock = targetBlock.getRelative(0, 1, 0);
                        if (fireBlock.getType().isAir() && 
                            !plugin.getInfectedBlocks().contains(fireBlock.getLocation())) {
                            
                            int distance = Math.abs(x) + Math.abs(z) + Math.abs(y);
                            int spawnChance;
                            
                            if (distance <= 1) {
                                spawnChance = 40; // 近距离中等几率
                            } else {
                                spawnChance = 20; // 远距离低几率
                            }
                            
                            if (random.nextInt(100) < spawnChance) {
                                // 生成病毒火
                                fireBlock.setType(Material.FIRE);
                                infectFireBlock(fireBlock, null);
                                blocksInfected++;
                                
                                // 视觉效果
                                Location fireLoc = fireBlock.getLocation();
                                spawnRedParticles(fireLoc.add(0.5, 0.5, 0.5), 5);
                                world.playSound(fireLoc, Sound.BLOCK_FIRE_AMBIENT, 0.2f, 0.9f);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 感染火焰方块
     */
    private void infectFireBlock(Block fireBlock, Entity source) {
        Location fireLoc = fireBlock.getLocation();
        
        if (plugin.getInfectedBlocks().contains(fireLoc)) {
            return; // 已经感染
        }
        
        plugin.getInfectedBlocks().add(fireLoc);
        plugin.getInfectedBlockTimes().put(fireLoc, System.currentTimeMillis());
        
        // 标记区块中的感染火焰
        Chunk chunk = fireBlock.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String fireKey = "infected_" + (fireLoc.getBlockX() & 15) + "_" + 
                        fireLoc.getBlockY() + "_" + (fireLoc.getBlockZ() & 15);
        pdc.set(new NamespacedKey(plugin, fireKey), 
               org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        
        // 红色粒子效果
        spawnRedParticles(fireLoc, 3);
    }
    
    /**
     * 更新病毒火粒子效果 - 显示红色粒子
     */
    private void updateViralFireParticles() {
        int particleCount = 0;
        int maxParticles = 200;
        
        synchronized (plugin.getInfectedBlocks()) {
            for (Location fireLoc : plugin.getInfectedBlocks()) {
                if (particleCount >= maxParticles) break;
                
                // 检查区块是否加载
                if (!fireLoc.getWorld().isChunkLoaded(fireLoc.getBlockX() >> 4, fireLoc.getBlockZ() >> 4)) {
                    continue;
                }
                
                Block fireBlock = fireLoc.getBlock();
                
                // 如果火焰已经被熄灭，跳过
                if (fireBlock.getType() != Material.FIRE) {
                    continue;
                }
                
                // 生成红色粒子效果
                spawnRedParticles(fireLoc, 1);
                particleCount++;
                
                // 偶尔生成额外的粒子爆发
                if (random.nextInt(100) < 5) {
                    spawnRedParticleBurst(fireLoc);
                }
            }
        }
    }
    
    /**
     * 生成红色粒子效果
     */
    private void spawnRedParticles(Location location, int count) {
        World world = location.getWorld();
        Location particleLoc = location.clone().add(0.5, 0.3, 0.5);
        
        if (USE_DUST_OPTIONS) {
            // 使用DustOptions的红色粒子（新版本）
            try {
                Object dustOptions = createDustOptions(Color.RED, 1.0f);
                world.spawnParticle(RED_PARTICLE, particleLoc, count, 
                                  0.2, 0.1, 0.2, 0, dustOptions);
            } catch (Exception e) {
                // 回退到普通粒子
                world.spawnParticle(Particle.FLAME, particleLoc, count, 0.2, 0.1, 0.2, 0.01);
            }
        } else {
            // 使用普通火焰粒子
            world.spawnParticle(Particle.FLAME, particleLoc, count, 0.2, 0.1, 0.2, 0.01);
            
            // 添加一些烟雾效果增强视觉
            if (random.nextInt(100) < 30) {
                world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0.15, 0.05, 0.15, 0.01);
            }
        }
    }
    
    /**
     * 创建DustOptions对象（兼容不同版本）
     */
    private Object createDustOptions(Color color, float size) {
        try {
            // 尝试使用新版API
            return new Particle.DustOptions(color, size);
        } catch (NoClassDefFoundError e) {
            try {
                // 尝试使用旧版API
                Class<?> dustOptionsClass = Class.forName("org.bukkit.Particle$DustOptions");
                java.lang.reflect.Constructor<?> constructor = dustOptionsClass.getConstructor(Color.class, float.class);
                return constructor.newInstance(color, size);
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    /**
     * 生成红色粒子爆发效果
     */
    private void spawnRedParticleBurst(Location location) {
        World world = location.getWorld();
        Location center = location.clone().add(0.5, 0.5, 0.5);
        
        // 粒子爆发
        for (int i = 0; i < 5; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.8;
            double offsetY = random.nextDouble() * 0.6;
            double offsetZ = (random.nextDouble() - 0.5) * 0.8;
            
            Location particleLoc = center.clone().add(offsetX, offsetY, offsetZ);
            
            if (USE_DUST_OPTIONS) {
                try {
                    Object dustOptions = createDustOptions(Color.RED, 1.2f);
                    world.spawnParticle(RED_PARTICLE, particleLoc, 1, 0, 0, 0, 0, dustOptions);
                } catch (Exception e) {
                    world.spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0.01);
                }
            } else {
                world.spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0.01);
            }
        }
        
        // 偶尔播放声音
        if (random.nextInt(100) < 20) {
            world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.2f, 0.7f);
        }
    }
    
    /**
     * 检测实体接触病毒火
     */
    private void checkEntityContactWithViralFire() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                // 如果实体已经被感染，跳过
                if (plugin.getInfectedEntities().contains(entity.getUniqueId())) {
                    continue;
                }
                
                // 检查实体是否站在病毒火上
                if (isStandingOnViralFire(entity)) {
                    infectEntityFromViralFire(entity);
                }
                
                // 检查实体是否在病毒火中
                if (isInViralFire(entity)) {
                    infectEntityFromViralFire(entity);
                }
            }
        }
    }
    
    /**
     * 检查实体是否站在病毒火上
     */
    private boolean isStandingOnViralFire(LivingEntity entity) {
        Location entityLoc = entity.getLocation();
        
        // 检查脚下方块
        Block standingBlock = entityLoc.getBlock();
        if (standingBlock.getType() == Material.FIRE && 
            plugin.getInfectedBlocks().contains(standingBlock.getLocation())) {
            return true;
        }
        
        // 检查脚下方块的下方（实体可能站在火焰上）
        Block belowBlock = entityLoc.clone().subtract(0, 1, 0).getBlock();
        Block fireOnBelow = belowBlock.getRelative(0, 1, 0);
        if (fireOnBelow.getType() == Material.FIRE && 
            plugin.getInfectedBlocks().contains(fireOnBelow.getLocation()) &&
            fireOnBelow.getLocation().getBlockY() == entityLoc.getBlockY()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查实体是否在病毒火中
     */
    private boolean isInViralFire(LivingEntity entity) {
        Location entityLoc = entity.getLocation();
        
        // 检查实体所在位置的方块
        Block currentBlock = entityLoc.getBlock();
        if (currentBlock.getType() == Material.FIRE && 
            plugin.getInfectedBlocks().contains(currentBlock.getLocation())) {
            return true;
        }
        
        // 检查实体周围的方块
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block nearbyBlock = entityLoc.clone().add(x, 0, z).getBlock();
                if (nearbyBlock.getType() == Material.FIRE && 
                    plugin.getInfectedBlocks().contains(nearbyBlock.getLocation())) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 从病毒火感染实体
     */
    private void infectEntityFromViralFire(LivingEntity entity) {
        // 玩家持有零度石则免疫
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (plugin.hasZeroStone(player)) {
                return;
            }
        }
        
        // 感染实体
        plugin.infectEntity(entity, null);
        
        // 感染效果
        Location entityLoc = entity.getLocation();
        World world = entity.getWorld();
        
        // 感染效果粒子
        if (USE_DUST_OPTIONS) {
            try {
                Object dustOptions = createDustOptions(Color.RED, 1.5f);
                world.spawnParticle(RED_PARTICLE, entityLoc.add(0, 1, 0), 15, 
                                  0.5, 0.5, 0.5, 0, dustOptions);
            } catch (Exception e) {
                world.spawnParticle(Particle.FLAME, entityLoc.add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.02);
            }
        } else {
            world.spawnParticle(Particle.FLAME, entityLoc.add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.02);
        }
        
        world.playSound(entityLoc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
        
        if (entity instanceof Player) {
            Player player = (Player) entity;
            player.sendMessage(ChatColor.RED + "你踩到了病毒火焰！感染正在蔓延...");
        }
    }
    
    /**
     * 手动创建病毒火（可用于命令或其他事件）
     */
    public void createViralFire(Location location, Entity source) {
        Block fireBlock = location.getBlock();
        
        if (fireBlock.getType().isAir()) {
            fireBlock.setType(Material.FIRE);
        }
        
        if (fireBlock.getType() == Material.FIRE) {
            infectFireBlock(fireBlock, source);
            
            // 视觉效果
            World world = location.getWorld();
            spawnRedParticleBurst(location);
            world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.8f);
        }
    }
    
    /**
     * 检查位置是否有病毒火
     */
    public boolean isViralFire(Location location) {
        return plugin.getInfectedBlocks().contains(location);
    }
    
    /**
     * 移除病毒火
     */
    public boolean removeViralFire(Location location) {
        boolean removed = plugin.getInfectedBlocks().remove(location);
        if (removed) {
            plugin.getInfectedBlockTimes().remove(location);
            
            // 清除区块标记
            Chunk chunk = location.getChunk();
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            String key = "infected_" + (location.getBlockX() & 15) + "_" + 
                        location.getBlockY() + "_" + (location.getBlockZ() & 15);
            pdc.remove(new NamespacedKey(plugin, key));
        }
        return removed;
    }
}
