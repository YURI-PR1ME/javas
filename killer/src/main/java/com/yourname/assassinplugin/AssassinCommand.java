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
        player.sendMessage("§8——————————————");
        player.sendMessage("§e档次1 (§730点§e) - 普通杀手");
        player.sendMessage("§e档次2 (§640点§e) - 精英卫道士（抢夺信用点）");
        player.sendMessage("§e档次3 (§480点§e) - 深海杀手（远程三叉戟+抢夺）");
        player.sendMessage("§8——————————————");
        player.sendMessage("§7配方书合成：非常昂贵但可能获得");
        
        // 显示当前冷却状态
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
        // 检查是否有暗网接入口
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
        
        // 打开GUI界面
        AssassinGUI.openMainMenu(player);
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
        
        // 给予暗网接入口
        ItemStack darkWebItem = assassinManager.createDarkWebAccessItem();
        target.getInventory().addItem(darkWebItem);
        
        // 给予配方书
        ItemStack recipeBook = assassinManager.createRecipeBook();
        target.getInventory().addItem(recipeBook);
        
        sender.sendMessage("§a✅ 已给予 " + target.getName() + " 暗网接入口和配方书");
        if (sender != target) {
            target.sendMessage("§a✅ 你获得了暗网接入口和配方书");
        }
    }
    
    private void handleListContracts(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1 && sender.hasPermission("assassin.admin")) {
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
        
        List<AssassinContract> contracts = assassinManager.getPlayerContracts(target.getUniqueId());
        
        if (contracts.isEmpty()) {
            sender.sendMessage("§7暂无活跃合约");
            return;
        }
        
        sender.sendMessage("§8=== §4" + target.getName() + "的合约 §8===");
        for (AssassinContract contract : contracts) {
            String status = contract.isActive() ? "§a进行中" : "§7等待中";
            String type = contract.getEmployerId().equals(target.getUniqueId()) ? "§6雇主" : "§c目标";
            Player contractTarget = Bukkit.getPlayer(contract.getTargetId());
            String targetName = contractTarget != null ? contractTarget.getName() : "未知";
            
            sender.sendMessage(String.format("§8[%s] §7目标: %s | 等级: %d | %s", 
                type, targetName, contract.getTier(), status));
        }
    }
    
    private void handleCancelContract(CommandSender sender, String[] args) {
        sender.sendMessage("§c⚠ 合约一旦发布无法取消，杀手已在路上...");
    }
    
    private void handleCooldown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("assassin.admin")) {
            sender.sendMessage("§c❌ 你没有权限管理冷却时间");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c❌ 用法: /assassin cooldown <set|clear|clearall|check>");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "set":
                if (args.length < 3) {
                    sender.sendMessage("§c❌ 用法: /assassin cooldown set <时间(毫秒)>");
                    return;
                }
                try {
                    long cooldown = Long.parseLong(args[2]);
                    assassinManager.setCooldownTime(cooldown);
                    sender.sendMessage("§a✅ 冷却时间已设置为 " + cooldown + " 毫秒 (" + (cooldown / 1000) + " 秒)");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c❌ 请输入有效的数字");
                }
                break;
                
            case "clear":
                if (args.length < 3) {
                    sender.sendMessage("§c❌ 用法: /assassin cooldown clear <玩家>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("§c❌ 玩家不存在或不在线");
                    return;
                }
                if (assassinManager.clearPlayerCooldown(target)) {
                    sender.sendMessage("§a✅ 已清除 " + target.getName() + " 的冷却时间");
                    target.sendMessage("§a✅ 你的买凶冷却时间已被管理员清除");
                } else {
                    sender.sendMessage("§7ℹ️ " + target.getName() + " 没有冷却时间");
                }
                break;
                
            case "clearall":
                assassinManager.clearAllCooldowns();
                sender.sendMessage("§a✅ 已清除所有玩家的冷却时间");
                break;
                
            case "check":
                if (args.length < 3) {
                    sender.sendMessage("§c❌ 用法: /assassin cooldown check <玩家>");
                    return;
                }
                Player checkTarget = Bukkit.getPlayer(args[2]);
                if (checkTarget == null) {
                    sender.sendMessage("§c❌ 玩家不存在或不在线");
                    return;
                }
                long remaining = assassinManager.getPlayerCooldownRemaining(checkTarget);
                if (remaining > 0) {
                    sender.sendMessage("§e⏰ " + checkTarget.getName() + " 的冷却剩余: " + (remaining / 1000) + "秒");
                } else {
                    sender.sendMessage("§a✅ " + checkTarget.getName() + " 没有冷却时间");
                }
                break;
                
            default:
                sender.sendMessage("§c❌ 用法: /assassin cooldown <set|clear|clearall|check>");
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("assassin.admin")) {
            sender.sendMessage("§c❌ 你没有权限重载配置");
            return;
        }
        
        assassinManager.reloadConfig();
        sender.sendMessage("§a✅ 买凶插件配置已重载");
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
        } else if (args.length == 2 && "cooldown".equals(args[0])) {
            completions.add("set");
            completions.add("clear");
            completions.add("clearall");
            completions.add("check");
        } else if (args.length == 3 && "cooldown".equals(args[0]) && 
                  ("clear".equals(args[1]) || "check".equals(args[1]))) {
            // 为clear和check提供玩家名补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "give".equals(args[0]) && sender.hasPermission("assassin.admin")) {
            // 为give提供玩家名补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "contracts".equals(args[0]) && sender.hasPermission("assassin.admin")) {
            // 为contracts提供玩家名补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
}
