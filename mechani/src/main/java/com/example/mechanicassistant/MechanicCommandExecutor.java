package com.example.mechanicassistant;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.example.mechanicassistant.items.*;

public class MechanicCommandExecutor implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }
        
        Player player = (Player) sender;
        
        switch (command.getName().toLowerCase()) {
            case "givewrench":
                ItemStack wrench = StructureWrench.createWrench();
                player.getInventory().addItem(wrench);
                player.sendMessage("§a已获得结构扳手！");
                break;
                
            case "giveglue":
                ItemStack glue = GlueWand.createGlueWand();
                player.getInventory().addItem(glue);
                player.sendMessage("§a已获得强力胶！");
                break;
                
            case "givemachine":
                // 创建一个空的压缩机械
                ItemStack machine = new ItemStack(org.bukkit.Material.IRON_INGOT);
                org.bukkit.inventory.meta.ItemMeta meta = machine.getItemMeta();
                meta.displayName(net.kyori.adventure.text.Component.text("§e压缩机械（空）"));
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(net.kyori.adventure.text.Component.text("§7右键放置结构"));
                lore.add(net.kyori.adventure.text.Component.text("§7潜行右键旋转方向"));
                meta.lore(lore);
                machine.setItemMeta(meta);
                
                player.getInventory().addItem(machine);
                player.sendMessage("§a已获得空的压缩机械！");
                break;
                
            default:
                return false;
        }
        
        return true;
    }
}
