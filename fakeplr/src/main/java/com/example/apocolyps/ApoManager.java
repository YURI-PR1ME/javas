package com.example.apocolyps;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class ApoManager {
    private final ApocolypsPlugin plugin;
    private final Map<UUID, ApoPlayer> apoPlayers = new HashMap<>();
    private final Set<Location> motherBlocks = new HashSet<>();
    private final Set<Location> healingPoles = new HashSet<>();
    
    // 改为管理每个玩家的绑定AI
    private final Map<UUID, Set<UUID>> playerBoundAIs = new HashMap<>(); 
    // 村庄非绑定AI，不计入玩家上限
    private final Set<UUID> villageAIs = new HashSet<>(); 
    
    // 传送冷却时间记录
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    
    private int nextId = 1;
    private int maxBoundPerPlayer = 2; // 每个玩家绑定AI上限
    private int maxVillageAIsPerPlayer = 3; // 每个玩家在村庄的非绑定AI上限
    private int maxNearMother = 20;
    
    // 传送相关设置 - 移除了 final 修饰符
    private long TELEPORT_COOLDOWN = 10000; // 10秒传送冷却
    private int TELEPORT_MIN_DISTANCE = 100; // 最小传送距离
    private int TELEPORT_MAX_DISTANCE = 128; // 最大传送距离
    
    // 村庄特征方块 - 钟作为主要标志
    private final Material[] VILLAGE_MATERIALS = {
        Material.BELL, // 钟是主要标志
        Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
        Material.STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
        Material.GLASS_PANE, Material.WHITE_WOOL, Material.LANTERN,
        Material.CHEST, Material.FURNACE, Material.CRAFTING_TABLE,
        Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
        Material.HAY_BLOCK, Material.WHEAT, Material.CARROTS, Material.POTATOES
    };
    
    public ApoManager(ApocolypsPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void startTasks() {
        // 每秒更新AI行为
        new BukkitRunnable() {
            @Override
            public void run() {
                // 检查并移除死亡的AI
                Iterator<Map.Entry<UUID, ApoPlayer>> iterator = apoPlayers.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, ApoPlayer> entry = iterator.next();
                    ApoPlayer apo = entry.getValue();
                    if (apo.isDead()) {
                        // 如果是绑定AI，安排复活
                        if (isBoundAI(entry.getKey())) {
                            scheduleRespawn(entry.getKey());
                        }
                        iterator.remove();
                    } else {
                        apo.updateBehavior();
                    }
                }
                
                // 检查绑定AI距离并传送
                checkAIDistance();
            }
        }.runTaskTimer(plugin, 0L, 20L);
        
        // 每2秒检查治疗
        new BukkitRunnable() {
            @Override
            public void run() {
                checkHealing();
            }
        }.runTaskTimer(plugin, 0L, 40L);
        
        // 每30秒生成村庄非绑定AI
        new BukkitRunnable() {
            @Override
            public void run() {
                checkVillageSpawning();
            }
        }.runTaskTimer(plugin, 0L, 600L); // 30秒
    }
    
    private void checkAIDistance() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Set<UUID> boundAIs = playerBoundAIs.getOrDefault(playerId, new HashSet<>());
            
            for (UUID aiId : boundAIs) {
                ApoPlayer apo = apoPlayers.get(aiId);
                if (apo != null && !apo.isDead()) {
                    double distance = apo.getLocation().distance(player.getLocation());
                    
                    // 检查传送冷却
                    long currentTime = System.currentTimeMillis();
                    Long lastTeleport = teleportCooldowns.get(aiId);
                    boolean canTeleport = lastTeleport == null || 
                                         (currentTime - lastTeleport) > TELEPORT_COOLDOWN;
                    
                    // 如果AI距离玩家超过20格，并且在冷却时间外，传送到玩家15-25格的位置
                    if (distance > 20 && canTeleport) {
                        Location teleportLoc = findLocationAtDistance(player.getLocation(), 
                                                                      TELEPORT_MIN_DISTANCE, 
                                                                      TELEPORT_MAX_DISTANCE);
                        if (teleportLoc != null) {
                            apo.teleport(teleportLoc);
                            // 记录传送时间
                            teleportCooldowns.put(aiId, currentTime);
                        }
                    }
                }
            }
        }
    }
    
    private Location findLocationAtDistance(Location center, double minDistance, double maxDistance) {
        Random random = new Random();
        World world = center.getWorld();
        
        for (int i = 0; i < 10; i++) {
            // 随机角度和距离
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            
            // 计算坐标
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            int y = world.getHighestBlockYAt((int)x, (int)z);
            
            Location loc = new Location(world, x, y + 1, z);
            if (loc.getBlock().getType().isAir() && 
                loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
        }
        return null;
    }
    
    private void scheduleRespawn(UUID aiId) {
        // 找到这个AI所属的玩家
        for (Map.Entry<UUID, Set<UUID>> entry : playerBoundAIs.entrySet()) {
            if (entry.getValue().contains(aiId)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    // 3秒后复活
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            respawnBoundAI(player, aiId);
                        }
                    }.runTaskLater(plugin, 60L); // 3秒 = 60 ticks
                }
                break;
            }
        }
    }
    
    private void respawnBoundAI(Player player, UUID oldAiId) {
        // 从原绑定集合移除旧AI
        Set<UUID> boundAIs = playerBoundAIs.getOrDefault(player.getUniqueId(), new HashSet<>());
        boundAIs.remove(oldAiId);
        
        // 生成新的绑定AI
        spawnBoundAIPlayer(player);
    }
    
    public boolean spawnBoundAIPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Set<UUID> boundAIs = playerBoundAIs.getOrDefault(playerId, new HashSet<>());
        
        // 清理已死亡的绑定AI
        boundAIs.removeIf(aiId -> !apoPlayers.containsKey(aiId) || apoPlayers.get(aiId).isDead());
        
        if (boundAIs.size() >= maxBoundPerPlayer) {
            return false;
        }
        
        Location spawnLoc = findSafeLocationNear(player.getLocation(), 10);
        if (spawnLoc == null) return false;
        
        ApoPlayer apoPlayer = new ApoPlayer(spawnLoc, nextId++, this, true);
        apoPlayers.put(apoPlayer.getUUID(), apoPlayer);
        boundAIs.add(apoPlayer.getUUID());
        playerBoundAIs.put(playerId, boundAIs);
        
        return true;
    }
    
    private void checkVillageSpawning() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            
            Location playerLoc = player.getLocation();
            
            // 检查玩家是否在村庄区域
            if (isInVillageArea(playerLoc)) {
                // 计算该玩家附近的村庄AI数量
                int villageAICount = countVillageAIsNearPlayer(player, 30);
                
                if (villageAICount < maxVillageAIsPerPlayer) {
                    spawnVillageAIPlayer(playerLoc);
                }
            }
        }
    }
    
    private boolean spawnVillageAIPlayer(Location location) {
        Location spawnLoc = findSafeLocationNear(location, 15);
        if (spawnLoc == null) return false;
        
        ApoPlayer apoPlayer = new ApoPlayer(spawnLoc, nextId++, this, false);
        // 村庄AI弱一些
        apoPlayer.setMaxHealth(20.0);
        apoPlayer.setAnimalAI(false); // 但仍然是智能的
        
        apoPlayers.put(apoPlayer.getUUID(), apoPlayer);
        villageAIs.add(apoPlayer.getUUID());
        
        // 生成粒子效果
        spawnLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, spawnLoc, 3, 0.5, 1, 0.5);
        return true;
    }
    
    private int countVillageAIsNearPlayer(Player player, double radius) {
        int count = 0;
        for (UUID aiId : villageAIs) {
            ApoPlayer apo = apoPlayers.get(aiId);
            if (apo != null && !apo.isDead() && 
                apo.getLocation().distance(player.getLocation()) <= radius) {
                count++;
            }
        }
        return count;
    }
    
    private boolean isBoundAI(UUID aiId) {
        for (Set<UUID> boundAIs : playerBoundAIs.values()) {
            if (boundAIs.contains(aiId)) {
                return true;
            }
        }
        return false;
    }
    
    private Location findSafeLocationNear(Location center, int radius) {
        Random random = new Random();
        World world = center.getWorld();
        
        for (int i = 0; i < 10; i++) {
            int x = center.getBlockX() + random.nextInt(radius * 2) - radius;
            int z = center.getBlockZ() + random.nextInt(radius * 2) - radius;
            int y = world.getHighestBlockYAt(x, z);
            
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (loc.getBlock().getType().isAir() && 
                loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
        }
        return null;
    }
    
    private boolean isInVillageArea(Location loc) {
        // 检查周围是否有村庄特征方块，特别是钟
        int villageBlockCount = 0;
        boolean hasBell = false;
        
        for (int x = -20; x <= 20; x += 5) {
            for (int z = -20; z <= 20; z += 5) {
                Location checkLoc = loc.clone().add(x, 0, z);
                Material blockType = checkLoc.getBlock().getType();
                
                // 优先检查钟
                if (blockType == Material.BELL) {
                    hasBell = true;
                    villageBlockCount += 3; // 钟的权重更高
                }
                
                for (Material villageMat : VILLAGE_MATERIALS) {
                    if (blockType == villageMat) {
                        villageBlockCount++;
                        break;
                    }
                }
                
                // 如果找到钟或者足够的村庄方块
                if (hasBell || villageBlockCount >= 5) {
                    return true;
                }
            }
        }
        
        return hasBell || villageBlockCount >= 5;
    }
    
    private void checkHealing() {
        for (ApoPlayer apo : apoPlayers.values()) {
            if (apo.isDead()) continue;
            
            Location loc = apo.getLocation();
            if (healingPoles.stream().anyMatch(pole -> pole.distance(loc) <= 3)) {
                apo.heal(1);
            }
        }
    }
    
    public void removeApoPlayer(UUID uuid) {
        ApoPlayer apo = apoPlayers.remove(uuid);
        if (apo != null) {
            apo.remove();
        }
        villageAIs.remove(uuid);
        teleportCooldowns.remove(uuid);
        
        // 从绑定AI中移除
        for (Set<UUID> boundAIs : playerBoundAIs.values()) {
            boundAIs.remove(uuid);
        }
    }
    
    public void disableAll() {
        for (ApoPlayer apo : apoPlayers.values()) {
            apo.remove();
        }
        apoPlayers.clear();
        playerBoundAIs.clear();
        villageAIs.clear();
        teleportCooldowns.clear();
    }
    
    public void setMotherBlock(Location loc, boolean add) {
        if (add) {
            motherBlocks.add(loc);
            loc.getBlock().setType(Material.OBSIDIAN);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
        } else {
            motherBlocks.remove(loc);
            loc.getBlock().setType(Material.AIR);
            // 降低所有AI到动物级别
            for (ApoPlayer apo : apoPlayers.values()) {
                apo.setAnimalAI(true);
            }
        }
    }
    
    public void setHealingPole(Location loc, boolean add) {
        if (add) {
            healingPoles.add(loc);
            loc.getBlock().setType(Material.LODESTONE);
        } else {
            healingPoles.remove(loc);
            loc.getBlock().setType(Material.AIR);
        }
    }
    
    // Getter方法
    public boolean isMotherBlock(Location loc) { return motherBlocks.contains(loc); }
    public boolean isHealingPole(Location loc) { return healingPoles.contains(loc); }
    public Collection<ApoPlayer> getAllAIPlayers() { return apoPlayers.values(); }
    public int getPlayerCount() { return apoPlayers.size(); }
    public void setMaxPerPlayer(int max) { this.maxBoundPerPlayer = max; }
    
    // 获取玩家的绑定AI数量
    public int getBoundAICount(UUID playerId) {
        return playerBoundAIs.getOrDefault(playerId, new HashSet<>()).size();
    }
    
    // 初始化玩家的绑定AI
    public void initializePlayerAIs(Player player) {
        for (int i = 0; i < maxBoundPerPlayer; i++) {
            spawnBoundAIPlayer(player);
        }
    }
    
    // 设置传送参数的方法（现在可以正常工作了）
    public void setTeleportParameters(int minDistance, int maxDistance, long cooldown) {
        this.TELEPORT_MIN_DISTANCE = minDistance;
        this.TELEPORT_MAX_DISTANCE = maxDistance;
        this.TELEPORT_COOLDOWN = cooldown;
    }
    
    // 新增：获取传送参数的getter方法（可选）
    public int getTeleportMinDistance() {
        return TELEPORT_MIN_DISTANCE;
    }
    
    public int getTeleportMaxDistance() {
        return TELEPORT_MAX_DISTANCE;
    }
    
    public long getTeleportCooldown() {
        return TELEPORT_COOLDOWN;
    }
}
