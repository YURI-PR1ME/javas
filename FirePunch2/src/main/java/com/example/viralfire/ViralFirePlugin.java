package com.example.viralfire;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class ViralFirePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final NamespacedKey viralFireKey = new NamespacedKey(this, "viral_fire");
    private final NamespacedKey viralFireWandKey = new NamespacedKey(this, "viral_fire_wand");
    private final NamespacedKey viralFireWandOneUseKey = new NamespacedKey(this, "viral_fire_wand_one_use");
    private final NamespacedKey zeroStoneKey = new NamespacedKey(this, "zero_stone");
    private final NamespacedKey infectedBlockKey = new NamespacedKey(this, "infected_block");
    private final NamespacedKey infectedEntityKey = new NamespacedKey(this, "infected_entity");
    private final NamespacedKey infectedArrowKey = new NamespacedKey(this, "infected_arrow");
    
    private final Set<Location> infectedBlocks = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> infectedEntities = Collections.synchronizedSet(new HashSet<>());
    
    private final Map<Location, Long> infectedBlockTimes = new HashMap<>();
    
    // 调整性能参数：增加限制但确保功能正常
    private final int INFECTION_RADIUS = 4; // 恢复传播半径
    private final int INFECTION_DURATION = 6000;
    private final int MAX_INFECTED_BLOCKS = 2000; // 增加方块上限
    private final int MAX_INFECTED_ENTITIES = 300; // 增加实体上限
    private final int MAX_PARTICLES_PER_TICK = 120; // 增加粒子限制
    private final int CLEANUP_INTERVAL = 6000;
    
    // 自定义粒子效果
    private final Particle CUSTOM_FIRE_PARTICLE = Particle.SOUL_FIRE_FLAME;
    private final Particle CUSTOM_FLAME_TRAIL = Particle.LAVA;
    private final Particle CUSTOM_SMOKE_PARTICLE = Particle.CAMPFIRE_COSY_SMOKE;
    
    // 系统实例
    private FireGenerator fireGenerator;
    private IndependentFireSystem independentFireSystem;
    
    // 性能统计
    private long lastCleanupTime = 0;
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        
        // 初始化系统
        fireGenerator = new FireGenerator(this);
        independentFireSystem = new IndependentFireSystem(this);
        independentFireSystem.startIndependentFireSystem();
        
        // 设置命令执行器
        Objects.requireNonNull(this.getCommand("getfirewand")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("getfirewandoneuse")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("getzerostone")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("cureall")).setExecutor(this);
        
        // 设置游戏规则，确保火焰传播
        setupGameRules();
        
        // 恢复任务频率以确保功能正常
        new ViralInfectionTask().runTaskTimer(this, 0L, 20L); // 恢复到20tick
        new InfectionParticleTask().runTaskTimer(this, 0L, 8L); // 恢复到8tick
        new CustomParticleEffectTask().runTaskTimer(this, 0L, 8L); // 恢复到8tick
        new FireToFireInfectionTask().runTaskTimer(this, 0L, 12L); // 恢复到12tick
        new PerformanceCleanupTask().runTaskTimer(this, 0L, CLEANUP_INTERVAL);
        
        // 启动火焰生成任务
        fireGenerator.startFireGeneration();
        
        getLogger().info("平衡版永久感染病毒火焰插件已启用!");
        
        // 恢复之前感染的方块和实体
        restoreInfections();
    }
    
    public IndependentFireSystem getIndependentFireSystem() {
        return independentFireSystem;
    }
    
    @Override
    public void onDisable() {
        saveInfections();
        infectedBlocks.clear();
        infectedEntities.clear();
        infectedBlockTimes.clear();
        getLogger().info("平衡版永久感染病毒火焰插件已禁用!");
    }

    private void setupGameRules() {
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_FIRE_TICK, true);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家才能执行此命令。");
            return true;
        }
        
        Player player = (Player) sender;
        String commandName = command.getName().toLowerCase();
        
        switch (commandName) {
            case "getfirewand":
                if (!player.hasPermission("viralfire.wand")) {
                    player.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
                    return true;
                }
                player.getInventory().addItem(createFireWand());
                player.sendMessage(ChatColor.DARK_RED + "你获得了平衡版病毒魔杖...火焰将正常传播!");
                return true;
                
            case "getfirewandoneuse":
                if (!player.hasPermission("viralfire.wand.oneuse")) {
                    player.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
                    return true;
                }
                player.getInventory().addItem(createFireWandOneUse());
                player.sendMessage(ChatColor.DARK_RED + "你获得了平衡版劣化版病毒魔杖（一次性）!");
                return true;
                
            case "getzerostone":
                if (!player.hasPermission("viralfire.stone")) {
                    player.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
                    return true;
                }
                player.getInventory().addItem(createZeroStone());
                player.sendMessage(ChatColor.AQUA + "已获得平衡版零度石!");
                return true;
                
            case "cureall":
                if (!player.hasPermission("viralfire.admin")) {
                    player.sendMessage(ChatColor.RED + "你没有管理员权限。");
                    return true;
                }
                infectedBlocks.clear();
                infectedEntities.clear();
                infectedBlockTimes.clear();
                getServer().broadcastMessage(ChatColor.GREEN + "所有病毒火焰已被永久清除！");
                return true;
                
            default:
                return false;
        }
    }

    private void restoreInfections() {
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                PersistentDataContainer chunkPdc = chunk.getPersistentDataContainer();
                
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                            String key = "infected_" + x + "_" + y + "_" + z;
                            if (chunkPdc.has(new NamespacedKey(this, key), PersistentDataType.BYTE)) {
                                Location loc = new Location(world, chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                                if (loc.getBlock().getType() == Material.FIRE) {
                                    // 恢复所有感染方块，不检查上限
                                    infectedBlocks.add(loc);
                                    infectedBlockTimes.put(loc, System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            }
        }
        getLogger().info("已恢复 " + infectedBlocks.size() + " 个永久感染火焰方块");
    }

    private void saveInfections() {
        synchronized (infectedBlocks) {
            for (Location loc : infectedBlocks) {
                Chunk chunk = loc.getChunk();
                PersistentDataContainer pdc = chunk.getPersistentDataContainer();
                String key = "infected_" + (loc.getBlockX() & 15) + "_" + loc.getBlockY() + "_" + (loc.getBlockZ() & 15);
                pdc.set(new NamespacedKey(this, key), PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    public ItemStack createFireWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "平衡版病毒魔杖");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "右键释放平衡版病毒火焰",
                    ChatColor.RED + "平衡性能与功能",
                    ChatColor.GRAY + "确保病毒火焰正常传播..."
            ));
            
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(viralFireWandKey, PersistentDataType.BYTE, (byte) 1);
            
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public ItemStack createFireWandOneUse() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "平衡版劣化魔杖");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "右键释放平衡版病毒火焰",
                    ChatColor.RED + "平衡性能与功能",
                    ChatColor.GRAY + "一次性使用..."
            ));
            
            meta.setUnbreakable(false);
            
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(viralFireWandOneUseKey, PersistentDataType.BYTE, (byte) 1);
            
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public ItemStack createZeroStone() {
        ItemStack stone = new ItemStack(Material.BLUE_ICE);
        ItemMeta meta = stone.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "平衡版零度石");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "携带时阻止病毒火焰感染",
                    ChatColor.RED + "平衡性能版本",
                    ChatColor.GRAY + "确保功能正常..."
            ));
            
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(zeroStoneKey, PersistentDataType.BYTE, (byte) 1);
            
            stone.setItemMeta(meta);
        }
        return stone;
    }

    public boolean isFlammable(Block block) {
        if (block.getType().isAir()) return false;
        
        Material type = block.getType();
        
        Set<Material> nonFlammable = Set.of(
            Material.BEDROCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.WATER, Material.LAVA, Material.NETHERITE_BLOCK,
            Material.ANCIENT_DEBRIS, Material.END_STONE, Material.END_PORTAL_FRAME,
            Material.STONE, Material.COBBLESTONE, Material.GRANITE, Material.DIORITE, 
            Material.ANDESITE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE
        );
        
        return !nonFlammable.contains(type);
    }

    // 修改：放宽感染限制
    private boolean canInfectMoreBlocks() {
        // 允许感染更多方块，但仍有上限
        return infectedBlocks.size() < MAX_INFECTED_BLOCKS;
    }

    // 修改：放宽实体感染限制
    private boolean canInfectMoreEntities() {
        // 允许感染更多实体，但仍有上限
        return infectedEntities.size() < MAX_INFECTED_ENTITIES;
    }

    public void infectBlock(Block block, Entity source) {
        // 检查是否达到上限 - 但允许魔杖等直接感染
        if (!canInfectMoreBlocks() && source == null) {
            // 如果是自然传播且达到上限，则跳过
            return;
        }
        
        if (!isFlammable(block) || block.getType().isAir()) return;
        
        Location loc = block.getLocation();
        if (infectedBlocks.contains(loc)) return;
        
        infectedBlocks.add(loc);
        infectedBlockTimes.put(loc, System.currentTimeMillis());
        
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String key = "infected_" + (loc.getBlockX() & 15) + "_" + loc.getBlockY() + "_" + (loc.getBlockZ() & 15);
        pdc.set(new NamespacedKey(this, key), PersistentDataType.BYTE, (byte) 1);
        
        // 恢复适当的粒子效果
        block.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, loc.clone().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
        block.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 0.7f);
        
        infectEntitiesFromParticles(loc.clone().add(0.5, 0.5, 0.5), 2.5);
        
        // 确保生成火焰
        if (!block.getType().isAir()) {
            Block fireBlock = block.getRelative(0, 1, 0);
            if (fireBlock.getType().isAir() && !infectedBlocks.contains(fireBlock.getLocation())) {
                fireBlock.setType(Material.FIRE);
                Location fireLoc = fireBlock.getLocation();
                infectedBlocks.add(fireLoc);
                infectedBlockTimes.put(fireLoc, System.currentTimeMillis());
                
                PersistentDataContainer firePdc = fireBlock.getChunk().getPersistentDataContainer();
                String fireKey = "infected_" + (fireLoc.getBlockX() & 15) + "_" + fireLoc.getBlockY() + "_" + (fireLoc.getBlockZ() & 15);
                firePdc.set(new NamespacedKey(this, fireKey), PersistentDataType.BYTE, (byte) 1);
                
                infectEntitiesFromParticles(fireLoc.clone().add(0.5, 0.5, 0.5), 2.5);
            }
        }
    }

    private boolean canInfectEntity(Entity entity) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                return false;
            }
        }
        return true;
    }

    private void cureEntity(Entity entity) {
        UUID entityId = entity.getUniqueId();
        if (infectedEntities.contains(entityId)) {
            infectedEntities.remove(entityId);
            
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.remove(infectedEntityKey);
            
            if (entity instanceof LivingEntity) {
                entity.setFireTicks(0);
            }
            
            Location loc = entity.getLocation();
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 8, 0.4, 0.6, 0.4, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.0f);
            
            if (entity instanceof Player) {
                Player player = (Player) entity;
                player.sendMessage(ChatColor.GREEN + "你已被治愈，病毒火焰的影响消失了。");
            }
        }
    }

    public void infectEntity(Entity entity, Entity source) {
        // 检查实体是否可以被感染
        if (!canInfectEntity(entity)) {
            return;
        }
        
        // 放宽实体感染限制
        if (!canInfectMoreEntities() && source == null) {
            // 如果是自然传播且达到上限，则跳过
            return;
        }
        
        if (entity instanceof Player || entity instanceof LivingEntity || entity instanceof Arrow) {
            UUID entityId = entity.getUniqueId();
            if (infectedEntities.contains(entityId)) return;
            
            infectedEntities.add(entityId);
            
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(infectedEntityKey, PersistentDataType.BYTE, (byte) 1);
            
            if (entity instanceof LivingEntity) {
                entity.setFireTicks(INFECTION_DURATION);
                applyInfectionEffects((LivingEntity) entity);
            }
            
            // 恢复适当的粒子效果
            Location entityLoc = entity.getLocation();
            entityLoc.getWorld().spawnParticle(CUSTOM_FLAME_TRAIL, entityLoc, 8, 0.4, 0.6, 0.4, 0);
            entityLoc.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, entityLoc, 6, 0.3, 0.4, 0.3, 0);
            entityLoc.getWorld().playSound(entityLoc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
            
            if (entity instanceof Player) {
                Player player = (Player) entity;
                player.sendMessage(ChatColor.RED + "你被平衡版病毒火焰感染了！");
            }
        }
    }
    
    private void applyInfectionEffects(LivingEntity entity) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false));
        
        try {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, false));
        } catch (IllegalArgumentException e) {
            try {
                PotionEffectType slowType = PotionEffectType.getByName("SLOW");
                if (slowType != null) {
                    entity.addPotionEffect(new PotionEffect(slowType, 100, 1, true, false));
                }
            } catch (Exception ex) {
                getLogger().warning("无法应用缓慢效果");
            }
        }
    }
    
    private void infectEntitiesFromParticles(Location particleLoc, double radius) {
        World world = particleLoc.getWorld();
        Random random = new Random();
        
        // 提高感染几率
        if (random.nextInt(100) < 35) {
            for (Entity entity : world.getNearbyEntities(particleLoc, radius, radius, radius)) {
                if (entity instanceof LivingEntity && 
                    !infectedEntities.contains(entity.getUniqueId()) &&
                    canInfectEntity(entity) &&
                    canInfectMoreEntities()) {
                    
                    boolean hasLineOfSight = hasLineOfSightToLocation((LivingEntity) entity, particleLoc);
                    
                    if (hasLineOfSight || entity.getLocation().distance(particleLoc) <= 1.5) {
                        infectEntity(entity, null);
                    }
                }
            }
        }
    }
    
    private boolean hasLineOfSightToLocation(LivingEntity entity, Location targetLoc) {
        Location eyeLoc = entity.getEyeLocation();
        Vector direction = targetLoc.toVector().subtract(eyeLoc.toVector());
        
        RayTraceResult result = entity.getWorld().rayTraceBlocks(
            eyeLoc, 
            direction, 
            eyeLoc.distance(targetLoc),
            org.bukkit.FluidCollisionMode.NEVER,
            true
        );
        
        return result == null;
    }

    private void spreadInfectionFromBlock(Block block) {
        Location center = block.getLocation();
        
        List<Block> blocksToInfect = new ArrayList<>();
        
        for (int x = -INFECTION_RADIUS; x <= INFECTION_RADIUS; x++) {
            for (int y = -1; y <= INFECTION_RADIUS; y++) {
                for (int z = -INFECTION_RADIUS; z <= INFECTION_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block target = center.clone().add(x, y, z).getBlock();
                    if (isFlammable(target) && !infectedBlocks.contains(target.getLocation())) {
                        // 提高感染几率
                        if (new Random().nextInt(100) < 40) {
                            blocksToInfect.add(target);
                        }
                    }
                }
            }
        }
        
        if (!blocksToInfect.isEmpty()) {
            for (Block target : blocksToInfect) {
                if (!infectedBlocks.contains(target.getLocation()) && canInfectMoreBlocks()) {
                    infectBlock(target, null);
                }
            }
        }
    }

    private void spreadInfectionFromEntity(Entity entity) {
        if (!canInfectEntity(entity)) {
            return;
        }
        
        Location center = entity.getLocation();
        
        for (Entity nearby : entity.getNearbyEntities(INFECTION_RADIUS, INFECTION_RADIUS, INFECTION_RADIUS)) {
            if (nearby instanceof LivingEntity && !infectedEntities.contains(nearby.getUniqueId())) {
                if (!canInfectEntity(nearby) || !canInfectMoreEntities()) {
                    continue;
                }
                
                if (nearby.getLocation().distance(center) <= 3.0) {
                    boolean hasLineOfSight = hasLineOfSightToEntity((LivingEntity) entity, (LivingEntity) nearby);
                    
                    if (hasLineOfSight || nearby.getLocation().distance(center) <= 2.0) {
                        infectEntity(nearby, entity);
                    }
                }
            }
        }
        
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (hasZeroStone(player)) {
                return;
            }
        }
        
        Block standingBlock = center.getBlock();
        if (isFlammable(standingBlock) && !infectedBlocks.contains(standingBlock.getLocation())) {
            // 提高脚下感染几率
            if (new Random().nextInt(100) < 70 && canInfectMoreBlocks()) {
                infectBlock(standingBlock, entity);
            }
        }
    }
    
    private boolean hasLineOfSightToEntity(LivingEntity from, LivingEntity to) {
        Location fromEyeLoc = from.getEyeLocation();
        Location toEyeLoc = to.getEyeLocation();
        Vector direction = toEyeLoc.toVector().subtract(fromEyeLoc.toVector());
        
        RayTraceResult result = from.getWorld().rayTraceBlocks(
            fromEyeLoc, 
            direction, 
            fromEyeLoc.distance(toEyeLoc),
            org.bukkit.FluidCollisionMode.NEVER,
            true
        );
        
        return result == null;
    }

    public boolean hasZeroStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(zeroStoneKey, PersistentDataType.BYTE)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<UUID> getInfectedEntities() {
        return infectedEntities;
    }

    public Set<Location> getInfectedBlocks() {
        return infectedBlocks;
    }
    
    public Map<Location, Long> getInfectedBlockTimes() {
        return infectedBlockTimes;
    }

    public Particle getCustomFireParticle() {
        return CUSTOM_FIRE_PARTICLE;
    }
    
    public Particle getCustomFlameTrail() {
        return CUSTOM_FLAME_TRAIL;
    }
    
    public Particle getCustomSmokeParticle() {
        return CUSTOM_SMOKE_PARTICLE;
    }

    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            
            if (pdc.has(viralFireWandKey, PersistentDataType.BYTE) && 
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                
                player.sendMessage(ChatColor.DARK_RED + "平衡版病毒火焰已被释放...");
                launchViralFire(player);
                event.setCancelled(true);
            }
            else if (pdc.has(viralFireWandOneUseKey, PersistentDataType.BYTE) && 
                     (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                
                player.sendMessage(ChatColor.DARK_RED + "平衡版不稳定病毒火焰被释放了！");
                launchViralFireOneUse(player);
                
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().removeItem(item);
                }
                
                event.setCancelled(true);
            }
        }
    }

    private void launchViralFire(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        
        player.getWorld().spawnParticle(Particle.EXPLOSION, start, 2);
        player.getWorld().playSound(start, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.8f);
        
        new BukkitRunnable() {
            double distance = 0;
            final int maxDistance = 30; // 恢复射程
            
            @Override
            public void run() {
                if (distance > maxDistance) {
                    cancel();
                    return;
                }
                
                Location checkLoc = start.clone().add(direction.clone().multiply(distance));
                
                for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 2.5, 2.5, 2.5)) {
                    if (entity instanceof LivingEntity && entity != player) {
                        if (canInfectEntity(entity) && canInfectMoreEntities()) {
                            infectEntity(entity, player);
                        }
                    }
                }
                
                Block block = checkLoc.getBlock();
                // 魔杖感染不受上限限制
                infectBlock(block, player);
                
                // 恢复适当的粒子效果
                checkLoc.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, checkLoc, 6, 0.3, 0.3, 0.3, 0);
                checkLoc.getWorld().spawnParticle(CUSTOM_SMOKE_PARTICLE, checkLoc, 3, 0.2, 0.2, 0.2, 0);
                
                infectEntitiesFromParticles(checkLoc, 3.0);
                
                distance += 1.0; // 恢复移动速度
            }
        }.runTaskTimer(this, 0L, 1L);
    }
    
    private void launchViralFireOneUse(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        
        player.getWorld().spawnParticle(Particle.CLOUD, start, 4);
        player.getWorld().playSound(start, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.6f);
        
        new BukkitRunnable() {
            double distance = 0;
            final int maxDistance = 25;
            
            @Override
            public void run() {
                if (distance > maxDistance) {
                    cancel();
                    return;
                }
                
                Location checkLoc = start.clone().add(direction.clone().multiply(distance));
                
                for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 2.0, 2.0, 2.0)) {
                    if (entity instanceof LivingEntity && entity != player) {
                        if (new Random().nextInt(100) < 50 && canInfectEntity(entity) && canInfectMoreEntities()) {
                            infectEntity(entity, player);
                        }
                    }
                }
                
                Block block = checkLoc.getBlock();
                if (new Random().nextInt(100) < 40) {
                    // 魔杖感染不受上限限制
                    infectBlock(block, player);
                }
                
                checkLoc.getWorld().spawnParticle(Particle.FLAME, checkLoc, 2, 0.2, 0.2, 0.2, 0);
                checkLoc.getWorld().spawnParticle(Particle.CLOUD, checkLoc, 1, 0.1, 0.1, 0.1, 0);
                
                if (new Random().nextInt(100) < 20) {
                    infectEntitiesFromParticles(checkLoc, 2.0);
                }
                
                distance += 0.8;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    // 恢复处理数量
    private class ViralInfectionTask extends BukkitRunnable {
        @Override
        public void run() {
            int processed = 0;
            int maxProcessPerTick = 50; // 恢复处理数量
            
            List<Location> blocksToProcess;
            synchronized (infectedBlocks) {
                blocksToProcess = new ArrayList<>(infectedBlocks);
            }
            
            Iterator<Location> blockIterator = blocksToProcess.iterator();
            while (blockIterator.hasNext() && processed < maxProcessPerTick) {
                Location loc = blockIterator.next();
                Block block = loc.getBlock();
                
                if (block.getType() != Material.FIRE) {
                    synchronized (infectedBlocks) {
                        infectedBlocks.remove(loc);
                        infectedBlockTimes.remove(loc);
                    }
                    continue;
                }
                
                spreadInfectionFromBlock(block);
                processed++;
            }
            
            List<UUID> entitiesToProcess;
            synchronized (infectedEntities) {
                entitiesToProcess = new ArrayList<>(infectedEntities);
            }
            
            Iterator<UUID> entityIterator = entitiesToProcess.iterator();
            while (entityIterator.hasNext()) {
                UUID entityId = entityIterator.next();
                Entity entity = getServer().getEntity(entityId);
                
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    synchronized (infectedEntities) {
                        infectedEntities.remove(entityId);
                    }
                    continue;
                }
                
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        cureEntity(entity);
                        continue;
                    }
                }
                
                if (entity instanceof LivingEntity && entity.getFireTicks() <= 100) {
                    entity.setFireTicks(INFECTION_DURATION);
                }
                
                spreadInfectionFromEntity(entity);
            }
        }
    }

    // 恢复粒子数量
    private class InfectionParticleTask extends BukkitRunnable {
        @Override
        public void run() {
            List<Location> blocksToProcess;
            synchronized (infectedBlocks) {
                blocksToProcess = new ArrayList<>(infectedBlocks);
            }
            
            int particleCount = 0;
            
            for (Location loc : blocksToProcess) {
                if (particleCount >= MAX_PARTICLES_PER_TICK) break;
                
                if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    // 恢复适当的粒子效果
                    loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.3, 0.5), 2, 0.2, 0.1, 0.2, 0);
                    particleCount++;
                }
            }
            
            List<UUID> entitiesToProcess;
            synchronized (infectedEntities) {
                entitiesToProcess = new ArrayList<>(infectedEntities);
            }
            
            for (UUID entityId : entitiesToProcess) {
                Entity entity = getServer().getEntity(entityId);
                if (entity != null && entity.isValid()) {
                    Location loc = entity.getLocation();
                    // 恢复适当的粒子效果
                    loc.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.3, 0.3, 0.3, 0);
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.2, 0.3, 0.2, 0);
                }
            }
        }
    }

    // 恢复自定义粒子
    private class CustomParticleEffectTask extends BukkitRunnable {
        @Override
        public void run() {
            List<Location> blocksToProcess;
            synchronized (infectedBlocks) {
                blocksToProcess = new ArrayList<>(infectedBlocks);
            }
            
            int particleCount = 0;
            
            for (Location loc : blocksToProcess) {
                if (particleCount >= MAX_PARTICLES_PER_TICK) break;
                
                if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    World world = loc.getWorld();
                    // 恢复适当的粒子效果
                    world.spawnParticle(CUSTOM_FIRE_PARTICLE, loc.clone().add(0.5, 0.3, 0.5), 2, 0.2, 0.1, 0.2, 0);
                    
                    // 恢复烟雾效果
                    if (new Random().nextInt(100) < 20) {
                        world.spawnParticle(CUSTOM_SMOKE_PARTICLE, loc.clone().add(0.5, 0.5, 0.5), 1, 0.1, 0.1, 0.1, 0);
                    }
                    
                    particleCount += 3;
                }
            }
            
            List<UUID> entitiesToProcess;
            synchronized (infectedEntities) {
                entitiesToProcess = new ArrayList<>(infectedEntities);
            }
            
            for (UUID entityId : entitiesToProcess) {
                Entity entity = getServer().getEntity(entityId);
                if (entity != null && entity.isValid() && entity instanceof LivingEntity) {
                    Location loc = entity.getLocation();
                    World world = loc.getWorld();
                    
                    // 恢复适当的粒子效果
                    world.spawnParticle(CUSTOM_FLAME_TRAIL, loc, 4, 0.3, 0.4, 0.3, 0);
                    
                    // 恢复光环效果
                    if (new Random().nextInt(100) < 30) {
                        for (int i = 0; i < 3; i++) {
                            double angle = Math.random() * Math.PI * 2;
                            double x = Math.cos(angle) * 0.8;
                            double z = Math.sin(angle) * 0.8;
                            Location particleLoc = loc.clone().add(x, 0.6, z);
                            world.spawnParticle(CUSTOM_FIRE_PARTICLE, particleLoc, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }
    }
    
    private class FireToFireInfectionTask extends BukkitRunnable {
        @Override
        public void run() {
            int processed = 0;
            int maxProcessPerTick = 35; // 恢复处理数量
            
            List<Location> blocksToProcess;
            synchronized (infectedBlocks) {
                blocksToProcess = new ArrayList<>(infectedBlocks);
            }
            
            for (Location loc : blocksToProcess) {
                if (processed >= maxProcessPerTick) break;
                
                Block block = loc.getBlock();
                if (block.getType() == Material.FIRE) {
                    infectNearbyFiresFromFire(block);
                    processed++;
                }
            }
        }
    }
    
    private void infectNearbyFiresFromFire(Block fireBlock) {
        Location fireLoc = fireBlock.getLocation();
        World world = fireBlock.getWorld();
        int radius = 3; // 恢复传播半径
        
        Random random = new Random();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block nearbyBlock = fireLoc.clone().add(x, y, z).getBlock();
                    
                    if (nearbyBlock.getType() == Material.FIRE && !infectedBlocks.contains(nearbyBlock.getLocation())) {
                        // 提高感染几率
                        if (random.nextInt(100) < 45 && canInfectMoreBlocks()) {
                            Location nearbyLoc = nearbyBlock.getLocation();
                            synchronized (infectedBlocks) {
                                infectedBlocks.add(nearbyLoc);
                            }
                            infectedBlockTimes.put(nearbyLoc, System.currentTimeMillis());
                            
                            Chunk chunk = nearbyBlock.getChunk();
                            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
                            String key = "infected_" + (nearbyLoc.getBlockX() & 15) + "_" + 
                                        nearbyLoc.getBlockY() + "_" + (nearbyLoc.getBlockZ() & 15);
                            pdc.set(new NamespacedKey(this, key), PersistentDataType.BYTE, (byte) 1);
                            
                            // 恢复适当的视觉效果
                            world.spawnParticle(CUSTOM_FIRE_PARTICLE, nearbyLoc.clone().add(0.5, 0.3, 0.5), 3, 0.2, 0.1, 0.2, 0);
                            world.playSound(nearbyLoc, Sound.BLOCK_FIRE_AMBIENT, 0.3f, 0.7f);
                        }
                    }
                }
            }
        }
    }

    // 修改：调整清理任务，减少清理强度
    private class PerformanceCleanupTask extends BukkitRunnable {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            
            // 只在感染方块数量接近上限时清理
            if (infectedBlocks.size() > MAX_INFECTED_BLOCKS * 0.8) {
                cleanupDistantInfectedBlocks();
            }
            
            // 限制感染方块数量
            enforceBlockLimits();
            
            // 限制感染实体数量
            enforceEntityLimits();
            
            lastCleanupTime = currentTime;
            
            // 记录性能统计
            if (new Random().nextInt(100) < 5) { // 降低日志频率
                getLogger().info("性能统计 - 感染方块: " + infectedBlocks.size() + 
                               ", 感染实体: " + infectedEntities.size());
            }
        }
        
        private void cleanupDistantInfectedBlocks() {
            List<Player> onlinePlayers = new ArrayList<>(getServer().getOnlinePlayers());
            if (onlinePlayers.isEmpty()) return;
            
            List<Location> blocksToRemove = new ArrayList<>();
            int cleanupRadius = 80; // 增加清理半径
            
            synchronized (infectedBlocks) {
                for (Location loc : infectedBlocks) {
                    boolean nearPlayer = false;
                    
                    for (Player player : onlinePlayers) {
                        if (player.getWorld().equals(loc.getWorld()) && 
                            player.getLocation().distance(loc) <= cleanupRadius) {
                            nearPlayer = true;
                            break;
                        }
                    }
                    
                    if (!nearPlayer) {
                        blocksToRemove.add(loc);
                    }
                }
                
                // 限制每次清理的数量
                int maxCleanup = Math.min(blocksToRemove.size(), 30); // 减少清理数量
                for (int i = 0; i < maxCleanup; i++) {
                    Location loc = blocksToRemove.get(i);
                    infectedBlocks.remove(loc);
                    infectedBlockTimes.remove(loc);
                }
                
                if (maxCleanup > 0) {
                    getLogger().info("清理了 " + maxCleanup + " 个远离玩家的感染方块");
                }
            }
        }
        
        private void enforceBlockLimits() {
            synchronized (infectedBlocks) {
                if (infectedBlocks.size() > MAX_INFECTED_BLOCKS) {
                    int toRemove = infectedBlocks.size() - MAX_INFECTED_BLOCKS;
                    List<Location> blocksList = new ArrayList<>(infectedBlocks);
                    
                    // 随机移除一些方块
                    Random random = new Random();
                    for (int i = 0; i < toRemove && i < 20; i++) { // 减少移除数量
                        if (blocksList.isEmpty()) break;
                        int index = random.nextInt(blocksList.size());
                        Location loc = blocksList.remove(index);
                        infectedBlocks.remove(loc);
                        infectedBlockTimes.remove(loc);
                    }
                    
                    getLogger().info("已达到感染方块上限，移除了 " + Math.min(toRemove, 20) + " 个方块");
                }
            }
        }
        
        private void enforceEntityLimits() {
            synchronized (infectedEntities) {
                if (infectedEntities.size() > MAX_INFECTED_ENTITIES) {
                    int toRemove = infectedEntities.size() - MAX_INFECTED_ENTITIES;
                    List<UUID> entitiesList = new ArrayList<>(infectedEntities);
                    
                    Random random = new Random();
                    for (int i = 0; i < toRemove && i < 15; i++) { // 减少移除数量
                        if (entitiesList.isEmpty()) break;
                        int index = random.nextInt(entitiesList.size());
                        UUID entityId = entitiesList.remove(index);
                        infectedEntities.remove(entityId);
                        
                        // 治愈实体
                        Entity entity = getServer().getEntity(entityId);
                        if (entity != null && entity instanceof LivingEntity) {
                            entity.setFireTicks(0);
                        }
                    }
                    
                    getLogger().info("已达到感染实体上限，移除了 " + Math.min(toRemove, 15) + " 个实体");
                }
            }
        }
    }

    @EventHandler
    public void onEntityContact(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity damager && 
            event.getEntity() instanceof LivingEntity target) {
            
            if (infectedEntities.contains(damager.getUniqueId()) && 
                !infectedEntities.contains(target.getUniqueId()) &&
                canInfectEntity(target) &&
                canInfectMoreEntities()) {
                infectEntity(target, damager);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        if (pdc.has(infectedArrowKey, PersistentDataType.BYTE) || 
            infectedEntities.contains(projectile.getUniqueId())) {
            
            Location hitLoc = event.getHitBlock() != null ? 
                event.getHitBlock().getLocation() : projectile.getLocation();
            
            for (Entity entity : hitLoc.getWorld().getNearbyEntities(hitLoc, 3.5, 3.5, 3.5)) {
                if (entity instanceof LivingEntity && !infectedEntities.contains(entity.getUniqueId()) &&
                    canInfectEntity(entity) &&
                    canInfectMoreEntities()) {
                    infectEntity(entity, projectile);
                }
            }
            
            for (int x = -3; x <= 3; x++) {
                for (int y = -2; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Block block = hitLoc.clone().add(x, y, z).getBlock();
                        if (isFlammable(block) && !infectedBlocks.contains(block.getLocation()) &&
                            canInfectMoreBlocks()) {
                            if (new Random().nextInt(100) < 45) {
                                infectBlock(block, projectile);
                            }
                        }
                    }
                }
            }
            
            Block hitBlock = hitLoc.getBlock();
            if (isFlammable(hitBlock) && canInfectMoreBlocks()) {
                infectBlock(hitBlock, projectile);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Skeleton && 
            infectedEntities.contains(event.getEntity().getUniqueId()) &&
            event.getProjectile() instanceof Arrow) {
            
            Arrow arrow = (Arrow) event.getProjectile();
            
            infectEntity(arrow, event.getEntity());
            
            PersistentDataContainer pdc = arrow.getPersistentDataContainer();
            pdc.set(infectedArrowKey, PersistentDataType.BYTE, (byte) 1);
            
            arrow.setVisualFire(true);
            arrow.setFireTicks(INFECTION_DURATION);
            
            // 恢复适当的粒子效果
            arrow.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, arrow.getLocation(), 5, 0.2, 0.2, 0.2, 0);
        }
    }

    @EventHandler
    public void onPotionEffectApply(EntityPotionEffectEvent event) {
        PotionEffect newEffect = event.getNewEffect();
        if (newEffect != null && 
            newEffect.getType().equals(PotionEffectType.FIRE_RESISTANCE) &&
            infectedEntities.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityEnterWater(EntityEnterBlockEvent event) {
        if (event.getBlock().getType() == Material.WATER && 
            infectedEntities.contains(event.getEntity().getUniqueId())) {
            event.getEntity().setFireTicks(INFECTION_DURATION);
        }
    }

    @EventHandler
    public void onFireExtinguish(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FIRE && infectedBlocks.contains(block.getLocation())) {
            event.setCancelled(true);
            
            if (event.getNewState().getType() == Material.AIR) {
                block.setType(Material.FIRE);
                
                Location loc = block.getLocation();
                // 恢复适当的粒子效果
                loc.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, loc.clone().add(0.5, 0.3, 0.5), 4, 0.2, 0.1, 0.2, 0);
                loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.6f);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        if (infectedBlocks.contains(loc)) {
            infectedBlocks.remove(loc);
            infectedBlockTimes.remove(loc);
            
            Chunk chunk = block.getChunk();
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            String key = "infected_" + (block.getX() & 15) + "_" + block.getY() + "_" + (block.getZ() & 15);
            pdc.remove(new NamespacedKey(this, key));
            
            loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        if (infectedEntities.contains(entityId)) {
            infectedEntities.remove(entityId);
            
            Location loc = event.getEntity().getLocation();
            // 恢复适当的粒子效果
            loc.getWorld().spawnParticle(CUSTOM_FIRE_PARTICLE, loc, 15, 0.4, 0.4, 0.4, 0);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
            
            for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 3.5, 3.5, 3.5)) {
                if (nearby instanceof LivingEntity && !infectedEntities.contains(nearby.getUniqueId()) &&
                    canInfectEntity(nearby) &&
                    canInfectMoreEntities()) {
                    if (new Random().nextInt(100) < 50) {
                        infectEntity(nearby, event.getEntity());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (infectedEntities.contains(player.getUniqueId())) {
            infectedEntities.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newGameMode = event.getNewGameMode();
        
        if (newGameMode == GameMode.CREATIVE || newGameMode == GameMode.SPECTATOR) {
            cureEntity(player);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        World world = chunk.getWorld();
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    String key = "infected_" + x + "_" + y + "_" + z;
                    if (pdc.has(new NamespacedKey(this, key), PersistentDataType.BYTE)) {
                        Location loc = new Location(world, chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                        if (loc.getBlock().getType() == Material.FIRE && !infectedBlocks.contains(loc)) {
                            // 恢复所有感染方块，不检查上限
                            infectedBlocks.add(loc);
                            infectedBlockTimes.put(loc, System.currentTimeMillis());
                        }
                    }
                }
            }
        }
    }
}
