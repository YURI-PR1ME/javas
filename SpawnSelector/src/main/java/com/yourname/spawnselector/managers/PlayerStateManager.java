package com.yourname.spawnselector.managers;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerStateManager {
    
    private final Set<UUID> frozenPlayers;
    
    public PlayerStateManager() {
        this.frozenPlayers = new HashSet<>();
    }
    
    // 注意：这个方法现在委托给PlayerDataManager
    public boolean hasPlayerChosenSpawn(Player player) {
        // 这个方法现在应该通过PlayerDataManager来检查
        // 为了保持兼容性，我们返回false，让调用者使用PlayerDataManager
        return false;
    }
    
    public void setPlayerChosenSpawn(Player player, boolean chosen) {
        // 这个方法现在应该通过PlayerDataManager来设置
        // 为了保持兼容性，我们不执行任何操作
    }
    
    public boolean isPlayerFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }
    
    public void setPlayerFrozen(Player player, boolean frozen) {
        if (frozen) {
            frozenPlayers.add(player.getUniqueId());
        } else {
            frozenPlayers.remove(player.getUniqueId());
        }
    }
    
    public void clearPlayerData(Player player) {
        // 只清除冻结状态，不清除选择状态
        frozenPlayers.remove(player.getUniqueId());
    }
}
