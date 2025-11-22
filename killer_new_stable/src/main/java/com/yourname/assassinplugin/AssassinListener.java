package com.yourname.assassinplugin;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.block.Action;

public class AssassinListener implements Listener {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        
        if (assassinManager.isDarkWebAccessItem(item)) {
            event.setCancelled(true);
            // 打开新的主菜单，包含AI杀手和玩家刺客两个选项
            AssassinGUI.openMainMenu(event.getPlayer());
        } else if (assassinManager.isTrackingCompass(item) && event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            updateTrackingCompass(event.getPlayer(), item);
        }
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 通讯书潜行+右键发送
        if (assassinManager.isCommunicationBook(item) && player.isSneaking()) {
            event.setCancelled(true);
            openCommunicationBookEditor(player, item);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        
        // 处理玩家刺客死亡
        if (assassinManager.isPlayerAssassin(player)) {
            assassinManager.handlePlayerAssassinDeath(player);
        }
        
        // 处理玩家刺客击杀
        if (killer != null && assassinManager.isPlayerAssassin(killer)) {
            assassinManager.handlePlayerAssassinKill(killer, player);
        }
        
        // 处理AI杀手击杀
        if (killer != null && killer != player) {
            assassinManager.handleKill(killer, player);
        }
        
        // 处理玩家死亡逻辑
        assassinManager.handlePlayerDeath(player);
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 检查是否是AI杀手攻击玩家
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
        
        // 检查是否是玩家攻击AI杀手
        if (isAssassin(event.getEntity()) && event.getDamager() instanceof Player) {
            LivingEntity assassin = (LivingEntity) event.getEntity();
            
            // 检查是否致命
            if (assassin.getHealth() - event.getFinalDamage() <= 0) {
                assassinManager.handleAssassinDeath(assassin);
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 检查死亡的实体是否是AI杀手
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
    
    // 检查AI杀手是否应该攻击这个玩家
    private boolean shouldAssassinAttack(LivingEntity assassin, Player target) {
        // 获取杀手的目标玩家UUID
        String targetUUIDStr = assassin.getPersistentDataContainer().get(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_target"),
            PersistentDataType.STRING
        );
        
        if (targetUUIDStr == null) return false;
        
        // 检查被攻击的玩家是否是杀手的目标
        boolean isCorrectTarget = target.getUniqueId().toString().equals(targetUUIDStr);
        
        // 如果是溺尸，即使目标暂时不正确也允许攻击（因为溺尸有特殊AI）
        if (assassin instanceof Drowned) {
            return true;
        }
        
        return isCorrectTarget;
    }
    
    private void updateTrackingCompass(Player player, ItemStack compass) {
        try {
            NamespacedKey sessionKey = new NamespacedKey(AssassinPlugin.getInstance(), "tracking_compass");
            String sessionIdStr = compass.getItemMeta().getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
            
            if (sessionIdStr != null) {
                java.util.UUID sessionId = java.util.UUID.fromString(sessionIdStr);
                PlayerContractSession session = assassinManager.getPlayerSession(sessionId);
                
                if (session != null && session.isActive()) {
                    Player target = org.bukkit.Bukkit.getPlayer(session.getTargetId());
                    if (target != null) {
                        // 更新指南针指向目标
                        player.setCompassTarget(target.getLocation());
                        player.sendMessage("§8[追踪] §7指南针已更新指向目标");
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage("§c❌ 指南针更新失败");
        }
    }
    
    private void openCommunicationBookEditor(Player player, ItemStack book) {
        ItemStack bookAndQuill = new ItemStack(org.bukkit.Material.WRITABLE_BOOK);
        org.bukkit.inventory.meta.BookMeta originalMeta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        org.bukkit.inventory.meta.BookMeta newMeta = (org.bukkit.inventory.meta.BookMeta) bookAndQuill.getItemMeta();
        
        if (originalMeta != null && originalMeta.hasPages()) {
            newMeta.setPages(originalMeta.getPages());
        }
        
        NamespacedKey sessionKey = new NamespacedKey(AssassinPlugin.getInstance(), "communication_book");
        String sessionId = originalMeta.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        newMeta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionId);
        
        NamespacedKey partnerKey = new NamespacedKey(AssassinPlugin.getInstance(), "communication_partner");
        String partnerId = originalMeta.getPersistentDataContainer().get(partnerKey, PersistentDataType.STRING);
        newMeta.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, partnerId);
        
        bookAndQuill.setItemMeta(newMeta);
        player.openBook(bookAndQuill);
    }
}
