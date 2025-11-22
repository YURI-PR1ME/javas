// [file name]: DeathCrownListener.java
package com.yourname.deathcrown;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DeathCrownListener implements Listener {
    
    private final DeathCrownPlugin plugin;
    private final DeathCrownManager crownManager;
    
    public DeathCrownListener(DeathCrownPlugin plugin) {
        this.plugin = plugin;
        this.crownManager = plugin.getCrownManager();
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 检查是否右键手持破碎王冠
        if (item != null && crownManager.isDeathCrown(item) && 
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            
            event.setCancelled(true);
            
            // 确认使用
            player.sendMessage("§4⚠ §4§l警告: 你即将使用破碎王冠!");
            player.sendMessage("§c这将召唤强大的§4溺尸王§c并消耗王冠!");
            player.sendMessage("§7每个世界只能使用一次!");
            player.sendMessage("§a确认使用？再次右键点击确认，或等待5秒取消。");
            
            // 设置确认标记
        player.setMetadata("deathcrown_confirmation", new FixedMetadataValue(plugin, System.currentTimeMillis()));}
        
        // 处理确认使用
        if (item != null && crownManager.isDeathCrown(item) && 
            player.hasMetadata("deathcrown_confirmation") &&
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            
            long confirmationTime = player.getMetadata("deathcrown_confirmation").get(0).asLong();
            long currentTime = System.currentTimeMillis();
            
            // 检查是否在5秒内确认
            if (currentTime - confirmationTime <= 5000) {
                player.removeMetadata("deathcrown_confirmation", plugin);
                
                // 使用王冠
                if (crownManager.useDeathCrown(player)) {
                    // 成功使用，事件已处理
                } else {
                    player.sendMessage("§c❌ 使用破碎王冠失败!");
                }
            } else {
                player.removeMetadata("deathcrown_confirmation", plugin);
                player.sendMessage("§7使用确认已超时，操作取消。");
            }
        }
    }
}
