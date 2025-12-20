package com.yourname.drownedking;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DrownedKingCommand implements CommandExecutor, TabCompleter {
    
    private final DrownedKingPlugin plugin;
    private final DrownedKingManager manager;
    
    public DrownedKingCommand(DrownedKingPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getDrownedKingManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                handleSpawn(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "config":
                handleConfig(sender, args);
                break;
            case "addtreasure":
                handleAddTreasure(sender, args);
                break;
            case "listtreasure":
                handleListTreasure(sender);
                break;
            case "removetreasure":
                handleRemoveTreasure(sender, args);
                break;
            case "help":
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8=== §4溺尸王Boss系统 §8===");
        sender.sendMessage("§6/drownedking spawn §7- 在当前位置生成溺尸王");
        sender.sendMessage("§6/drownedking spawn <玩家> §7- 在指定玩家位置生成");
        sender.sendMessage("§6/drownedking list §7- 查看活跃的Boss");
        sender.sendMessage("§6/drownedking reload §7- 重载配置");
        sender.sendMessage("§6/drownedking config blockdamage <on|off> §7- 设置狂欢节爆炸是否破坏方块");
        sender.sendMessage("§6/drownedking config status §7- 查看当前配置");
        sender.sendMessage("§6/drownedking addtreasure <ID> <概率> §7- 添加手中物品到宝藏袋");
        sender.sendMessage("§6/drownedking listtreasure §7- 查看宝藏袋物品");
        sender.sendMessage("§6/drownedking removetreasure <ID> §7- 移除宝藏袋物品");
        sender.sendMessage("§6/drownedking help §7- 显示此帮助");
        
        if (sender.hasPermission("drownedking.admin")) {
            sender.sendMessage("§8[管理员命令]");
        }
    }
    
    private void handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("drownedking.spawn")) {
            sender.sendMessage("§c❌ 你没有权限生成溺尸王");
            return;
        }
        
        Location spawnLocation;
        
        if (args.length > 1) {
            // 在指定玩家位置生成
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c❌ 玩家不存在或不在线");
                return;
            }
            spawnLocation = target.getLocation();
        } else if (sender instanceof Player) {
            // 在发送者位置生成
            spawnLocation = ((Player) sender).getLocation();
        } else {
            sender.sendMessage("§c❌ 控制台请指定玩家名: /drownedking spawn <玩家>");
            return;
        }
        
        Player spawner = sender instanceof Player ? (Player) sender : null;
        
        if (manager.spawnDrownedKing(spawner, spawnLocation)) {
            sender.sendMessage("§a✅ 溺尸王已生成!");
        } else {
            sender.sendMessage("§c❌ 生成溺尸王失败");
        }
    }
    
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("drownedking.list")) {
            sender.sendMessage("§c❌ 你没有权限查看Boss列表");
            return;
        }
        
        int activeCount = manager.getActiveBosses().size();
        sender.sendMessage("§8=== §4活跃的溺尸王 §8===");
        sender.sendMessage("§7数量: §e" + activeCount);
        
        if (activeCount > 0) {
            for (DrownedKingBoss boss : manager.getActiveBosses().values()) {
                sender.sendMessage("§8- §7ID: §f" + boss.getBossId().toString().substring(0, 8));
                sender.sendMessage("  §7生成者: §f" + boss.getSpawnedBy());
                sender.sendMessage("  §7位置: §f" + formatLocation(boss.getSpawnLocation()));
                sender.sendMessage("  §7总击杀数: §c" + boss.getPlayersKilled());
                sender.sendMessage("  §7电涌计数: §e" + boss.getSurgeAttackCount());
                
                // 显示玩家死亡计数
                Map<UUID, Integer> deathCounts = boss.getPlayerDeathCounts();
                if (!deathCounts.isEmpty()) {
                    sender.sendMessage("  §7玩家死亡计数:");
                    for (Map.Entry<UUID, Integer> entry : deathCounts.entrySet()) {
                        String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        if (playerName == null) playerName = "未知玩家";
                        sender.sendMessage("    §8- §7" + playerName + ": §c" + entry.getValue() + "次");
                    }
                }
            }
        }
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("drownedking.reload")) {
            sender.sendMessage("§c❌ 你没有权限重载配置");
            return;
        }
        
        manager.reloadConfig();
        sender.sendMessage("§a✅ 溺尸王插件配置已重载");
    }
    
    private void handleConfig(CommandSender sender, String[] args) {
        if (!sender.hasPermission("drownedking.admin")) {
            sender.sendMessage("§c❌ 你没有权限修改配置");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c❌ 用法: /drownedking config <blockdamage|status> [on|off]");
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "blockdamage":
                if (args.length < 3) {
                    boolean current = manager.getTridentFrenzyBlockDamage();
                    sender.sendMessage("§7当前三叉戟狂欢节方块破坏: " + (current ? "§a开启" : "§c关闭"));
                    return;
                }
                
                if (args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true")) {
                    manager.setTridentFrenzyBlockDamage(true);
                    sender.sendMessage("§a✅ 已开启三叉戟狂欢节方块破坏");
                } else if (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("false")) {
                    manager.setTridentFrenzyBlockDamage(false);
                    sender.sendMessage("§a✅ 已关闭三叉戟狂欢节方块破坏");
                } else {
                    sender.sendMessage("§c❌ 无效参数，使用 on 或 off");
                }
                break;
                
            case "status":
                boolean blockDamage = manager.getTridentFrenzyBlockDamage();
                sender.sendMessage("§8=== §4溺尸王配置状态 §8===");
                sender.sendMessage("§7三叉戟狂欢节方块破坏: " + (blockDamage ? "§a开启" : "§c关闭"));
                break;
                
            default:
                sender.sendMessage("§c❌ 未知配置项，可用选项: blockdamage, status");
        }
    }
    
    // 添加宝藏袋物品
    private void handleAddTreasure(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令!");
            return;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("drownedking.addtreasure")) {
            player.sendMessage("§c你没有权限添加宝藏袋物品!");
            return;
        }
        
        if (args.length < 3) {
            player.sendMessage("§c用法: /drownedking addtreasure <奖励ID> <概率>");
            player.sendMessage("§b示例: /drownedking addtreasure my_trident 15.5");
            return;
        }
        
        String rewardId = args[1];
        double chance;
        
        try {
            chance = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c概率必须是数字!");
            return;
        }
        
        if (chance < 0 || chance > 100) {
            player.sendMessage("§c概率必须在0-100之间!");
            return;
        }
        
        // 添加物品到宝藏袋
        boolean success = plugin.getTreasureManager().addItemToTreasure(player, rewardId, chance);
        if (success) {
            player.sendMessage("§a成功将手中物品添加到溺尸王宝藏袋奖励中，ID: " + rewardId);
        } else {
            player.sendMessage("§c添加失败，请确保手中有物品!");
        }
    }
    
    // 列出宝藏袋物品
    private void handleListTreasure(CommandSender sender) {
        if (!sender.hasPermission("drownedking.listtreasure")) {
            sender.sendMessage("§c你没有权限查看宝藏袋物品!");
            return;
        }
        
        plugin.getTreasureManager().listTreasureItems(sender);
    }
    
    // 移除宝藏袋物品
    private void handleRemoveTreasure(CommandSender sender, String[] args) {
        if (!sender.hasPermission("drownedking.removetreasure")) {
            sender.sendMessage("§c你没有权限移除宝藏袋物品!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c用法: /drownedking removetreasure <奖励ID>");
            return;
        }
        
        String rewardId = args[1];
        boolean success = plugin.getTreasureManager().removeTreasureItem(rewardId);
        
        if (success) {
            sender.sendMessage("§a成功移除溺尸王宝藏袋物品: " + rewardId);
        } else {
            sender.sendMessage("§c未找到指定的宝藏袋物品: " + rewardId);
        }
    }
    
    private String formatLocation(Location location) {
        return String.format("世界: %s, X: %.0f, Y: %.0f, Z: %.0f", 
            location.getWorld().getName(), 
            location.getX(), location.getY(), location.getZ());
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("spawn");
            completions.add("list");
            completions.add("reload");
            completions.add("config");
            completions.add("addtreasure");
            completions.add("listtreasure");
            completions.add("removetreasure");
            completions.add("help");
        } else if (args.length == 2 && "spawn".equals(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2 && "config".equals(args[0])) {
            completions.add("blockdamage");
            completions.add("status");
        } else if (args.length == 3 && "config".equals(args[0]) && "blockdamage".equals(args[1])) {
            completions.add("on");
            completions.add("off");
        }
        
        return completions;
    }
}
