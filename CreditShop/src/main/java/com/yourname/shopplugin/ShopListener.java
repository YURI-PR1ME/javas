package com.yourname.shopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {
    
    private final ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        ItemStack item = event.getItem();
        if (item == null) return;
        
        // 检查是否是商店终端物品
        if (ShopTerminal.isShopTerminal(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            ShopGUI.openMainMenu(player);
            return;
        }
        
        // 检查是否是旧的商店物品（兼容性）
        if (isShopOpenerItem(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            ShopGUI.openMainMenu(player);
        }
    }
    
    private boolean isShopOpenerItem(ItemStack item) {
        if (item.getType() != shopManager.getShopOpenerItem().getType()) return false;
        if (!item.hasItemMeta()) return false;
        
        String targetName = shopManager.getShopOpenerItem().getItemMeta().getDisplayName();
        return item.getItemMeta().getDisplayName().equals(targetName);
    }
}
