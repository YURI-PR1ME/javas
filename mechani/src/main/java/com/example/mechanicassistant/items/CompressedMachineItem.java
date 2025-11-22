package com.example.mechanicassistant.items;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;

import java.util.*;

import com.example.mechanicassistant.MechanicAssistant;
import com.example.mechanicassistant.utils.StructureSerializer;

public class CompressedMachineItem implements Listener {
    private static final Map<UUID, BlockFace> playerPlacementDirections = new HashMap<>();
    
    private static NamespacedKey getStructureDataKey() {
        return new NamespacedKey(MechanicAssistant.getInstance(), "structure_data");
    }
    
    public static ItemStack createWithStructure(String structureData, Location loc1, Location loc2) {
        ItemStack machine = new ItemStack(Material.IRON_INGOT);
        ItemMeta meta = machine.getItemMeta();
        
        // 计算结构尺寸
        int width = Math.abs(loc1.getBlockX() - loc2.getBlockX()) + 1;
        int height = Math.abs(loc1.getBlockY() - loc2.getBlockY()) + 1;
        int length = Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) + 1;
        
        meta.displayName(Component.text("§e压缩机械"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7存储的结构: " + width + "x" + height + "x" + length));
        lore.add(Component.text("§7右键放置结构"));
        lore.add(Component.text("§7潜行右键旋转方向"));
        meta.lore(lore);
        
        // 使用PersistentDataContainer存储结构数据
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(getStructureDataKey(), PersistentDataType.STRING, structureData);
        
        machine.setItemMeta(meta);
        return machine;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) return;
        
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(getStructureDataKey())) return;
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            
            String structureData = pdc.get(getStructureDataKey(), PersistentDataType.STRING);
            if (structureData == null) return;
            
            BlockFace facing = getPlayerFacing(player);
            UUID playerId = player.getUniqueId();
            
            // 处理潜行旋转
            if (player.isSneaking()) {
                BlockFace currentFace = playerPlacementDirections.getOrDefault(playerId, BlockFace.NORTH);
                BlockFace newFace = rotateFacing(currentFace);
                playerPlacementDirections.put(playerId, newFace);
                player.sendMessage("§a放置方向: " + facingToString(newFace));
                return;
            }
            
            // 放置结构
            Location pasteLoc = calculatePasteLocation(event.getClickedBlock(), event.getBlockFace());
            BlockFace placementFacing = playerPlacementDirections.getOrDefault(playerId, facing);
            
            boolean success = StructureSerializer.pasteStructure(pasteLoc, structureData, placementFacing);
            if (success) {
                player.sendMessage("§a结构放置成功！");
                
                // 移除手中的压缩机械物品
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            } else {
                player.sendMessage("§c结构放置失败，空间不足或位置无效！");
            }
        }
    }
    
    private BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        else if (yaw < 135) return BlockFace.WEST;
        else if (yaw < 225) return BlockFace.NORTH;
        else return BlockFace.EAST;
    }
    
    private BlockFace rotateFacing(BlockFace current) {
        return switch (current) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.NORTH;
        };
    }
    
    private String facingToString(BlockFace facing) {
        return switch (facing) {
            case NORTH -> "北";
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            default -> "未知";
        };
    }
    
    private Location calculatePasteLocation(Block clickedBlock, BlockFace blockFace) {
        return clickedBlock.getRelative(blockFace).getLocation();
    }
}
