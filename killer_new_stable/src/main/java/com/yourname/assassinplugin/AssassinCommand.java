package com.yourname.assassinplugin;

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
import java.util.UUID;

public class AssassinCommand implements CommandExecutor, TabCompleter {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelp((Player) sender);
            } else {
                sendConsoleHelp(sender);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                    return true;
                }
                openAssassinGUI((Player) sender);
                break;
            case "give":
                handleGiveItem(sender, args);
                break;
            case "contracts":
                handleListContracts(sender, args);
                break;
            case "cancel":
                handleCancelContract(sender, args);
                break;
            case "cooldown":
                handleCooldown(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "offer":
                handleOffer(sender, args);
                break;
            case "accept":
                handleAccept(sender, args);
                break;
            case "register":
                handleRegister(sender, args);
                break;
            default:
                if (sender instanceof Player) {
                    sendHelp((Player) sender);
                } else {
                    sendConsoleHelp(sender);
                }
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§8=== §4暗网买凶系统 §8===");
        player.sendMessage("§6/assassin gui §7- 打开买凶界面");
        player.sendMessage("§6/assassin give §7- 获得暗网接入口和配方书");
        player.sendMessage("§6/assassin contracts §7- 查看我的合约");
        player.sendMessage("§6/assassin cancel <ID> §7- 取消合约");
        player.sendMessage("§6/assassin register §7- 注册成为玩家刺客");
        player.sendMessage("§6/assassin offer <ID> <金额> §7- 刺客报价");
        player.sendMessage("§6/assassin accept <ID> §7- 买家接受报价");
        player.sendMessage("§8——————————————");
        player.sendMessage("§e档次1 (§730点§e) - 普通杀手");
        player.sendMessage("§e档次2 (§640点§e) - 精英卫道士（抢夺信用点）");
        player.sendMessage("§e档次3 (§480点§e) - 深海杀手（远程三叉戟+抢夺）");
        player.sendMessage("§8——————————————");
        player.sendMessage("§7配方书合成：非常昂贵但可能获得");
        
        long remaining = assassinManager.getPlayerCooldownRemaining(player);
        if (remaining > 0) {
            player.sendMessage("§c⏰ 冷却剩余: " + (remaining / 1000) + "秒");
        } else {
            player.sendMessage("§a✅ 冷却已结束，可以买凶");
        }
    }
    
    private void sendConsoleHelp(CommandSender sender) {
        sender.sendMessage("§8=== §4暗网买凶系统 §8===");
        sender.sendMessage("§6/assassin give <玩家> §7- 给予玩家暗网接入口和配方书");
        sender.sendMessage("§6/assassin contracts <玩家> §7- 查看玩家合约");
        sender.sendMessage("§6/assassin cooldown set <时间> §7- 设置冷却时间（毫秒）");
        sender.sendMessage("§6/assassin cooldown clear <玩家> §7- 清除玩家冷却");
        sender.sendMessage("§6/assassin cooldown clearall §7- 清除所有冷却");
        sender.sendMessage("§6/assassin reload §7- 重载配置");
    }
    
    private void openAssassinGUI(Player player) {
        boolean hasAccessItem = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (assassinManager.isDarkWebAccessItem(item)) {
                hasAccessItem = true;
                break;
            }
        }
        
        if (!hasAccessItem) {
            player.sendMessage("§c❌ 你需要暗网接入口才能使用此功能");
            return;
        }
        
        AssassinGUI.openPlayerAssassinMenu(player);
    }
    
    private void handleGiveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("assassin.admin")) {
            sender.sendMessage("§c❌ 你没有权限");
            return;
        }
        
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c❌ 玩家不存在或不在线");
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§c❌ 控制台请指定玩家名");
            return;
        }
        
        ItemStack darkWebItem = assassinManager.createDarkWebAccessItem();
        target.getInventory().addItem(darkWebItem);
        
        ItemStack recipeBook = assassinManager.createRecipeBook();
        target.getInventory().addItem(recipeBook);
        
        sender.sendMessage("§a✅ 已给予 " + target.getName() + " 暗网接入口和配方书");
        if (sender != target) {
            target.sendMessage("§a✅ 你获得了暗网接入口和配方书");
        }
    }
    
    private void handleListContracts(CommandSender sender, String[] args) {
        // 原有实现...
    }
    
    private void handleCancelContract(CommandSender sender, String[] args) {
        sender.sendMessage("§c⚠ 合约一旦发布无法取消，杀手已在路上...");
    }
    
    private void handleCooldown(CommandSender sender, String[] args) {
        // 原有实现...
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("assassin.admin")) {
            sender.sendMessage("§c❌ 你没有权限重载配置");
            return;
        }
        
        assassinManager.reloadConfig();
        sender.sendMessage("§a✅ 买凶插件配置已重载");
    }
    
    private void handleOffer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 3) {
            player.sendMessage("§c❌ 用法: /assassin offer <会话ID> <金额>");
            return;
        }
        
        try {
            UUID sessionId = UUID.fromString(args[1]);
            int price = Integer.parseInt(args[2]);
            
            if (price <= 0) {
                player.sendMessage("§c❌ 价格必须为正数");
                return;
            }
            
            if (assassinManager.handleAssassinOffer(player, sessionId, price)) {
                player.sendMessage("§a✅ 报价已发送: " + price + " 信用点");
            } else {
                player.sendMessage("§c❌ 报价失败，请检查会话ID");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c❌ 无效的会话ID或金额");
        }
    }
    
    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            player.sendMessage("§c❌ 用法: /assassin accept <会话ID>");
            return;
        }
        
        try {
            UUID sessionId = UUID.fromString(args[1]);
            
            if (assassinManager.acceptContractOffer(player, sessionId)) {
                player.sendMessage("§a✅ 合约已激活！");
            } else {
                player.sendMessage("§c❌ 接受合约失败");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c❌ 无效的会话ID");
        }
    }
    
    private void handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }
        
        Player player = (Player) sender;
        int entryFee = assassinManager.getAssassinEntryFee();
        
        if (assassinManager.registerPlayerAssassin(player, entryFee)) {
            player.sendMessage("§a✅ 注册成功！成为暗网刺客");
            player.sendMessage("§e💰 入场费: " + entryFee + " 信用点");
            player.sendMessage("§7现在你可以在暗网终端接取合约");
        } else {
            player.sendMessage("§c❌ 注册失败！可能原因：");
            player.sendMessage("§c• 已经注册为刺客");
            player.sendMessage("§c• 信用点不足（需要入场费2倍以上）");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("gui");
            completions.add("give");
            completions.add("contracts");
            completions.add("cancel");
            completions.add("cooldown");
            completions.add("reload");
            completions.add("register");
            completions.add("offer");
            completions.add("accept");
        } else if (args.length == 2 && "cooldown".equals(args[0])) {
            completions.add("set");
            completions.add("clear");
            completions.add("clearall");
            completions.add("check");
        } else if (args.length == 3 && "cooldown".equals(args[0]) && 
                  ("clear".equals(args[1]) || "check".equals(args[1]))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "give".equals(args[0]) && sender.hasPermission("assassin.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "contracts".equals(args[0]) && sender.hasPermission("assassin.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
    
    // 原有的createRecipeBook方法
    public ItemStack createRecipeBook() {
        // 原有实现...
        return new ItemStack(org.bukkit.Material.WRITTEN_BOOK);
    }
}
