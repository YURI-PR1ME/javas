package com.yourname.assassinplugin;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class AssassinListener implements Listener {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        
        if (assassinManager.isDarkWebAccessItem(item)) {
            event.setCancelled(true);
            AssassinGUI.openMainMenu(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 检查是否是杀手攻击玩家
        if (event.getEntity() instanceof Player && isAssassin(event.getDamager())) {
            Player target = (Player) event.getEntity();
            LivingEntity assassin = (LivingEntity) event.getDamager();
            
            // 检查杀手是否应该攻击这个玩家
            if (!shouldAssassinAttack(assassin, target)) {
                event.setCancelled(true);
                return;
            }
            
            // 检查是否致命
            if (target.getHealth() - event.getFinalDamage() <= 0) {
                assassinManager.handleAssassinKill(assassin, target);
            }
        }
        
        // 检查是否是玩家攻击杀手
        if (isAssassin(event.getEntity()) && event.getDamager() instanceof Player) {
            LivingEntity assassin = (LivingEntity) event.getEntity();
            
            // 检查是否致命
            if (assassin.getHealth() - event.getFinalDamage() <= 0) {
                assassinManager.handleAssassinDeath(assassin);
            }
        }
        
        // 检查是否是骷髅狙击手的箭矢
        if (event.getDamager() instanceof Arrow && event.getEntity() instanceof Player) {
            Arrow arrow = (Arrow) event.getDamager();
            if (arrow.getShooter() instanceof Skeleton && isAssassin((Skeleton) arrow.getShooter())) {
                Player target = (Player) event.getEntity();
                Skeleton sniper = (Skeleton) arrow.getShooter();
                
                // 检查是否致命
                if (target.getHealth() - event.getFinalDamage() <= 0) {
                    assassinManager.handleAssassinKill(sniper, target);
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 检查死亡的实体是否是杀手
        if (isAssassin(event.getEntity())) {
            LivingEntity assassin = event.getEntity();
            assassinManager.handleAssassinDeath(assassin);
            
            // 清除掉落物
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
    
    private boolean isAssassin(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        
        return entity.getPersistentDataContainer().has(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_contract"),
            PersistentDataType.STRING
        );
    }
    
    // 检查杀手是否应该攻击这个玩家
    private boolean shouldAssassinAttack(LivingEntity assassin, Player target) {
        // 获取杀手的目标玩家UUID
        String targetUUIDStr = assassin.getPersistentDataContainer().get(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_target"),
            PersistentDataType.STRING
        );
        
        if (targetUUIDStr == null) return false;
        
        // 检查被攻击的玩家是否是杀手的目标
        return target.getUniqueId().toString().equals(targetUUIDStr);
    }
}
