package com.yourname.orionboss;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TreasureBagListener implements Listener {

    private final OrionBossPlugin plugin;
    private final TreasureManager treasureManager;

    public TreasureBagListener(OrionBossPlugin plugin, TreasureManager treasureManager) {
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
        
        // Check if it's Orion's Treasure Bag
        if (meta.getDisplayName().equals("§6§l猎户座宝藏袋")) {
            event.setCancelled(true);
            
            // Open treasure bag
            treasureManager.openTreasureBag(player);
            
            // Consume one treasure bag
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            
            // Play sound
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUNDLE_DROP_CONTENTS, 1.0f, 1.0f);
        }
    }
}
