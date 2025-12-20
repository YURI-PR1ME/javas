package com.yourname.tyrantboss;

import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

public class TyrantNPCListener implements Listener {
    
    private final TyrantBossPlugin plugin;
    
    public TyrantNPCListener(TyrantBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteractNPC(PlayerInteractEntityEvent event) {
        // 检查是否右键点击了凋零骷髅
        if (!(event.getRightClicked() instanceof WitherSkeleton)) {
            return;
        }
        
        WitherSkeleton entity = (WitherSkeleton) event.getRightClicked();
        Player player = event.getPlayer();
        
        // 检查是否是NPC暴君
        TyrantNPC npc = plugin.getNPCTyrants().get(entity.getUniqueId());
        if (npc == null) {
            return;
        }
        
        event.setCancelled(true); // 防止其他交互
        
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        // 检查是否手持太平洋之风
        if (plugin.isPacificWind(handItem)) {
            // 激活战斗暴君
            plugin.activateTyrantBossFromNPC(player, entity);
        } else {
            // 显示随机对话
            String dialog = plugin.getRandomNPCDialog();
            player.sendMessage(dialog);
            
            // 播放音效
            player.playSound(player.getLocation(), 
                org.bukkit.Sound.ENTITY_VILLAGER_NO, 
                1.0f, 0.8f);
            
            // 显示粒子效果
            player.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER,
                entity.getLocation().add(0, 2, 0),
                5, 0.3, 0.3, 0.3, 0.01);
        }
    }
}
