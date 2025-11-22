package com.yourname.arenaplugin;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Arena {
    private final String name;
    private final Location corner1;
    private final Location corner2;
    private final BoundingBox bounds;
    private final BoundingBox innerBounds; // 用于边界提醒
    
    private ArenaState state = ArenaState.WAITING_FOR_PLAYERS;
    private Map<UUID, Team> fighters = new HashMap<>();
    private Player redPlayer;
    private Player bluePlayer;
    
    public Arena(String name, Location pos1, Location pos2) {
        this.name = name;
        this.corner1 = pos1;
        this.corner2 = pos2;
        
        // 创建边界框 - 明确高度范围为0到255
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        // 固定高度范围：0到255
        double minY = 0;
        double maxY = 255;
        
        this.bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        
        // 创建内部边界（用于提醒）
        int alertDistance = ArenaPlugin.getInstance().getConfig().getInt("arena.boundary-alert-distance", 3);
        this.innerBounds = new BoundingBox(
            minX + alertDistance, minY, minZ + alertDistance,
            maxX - alertDistance, maxY, maxZ - alertDistance
        );
        
        // 输出调试信息
        Bukkit.getLogger().info("擂台 '" + name + "' 已创建:");
        Bukkit.getLogger().info("范围: X[" + minX + " to " + maxX + "], Z[" + minZ + " to " + maxZ + "], Y[0 to 255]");
        Bukkit.getLogger().info("世界: " + pos1.getWorld().getName());
    }
   public boolean areBothFightersInArena() {
    if (redPlayer == null || bluePlayer == null) {
        return false;
    }
    
    return isInArena(redPlayer) && isInArena(bluePlayer);
} 
    // 检查玩家是否在擂台内
    public boolean isInArena(Player player) {
        if (player == null) return false;
        
        Location playerLoc = player.getLocation();
        boolean inArena = bounds.contains(playerLoc.toVector());
        
        // 调试信息
        if (inArena && !player.hasMetadata("arena_debug")) {
            player.setMetadata("arena_debug", new org.bukkit.metadata.FixedMetadataValue(ArenaPlugin.getInstance(), true));
            player.sendMessage(ChatColor.GRAY + "[调试] 你在擂台区域内: " + 
                "X=" + (int)playerLoc.getX() + ", Z=" + (int)playerLoc.getZ());
        } else if (!inArena && player.hasMetadata("arena_debug")) {
            player.removeMetadata("arena_debug", ArenaPlugin.getInstance());
        }
        
        return inArena;
    }
    
    // 检查玩家是否接近边界
    public boolean isNearBoundary(Player player) {
        if (player == null) return false;
        return !innerBounds.contains(player.getLocation().toVector());
    }
    
    // 检查玩家是否是选手
    public boolean isFighter(Player player) {
        return player != null && fighters.containsKey(player.getUniqueId());
    }
    
    // 设置选手
    public void setFighters(Player player1, Player player2) {
        this.fighters.clear();
        
        if (player1 != null) {
            this.fighters.put(player1.getUniqueId(), Team.RED);
            this.redPlayer = player1;
            player1.sendMessage(ChatColor.RED + "你被分配到红队！");
        }
        
        if (player2 != null) {
            this.fighters.put(player2.getUniqueId(), Team.BLUE);
            this.bluePlayer = player2;
            player2.sendMessage(ChatColor.BLUE + "你被分配到蓝队！");
        }
        
        // 广播选手信息
        if (player1 != null && player2 != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "🎯 选手已确定: " + 
                ChatColor.RED + player1.getName() + ChatColor.WHITE + " vs " + 
                ChatColor.BLUE + player2.getName());
        }
    }
    
    // 获取对手
    public Player getOpponent(Player player) {
        if (player == null) return null;
        
        if (player.equals(redPlayer)) {
            return bluePlayer;
        } else if (player.equals(bluePlayer)) {
            return redPlayer;
        }
        return null;
    }
    
    // 获取玩家队伍
    public Team getPlayerTeam(Player player) {
        if (player == null) return null;
        return fighters.get(player.getUniqueId());
    }
    
    // 向选手广播消息
    public void broadcastToFighters(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        if (redPlayer != null) redPlayer.sendMessage(formatted);
        if (bluePlayer != null) bluePlayer.sendMessage(formatted);
    }
    
    // 重置擂台
    public void reset() {
        this.state = ArenaState.WAITING_FOR_PLAYERS;
        this.fighters.clear();
        this.redPlayer = null;
        this.bluePlayer = null;
    }
    
    // 获取擂台中心点（用于特效）
    public Location getCenter() {
        return corner1.clone().add(
            corner2.clone().subtract(corner1).multiply(0.5)
        );
    }
    
    // Getter 和 Setter 方法
    public String getName() { return name; }
    public Location getCorner1() { return corner1; }
    public Location getCorner2() { return corner2; }
    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }
    public Player getRedPlayer() { return redPlayer; }
    public Player getBluePlayer() { return bluePlayer; }
    public boolean isPlayerInArena(Player player) {
        return isInArena(player);
    }
    
    // 获取边界信息（用于调试）
    public String getBoundsInfo() {
        return String.format("X[%.1f-%.1f], Z[%.1f-%.1f], Y[0-255]", 
            bounds.getMinX(), bounds.getMaxX(), bounds.getMinZ(), bounds.getMaxZ());
    }
}

enum ArenaState {
    WAITING_FOR_PLAYERS, // 等待选手
    PREPARATION,         // 准备阶段（下注）
    IN_PROGRESS,         // 比赛进行中
    FINISHED             // 比赛结束
}

enum Team {
    RED, BLUE
}
