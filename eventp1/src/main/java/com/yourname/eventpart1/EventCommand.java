// EventCommand.java
package com.yourname.eventpart1;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class EventCommand implements CommandExecutor, TabCompleter {
    
    private final EventManager eventManager = EventPart1.getInstance().getEventManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "socialpurification":
            case "sp":
                handleSocialPurification(sender, args);
                break;
            case "resourcetax":
            case "rt":
                handleResourceTax(sender, args);
                break;
            case "status":
                handleStatus(sender);
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
        sender.sendMessage(ChatColor.GOLD + "=== 事件系统帮助 ===");
        if (sender.hasPermission("eventpart1.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/event socialpurification <start|stop|status> " + ChatColor.WHITE + "- 社会净化演习");
            sender.sendMessage(ChatColor.YELLOW + "/event resourcetax <start|stop|status> " + ChatColor.WHITE + "- 资源税系统");
            sender.sendMessage(ChatColor.YELLOW + "/event status " + ChatColor.WHITE + "- 查看事件状态");
            sender.sendMessage(ChatColor.YELLOW + "/event reload " + ChatColor.WHITE + "- 重载配置");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "/event status " + ChatColor.WHITE + "- 查看事件状态");
        }
    }
    
    private void handleSocialPurification(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /event socialpurification <start|stop|status>");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "start":
                eventManager.startSocialPurification();
                sender.sendMessage(ChatColor.GREEN + "✅ 社会净化演习已启动！");
                break;
            case "stop":
                eventManager.stopSocialPurification();
                sender.sendMessage(ChatColor.GREEN + "✅ 社会净化演习已停止！");
                break;
            case "status":
                boolean active = eventManager.isSocialPurificationActive();
                long timeLeft = eventManager.getSocialPurificationTimeLeft();
                String status = active ? ChatColor.RED + "进行中" : ChatColor.GREEN + "未激活";
                String timeInfo = active ? " (剩余: " + (timeLeft / 60000) + "分钟)" : "";
                sender.sendMessage(ChatColor.YELLOW + "📊 社会净化演习状态: " + status + timeInfo);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "❌ 用法: /event socialpurification <start|stop|status>");
        }
    }
    
    private void handleResourceTax(CommandSender sender, String[] args) {
        if (!checkAdminPermission(sender)) return;
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /event resourcetax <start|stop|status>");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "start":
                eventManager.setResourceTaxActive(true);
                sender.sendMessage(ChatColor.GREEN + "✅ 资源税系统已启动！");
                break;
            case "stop":
                eventManager.setResourceTaxActive(false);
                sender.sendMessage(ChatColor.GREEN + "✅ 资源税系统已停止！");
                break;
            case "status":
                boolean active = eventManager.isResourceTaxActive();
                String status = active ? ChatColor.RED + "激活" : ChatColor.GREEN + "未激活";
                sender.sendMessage(ChatColor.YELLOW + "📊 资源税系统状态: " + status);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "❌ 用法: /event resourcetax <start|stop|status>");
        }
    }
    
    private void handleStatus(CommandSender sender) {
        // 社会净化演习状态
        boolean spActive = eventManager.isSocialPurificationActive();
        long spTimeLeft = eventManager.getSocialPurificationTimeLeft();
        String spStatus = spActive ? ChatColor.RED + "进行中" : ChatColor.GREEN + "未激活";
        String spTimeInfo = spActive ? " (剩余: " + (spTimeLeft / 60000) + "分钟)" : "";
        
        // 资源税状态
        boolean rtActive = eventManager.isResourceTaxActive();
        String rtStatus = rtActive ? ChatColor.RED + "激活" : ChatColor.GREEN + "未激活";
        
        sender.sendMessage(ChatColor.GOLD + "=== 事件系统状态 ===");
        sender.sendMessage(ChatColor.YELLOW + "社会净化演习: " + spStatus + spTimeInfo);
        sender.sendMessage(ChatColor.YELLOW + "资源税系统: " + rtStatus);
        
        // 显示税收监管玩家数量
        if (sender.hasPermission("eventpart1.admin")) {
            int surveillanceCount = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (eventManager.isPlayerUnderTaxSurveillance(player)) {
                    surveillanceCount++;
                }
            }
            sender.sendMessage(ChatColor.YELLOW + "税收监管玩家: " + surveillanceCount + "人");
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!checkAdminPermission(sender)) return;
        
        EventPart1.getInstance().reloadConfig();
        eventManager.saveAllData();
        sender.sendMessage(ChatColor.GREEN + "✅ 配置已重载");
    }
    
    private boolean checkAdminPermission(CommandSender sender) {
        if (!sender.hasPermission("eventpart1.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有管理事件的权限");
            return false;
        }
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("socialpurification");
            completions.add("resourcetax");
            completions.add("status");
            if (sender.hasPermission("eventpart1.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && sender.hasPermission("eventpart1.admin")) {
            if (args[0].equalsIgnoreCase("socialpurification") || args[0].equalsIgnoreCase("sp")) {
                completions.addAll(List.of("start", "stop", "status"));
            } else if (args[0].equalsIgnoreCase("resourcetax") || args[0].equalsIgnoreCase("rt")) {
                completions.addAll(List.of("start", "stop", "status"));
            }
        }
        
        return completions;
    }
}
