package com.yourname.tyrantpickaxe;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TyrantPickaxeManager {

    private final TyrantPickaxePlugin plugin;
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private final Map<UUID, Long> rapidDisplacementCooldowns = new HashMap<>();
    private final Map<UUID, Integer> killCounters = new HashMap<>();
    private final Map<UUID, Boolean> consecutiveHitRewards = new HashMap<>();
    private final Map<UUID, BukkitRunnable> actionBarTasks = new HashMap<>();
    
    // 新的快速位移技能相关变量
    private final Map<UUID, RapidDisplacementSession> activeRapidDisplacements = new HashMap<>();

    public TyrantPickaxeManager(TyrantPickaxePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOnTeleportCooldown(Player player) {
        if (!teleportCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        
        long cooldownEnd = teleportCooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() < cooldownEnd;
    }

    public long getTeleportCooldownRemaining(Player player) {
        if (!teleportCooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        
        long remaining = teleportCooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void setTeleportCooldown(Player player, long seconds) {
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void resetTeleportCooldown(Player player) {
        teleportCooldowns.remove(player.getUniqueId());
    }

    public boolean isOnRapidDisplacementCooldown(Player player) {
        if (!rapidDisplacementCooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        
        long cooldownEnd = rapidDisplacementCooldowns.get(player.getUniqueId());
        return System.currentTimeMillis() < cooldownEnd;
    }

    public long getRapidDisplacementCooldownRemaining(Player player) {
        if (!rapidDisplacementCooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        
        long remaining = rapidDisplacementCooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public void setRapidDisplacementCooldown(Player player, long seconds) {
        rapidDisplacementCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));
    }

    public void incrementKillCounter(Player player) {
        UUID playerId = player.getUniqueId();
        int currentCount = killCounters.getOrDefault(playerId, 0) + 1;
        killCounters.put(playerId, currentCount);
    }

    public boolean shouldTriggerNegativeEffect(Player player) {
        int count = killCounters.getOrDefault(player.getUniqueId(), 0);
        return count >= 5;
    }

    public void resetKillCounter(Player player) {
        killCounters.put(player.getUniqueId(), 0);
    }

    public void setConsecutiveHitReward(Player player, boolean hasReward) {
        consecutiveHitRewards.put(player.getUniqueId(), hasReward);
    }

    public boolean hasConsecutiveHitReward(Player player) {
        return consecutiveHitRewards.getOrDefault(player.getUniqueId(), false);
    }

    public void removeConsecutiveHitReward(Player player) {
        consecutiveHitRewards.remove(player.getUniqueId());
    }

    // 新的快速位移技能方法 - 现在接受LivingEntity
    public void startRapidDisplacement(Player player, List<LivingEntity> targets) {
        UUID playerId = player.getUniqueId();
        
        // 取消现有的位移会话
        if (activeRapidDisplacements.containsKey(playerId)) {
            activeRapidDisplacements.get(playerId).cancel();
            activeRapidDisplacements.remove(playerId);
        }
        
        // 创建新的位移会话
        RapidDisplacementSession session = new RapidDisplacementSession(player, targets);
        activeRapidDisplacements.put(playerId, session);
        
        // 开始会话
        session.start();
        
        player.sendMessage("§6Rapid Displacement activated! You will teleport between " + targets.size() + " targets for 3 rounds.");
        player.sendMessage("§7Attack a target within 2 seconds to continue the chain!");
    }

    public boolean isInRapidDisplacement(Player player) {
        return activeRapidDisplacements.containsKey(player.getUniqueId());
    }

    public void handleRapidDisplacementAttack(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        if (activeRapidDisplacements.containsKey(playerId)) {
            RapidDisplacementSession session = activeRapidDisplacements.get(playerId);
            session.handleAttack(target);
        }
    }

    public void endRapidDisplacement(Player player, int totalAttackedTargets, int completedRounds) {
        UUID playerId = player.getUniqueId();
        if (activeRapidDisplacements.containsKey(playerId)) {
            RapidDisplacementSession session = activeRapidDisplacements.get(playerId);
            session.cancel();
            activeRapidDisplacements.remove(playerId);
            
            // 计算冷却时间：攻击的总人数 * 5 + 10秒
            int cooldownSeconds = totalAttackedTargets * 5 + 10;
            setRapidDisplacementCooldown(player, cooldownSeconds);
            
            player.sendMessage("§6Rapid Displacement ended! Completed " + completedRounds + "/3 rounds. Cooldown: " + cooldownSeconds + " seconds");
            
            // 确保玩家落地
            if (player.isOnline()) {
                Location currentLocation = player.getLocation();
                player.teleport(currentLocation);
            }
        }
    }

    public void teleportBehindTarget(Player player, LivingEntity target) {
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

    public void startActionBarUpdates(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel existing task if any
        if (actionBarTasks.containsKey(playerId)) {
            actionBarTasks.get(playerId).cancel();
        }
        
        // Create new task for ActionBar updates
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    actionBarTasks.remove(playerId);
                    return;
                }
                
                // Check if player is holding the Tyrant Pickaxe
                if (!plugin.isTyrantPickaxe(player.getInventory().getItemInMainHand())) {
                    return;
                }
                
                // Update ActionBar with cooldown information
                updateActionBar(player);
            }
        };
        
        actionBarTasks.put(playerId, task);
        task.runTaskTimer(plugin, 0L, 5L); // Update every 5 ticks (4 times per second)
    }

    private void updateActionBar(Player player) {
        StringBuilder actionBar = new StringBuilder();
        
        // Teleport cooldown
        if (isOnTeleportCooldown(player)) {
            long remaining = getTeleportCooldownRemaining(player) / 1000;
            actionBar.append("§cTeleport: ").append(remaining).append("s ");
        } else {
            actionBar.append("§aTeleport: Ready ");
        }
        
        // Rapid displacement cooldown
        if (isOnRapidDisplacementCooldown(player)) {
            long remaining = getRapidDisplacementCooldownRemaining(player) / 1000;
            actionBar.append("§cRapid: ").append(remaining).append("s");
        } else {
            actionBar.append("§aRapid: Ready");
        }
        
        // Consecutive hit reward status
        if (hasConsecutiveHitReward(player)) {
            actionBar.append(" §6[FIRE READY]");
        }
        
        // Send ActionBar message
        player.sendActionBar(net.kyori.adventure.text.Component.text(actionBar.toString()));
    }

    public void stopActionBarUpdates(Player player) {
        UUID playerId = player.getUniqueId();
        if (actionBarTasks.containsKey(playerId)) {
            actionBarTasks.get(playerId).cancel();
            actionBarTasks.remove(playerId);
        }
    }

    public void shootFireballs(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        
        for (int i = 0; i < 5; i++) {
            // Slightly spread the fireballs
            Vector spread = direction.clone();
            if (i > 0) {
                double spreadAmount = 0.2;
                spread.add(new Vector(
                    (Math.random() - 0.5) * spreadAmount,
                    (Math.random() - 0.5) * spreadAmount,
                    (Math.random() - 0.5) * spreadAmount
                )).normalize();
            }
            
            // Create and launch fireball
            Fireball fireball = player.getWorld().spawn(eyeLocation, Fireball.class);
            fireball.setDirection(spread);
            fireball.setShooter(player);
            
            // 设置更高的爆炸威力
            fireball.setYield(3.0f); // 增加爆炸威力
            fireball.setIsIncendiary(true);
            
            // 添加自定义标签来识别这是Tyrant Pickaxe的火焰弹
            fireball.setCustomName("TyrantFireball");
            fireball.setCustomNameVisible(false);
            
            // Add velocity to fireball
            fireball.setVelocity(spread.multiply(1.5));
        }
    }

    public void cleanup() {
        // Cancel all active tasks
        for (BukkitRunnable task : actionBarTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        actionBarTasks.clear();
        
        // Cancel all active rapid displacement sessions
        for (RapidDisplacementSession session : activeRapidDisplacements.values()) {
            if (session != null) {
                session.cancel();
            }
        }
        activeRapidDisplacements.clear();
    }

    // 快速位移会话内部类 - 现在处理LivingEntity
    private class RapidDisplacementSession {
        private final Player player;
        private List<LivingEntity> targets;
        private final Map<UUID, Integer> hitCounts;
        private int currentTargetIndex;
        private BukkitRunnable timeoutTask;
        private BukkitRunnable followTask;
        private int totalAttackedTargets;
        
        public RapidDisplacementSession(Player player, List<LivingEntity> targets) {
            this.player = player;
            this.targets = new ArrayList<>(targets);
            this.hitCounts = new HashMap<>();
            this.currentTargetIndex = 0;
            this.totalAttackedTargets = 0;
            
            // 初始化命中计数
            for (LivingEntity target : targets) {
                hitCounts.put(target.getUniqueId(), 0);
            }
        }
        
        public void start() {
            // 红色粒子炸开效果
            createExplosiveRedParticleEffect(player.getLocation());
            
            // 传送到第一个目标
            teleportToCurrentTarget();
        }
        
        public void handleAttack(LivingEntity target) {
            UUID targetId = target.getUniqueId();
            
            // 检查是否是当前目标
            if (currentTargetIndex < targets.size() && targets.get(currentTargetIndex).getUniqueId().equals(targetId)) {
                // 增加命中计数
                int currentHits = hitCounts.get(targetId) + 1;
                hitCounts.put(targetId, currentHits);
                
                // 更新总攻击目标数（如果这是第一次攻击这个目标）
                if (currentHits == 1) {
                    totalAttackedTargets++;
                }
                
                String targetName = getEntityName(target);
                player.sendMessage("§eHit " + targetName + "! (" + currentHits + "/3)");
                
                // 检查是否所有目标都被攻击了3次
                if (allTargetsHitThreeTimes()) {
                    player.sendMessage("§6All targets hit 3 times! Rapid Displacement ending.");
                    endRapidDisplacement(player, totalAttackedTargets, 3);
                    return;
                }
                
                // 移动到下一个目标
                moveToNextTarget();
                
                // 重置超时计时器
                resetTimeout();
            }
        }
        
        private boolean allTargetsHitThreeTimes() {
            for (LivingEntity target : targets) {
                if (hitCounts.getOrDefault(target.getUniqueId(), 0) < 3) {
                    return false;
                }
            }
            return true;
        }
        
        private void teleportToCurrentTarget() {
            // 清理死亡的目标
            cleanupDeadTargets();
            
            if (targets.isEmpty()) {
                player.sendMessage("§cAll targets are gone! Skill ending.");
                endRapidDisplacement(player, totalAttackedTargets, calculateCompletedRounds());
                return;
            }
            
            // 确保索引在有效范围内
            if (currentTargetIndex >= targets.size()) {
                currentTargetIndex = 0;
            }
            
            // 寻找下一个需要攻击的目标（攻击次数少于3次）
            LivingEntity nextTarget = findNextValidTarget();
            if (nextTarget == null) {
                player.sendMessage("§6All targets hit 3 times! Rapid Displacement ending.");
                endRapidDisplacement(player, totalAttackedTargets, 3);
                return;
            }
            
            // 更新当前目标索引
            currentTargetIndex = targets.indexOf(nextTarget);
            LivingEntity target = targets.get(currentTargetIndex);
            
            // 取消现有的跟随任务
            if (followTask != null) {
                followTask.cancel();
            }
            
            // 立即传送到目标身后
            updatePlayerPosition(target);
            
            // 启动跟随任务，每tick更新玩家位置
            startFollowingTarget(target);
            
            // 重置超时计时器
            resetTimeout();
            
            String targetName = getEntityName(target);
            int hitCount = hitCounts.getOrDefault(target.getUniqueId(), 0);
            player.sendMessage("§7Now targeting: " + targetName + " (" + hitCount + "/3 hits)");
        }
        
        private LivingEntity findNextValidTarget() {
            // 从当前索引开始寻找下一个需要攻击的目标
            for (int i = 0; i < targets.size(); i++) {
                int index = (currentTargetIndex + i) % targets.size();
                LivingEntity target = targets.get(index);
                if (isTargetValid(target) && hitCounts.getOrDefault(target.getUniqueId(), 0) < 3) {
                    return target;
                }
            }
            return null; // 所有目标都已经攻击了3次
        }
        
        private void moveToNextTarget() {
            currentTargetIndex++;
            teleportToCurrentTarget();
        }
        
        private void cleanupDeadTargets() {
            // 移除死亡或无效的目标
            targets.removeIf(target -> !isTargetValid(target));
            
            // 如果当前索引超出范围，重置为0
            if (!targets.isEmpty() && currentTargetIndex >= targets.size()) {
                currentTargetIndex = 0;
            }
        }
        
        private int calculateCompletedRounds() {
            // 计算完成的轮次（基于平均攻击次数）
            if (targets.isEmpty()) return 0;
            
            int totalHits = 0;
            for (LivingEntity target : targets) {
                totalHits += hitCounts.getOrDefault(target.getUniqueId(), 0);
            }
            
            int averageHits = totalHits / targets.size();
            return Math.min(3, averageHits);
        }
        
        private boolean isTargetValid(LivingEntity target) {
            return target != null && 
                   target.isValid() && 
                   !target.isDead() && 
                   target.getWorld().equals(player.getWorld()) &&
                   target.getLocation().distance(player.getLocation()) <= 30; // 增加距离检查，防止目标跑太远
        }
        
        private void updatePlayerPosition(LivingEntity target) {
            // 计算位置在目标身后1格，上方1格
            Vector direction = target.getLocation().getDirection();
            Vector behind = direction.multiply(-1).normalize();
            
            Location teleportLoc = target.getLocation().add(behind);
            teleportLoc.setY(teleportLoc.getY() + 1);
            
            // 确保玩家面向目标
            Vector lookDirection = target.getLocation().toVector().subtract(teleportLoc.toVector()).normalize();
            teleportLoc.setDirection(lookDirection);
            
            // 传送玩家
            player.teleport(teleportLoc);
            
            // 创建红色粒子效果
            createRedParticleEffect(teleportLoc);
        }
        
        private void startFollowingTarget(LivingEntity target) {
            followTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // 检查目标是否仍然有效
                    if (!isTargetValid(target)) {
                        cancel();
                        player.sendMessage("§cTarget lost! Finding next target...");
                        moveToNextTarget();
                        return;
                    }
                    
                    // 持续更新玩家位置，保持在目标身后
                    updatePlayerPosition(target);
                }
            };
            
            // 每tick运行一次（20次/秒）
            followTask.runTaskTimer(plugin, 0L, 1L);
        }
        
        private void resetTimeout() {
            // 取消现有的超时任务
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }
            
            // 创建新的超时任务（2秒后结束技能）
            timeoutTask = new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage("§cTime's up! Rapid Displacement ending.");
                    endRapidDisplacement(player, totalAttackedTargets, calculateCompletedRounds());
                }
            };
            timeoutTask.runTaskLater(plugin, 40L); // 2秒 = 40 ticks
        }
        
        public void cancel() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }
            if (followTask != null) {
                followTask.cancel();
            }
        }
        
        private String getEntityName(LivingEntity entity) {
            if (entity instanceof Player) {
                return ((Player) entity).getName();
            } else {
                // 获取生物类型名称，并格式化为友好名称
                String typeName = entity.getType().toString().toLowerCase();
                typeName = typeName.replace("_", " ");
                return typeName;
            }
        }
        
        private void createExplosiveRedParticleEffect(Location location) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 2.0f);
            
            // 创建爆炸式粒子效果
            for (int i = 0; i < 100; i++) {
                double angle = 2 * Math.PI * Math.random();
                double radius = 3 * Math.random();
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = Math.random() * 4;
                
                location.getWorld().spawnParticle(
                    Particle.DUST,
                    location.getX() + x,
                    location.getY() + y,
                    location.getZ() + z,
                    1,
                    0, 0, 0,
                    dustOptions
                );
            }
            
            // 添加一些火焰粒子增强效果
            location.getWorld().spawnParticle(
                Particle.FLAME,
                location,
                30,
                2, 2, 2,
                0.1
            );
        }
    }
}
