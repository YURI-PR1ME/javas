package com.yourname.tyrantboss;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TreasureBagListener implements Listener {

    private final TyrantBossPlugin plugin;
    private final TreasureManager treasureManager;

    public TreasureBagListener(TyrantBossPlugin plugin, TreasureManager treasureManager) {
        this.plugin = plugin;
        this.treasureManager = treasureManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.BUNDLE) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        
        // 检查是否是暴君宝藏袋
        if (meta.getDisplayName().equals("§6§l暴君宝藏袋")) {
            event.setCancelled(true);
            
            // 打开宝藏袋
            treasureManager.openTreasureBag(player);
            
            // 消耗一个宝藏袋
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            
            // 播放音效
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
        }
    }
}
