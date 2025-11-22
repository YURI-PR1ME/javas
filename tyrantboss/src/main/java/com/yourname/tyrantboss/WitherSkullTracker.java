package com.yourname.tyrantboss;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WitherSkullTracker extends BukkitRunnable {

    private final WitherSkull skull;
    private final TyrantBoss boss;
    private int lifespan = 0;
    private static final int MAX_LIFESPAN = 200; // 10秒最大寿命

    public WitherSkullTracker(WitherSkull skull, TyrantBoss boss) {
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
        
        // 寻找最近的玩家
        Player target = findNearestPlayer();
        if (target == null) return;

        // 计算追踪方向
        Location skullLoc = skull.getLocation();
        Location targetLoc = target.getLocation().add(0, 1, 0); // 瞄准玩家身体中心
        
        Vector direction = targetLoc.toVector().subtract(skullLoc.toVector()).normalize();
        
        // 平滑转向（避免瞬间转向）
        Vector currentDirection = skull.getVelocity().normalize();
        Vector newDirection = currentDirection.multiply(0.7).add(direction.multiply(0.3)).normalize();
        
        // 应用新方向并保持速度
        double speed = skull.getVelocity().length();
        skull.setVelocity(newDirection.multiply(speed));
        
        // 更新骷髅头方向
        skull.setDirection(newDirection);
    }

    private Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : skull.getWorld().getPlayers()) {
            // 只追踪生存模式的玩家
            if (player.getGameMode() != GameMode.SURVIVAL) continue;
            
            double distance = player.getLocation().distance(skull.getLocation());
            if (distance < nearestDistance && distance <= 30) { // 30格内追踪
                nearest = player;
                nearestDistance = distance;
            }
        }
        
        return nearest;
    }
}
