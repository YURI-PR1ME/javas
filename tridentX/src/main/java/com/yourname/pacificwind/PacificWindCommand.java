// [file name]: PacificWindCommand.java
package com.yourname.pacificwind;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PacificWindCommand implements CommandExecutor, TabCompleter {
    
    private final PacificWindPlugin plugin;
    private final PacificWindManager windManager;
    
    public PacificWindCommand(PacificWindPlugin plugin) {
        this.plugin = plugin;
        this.windManager = plugin.getWindManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "give":
                handleGive(sender, args);
                break;
            case "reset":
                handleReset(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "help":
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8=== §9太平洋之风 §8===");
        sender.sendMessage("§6/pacificwind give §7- 给自己太平洋之风三叉戟");
        sender.sendMessage("§6/pacificwind give <玩家> §7- 给指定玩家太平洋之风三叉戟");
        sender.sendMessage("§6/pacificwind status §7- 查看暴君召唤状态");
        
        if (sender.hasPermission("pacificwind.admin")) {
            sender.sendMessage("§8[管理员命令]");
            sender.sendMessage("§6/pacificwind reset §7- 重置暴君召唤限制");
            sender.sendMessage("§6/pacificwind help §7- 显示此帮助");
        }
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pacificwind.give")) {
            sender.sendMessage("§c❌ 你没有权限给予太平洋之风");
            return;
        }
        
        Player targetPlayer;
        
        if (args.length > 1) {
            // 给指定玩家
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage("§c❌ 玩家不存在或不在线");
                return;
            }
        } else if (sender instanceof Player) {
            // 给自己
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage("§c❌ 控制台请指定玩家名: /pacificwind give <玩家>");
            return;
        }
        
        windManager.givePacificWindToPlayer(targetPlayer);
        
        if (!targetPlayer.equals(sender)) {
            sender.sendMessage("§a✅ 已给予 §9" + targetPlayer.getName() + " §a太平洋之风三叉戟");
        }
    }
    
    private void handleReset(CommandSender sender) {
        if (!sender.hasPermission("pacificwind.admin")) {
            sender.sendMessage("§c❌ 你没有权限重置暴君召唤限制");
            return;
        }
        
        plugin.resetTyrantSummoned();
        sender.sendMessage("§a✅ 暴君召唤限制已重置，现在可以重新召唤暴君");
        plugin.getLogger().info("暴君召唤限制已被 " + sender.getName() + " 重置");
    }
    
    private void handleStatus(CommandSender sender) {
        boolean isSummoned = plugin.isTyrantSummoned();
        if (isSummoned) {
            sender.sendMessage("§c⚡ 暴君已被召唤，无法再次召唤");
        } else {
            sender.sendMessage("§a✅ 暴君尚未被召唤，可以召唤");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("give");
            completions.add("help");
            completions.add("status");
            
            if (sender.hasPermission("pacificwind.admin")) {
                completions.add("reset");
            }
        } else if (args.length == 2 && "give".equals(args[0])) {
            // 在线玩家列表
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
