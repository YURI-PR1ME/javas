package com.yourname.orionboss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class OrionBoss {

    private final Wither boss;
    private final OrionBossPlugin plugin;
    private BukkitRunnable behaviorTask;
    private int attackCounter = 0;
    private final Random random = new Random();
    
    // Skill cooldowns
    private long lastLavaAttack = 0;
    private long lastSkullAttack = 0;
    private long lastCloneAttack = 0;
    private long lastVoidAttack = 0;
    private long lastCrystalAttack = 0;
    private long lastRainAttack = 0;
    private long lastUltimateAttack = 0;
    
    // Cooldown constants
    private static final long LAVA_COOLDOWN = 3000;
    private static final long SKULL_COOLDOWN = 10000;
    private static final long CLONE_COOLDOWN = 20000;
    private static final long VOID_COOLDOWN = 25000;
    private static final long CRYSTAL_COOLDOWN = 30000;
    private static final long RAIN_COOLDOWN = 35000;
    private static final long ULTIMATE_COOLDOWN = 60000;
// 在现有的冷却时间常量后添加
private static final long EXECUTION_COOLDOWN = 45000;
private long lastExecutionAttack = 0;    
    // Track players for void attack
    private final Map<UUID, Location> playerOriginalLocations = new HashMap<>();
    private final Map<UUID, BukkitRunnable> voidTasks = new HashMap<>();

    public OrionBoss(Wither boss, OrionBossPlugin plugin) {
        this.boss = boss;
        this.plugin = plugin;
    }

    public void startBossBehavior() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 添加死亡检测和宝藏袋掉落
                if (!boss.isValid() || boss.isDead()) {
                    if (boss.isDead()) {
                        // Boss死亡时掉落宝藏袋
                        plugin.createTreasureBag(boss.getLocation());
                        // 广播死亡消息
                        Bukkit.broadcastMessage("§6§lORION §e§lTHE HUNTER §6§lhas been defeated!");
                        Bukkit.broadcastMessage("§aTreasure bag dropped at the death location!");
                    }
                    
                    // 清理Boss
                    cleanup();
                    plugin.getActiveBosses().remove(boss.getUniqueId());
                    plugin.removeBossBar(boss.getUniqueId());
                    cancel();
                    return;
                }

                performRandomAttack();
                updateBossEffects();
                plugin.updateBossBar(boss);
            }
        };
        behaviorTask.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

private void performRandomAttack() {
    Player target = findNearestPlayer();
    if (target == null) return;

    long currentTime = System.currentTimeMillis();
    List<Runnable> availableAttacks = new ArrayList<>();

    // 检查血量是否低于50%且Execution技能可用
    boolean canUseExecution = boss.getHealth() < boss.getMaxHealth() * 0.5;
    
    // Check available attacks based on cooldown
    if (currentTime - lastLavaAttack > LAVA_COOLDOWN) {
        availableAttacks.add(() -> useLavaAttack(target));
    }
    if (currentTime - lastSkullAttack > SKULL_COOLDOWN) {
        availableAttacks.add(() -> useSkullAttack(target));
    }
    if (currentTime - lastCloneAttack > CLONE_COOLDOWN) {
        availableAttacks.add(() -> useCloneAttack());
    }
    if (currentTime - lastVoidAttack > VOID_COOLDOWN) {
        availableAttacks.add(() -> useVoidAttack(target));
    }
    if (currentTime - lastCrystalAttack > CRYSTAL_COOLDOWN) {
        availableAttacks.add(() -> useCrystalAttack(target));
    }
    if (currentTime - lastRainAttack > RAIN_COOLDOWN) {
        availableAttacks.add(() -> useRainAttack(target));
    }
    if (currentTime - lastUltimateAttack > ULTIMATE_COOLDOWN) {
        availableAttacks.add(() -> useUltimateAttack(target));
    }
    // 新增Execution技能检查
    if (canUseExecution && currentTime - lastExecutionAttack > EXECUTION_COOLDOWN) {
        availableAttacks.add(() -> useExecutionAttack(target));
    }

    if (!availableAttacks.isEmpty()) {
        // 权重随机攻击 - 基础攻击更频繁
        int randomIndex = random.nextInt(availableAttacks.size() + 2); // +2 for basic attacks
        if (randomIndex < availableAttacks.size()) {
            availableAttacks.get(randomIndex).run();
        } else {
            // 基础攻击
            if (random.nextBoolean()) {
                useLavaAttack(target);
            } else {
                useSkullAttack(target);
            }
        }
    }
}
    private void useLavaAttack(Player target) {
        lastLavaAttack = System.currentTimeMillis();
        
        Location playerLoc = target.getLocation();
        Location lavaCenter = playerLoc.clone().subtract(0, 1, 0); // Below player
        
        // Create 2x2 lava area
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                Location lavaLoc = lavaCenter.clone().add(x, 0, z);
                if (lavaLoc.getBlock().getType().isSolid()) {
                    lavaLoc.getBlock().setType(Material.LAVA);
                }
            }
        }
        
        // Effects
        target.getWorld().playSound(playerLoc, Sound.BLOCK_LAVA_AMBIENT, 1.0f, 1.0f);
        target.getWorld().spawnParticle(Particle.LAVA, playerLoc, 20, 1, 0, 1);
        
        target.sendMessage("§cOrion turned the ground beneath you into lava!");
    }

    private void useSkullAttack(Player target) {
        lastSkullAttack = System.currentTimeMillis();
        
        new BukkitRunnable() {
            private int rounds = 0;
            
            @Override
            public void run() {
                if (rounds >= 3 || boss.isDead()) {
                    cancel();
                    return;
                }
                
                // Shoot 3 skulls per round
                for (int i = 0; i < 3; i++) {
                    Location eyeLocation = boss.getEyeLocation();
                    Vector direction = target.getLocation().add(0, 1, 0)
                            .subtract(eyeLocation).toVector().normalize();
                    
                    // Add some spread
                    direction.add(new Vector(
                        (random.nextDouble() - 0.5) * 0.3,
                        (random.nextDouble() - 0.5) * 0.3,
                        (random.nextDouble() - 0.5) * 0.3
                    )).normalize();
                    
                    WitherSkull skull = boss.launchProjectile(WitherSkull.class);
                    skull.setDirection(direction);
                    skull.setCharged(false);
                }
                
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 2.0f, 0.8f);
                rounds++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1 second between rounds
        
        target.sendMessage("§4Orion is launching wither skulls at you!");
    }

    private void useCloneAttack() {
        lastCloneAttack = System.currentTimeMillis();
        
        List<Player> nearbyPlayers = getNearbyPlayers(30);
        if (nearbyPlayers.isEmpty()) return;
        
        for (Player player : nearbyPlayers) {
            for (int i = 0; i < 3; i++) {
                spawnPlayerClone(player);
            }
        }
        
        Bukkit.broadcastMessage("§5§lOrion has summoned shadow clones of all players!");
    }

    private void spawnPlayerClone(Player original) {
        Location spawnLoc = findSpawnLocationAround(original.getLocation(), 5);
        Husk clone = (Husk) original.getWorld().spawnEntity(spawnLoc, EntityType.HUSK);
        
        // Set clone properties
        clone.setCustomName("§8SHADOW OF " + original.getName());
        clone.setCustomNameVisible(true);
        Objects.requireNonNull(clone.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
        clone.setHealth(20.0);
        Objects.requireNonNull(clone.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(15.0);
        
        // Copy player equipment
        clone.getEquipment().setHelmet(original.getInventory().getHelmet());
        clone.getEquipment().setChestplate(original.getInventory().getChestplate());
        clone.getEquipment().setLeggings(original.getInventory().getLeggings());
        clone.getEquipment().setBoots(original.getInventory().getBoots());
        clone.getEquipment().setItemInMainHand(original.getInventory().getItemInMainHand());
        
        // Add explosion attack AI
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    cancel();
                    return;
                }
                
                Player target = findNearestPlayerTo(clone.getLocation());
                if (target != null && target.getLocation().distance(clone.getLocation()) < 3) {
                    // Create end crystal explosion
                    Location explosionLoc = clone.getLocation();
                    explosionLoc.getWorld().createExplosion(explosionLoc, 4.0f, true, true, clone);
                    target.damage(20.0, clone);
                    clone.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void useVoidAttack(Player target) {
        lastVoidAttack = System.currentTimeMillis();
        
        // Store original location
        Location originalLocation = target.getLocation().clone();
        Location bossOriginalLocation = boss.getLocation().clone();
        
        // Calculate void location (deep below in The End)
        Location voidLocation = new Location(
            target.getWorld(),
            target.getLocation().getX(),
            -10, // Deep in void
            target.getLocation().getZ()
        );
        
        // Teleport player and boss to void
        target.teleport(voidLocation);
        boss.teleport(voidLocation.add(0, 5, 0));
        
        playerOriginalLocations.put(target.getUniqueId(), originalLocation);
        
        target.sendTitle("§4§lVOID DROWNING", "§cYou've been cast into the void!", 10, 40, 10);
        
        // Schedule return after 1 second
        BukkitRunnable returnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isOnline() && !target.isDead()) {
                    target.teleport(originalLocation);
                    boss.teleport(bossOriginalLocation);
                    target.sendMessage("§6You've been pulled back from the void!");
                }
                playerOriginalLocations.remove(target.getUniqueId());
                voidTasks.remove(target.getUniqueId());
            }
        };
        
        voidTasks.put(target.getUniqueId(), returnTask);
        returnTask.runTaskLater(plugin, 20L); // 1 second
    }

    private void useCrystalAttack(Player target) {
        lastCrystalAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lOrion is preparing FINAL ANNIHILATION!");
        
        new BukkitRunnable() {
            private int crystalsPlaced = 0;
            private final int totalCrystals = 12;
            private final List<EnderCrystal> crystals = new ArrayList<>();
            
            @Override
            public void run() {
                if (crystalsPlaced >= totalCrystals || boss.isDead()) {
                    // Detonate all crystals
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (EnderCrystal crystal : crystals) {
                                if (crystal.isValid()) {
                                    crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true, boss);
                                    crystal.remove();
                                }
                            }
                        }
                    }.runTaskLater(plugin, 20L);
                    cancel();
                    return;
                }
                
                // Place crystal in random position around player
                double angle = 2 * Math.PI * crystalsPlaced / totalCrystals;
                double distance = 5 + random.nextDouble() * 5;
                double x = target.getLocation().getX() + distance * Math.cos(angle);
                double z = target.getLocation().getZ() + distance * Math.sin(angle);
                double y = findGroundLevel(target.getWorld(), x, z);
                
                Location crystalLoc = new Location(target.getWorld(), x, y + 1, z);
                EnderCrystal crystal = target.getWorld().spawn(crystalLoc, EnderCrystal.class);
                crystals.add(crystal);
                
                crystalsPlaced++;
            }
        }.runTaskTimer(plugin, 0L, 5L); // Fast placement
    }

    private void useRainAttack(Player target) {
        lastRainAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lOrion calls upon the CELESTIAL WRATH!");
        
        Location center = target.getLocation();
        int duration = 100; // 5 seconds
        int attacksPerTick = 3;
        
        new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= duration || boss.isDead()) {
                    // Spawn enhanced clones at the end
                    spawnEnhancedClones();
                    cancel();
                    return;
                }
                
                // Spawn various projectiles
                for (int i = 0; i < attacksPerTick; i++) {
                    spawnRandomProjectile(center);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnRandomProjectile(Location center) {
        double x = center.getX() + (random.nextDouble() - 0.5) * 20;
        double z = center.getZ() + (random.nextDouble() - 0.5) * 20;
        double y = center.getY() + 25;
        
        Location spawnLoc = new Location(center.getWorld(), x, y, z);
        
        int attackType = random.nextInt(5);
        switch (attackType) {
            case 0: // Arrow
                Arrow arrow = center.getWorld().spawn(spawnLoc, Arrow.class);
                arrow.setVelocity(new Vector(0, -2, 0));
                arrow.setShooter(boss);
                break;
            case 1: // Dragon Fireball
                DragonFireball fireball = center.getWorld().spawn(spawnLoc, DragonFireball.class);
                fireball.setVelocity(new Vector(0, -1, 0));
                fireball.setShooter(boss);
                break;
            case 2: // Small Fireball
                SmallFireball smallFireball = center.getWorld().spawn(spawnLoc, SmallFireball.class);
                smallFireball.setVelocity(new Vector(0, -1.5, 0));
                smallFireball.setShooter(boss);
                break;
            case 3: // Splash Potion - Instant Damage
                ThrownPotion damagePotion = center.getWorld().spawn(spawnLoc, ThrownPotion.class);
                damagePotion.setItem(new ItemStack(Material.SPLASH_POTION));
                // Note: Potion effects would need to be set properly
                damagePotion.setVelocity(new Vector(0, -1, 0));
                break;
            case 4: // Splash Potion - Poison
                ThrownPotion poisonPotion = center.getWorld().spawn(spawnLoc, ThrownPotion.class);
                poisonPotion.setItem(new ItemStack(Material.SPLASH_POTION));
                // Note: Potion effects would need to be set properly
                poisonPotion.setVelocity(new Vector(0, -1, 0));
                break;
        }
    }

    private void spawnEnhancedClones() {
        for (Player player : getNearbyPlayers(50)) {
            spawnEnhancedPlayerClone(player);
        }
    }

    private void spawnEnhancedPlayerClone(Player original) {
        Location spawnLoc = findSpawnLocationAround(original.getLocation(), 3);
        Husk clone = (Husk) original.getWorld().spawnEntity(spawnLoc, EntityType.HUSK);
        
        // Enhanced properties
        clone.setCustomName("§4ENHANCED SHADOW OF " + original.getName());
        clone.setCustomNameVisible(true);
        Objects.requireNonNull(clone.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(40.0);
        clone.setHealth(40.0);
        Objects.requireNonNull(clone.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(20.0);
        
        // Strength effect
        clone.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
        
        // Copy equipment
        clone.getEquipment().setHelmet(original.getInventory().getHelmet());
        clone.getEquipment().setChestplate(original.getInventory().getChestplate());
        clone.getEquipment().setLeggings(original.getInventory().getLeggings());
        clone.getEquipment().setBoots(original.getInventory().getBoots());
        clone.getEquipment().setItemInMainHand(original.getInventory().getItemInMainHand());
        
        // Teleport AI
        new BukkitRunnable() {
            @Override
            public void run() {
                if (clone.isDead() || !clone.isValid()) {
                    cancel();
                    return;
                }
                
                Player target = findNearestPlayerTo(clone.getLocation());
                if (target != null) {
                    if (target.getLocation().distance(clone.getLocation()) > 8) {
                        // Teleport closer to player
                        Location teleportLoc = findSpawnLocationAround(target.getLocation(), 3);
                        clone.teleport(teleportLoc);
                    }
                    
                    // Attack with explosion
                    if (target.getLocation().distance(clone.getLocation()) < 4) {
                        Location explosionLoc = clone.getLocation();
                        explosionLoc.getWorld().createExplosion(explosionLoc, 5.0f, true, true, clone);
                        target.damage(25.0, clone);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Check every 2 seconds
    }

    private void useUltimateAttack(Player target) {
        lastUltimateAttack = System.currentTimeMillis();
        
        Bukkit.broadcastMessage("§4§lORION UNLEASHES HIS ULTIMATE POWER!");
        Bukkit.broadcastMessage("§6§lTHE VOID'S EMBRACE!");
        
        Location center = target.getLocation();
        
        // Phase 1: Replace air with lava in 4 block radius
        replaceAirWithLava(center, 4);
        
        // Phase 2: After 3 seconds, replace lava with end crystals and explode
        new BukkitRunnable() {
            @Override
            public void run() {
                replaceLavaWithCrystals(center, 4);
                
                // Phase 3: Lightning strikes
                strikeLightningAround(center, 4);
            }
        }.runTaskLater(plugin, 60L); // 3 seconds
    }

    private void replaceAirWithLava(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.AIR) {
                        checkLoc.getBlock().setType(Material.LAVA);
                    }
                }
            }
        }
        
        center.getWorld().playSound(center, Sound.BLOCK_LAVA_AMBIENT, 2.0f, 0.8f);
    }

private void useExecutionAttack(Player target) {
    lastExecutionAttack = System.currentTimeMillis();
    
    Bukkit.broadcastMessage("§4§lORION UNLEASHES EXECUTION! §c§lDRAGONS FROM ABYSS!");
    
    Location bossLoc = boss.getLocation();
    
    // 召唤3条龙从不同方向攻击
    summonExecutionDragon(target, bossLoc.clone().add(10, 8, 0)); // 右侧
    summonExecutionDragon(target, bossLoc.clone().add(-10, 8, 0)); // 左侧
    summonExecutionDragon(target, bossLoc.clone().add(0, 12, 10)); // 前方
    
    // 技能特效
    boss.getWorld().playSound(bossLoc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 3.0f, 0.7f);
    boss.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, bossLoc, 100, 5, 5, 5);
    
    // 对所有附近玩家显示标题
    for (Player player : getNearbyPlayers(50)) {
        player.sendTitle("§4§lEXECUTION", "§cDragons are coming!", 10, 40, 10);
    }
}

private void summonExecutionDragon(Player target, Location spawnLocation) {
    // 修复：只传递3个参数，移除速度参数
    ExecutionDragon executionDragon = new ExecutionDragon(plugin, target, 40.0);
    executionDragon.spawn(spawnLocation);
    
    // 生成时的局部特效
    spawnLocation.getWorld().playSound(spawnLocation, 
        org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.8f);
    spawnLocation.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, 
        spawnLocation, 30, 2, 2, 2);
    spawnLocation.getWorld().spawnParticle(org.bukkit.Particle.FLAME, 
        spawnLocation, 20, 1, 1, 1);
}
private void replaceLavaWithCrystals(Location center, int radius) {
        List<EnderCrystal> crystals = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    Location checkLoc = center.clone().add(x, y, z);
                    if (checkLoc.getBlock().getType() == Material.LAVA) {
                        checkLoc.getBlock().setType(Material.AIR);
                        Location crystalLoc = checkLoc.clone().add(0, 1, 0);
                        EnderCrystal crystal = center.getWorld().spawn(crystalLoc, EnderCrystal.class);
                        crystals.add(crystal);
                    }
                }
            }
        }
        
        // Explode all crystals after a brief moment
        new BukkitRunnable() {
            @Override
            public void run() {
                for (EnderCrystal crystal : crystals) {
                    if (crystal.isValid()) {
                        crystal.getWorld().createExplosion(crystal.getLocation(), 8.0f, true, true, boss);
                        crystal.remove();
                    }
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    private void strikeLightningAround(Location center, int radius) {
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location strikeLoc = new Location(center.getWorld(), x, center.getY(), z);
            
            center.getWorld().strikeLightning(strikeLoc);
        }
    }

    private void updateBossEffects() {
        // Constant boss effects
        boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 10, 2, 2, 2);
        
        // Health-based effects
        double healthPercent = boss.getHealth() / boss.getMaxHealth();
        if (healthPercent < 0.3) {
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 20, 3, 3, 3);
        }
    }

    private Player findNearestPlayer() {
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

    private Player findNearestPlayerTo(Location location) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : location.getWorld().getPlayers()) {
            if (!isValidTarget(player)) continue;
            
            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }

    private List<Player> getNearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (isValidTarget(player) && player.getLocation().distance(boss.getLocation()) <= radius) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean isValidTarget(Player player) {
        return player != null && 
               player.isOnline() && 
               !player.isDead() && 
               player.getGameMode() == GameMode.SURVIVAL;
    }

    private Location findSpawnLocationAround(Location center, double radius) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        double y = findGroundLevel(center.getWorld(), x, z);
        
        return new Location(center.getWorld(), x, y, z);
    }

    private double findGroundLevel(World world, double x, double z) {
        Location testLoc = new Location(world, x, 0, z);
        return world.getHighestBlockYAt(testLoc);
    }

    public void cleanup() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }
        
        // Cancel all void tasks
        for (BukkitRunnable task : voidTasks.values()) {
            task.cancel();
        }
        voidTasks.clear();
        playerOriginalLocations.clear();
    }

    public Wither getBoss() {
        return boss;
    }
}
