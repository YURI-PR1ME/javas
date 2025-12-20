package com.yourname.hiddenscore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreCommand implements CommandExecutor, TabCompleter {
    
    private final ScoreManager scoreManager = HiddenScorePlugin.getInstance().getScoreManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        if (!sender.hasPermission("hiddenscore.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限使用此命令");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "view":
                handleView(sender, args);
                break;
            case "lowestcredit":
                handleLowestCredit(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 隐藏分系统帮助 ===");
        if (sender.hasPermission("hiddenscore.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/hiddenscore view [玩家] " + ChatColor.WHITE + "- 查看隐藏分");
            sender.sendMessage(ChatColor.YELLOW + "/hiddenscore lowestcredit " + ChatColor.WHITE + "- 触发信用点最低统计");
            sender.sendMessage(ChatColor.YELLOW + "/hiddenscore reload " + ChatColor.WHITE + "- 重载配置");
        }
    }
    
    private void handleView(CommandSender sender, String[] args) {
        if (args.length > 1) {
            // 查看指定玩家的分数
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
                return;
            }
            
            int score = scoreManager.getScore(target);
            sender.sendMessage(ChatColor.GREEN + "📊 " + target.getName() + " 的隐藏分: " + score);
        } else {
            // 查看所有玩家分数
            Map<UUID, Integer> allScores = scoreManager.getAllScores();
            
            if (allScores.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ 暂无隐藏分数据");
                return;
            }
            
            sender.sendMessage(ChatColor.GOLD + "=== 所有玩家隐藏分 ===");
            for (Map.Entry<UUID, Integer> entry : allScores.entrySet()) {
                String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (playerName == null) playerName = "未知玩家";
                sender.sendMessage(ChatColor.WHITE + playerName + ": " + ChatColor.GREEN + entry.getValue());
            }
        }
    }
    
    private void handleLowestCredit(CommandSender sender) {
        scoreManager.processLowestCreditPlayer();
        sender.sendMessage(ChatColor.GREEN + "✅ 已触发信用点最低玩家统计");
    }
    
    private void handleReload(CommandSender sender) {
        HiddenScorePlugin.getInstance().reloadConfig();
        scoreManager.saveAllData();
        sender.sendMessage(ChatColor.GREEN + "✅ 配置已重载");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("view");
            if (sender.hasPermission("hiddenscore.admin")) {
                completions.addAll(List.of("lowestcredit", "reload"));
            }
        } else if (args.length == 2 && "view".equals(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
