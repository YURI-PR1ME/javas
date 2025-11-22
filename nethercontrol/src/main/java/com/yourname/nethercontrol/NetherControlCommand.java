// NetherControlCommand.java
package com.yourname.nethercontrol;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NetherControlCommand implements CommandExecutor, TabCompleter {
    
    private final NetherControlManager controlManager = NetherControlPlugin.getInstance().getControlManager();
    private final CreditIntegration creditIntegration = NetherControlPlugin.getInstance().getCreditIntegration();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "checkcredit": // 新增命令：检查玩家信用点状态
                handleCheckCredit(sender, args);
                break;
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 地狱门控制帮助 ===");
        if (sender.hasPermission("nethercontrol.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/nethercontrol reload " + ChatColor.WHITE + "- 重载配置");
            sender.sendMessage(ChatColor.YELLOW + "/nethercontrol status " + ChatColor.WHITE + "- 查看状态");
            sender.sendMessage(ChatColor.YELLOW + "/nethercontrol give [玩家] " + ChatColor.WHITE + "- 给予沉星物品");
            sender.sendMessage(ChatColor.YELLOW + "/nethercontrol set <lock|unlock> " + ChatColor.WHITE + "- 设置封锁状态");
            sender.sendMessage(ChatColor.YELLOW + "/nethercontrol checkcredit [玩家] " + ChatColor.WHITE + "- 检查信用点状态");
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!checkPermission(sender, "nethercontrol.admin")) return;
        
        NetherControlPlugin.getInstance().reloadPluginConfig();
        String message = NetherControlPlugin.getInstance().getConfig().getString("messages.reloaded", 
            "&a✅ 配置已重载。");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    private void handleStatus(CommandSender sender) {
        boolean isUnlocked = controlManager.isUnlocked();
        
        if (isUnlocked) {
            String message = NetherControlPlugin.getInstance().getConfig().getString("messages.status-unlocked", 
                "&a🔓 地狱门限制：已解除");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            String message = NetherControlPlugin.getInstance().getConfig().getString("messages.status-locked", 
                "&c🔒 地狱门限制：已封锁");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
        
        // 显示信用点系统状态
        if (creditIntegration.isCreditAvailable()) {
            sender.sendMessage(ChatColor.GREEN + "✅ 信用点系统：已集成");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "⚠ 信用点系统：未检测到");
        }
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "nethercontrol.admin")) return;
        
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "❌ 控制台请指定玩家名");
            return;
        }
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        ItemStack star = controlManager.createStarItem();
        target.getInventory().addItem(star);
        
        String message = NetherControlPlugin.getInstance().getConfig().getString("messages.star-given", 
            "&a✅ 你获得了沉星物品。");
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "✅ 已给予 " + target.getName() + " 沉星物品");
        }
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "nethercontrol.admin")) return;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /nethercontrol set <lock|unlock>");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "lock":
                controlManager.setUnlocked(false);
                sender.sendMessage(ChatColor.RED + "🔒 地狱门限制已封锁");
                if (creditIntegration.isCreditAvailable()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ 将根据玩家信用点自动传送");
                }
                break;
            case "unlock":
                controlManager.setUnlocked(true);
                sender.sendMessage(ChatColor.GREEN + "🔓 地狱门限制已解除");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "❌ 用法: /nethercontrol set <lock|unlock>");
        }
    }
    
    private void handleCheckCredit(CommandSender sender, String[] args) {
        if (!creditIntegration.isCreditAvailable()) {
            sender.sendMessage(ChatColor.RED + "❌ 信用点系统不可用");
            return;
        }
        
        Player target;
        if (args.length > 1) {
            if (!sender.hasPermission("nethercontrol.admin")) {
                sender.sendMessage(ChatColor.RED + "❌ 你没有权限查看其他玩家的信用点");
                return;
            }
            target = Bukkit.getPlayer(args[1]);
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "❌ 控制台请指定玩家名");
                return;
            }
            target = (Player) sender;
        }
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        int credits = creditIntegration.getPlayerCredits(target);
        boolean inNether = creditIntegration.isPlayerInNether(target);
        String location = inNether ? ChatColor.RED + "地狱" : ChatColor.GREEN + "主世界/末地";
        
        sender.sendMessage(ChatColor.GREEN + "📊 " + target.getName() + " 的信用点状态:");
        sender.sendMessage(ChatColor.WHITE + "• 信用点: " + 
            (credits > 0 ? ChatColor.GREEN : credits < 0 ? ChatColor.RED : ChatColor.YELLOW) + credits);
        sender.sendMessage(ChatColor.WHITE + "• 位置: " + location);
        sender.sendMessage(ChatColor.WHITE + "• 地狱门状态: " + 
            (controlManager.isUnlocked() ? ChatColor.GREEN + "已解锁" : ChatColor.RED + "已封锁"));
        
        if (!controlManager.isUnlocked()) {
            if (inNether && credits > 0) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ 此玩家将被自动传送到主世界");
            } else if (!inNether && credits < 0) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ 此玩家将被自动传送到地狱");
            }
        }
        sender.sendMessage(ChatColor.GREEN + "📊 " + target.getName() + " 的信用点状态:");
    sender.sendMessage(ChatColor.WHITE + "• 信用点: " + 
        (credits > 0 ? ChatColor.GREEN : credits < 0 ? ChatColor.RED : ChatColor.YELLOW) + credits);
    sender.sendMessage(ChatColor.WHITE + "• 位置: " + location);
    sender.sendMessage(ChatColor.WHITE + "• 地狱门状态: " + 
        (controlManager.isUnlocked() ? ChatColor.GREEN + "已解锁" : ChatColor.RED + "已封锁"));
    
    // 更新状态提示
    if (credits < 0 && !inNether) {
        sender.sendMessage(ChatColor.YELLOW + "⚠ 此玩家将被自动传送到地狱（不论地狱门状态）");
    } else if (!controlManager.isUnlocked() && inNether && credits > 0) {
        sender.sendMessage(ChatColor.YELLOW + "⚠ 此玩家将被自动传送到主世界（地狱门封锁状态）");
    }
    }
    
    private boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            String message = NetherControlPlugin.getInstance().getConfig().getString("messages.no-permission", 
                "&c❌ 你没有权限执行此命令。");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return false;
        }
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if (sender.hasPermission("nethercontrol.admin")) {
                completions.addAll(Arrays.asList("reload", "status", "give", "set", "checkcredit"));
            } else {
                completions.add("status");
            }
        } else if (args.length == 2 && sender.hasPermission("nethercontrol.admin")) {
            if ("give".equals(args[0]) || "checkcredit".equals(args[0])) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("set".equals(args[0])) {
                completions.addAll(Arrays.asList("lock", "unlock"));
            }
        }
        
        return completions;
    }
}
