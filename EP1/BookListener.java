package com.yourname.creditplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

public class BookListener implements Listener {
    
    private final CreditManager creditManager = CreditPlugin.getInstance().getCreditManager();
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player clicker = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        
        ItemStack item = clicker.getInventory().getItemInMainHand();
        
        // 检查是否拿着信用点书且潜行
        if (creditManager.isCreditBook(item) && clicker.isSneaking()) {
            event.setCancelled(true);
            
            // 发起交易
            if (creditManager.transferCredits(clicker, target, 1)) {
                // 交易成功
                clicker.sendMessage(ChatColor.GREEN + "✅ 交易成功！");
            }
        }
    }
}
