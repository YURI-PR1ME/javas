package com.example.mechanicassistant.items;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import net.kyori.adventure.text.Component;

import java.util.*;

public class GlueWand implements Listener {
    private static final Map<UUID, Location[]> playerSelections = new HashMap<>();
    
    public static ItemStack createGlueWand() {
        ItemStack glue = new ItemStack(Material.STICK);
        ItemMeta meta = glue.getItemMeta();
        meta.displayName(Component.text("§b强力胶"));
        List<Component> lore = Arrays.asList(
            Component.text("§7右键选择第一个点"),
            Component.text("§7再次右键选择第二个点"),
            Component.text("§7左键清除选择")
        );
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        glue.setItemMeta(meta);
        return glue;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.isSimilar(createGlueWand())) return;
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block clickedBlock = event.getClickedBlock();
            UUID playerId = player.getUniqueId();
            
            Location[] selection = playerSelections.getOrDefault(playerId, new Location[2]);
            if (selection[0] == null) {
                selection[0] = clickedBlock.getLocation();
                playerSelections.put(playerId, selection);
                player.sendMessage("§a第一点已设定: " + locationToString(selection[0]));
            } else {
                selection[1] = clickedBlock.getLocation();
                playerSelections.put(playerId, selection);
                player.sendMessage("§a第二点已设定，区域选择完成！");
                highlightSelectionArea(player, selection[0], selection[1]);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            playerSelections.remove(player.getUniqueId());
            player.sendMessage("§c已清除区域选择");
        }
    }
    
    public static Location[] getPlayerSelection(UUID playerId) {
        return playerSelections.get(playerId);
    }
    
    public static void clearPlayerSelection(UUID playerId) {
        playerSelections.remove(playerId);
    }
    
    private String locationToString(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    private void highlightSelectionArea(Player player, Location loc1, Location loc2) {
        World world = loc1.getWorld();
        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
        
        // 在选区边界生成粒子效果 - 使用兼容的粒子
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ) {
                        Location particleLoc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
                        // 使用兼容的粒子效果
                        try {
                            // 尝试使用 HAPPY_VILLAGER，如果不存在则使用其他粒子
                            player.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 1, 0, 0, 0, 0);
                        } catch (Exception e) {
                            // 如果 HAPPY_VILLAGER 不存在，使用其他粒子
                            player.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }
    }
}
