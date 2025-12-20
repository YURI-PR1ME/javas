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
import java.util.UUID;

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
            case "cooldown":
                handleCooldown(sender, args);
                break;
            case "kills":
                handleKills(sender, args);
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
        sender.sendMessage("§6/pacificwind cooldown [玩家] §7- 查看下雨冷却时间");
        sender.sendMessage("§6/pacificwind kills [玩家] §7- 查看击杀进度");
        
        if (sender.hasPermission("pacificwind.admin")) {
            sender.sendMessage("§8[管理员命令]");
            sender.sendMessage("§6/pacificwind reset §7- 重置暴君召唤限制");
            sender.sendMessage("§6/pacificwind cooldown clear [玩家] §7- 清除下雨冷却");
            sender.sendMessage("§6/pacificwind kills reset [玩家] §7- 重置玩家击杀计数");
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
        
        // 显示下雨冷却信息和击杀进度（如果是玩家）
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();
            
            if (windManager.isRainOnCooldown(playerId)) {
                long remaining = windManager.getRainCooldownRemaining(playerId);
                sender.sendMessage("§c⏳ 下雨冷却中: " + remaining + "秒");
            } else {
                sender.sendMessage("§a✅ 下雨技能可用");
            }
            
            int kills = windManager.getKillCount(playerId);
            //sender.sendMessage("§9⚔ 当前击杀进度: §e" + kills + "§9/20");
            
            if (kills > 0) {
                //sender.sendMessage("§7再击杀 §e" + (20 - kills) + " §7个实体可以重置下雨冷却");
            }
        }
    }
    
    private void handleCooldown(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            // 清除冷却
            if (!sender.hasPermission("pacificwind.admin")) {
                sender.sendMessage("§c❌ 你没有权限清除冷却");
                return;
            }
            
            if (args.length > 2) {
                // 清除指定玩家的冷却
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("§c❌ 玩家不存在或不在线");
                    return;
                }
                
                windManager.clearRainCooldown(target.getUniqueId());
                sender.sendMessage("§a✅ 已清除 " + target.getName() + " 的下雨冷却");
                target.sendMessage("§a✅ 你的下雨冷却已被管理员清除");
            } else if (sender instanceof Player) {
                // 清除自己的冷却
                windManager.clearRainCooldown(((Player) sender).getUniqueId());
                sender.sendMessage("§a✅ 已清除你的下雨冷却");
            } else {
                sender.sendMessage("§c❌ 控制台请指定玩家名: /pacificwind cooldown clear <玩家>");
            }
            return;
        }
        
        // 查看冷却
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (windManager.isRainOnCooldown(player.getUniqueId())) {
                long remaining = windManager.getRainCooldownRemaining(player.getUniqueId());
                sender.sendMessage("§c⏳ 下雨冷却剩余: " + remaining + "秒");
            } else {
                sender.sendMessage("§a✅ 下雨技能可用");
            }
        } else {
            sender.sendMessage("§c❌ 只有玩家可以查看冷却时间");
        }
    }
    
    private void handleKills(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
            // 重置击杀计数
            if (!sender.hasPermission("pacificwind.admin")) {
                sender.sendMessage("§c❌ 你没有权限重置击杀计数");
                return;
            }
            
            if (args.length > 2) {
                // 重置指定玩家的击杀计数
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("§c❌ 玩家不存在或不在线");
                    return;
                }
                
                windManager.resetKillCount(target.getUniqueId());
                sender.sendMessage("§a✅ 已重置 " + target.getName() + " 的击杀计数");
                target.sendMessage("§a✅ 你的击杀计数已被管理员重置");
            } else if (sender instanceof Player) {
                // 重置自己的击杀计数
                windManager.resetKillCount(((Player) sender).getUniqueId());
                sender.sendMessage("§a✅ 已重置你的击杀计数");
            } else {
                sender.sendMessage("§c❌ 控制台请指定玩家名: /pacificwind kills reset <玩家>");
            }
            return;
        }
        
        // 查看击杀进度
        if (args.length > 1) {
            // 查看指定玩家的击杀进度
            if (!sender.hasPermission("pacificwind.admin")) {
                sender.sendMessage("§c❌ 你没有权限查看其他玩家的击杀进度");
                return;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c❌ 玩家不存在或不在线");
                return;
            }
            
            int kills = windManager.getKillCount(target.getUniqueId());
            sender.sendMessage("§9⚔ " + target.getName() + " 的击杀进度: §e" + kills + "§9/20");
            
            if (kills > 0) {
                sender.sendMessage("§7再击杀 §e" + (20 - kills) + " §7个实体可以重置下雨冷却");
            }
        } else if (sender instanceof Player) {
            // 查看自己的击杀进度
            Player player = (Player) sender;
            int kills = windManager.getKillCount(player.getUniqueId());
            sender.sendMessage("§9⚔ 你的击杀进度: §e" + kills + "§9/20");
            
            if (kills > 0) {
                sender.sendMessage("§7再击杀 §e" + (20 - kills) + " §7个实体可以重置下雨冷却");
            }
        } else {
            sender.sendMessage("§c❌ 控制台请指定玩家名: /pacificwind kills <玩家>");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("give");
            completions.add("help");
            completions.add("status");
            completions.add("cooldown");
            completions.add("kills");
            
            if (sender.hasPermission("pacificwind.admin")) {
                completions.add("reset");
            }
        } else if (args.length == 2) {
            if ("give".equals(args[0])) {
                // 在线玩家列表
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("cooldown".equals(args[0])) {
                completions.add("clear");
                // 在线玩家列表
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("kills".equals(args[0])) {
                completions.add("reset");
                // 在线玩家列表
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3) {
            if ("cooldown".equals(args[0]) && "clear".equals(args[1])) {
                // 在线玩家列表
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("kills".equals(args[0]) && "reset".equals(args[1])) {
                // 在线玩家列表
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }
}
