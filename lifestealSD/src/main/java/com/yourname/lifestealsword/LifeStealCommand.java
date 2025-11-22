package com.yourname.lifestealsword;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LifeStealCommand implements CommandExecutor, TabCompleter {
    
    private final LifeStealSwordPlugin plugin;
    private final LifeStealListener listener;
    
    // 修复：使用无参构造函数，在内部获取实例
    public LifeStealCommand() {
        this.plugin = LifeStealSwordPlugin.getInstance();
        this.listener = new LifeStealListener();
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
            case "info":
                handleInfo(sender);
                break;
            case "help":
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8=== §6生命窃取剑管理 §8===");
        sender.sendMessage("§6/lifestealsword give §7- 给自己一把生命窃取剑");
        sender.sendMessage("§6/lifestealsword give <玩家> §7- 给指定玩家生命窃取剑");
        sender.sendMessage("§6/lifestealsword info §7- 查看插件信息");
        sender.sendMessage("§6/lifestealsword help §7- 显示此帮助");
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifestealsword.admin")) {
            sender.sendMessage("§c❌ 你没有权限使用此命令");
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
            sender.sendMessage("§c❌ 控制台请指定玩家名: /lifestealsword give <玩家>");
            return;
        }
        
        // 创建生命窃取剑
        ItemStack sword = listener.createLifeStealSword();
        
        // 添加到玩家背包
        if (targetPlayer.getInventory().addItem(sword).isEmpty()) {
            // 成功添加
            targetPlayer.sendMessage("§a✅ 你获得了一把 §6生命窃取剑§a!");
            if (!targetPlayer.equals(sender)) {
                sender.sendMessage("§a✅ 已给予 §6" + targetPlayer.getName() + " §a生命窃取剑");
            }
            
            // 播放获得音效
            targetPlayer.playSound(targetPlayer.getLocation(), 
                org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
            targetPlayer.playSound(targetPlayer.getLocation(), 
                org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
            
        } else {
            // 背包已满
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), sword);
            targetPlayer.sendMessage("§6💡 背包已满，生命窃取剑已掉落在地面上");
            if (!targetPlayer.equals(sender)) {
                sender.sendMessage("§6💡 " + targetPlayer.getName() + " 背包已满，生命窃取剑已掉落在地面上");
            }
        }
    }
    
    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§8=== §6生命窃取剑信息 §8===");
        sender.sendMessage("§7插件版本: §a1.0.0");
        sender.sendMessage("§7作者: §6YourName");
        sender.sendMessage("§7功能说明:");
        sender.sendMessage("  §8• §7基础伤害: §c13点§7 (锋利V下界合金剑)");
        sender.sendMessage("  §8• §7生命窃取: §c+2点§7最大生命值/玩家击杀");
        sender.sendMessage("  §8• §7生命恢复: §a4秒§7恢复效果");
        sender.sendMessage("  §8• §7音波攻击: §e15格§7范围，§c13点§7伤害");
        sender.sendMessage("  §8• §7冷却时间: §b5秒");
        sender.sendMessage("§7获取方式:");
        sender.sendMessage("  §8• §7溺尸王§650%§7几率掉落");
        sender.sendMessage("  §8• §7管理员命令§6/lifestealsword give");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("give");
            completions.add("info");
            completions.add("help");
        } else if (args.length == 2 && "give".equals(args[0])) {
            // 在线玩家列表
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
