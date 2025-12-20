package com.yourname.creditexchange;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ExchangeCommand implements CommandExecutor, TabCompleter {
    
    private final ExchangeManager exchangeManager = CreditExchangePlugin.getInstance().getExchangeManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令！");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showExchangeInfo(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "info":
                showExchangeInfo(player);
                break;
            case "do":
                performExchange(player);
                break;
            case "list":
                listExchangeRules(player);
                break;
            case "status":
                showExchangeStatus(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "❌ 未知命令！使用 /exchange info 查看帮助");
        }
        
        return true;
    }
    
    private void showExchangeInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 信用点兑换系统 ===");
        player.sendMessage(ChatColor.YELLOW + "/exchange do " + ChatColor.WHITE + "- 兑换手持物品");
        player.sendMessage(ChatColor.YELLOW + "/exchange list " + ChatColor.WHITE + "- 查看可兑换物品列表");
        player.sendMessage(ChatColor.YELLOW + "/exchange status " + ChatColor.WHITE + "- 查看个人兑换状态");
        player.sendMessage(ChatColor.YELLOW + "/exchange info " + ChatColor.WHITE + "- 查看帮助信息");
        player.sendMessage(ChatColor.GREEN + "💡 手持要兑换的物品使用 /exchange do 进行兑换");
        player.sendMessage(ChatColor.RED + "🔥 负信用点状态：所有兑换冷却取消！");
    }
    
    private void performExchange(Player player) {
        ExchangeManager.ExchangeResult result = exchangeManager.exchangeItems(player);
        player.sendMessage(result.getMessage());
    }
    
    private void listExchangeRules(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 可兑换物品列表 ===");
        player.sendMessage(ChatColor.YELLOW + "【食物类 - 每2日最多100点（负信用点无限制）】");
        
        for (ExchangeManager.FoodExchangeRule rule : exchangeManager.getFoodRules().values()) {
            player.sendMessage(ChatColor.WHITE + "• " + getItemDisplayName(rule.getMaterial()) + 
                             ChatColor.GREEN + " x" + rule.getRequiredAmount() + 
                             ChatColor.WHITE + " → " + ChatColor.AQUA + rule.getPoints() + "点");
        }
        
        player.sendMessage(ChatColor.YELLOW + "【珍贵物品 - 独立冷却（负信用点无冷却）】");
        for (ExchangeManager.PreciousExchangeRule rule : exchangeManager.getPreciousRules().values()) {
            player.sendMessage(ChatColor.WHITE + "• " + getItemDisplayName(rule.getMaterial()) + 
                             ChatColor.GREEN + " x" + rule.getRequiredAmount() + 
                             ChatColor.WHITE + " → " + ChatColor.AQUA + rule.getPoints() + "点" +
                             ChatColor.GRAY + " (冷却:" + rule.getCooldownDays() + "日)");
        }
    }
    
    private void showExchangeStatus(Player player) {
        String status = exchangeManager.getExchangeStatus(player);
        player.sendMessage(status);
    }
    
    private String getItemDisplayName(Material material) {
        switch (material) {
            case COOKED_BEEF: return "牛排";
            case COOKED_PORKCHOP: return "猪排";
            case COOKED_CHICKEN: return "鸡肉";
            case BREAD: return "面包";
            case GOLDEN_CARROT: return "金胡萝卜";
            case GOLDEN_APPLE: return "金苹果";
            case ENCHANTED_GOLDEN_APPLE: return "附魔金苹果";
            case IRON_INGOT: return "铁锭";
            case GOLD_INGOT: return "金锭";
            case DIAMOND: return "钻石";
            case ENDER_PEARL: return "末影珍珠";
            case TOTEM_OF_UNDYING: return "不死图腾";
            case BLAZE_ROD: return "烈焰棒";
            case GLOWSTONE_DUST: return "荧石粉";
            default: return material.toString();
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("do");
            completions.add("list");
            completions.add("status");
            completions.add("info");
        }
        
        return completions;
    }
}
