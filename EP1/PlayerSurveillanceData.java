package com.yourname.creditsurveillance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSurveillanceData {
    
    private final UUID playerId;
    private final List<UUID> guardianIds;
    private int requiredGuardians;
    private double surveillanceRadius; // 触发传送的距离
    private double teleportMinDistance; // 传送最小距离
    private double teleportMaxDistance; // 传送最大距离
    private long lastAITick;
    
    public PlayerSurveillanceData(UUID playerId) {
        this.playerId = playerId;
        this.guardianIds = new ArrayList<>();
        this.requiredGuardians = 0;
        this.surveillanceRadius = 90.0; // 默认第一阶段
        this.teleportMinDistance = 48.0;
        this.teleportMaxDistance = 80.0;
        this.lastAITick = 0;
    }
    
    public Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }
    
    public int getCurrentGuardianCount() {
        return guardianIds.size();
    }
    
    public int getRequiredGuardians() {
        return requiredGuardians;
    }
    
    public void setRequiredGuardians(int requiredGuardians) {
        this.requiredGuardians = Math.max(0, Math.min(3, requiredGuardians)); // 最多3个
    }
    
    public double getSurveillanceRadius() {
        return surveillanceRadius;
    }
    
    public void setSurveillanceRadius(double radius) {
        this.surveillanceRadius = radius;
    }
    
    public void setTeleportMinDistance(double minDistance) {
        this.teleportMinDistance = minDistance;
    }
    
    public void setTeleportMaxDistance(double maxDistance) {
        this.teleportMaxDistance = maxDistance;
    }
    
    // 根据信用点计算需要的监管者数量
    public int calculateRequiredFromCredits(int credits) {
        if (credits < 2) return 3;
        if (credits < 4) return 1;
        return 0;
    }
    
    // 更新监管者数量
    public void updateGuardianCount() {
        Player player = getPlayer();
        if (player == null || !player.isOnline()) {
            removeAllGuardians();
            return;
        }
        
        // 添加缺少的监管者
        while (guardianIds.size() < requiredGuardians) {
            spawnGuardian(player);
        }
        
        // 移除多余的监管者
        while (guardianIds.size() > requiredGuardians) {
            removeOldestGuardian();
        }
        
        // 如果不需要监管者，全部移除
        if (requiredGuardians == 0) {
            removeAllGuardians();
        }
    }
    
    // 生成监管者
    private void spawnGuardian(Player player) {
        // 初始生成位置在传送范围内
        Location spawnLoc = calculateTeleportLocation(player.getLocation(), teleportMinDistance, teleportMaxDistance);
        
        Zombie guardian = player.getWorld().spawn(spawnLoc, Zombie.class);
        
        // 设置监管者属性 - 名称改为英文Apocolyps
        guardian.setCustomName("§cApocolyps");
        guardian.setCustomNameVisible(true);
        guardian.setAI(true);
        guardian.setCanPickupItems(false);
        guardian.setAdult();
        
        // 添加效果使其更具威胁性
        guardian.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        guardian.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));
        
        // 设置无敌（不会自然死亡）
        guardian.setInvulnerable(true);
        
        guardianIds.add(guardian.getUniqueId());
        
        player.sendMessage("§c⚠ Armageddon is here!");
    }
    
    // 移除最老的监管者
    private void removeOldestGuardian() {
        if (!guardianIds.isEmpty()) {
            UUID guardianId = guardianIds.remove(0);
            Zombie guardian = (Zombie) Bukkit.getEntity(guardianId);
            if (guardian != null && guardian.isValid()) {
                guardian.remove();
            }
        }
    }
    
    // 移除所有监管者
    public void removeAllGuardians() {
        for (UUID guardianId : guardianIds) {
            Zombie guardian = (Zombie) Bukkit.getEntity(guardianId);
            if (guardian != null && guardian.isValid()) {
                guardian.remove();
            }
        }
        guardianIds.clear();
    }
    
    // 更新监管者AI
    public void updateGuardianAI() {
        Player player = getPlayer();
        if (player == null || !player.isOnline() || guardianIds.isEmpty()) {
            return;
        }
        
        Location playerLoc = player.getLocation();
        
        for (UUID guardianId : new ArrayList<>(guardianIds)) {
            Zombie guardian = (Zombie) Bukkit.getEntity(guardianId);
            if (guardian == null || !guardian.isValid()) {
                guardianIds.remove(guardianId);
                continue;
            }
            
            // 计算与玩家的距离
            double distance = guardian.getLocation().distance(playerLoc);
            
            // 当距离超过监管半径时，传送到指定范围内
            if (distance >= surveillanceRadius) {
                Location teleportLoc = calculateTeleportLocation(playerLoc, teleportMinDistance, teleportMaxDistance);
                guardian.teleport(teleportLoc);
                continue; // 传送后跳过本次AI的其他逻辑
            }
            
            // 如果玩家进入3格范围内，攻击玩家
            if (distance <= 3.0) {
                guardian.setTarget(player);
            } else {
                // 否则在监管半径内自由活动（不强制传送，允许接近玩家）
                guardian.setTarget(null);
                
                // 偶尔向玩家方向移动，但不强制攻击
                if (System.currentTimeMillis() - lastAITick > 3000) { // 每3秒
                    // 有30%概率向玩家方向移动
                    if (Math.random() < 0.3) {
                        // 使用简单的移动方法
                        moveGuardianTowardPlayer(guardian, playerLoc);
                    }
                }
            }
        }
        
        if (System.currentTimeMillis() - lastAITick > 3000) {
            lastAITick = System.currentTimeMillis();
        }
    }
    
    // 简单的移动方法，使监管者向玩家方向移动
    private void moveGuardianTowardPlayer(Zombie guardian, Location playerLoc) {
        Location guardianLoc = guardian.getLocation();
        
        // 计算从监管者到玩家的方向向量
        double dx = playerLoc.getX() - guardianLoc.getX();
        double dz = playerLoc.getZ() - guardianLoc.getZ();
        
        // 标准化向量并设置速度
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            dx /= length;
            dz /= length;
            
            // 设置速度，但不直接传送到玩家位置
            double speed = 0.5; // 移动速度
            guardian.setVelocity(guardian.getVelocity().setX(dx * speed).setZ(dz * speed));
        }
    }
    
    // 计算传送位置（在指定最小和最大距离范围内）
    private Location calculateTeleportLocation(Location playerLoc, double minDistance, double maxDistance) {
        double angle = Math.random() * 2 * Math.PI;
        double distance = minDistance + Math.random() * (maxDistance - minDistance);
        
        double x = playerLoc.getX() + Math.cos(angle) * distance;
        double z = playerLoc.getZ() + Math.sin(angle) * distance;
        
        Location teleportLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
        
        // 寻找安全的地面位置
        int highestY = playerLoc.getWorld().getHighestBlockYAt(teleportLoc);
        if (highestY > 0) {
            teleportLoc.setY(highestY + 1);
        } else {
            teleportLoc.setY(playerLoc.getY()); // 如果找不到地面，使用玩家高度
        }
        
        return teleportLoc;
    }
}
