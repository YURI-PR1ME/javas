package com.example.apocolyps;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ApoCommandExecutor implements CommandExecutor {
    private final ApoManager manager;
    
    public ApoCommandExecutor(ApoManager manager) {
        this.manager = manager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (manager.spawnBoundAIPlayer(player)) {
                        sender.sendMessage("§a绑定AI玩家生成成功！");
                    } else {
                        sender.sendMessage("§c已达到绑定AI上限！");
                    }
                } else {
                    sender.sendMessage("§c只有玩家可以执行此命令！");
                }
                break;
                
            case "remove":
                manager.disableAll();
                sender.sendMessage("§a所有AI玩家已移除！");
                break;
                
            case "setlimit":
                if (args.length >= 2) {
                    try {
                        int limit = Integer.parseInt(args[1]);
                        manager.setMaxPerPlayer(limit);
                        sender.sendMessage("§a每个玩家的绑定AI上限已设置为: " + limit);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c请输入有效的数字！");
                    }
                } else {
                    sender.sendMessage("§c用法: /apocontrol setlimit <数量>");
                }
                break;
                
            case "getwand":
                if (sender instanceof Player) {
                    giveCommandWand((Player) sender);
                    sender.sendMessage("§a已获得天启指挥棒！");
                } else {
                    sender.sendMessage("§c只有玩家可以执行此命令！");
                }
                break;
                
            case "stats":
                sender.sendMessage("§6=== 天启系统统计 ===");
                sender.sendMessage("§e活跃AI数量: §f" + manager.getPlayerCount());
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    sender.sendMessage("§e你的绑定AI数量: §f" + manager.getBoundAICount(player.getUniqueId()));
                }
                break;
                
            case "init":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    manager.initializePlayerAIs(player);
                    sender.sendMessage("§a已初始化你的绑定AI！");
                } else {
                    sender.sendMessage("§c只有玩家可以执行此命令！");
                }
                break;
                
            case "teleportsettings":
                if (args.length >= 4) {
                    try {
                        int minDistance = Integer.parseInt(args[1]);
                        int maxDistance = Integer.parseInt(args[2]);
                        long cooldown = Long.parseLong(args[3]);
                        
                        manager.setTeleportParameters(minDistance, maxDistance, cooldown);
                        sender.sendMessage("§a传送参数已设置！");
                        sender.sendMessage("§e最小距离: §f" + minDistance);
                        sender.sendMessage("§e最大距离: §f" + maxDistance);
                        sender.sendMessage("§e冷却时间: §f" + cooldown + "ms");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c参数格式错误！用法: /apocontrol teleportsettings <最小距离> <最大距离> <冷却时间ms>");
                    }
                } else {
                    sender.sendMessage("§c用法: /apocontrol teleportsettings <最小距离> <最大距离> <冷却时间ms>");
                }
                break;
                
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== 天启奴隶主控制系统 ===");
        sender.sendMessage("§e/apocontrol init §f- 初始化你的绑定AI（5个）");
        sender.sendMessage("§e/apocontrol spawn §f- 生成一个绑定AI");
        sender.sendMessage("§e/apocontrol remove §f- 移除所有AI玩家");
        sender.sendMessage("§e/apocontrol setlimit <数量> §f- 设置绑定AI上限");
        sender.sendMessage("§e/apocontrol getwand §f- 获取指挥棒");
        sender.sendMessage("§e/apocontrol stats §f- 查看统计");
        sender.sendMessage("§e/apocontrol teleportsettings <min> <max> <cooldown> §f- 设置传送参数");
    }
    
    private void giveCommandWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6天启指挥棒");
        wand.setItemMeta(meta);
        
        player.getInventory().addItem(wand);
    }
}
