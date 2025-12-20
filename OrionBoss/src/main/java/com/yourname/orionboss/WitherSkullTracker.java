package com.yourname.orionboss;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Random;

public class WitherSkullTracker extends BukkitRunnable {

    private final WitherSkull skull;
    private final OrionBoss boss;
    private int lifespan = 0;
    private static final int MAX_LIFESPAN = 200; // 10秒最大寿命
    private Player currentTarget = null;
    private final Random random = new Random();
    
    // 追踪参数
    private double trackingStrength = 0.3; // 初始追踪强度
    private final double maxTrackingStrength = 0.8; // 最大追踪强度
    private final double trackingStrengthIncrease = 0.02; // 每tick追踪强度增加
    
    public WitherSkullTracker(WitherSkull skull, OrionBoss boss) {
        this.skull = skull;
        this.boss = boss;
    }

    public void startTracking() {
        this.runTaskTimer(boss.getPlugin(), 0L, 1L); // 每tick运行
    }

    @Override
    public void run() {
        // 检查骷髅头是否还存在
        if (!skull.isValid() || skull.isDead() || lifespan >= MAX_LIFESPAN) {
            boss.removeSkullTracker(skull.getUniqueId());
            cancel();
            return;
        }

        lifespan++;
        
        // 随时间增加追踪强度
        if (trackingStrength < maxTrackingStrength) {
            trackingStrength += trackingStrengthIncrease;
        }
        
        // 每隔几tick重新选择目标，避免过度切换
        if (currentTarget == null || lifespan % 20 == 0) {
            currentTarget = findBestTarget();
        }
        
        if (currentTarget == null || !currentTarget.isOnline() || currentTarget.isDead()) {
            return;
        }

        // 计算追踪方向
        Location skullLoc = skull.getLocation();
        Location targetLoc = currentTarget.getLocation();
        
        // 预测目标移动，瞄准前方一点位置
        Vector targetVelocity = currentTarget.getVelocity();
        double predictionFactor = 0.5; // 预测因子
        Vector predictedOffset = targetVelocity.multiply(predictionFactor);
        targetLoc = targetLoc.clone().add(predictedOffset);
        
        // 瞄准玩家身体中心
        targetLoc = targetLoc.add(0, 1, 0);
        
        Vector direction = targetLoc.toVector().subtract(skullLoc.toVector()).normalize();
        
        // 平滑转向（动态追踪强度）
        Vector currentDirection = skull.getVelocity().normalize();
        if (currentDirection.length() < 0.1) {
            currentDirection = direction; // 如果当前速度太小，使用目标方向
        }
        
        Vector newDirection = currentDirection.multiply(1 - trackingStrength)
                .add(direction.multiply(trackingStrength)).normalize();
        
        // 应用新方向并保持速度
        double speed = Math.max(0.8, skull.getVelocity().length()); // 保持最小速度
        skull.setVelocity(newDirection.multiply(speed));
        
        // 更新骷髅头方向
        skull.setDirection(newDirection);
        
        // 添加追踪粒子效果
        if (lifespan % 5 == 0) {
            skull.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, 
                skullLoc, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private Player findBestTarget() {
        Player bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (Player player : skull.getWorld().getPlayers()) {
            // 只追踪生存模式的玩家
            if (player.getGameMode() != GameMode.SURVIVAL) continue;
            
            double distance = player.getLocation().distance(skull.getLocation());
            
            // 过滤过远的目标（最大追踪范围40格）
            if (distance > 40) continue;
            
            // 计算综合分数：距离近 + 视线内 + 移动速度适中
            double score = 0;
            
            // 距离分数：越近分数越高
            score += (40 - distance) * 2;
            
            // 视线检测：使用rayTrace检查是否有阻挡
            Location skullLoc = skull.getLocation();
            Location playerLoc = player.getLocation().add(0, 1, 0);
            Vector direction = playerLoc.toVector().subtract(skullLoc.toVector());
            
            // 检查视线是否被阻挡（不考虑透明方块）
            if (skull.getWorld().rayTraceBlocks(skullLoc, direction, distance) == null) {
                score += 15;
            }
            
            // 移动速度分数：移动的目标更容易被追踪
            double speed = player.getVelocity().length();
            score += speed * 10;
            
            // 随机因素：避免所有头颅都追踪同一目标
            score += random.nextDouble() * 5;
            
            if (score > bestScore) {
                bestScore = score;
                bestTarget = player;
            }
        }
        
        return bestTarget;
    }
}
