package com.yourname.drownedking;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class DrownedKingListener implements Listener {
    
    private final DrownedKingPlugin plugin;
    private final DrownedKingManager manager;
    
    public DrownedKingListener(DrownedKingPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getDrownedKingManager();
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 检查是否是溺尸王攻击玩家
        if (event.getEntity() instanceof Player && isDrownedKing(event.getDamager())) {
            Player player = (Player) event.getEntity();
            Drowned boss = (Drowned) event.getDamager();
            
            // 检查是否致命
            if (player.getHealth() - event.getFinalDamage() <= 0) {
                manager.handlePlayerDeath(player, boss);
            }
        }
        
        // 检查是否是玩家攻击溺尸王
        if (isDrownedKing(event.getEntity()) && event.getDamager() instanceof Player) {
            Drowned boss = (Drowned) event.getEntity();
            
            // 检查是否致命
            if (boss.getHealth() - event.getFinalDamage() <= 0) {
                manager.handleBossDeath(boss);
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 检查死亡的实体是否是溺尸王
        if (isDrownedKing(event.getEntity())) {
            Drowned boss = (Drowned) event.getEntity();
            manager.handleBossDeath(boss);
            
            // 清除掉落物
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        
        // 检查死亡的实体是否是溺尸守卫
        if (event.getEntity() instanceof Drowned) {
            Drowned drowned = (Drowned) event.getEntity();
            if (isMinion(drowned)) {
                // 清除守卫的掉落物
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 新玩家加入时添加到所有活跃Boss的血条
        manager.addPlayerToAllBossBars(event.getPlayer());
    }
    
    // 新增事件：处理守卫发射三叉戟
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) {
            return;
        }
        
        Trident trident = (Trident) event.getEntity();
        
        // 检查发射者是否是溺尸守卫
        if (trident.getShooter() instanceof Drowned) {
            Drowned shooter = (Drowned) trident.getShooter();
            
            // 如果是溺尸守卫，则处理其三叉戟
            if (isMinion(shooter)) {
                manager.handleMinionTrident(trident, shooter);
            }
        }
    }
    
    private boolean isDrownedKing(Entity entity) {
        if (!(entity instanceof Drowned)) return false;
        
        return entity.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "drowned_king_boss"),
            PersistentDataType.STRING
        );
    }
    
    private boolean isMinion(Entity entity) {
        if (!(entity instanceof Drowned)) return false;
        
        return entity.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "minion_of"),
            PersistentDataType.STRING
        );
    }
}
