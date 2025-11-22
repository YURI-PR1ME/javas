// [file name]: DeathCrownCommand.java
package com.yourname.deathcrown;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DeathCrownCommand implements CommandExecutor, TabCompleter {
    
    private final DeathCrownPlugin plugin;
    private final DeathCrownManager crownManager;
    
    public DeathCrownCommand(DeathCrownPlugin plugin) {
        this.plugin = plugin;
        this.crownManager = plugin.getCrownManager();
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
                handleReset(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "help":
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8=== §4死亡王冠管理 §8===");
        sender.sendMessage("§6/deathcrown give §7- 给自己死亡王冠");
        sender.sendMessage("§6/deathcrown give <玩家> §7- 给指定玩家死亡王冠");
        sender.sendMessage("§6/deathcrown reset §7- 重置当前世界使用状态");
        sender.sendMessage("§6/deathcrown reset <世界> §7- 重置指定世界使用状态");
        sender.sendMessage("§6/deathcrown reset all §7- 重置所有世界使用状态");
        sender.sendMessage("§6/deathcrown status §7- 查看世界使用状态");
        sender.sendMessage("§6/deathcrown reload §7- 重载配置");
        sender.sendMessage("§6/deathcrown help §7- 显示此帮助");
        
        if (sender.hasPermission("deathcrown.admin")) {
            sender.sendMessage("§8[管理员命令]");
        }
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("deathcrown.give")) {
            sender.sendMessage("§c❌ 你没有权限给予死亡王冠");
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
            sender.sendMessage("§c❌ 控制台请指定玩家名: /deathcrown give <玩家>");
            return;
        }
        
        crownManager.giveDeathCrownToPlayer(targetPlayer);
        
        if (!targetPlayer.equals(sender)) {
            sender.sendMessage("§a✅ 已给予 §4" + targetPlayer.getName() + " §a死亡王冠");
        }
    }
    
    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("deathcrown.admin")) {
            sender.sendMessage("§c❌ 你没有权限重置世界使用状态");
            return;
        }
        
        if (args.length == 1) {
            // 重置当前世界（如果是玩家执行）
            if (sender instanceof Player) {
                Player player = (Player) sender;
                World world = player.getWorld();
                plugin.resetWorldUsage(world.getUID());
                sender.sendMessage("§a✅ 已重置世界 §e" + world.getName() + " §a的死亡王冠使用状态");
            } else {
                sender.sendMessage("§c❌ 控制台请指定世界名: /deathcrown reset <世界名|all>");
            }
        } else {
            if (args[1].equalsIgnoreCase("all")) {
                // 重置所有世界
                plugin.resetAllWorldUsage();
                sender.sendMessage("§a✅ 已重置所有世界的死亡王冠使用状态");
            } else {
                // 重置指定世界
                World world = Bukkit.getWorld(args[1]);
                if (world == null) {
                    sender.sendMessage("§c❌ 世界不存在: " + args[1]);
                    return;
                }
                plugin.resetWorldUsage(world.getUID());
                sender.sendMessage("§a✅ 已重置世界 §e" + world.getName() + " §a的死亡王冠使用状态");
            }
        }
    }
    
    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("deathcrown.status")) {
            sender.sendMessage("§c❌ 你没有权限查看状态");
            return;
        }
        
        sender.sendMessage("§8=== §4死亡王冠状态 §8===");
        
        for (World world : Bukkit.getWorlds()) {
            boolean used = plugin.isWorldUsed(world.getUID());
            String status = used ? "§c已使用" : "§a未使用";
            sender.sendMessage("§7" + world.getName() + ": " + status);
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("deathcrown.admin")) {
            sender.sendMessage("§c❌ 你没有权限重载配置");
            return;
        }
        
        plugin.reloadConfig();
        plugin.loadUsedWorlds();
        sender.sendMessage("§a✅ 死亡王冠配置已重载");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("give");
            completions.add("reset");
            completions.add("status");
            completions.add("reload");
            completions.add("help");
        } else if (args.length == 2 && "give".equals(args[0])) {
            // 在线玩家列表
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "reset".equals(args[0])) {
            // 世界列表
            completions.add("all");
            for (World world : Bukkit.getWorlds()) {
                completions.add(world.getName());
            }
        }
        
        return completions;
    }
}
