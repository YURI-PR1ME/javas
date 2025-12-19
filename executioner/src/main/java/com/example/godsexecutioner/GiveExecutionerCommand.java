package com.example.godsexecutioner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveExecutionerCommand implements CommandExecutor, TabCompleter {

    private final GodsExecutionerPlugin plugin;

    public GiveExecutionerCommand(GodsExecutionerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查权限
        if (!sender.hasPermission("godsexecutioner.give")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
            return true;
        }

        Player targetPlayer;

        if (args.length > 0) {
            // 给指定玩家
            targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "玩家不存在或不在线！");
                return true;
            }
        } else if (sender instanceof Player) {
            // 给自己
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "控制台使用请指定玩家名：/getexecutioner <玩家>");
            return true;
        }

        // 创建并给予物品
        ItemStack executioner = ExecutionerManager.createGodsExecutioner(plugin);
        
        if (targetPlayer.getInventory().addItem(executioner).isEmpty()) {
            targetPlayer.sendMessage(ChatColor.GOLD + "§l你获得了神之执行者！");
            targetPlayer.sendMessage(ChatColor.GRAY + "右键冲锋 | 潜行+左键发射凋零骷髅头 | 潜行+右键引力场");
            targetPlayer.playSound(targetPlayer.getLocation(), 
                org.bukkit.Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
            
            if (!targetPlayer.equals(sender)) {
                sender.sendMessage(ChatColor.GREEN + "已给予 " + targetPlayer.getName() + " 神之执行者");
            }
        } else {
            // 背包已满
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), executioner);
            targetPlayer.sendMessage(ChatColor.YELLOW + "背包已满，物品已掉落在地面上");
            
            if (!targetPlayer.equals(sender)) {
                sender.sendMessage(ChatColor.YELLOW + targetPlayer.getName() + " 的背包已满，物品已掉落");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 在线玩家列表
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
