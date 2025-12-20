package com.yourname.shopplugin;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {
    
    private final ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 移除直接打开商店的功能，改为提示使用终端
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.YELLOW + "💡 使用商店终端来打开商店界面");
                player.sendMessage(ChatColor.GRAY + "合成配方: 钻石-绿宝石-黑曜石");
                player.sendMessage(ChatColor.GRAY + "DED");
                player.sendMessage(ChatColor.GRAY + "EOE"); 
                player.sendMessage(ChatColor.GRAY + "DED");
            } else {
                sender.sendMessage(ChatColor.RED + "控制台无法打开商店界面");
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "admin":
                handleAdmin(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "giveterminal":
                handleGiveTerminal(sender, args);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "help":
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void handleAdmin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以打开管理界面！");
            return;
        }
        
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有管理商店的权限！");
            return;
        }
        
        Player player = (Player) sender;
        AdminGUI.openAdminMenu(player);
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有重载商店的权限！");
            return;
        }
        
        ShopPlugin.getInstance().reloadAllConfigs();
        sender.sendMessage(ChatColor.GREEN + "✅ 商店配置已重载！");
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限给予商店物品！");
            return;
        }
        
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "❌ 控制台请指定玩家名！");
            return;
        }
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线！");
            return;
        }
        
        ItemStack shopItem = shopManager.getShopOpenerItem();
        target.getInventory().addItem(shopItem);
        sender.sendMessage(ChatColor.GREEN + "✅ 已给予 " + target.getName() + " 商店物品");
    }
    
    private void handleGiveTerminal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限给予商店终端！");
            return;
        }
        
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "❌ 控制台请指定玩家名！");
            return;
        }
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ 玩家不存在或不在线！");
            return;
        }
        
        ItemStack terminal = ShopTerminal.createShopTerminal();
        target.getInventory().addItem(terminal);
        sender.sendMessage(ChatColor.GREEN + "✅ 已给予 " + target.getName() + " 商店终端");
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令！");
            return;
        }
        
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有添加商品的权限！");
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /shop add <价格> <分类> [商品ID]");
            return;
        }
        
        Player player = (Player) sender;
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        if (handItem.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "❌ 请手持要添加的物品！");
            return;
        }
        
        try {
            int price = Integer.parseInt(args[1]);
            String category = args[2];
            String itemId = args.length > 3 ? args[3] : generateItemId(handItem);
            
            if (shopManager.getShopItem(itemId) != null) {
                player.sendMessage(ChatColor.RED + "❌ 商品ID已存在！");
                return;
            }
            
            ShopItem shopItem = new ShopItem(itemId, handItem, price, category);
            shopManager.addShopItem(itemId, shopItem);
            
            player.sendMessage(ChatColor.GREEN + "✅ 商品添加成功！ID: " + itemId);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "❌ 价格必须是数字！");
        }
    }
    
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有移除商品的权限！");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /shop remove <商品ID>");
            return;
        }
        
        String itemId = args[1];
        if (shopManager.getShopItem(itemId) == null) {
            sender.sendMessage(ChatColor.RED + "❌ 商品不存在！");
            return;
        }
        
        shopManager.removeShopItem(itemId);
        sender.sendMessage(ChatColor.GREEN + "✅ 商品移除成功！");
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 商店系统帮助 ===");
        sender.sendMessage(ChatColor.YELLOW + "使用商店终端来打开商店界面");
        
        if (sender.hasPermission("shop.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/shop admin " + ChatColor.WHITE + "- 打开管理界面");
            sender.sendMessage(ChatColor.YELLOW + "/shop reload " + ChatColor.WHITE + "- 重载配置");
            sender.sendMessage(ChatColor.YELLOW + "/shop give [玩家] " + ChatColor.WHITE + "- 给予商店物品");
            sender.sendMessage(ChatColor.YELLOW + "/shop giveterminal [玩家] " + ChatColor.WHITE + "- 给予商店终端");
            sender.sendMessage(ChatColor.YELLOW + "/shop add <价格> <分类> [ID] " + ChatColor.WHITE + "- 添加商品");
            sender.sendMessage(ChatColor.YELLOW + "/shop remove <商品ID> " + ChatColor.WHITE + "- 移除商品");
        }
    }
    
    private String generateItemId(ItemStack item) {
        String baseId = item.getType().toString().toLowerCase();
        int counter = 1;
        String itemId = baseId;
        
        while (shopManager.getShopItem(itemId) != null) {
            itemId = baseId + "_" + counter;
            counter++;
        }
        
        return itemId;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("admin");
            completions.add("reload");
            completions.add("give");
            completions.add("giveterminal");
            completions.add("add");
            completions.add("remove");
            completions.add("help");
        } else if (args.length == 2 && "remove".equals(args[0])) {
            completions.addAll(shopManager.getShopItems().keySet());
        } else if (args.length == 3 && "add".equals(args[0])) {
            completions.addAll(shopManager.getCategories().keySet());
        }
        
        return completions;
    }
}
