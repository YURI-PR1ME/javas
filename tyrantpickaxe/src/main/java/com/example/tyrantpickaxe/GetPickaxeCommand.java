package com.example.tyrantpickaxe;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetPickaxeCommand implements CommandExecutor {

    private final TyrantPickaxe plugin;

    public GetPickaxeCommand(TyrantPickaxe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("这个指令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;
        
        // 检查权限
        if (!player.hasPermission("tyrantpickaxe.get")) {
            player.sendMessage(ChatColor.RED + "你没有权限执行此操作。");
            return true;
        }

        // 创建并给予物品
        ItemStack pickaxe = ItemManager.createTyrantPickaxe(plugin);
        player.getInventory().addItem(pickaxe);
        player.sendMessage(ChatColor.GREEN + "你获得了 " + pickaxe.getItemMeta().getDisplayName());

        return true;
    }
}
