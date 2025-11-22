package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location; // 添加这行导入
import org.bukkit.World; // 添加这行导入
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class CreditCommand implements CommandExecutor, TabCompleter {
    
    private final CreditManager creditManager = CreditPlugin.getInstance().getCreditManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "check":
                handleCheck(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "givebook":
                handleGiveBook(sender, args);
                break;
            case "giverevive":
                handleGiveRevive(sender);
                break;
            case "revive":
                handleRevive(sender, args);
                break;
            case "killingday":
                handleKillingDay(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "sendtonether": // 新增命令：强制传送玩家到地狱
                handleSendToNether(sender, args);
                break;
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 信用点系统帮助 ===");
        if (sender.hasPermission("credit.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/credit set <玩家> <点数> " + ChatColor.WHITE + "- 设置信用点");
            sender.sendMessage(ChatColor.YELLOW + "/credit add <玩家> <点数> " + ChatColor.WHITE + "- 添加信用点");
            sender.sendMessage(ChatColor.YELLOW + "/credit remove <玩家> <点数> " + ChatColor.WHITE + "- 移除信用点");
            sender.sendMessage(ChatColor.YELLOW + "/credit givebook <玩家> " + ChatColor.WHITE + "- 给予信用点书");
            sender.sendMessage(ChatColor.YELLOW + "/credit giverevive " + ChatColor.WHITE + "- 获得复活选择台");
            sender.sendMessage(ChatColor.YELLOW + "/credit revive <玩家> " + ChatColor.WHITE + "- 直接复活玩家");
            sender.sendMessage(ChatColor.YELLOW + "/credit killingday <start|stop|status> " + ChatColor.WHITE + "- 管理杀人日");
            sender.sendMessage(ChatColor.YELLOW + "/credit sendtonether <玩家> " + ChatColor.WHITE + "- 强制传送玩家到地狱");
            sender.sendMessage(ChatColor.YELLOW + "/credit reload " + ChatColor.WHITE + "- 重载配置");
        }
        sender.sendMessage(ChatColor.YELLOW + "/credit check [玩家] " + ChatColor.WHITE + "- 查看信用点");
    }
    
    private void handleCheck(CommandSender sender, String[] args) {
        Player target;
        
        if (args.length > 1) {
            if (!sender.hasPermission("credit.admin")) {
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
        
        int credits = creditManager.getCredits(target);
        String location = creditManager.isInNether(target) ? ChatColor.RED + "地狱" : ChatColor.GREEN + "主世界/末地";
        
        sender.sendMessage(ChatColor.GREEN + "📊 " + target.getName() + " 的信用点: " + credits + 
                         " | 位置: " + location);
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit set <玩家> <点数>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        try {
            int credits = Integer.parseInt(args[2]);
            creditManager.setCredits(target, credits);
            sender.sendMessage(ChatColor.GREEN + "✅ 已设置 " + target.getName() + " 的信用点为 " + credits);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ 请输入有效的数字");
        }
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit add <玩家> <点数>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            creditManager.addCredits(target, amount);
            sender.sendMessage(ChatColor.GREEN + "✅ 已为 " + target.getName() + " 添加 " + amount + " 点信用点");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ 请输入有效的数字");
        }
    }
    
    private void handleRemove(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit remove <玩家> <点数>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            creditManager.removeCredits(target, amount);
            sender.sendMessage(ChatColor.GREEN + "✅ 已从 " + target.getName() + " 移除 " + amount + " 点信用点");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ 请输入有效的数字");
        }
    }
    
    private void handleGiveBook(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        
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
        
        creditManager.giveCreditBook(target);
        sender.sendMessage(ChatColor.GREEN + "✅ 已给予 " + target.getName() + " 信用点书");
    }
    
    private void handleGiveRevive(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令");
            return;
        }
        
        if (!sender.hasPermission("credit.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限获得复活选择台");
            return;
        }
        
        Player player = (Player) sender;
        ItemStack reviveStation = ReviveItem.createReviveStation();
        player.getInventory().addItem(reviveStation);
        player.sendMessage(ChatColor.GREEN + "✅ 你获得了复活选择台");
    }
    
    private void handleRevive(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit revive <玩家>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        // 只允许复活在地狱的玩家
        if (!creditManager.isInNether(target)) {
            sender.sendMessage(ChatColor.RED + "❌ 该玩家不需要复活");
            return;
        }
        
        // 管理员直接复活，不消耗点数
        World overworld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        
        if (overworld != null) {
            Location safeLocation = creditManager.findSafeLocation(overworld, overworld.getSpawnLocation());
            target.teleport(safeLocation);
            
            // 如果目标玩家信用点为负数，补正到0
            if (creditManager.getCredits(target) < 0) {
                creditManager.addCredits(target, -creditManager.getCredits(target));
            }
            
            target.sendMessage(ChatColor.GREEN + "✨ 你已被管理员救回主世界！");
            sender.sendMessage(ChatColor.GREEN + "✅ 你已复活 " + target.getName());
        }
    }
    
    private void handleKillingDay(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit killingday <start|stop|status>");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "start":
                creditManager.startKillingDay();
                sender.sendMessage(ChatColor.GREEN + "✅ 杀人日已开启！");
                break;
            case "stop":
                creditManager.stopKillingDay();
                sender.sendMessage(ChatColor.GREEN + "✅ 杀人日已关闭！");
                break;
            case "status":
                boolean status = creditManager.isKillingDay();
                sender.sendMessage(ChatColor.YELLOW + "📊 当前杀人日状态: " + 
                    (status ? ChatColor.RED + "开启" : ChatColor.GREEN + "关闭"));
                break;
            default:
                sender.sendMessage(ChatColor.RED + "❌ 用法: /credit killingday <start|stop|status>");
        }
    }
    
    private void handleSendToNether(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /credit sendtonether <玩家>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线");
            return;
        }
        
        creditManager.teleportToNether(target);
        sender.sendMessage(ChatColor.GREEN + "✅ 已将 " + target.getName() + " 传送到地狱");
    }
    
    private void handleReload(CommandSender sender) {
        if (!checkAdminPermission(sender)) return;
        
        CreditPlugin.getInstance().reloadConfig();
        creditManager.saveAllData();
        sender.sendMessage(ChatColor.GREEN + "✅ 配置已重载");
    }
    
    private boolean checkAdminPermission(CommandSender sender) {
        if (!sender.hasPermission("credit.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有管理信用点的权限");
            return false;
        }
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("check");
            if (sender.hasPermission("credit.admin")) {
                completions.addAll(List.of("set", "add", "remove", "givebook", "giverevive", "revive", "killingday", "sendtonether", "reload"));
            }
        } else if (args.length == 2 && sender.hasPermission("credit.admin")) {
            if (List.of("set", "add", "remove", "givebook", "revive", "sendtonether").contains(args[0].toLowerCase())) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if ("killingday".equals(args[0].toLowerCase())) {
                completions.addAll(List.of("start", "stop", "status"));
            }
        }
        
        return completions;
    }
}
