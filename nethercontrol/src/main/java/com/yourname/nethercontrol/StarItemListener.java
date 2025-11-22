package com.yourname.nethercontrol;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class StarItemListener implements Listener {
    
    private final NetherControlManager controlManager = NetherControlPlugin.getInstance().getControlManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查是否是右键动作（包括潜行右键）
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 检查是否拿着沉星物品
        if (item != null && controlManager.isStarItem(item)) {
            event.setCancelled(true);
            
            // 处理沉星使用
            if (controlManager.handleStarUse()) {
                String message = NetherControlPlugin.getInstance().getConfig().getString("messages.star-used", 
                    "&a✨ 你使用了沉星，地狱门限制已解除！");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                
                // 消耗物品（减少数量或移除）
                PlayerInventory inventory = player.getInventory();
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    inventory.setItemInMainHand(null);
                }
                
                // 更新背包
                player.updateInventory();
            } else {
                player.sendMessage(ChatColor.YELLOW + "⚠ 地狱门限制已经被解除了！");
            }
        }
    }
}
