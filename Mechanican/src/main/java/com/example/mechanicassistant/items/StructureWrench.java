package com.example.mechanicassistant.items;

import org.bukkit.*;
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

import com.example.mechanicassistant.utils.StructureSerializer;

public class StructureWrench implements Listener {
    
    public static ItemStack createWrench() {
        ItemStack wrench = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = wrench.getItemMeta();
        meta.displayName(Component.text("§6结构扳手"));
        List<Component> lore = Arrays.asList(
            Component.text("§7潜行右键保存结构"),
            Component.text("§7到压缩机械")
        );
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        wrench.setItemMeta(meta);
        return wrench;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.isSimilar(createWrench())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!player.isSneaking()) return;
        
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        Location[] selection = GlueWand.getPlayerSelection(playerId);
        
        if (selection == null || selection[0] == null || selection[1] == null) {
            player.sendMessage("§c请先用强力胶选择一个区域！");
            return;
        }
        
        // 检查区域大小
        Location loc1 = selection[0];
        Location loc2 = selection[1];
        int width = Math.abs(loc1.getBlockX() - loc2.getBlockX()) + 1;
        int height = Math.abs(loc1.getBlockY() - loc2.getBlockY()) + 1;
        int length = Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) + 1;
        
        if (width * height * length > 1000) {
            player.sendMessage("§c区域太大！最大允许 1000 个方块。");
            return;
        }
        
        // 序列化结构
        String structureData = StructureSerializer.serializeStructure(loc1, loc2);
        
        if (structureData == null || structureData.isEmpty()) {
            player.sendMessage("§c结构保存失败！");
            return;
        }
        
        // 创建压缩机械物品
        ItemStack compressedMachine = CompressedMachineItem.createWithStructure(structureData, loc1, loc2);
        
        // 给予玩家物品
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(compressedMachine);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), compressedMachine);
        }
        
        // 清除原区域方块和选择
        StructureSerializer.clearStructure(loc1, loc2);
        GlueWand.clearPlayerSelection(playerId);
        
        player.sendMessage("§a结构已成功保存到压缩机械！");
        player.sendMessage("§b结构尺寸: " + width + "x" + height + "x" + length);
    }
}
