package com.yourname.orionboss;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class OrionBossListener implements Listener {

    private final OrionBossPlugin plugin;

    public OrionBossListener(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if right-clicking with Tyrant Pickaxe on Dragon Egg in The End
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.DRAGON_EGG &&
            item != null && isTyrantPickaxe(item) &&
            player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            
            event.setCancelled(true);
            
            // 更严格的Boss存在检查
            if (!plugin.getActiveBosses().isEmpty()) {
                // 额外检查Boss是否还存活
                for (OrionBoss activeBoss : plugin.getActiveBosses().values()) {
                    if (activeBoss.getBoss() != null && !activeBoss.getBoss().isDead()) {
                        player.sendMessage("§cOrion is already active in this world!");
                        return;
                    }
                }
                // 如果Boss已死亡但仍在map中，清理它们
                plugin.getActiveBosses().clear();
            }
            
            // Spawn Orion Boss
            Location spawnLocation = event.getClickedBlock().getLocation().add(0, 2, 0);
            plugin.spawnOrionBoss(spawnLocation);
            
            // Remove dragon egg
            event.getClickedBlock().setType(Material.AIR);
            
            // Play effects
            player.getWorld().playSound(spawnLocation, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
            player.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, spawnLocation, 100, 3, 3, 3);
        }
    }

    private boolean isTyrantPickaxe(ItemStack item) {
        if (item.getType() != Material.NETHERITE_PICKAXE) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        
        return meta.getDisplayName().equals("§6暴君之镐");
    }
}
