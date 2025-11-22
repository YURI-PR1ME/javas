package com.yourname.arenaplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArenaCommand implements CommandExecutor, TabCompleter {
    
    private final ArenaPlugin plugin;
    private final ArenaManager arenaManager;
    private final BetManager betManager;
    private final ArenaListener arenaListener;
    
    public ArenaCommand(ArenaPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.betManager = plugin.getBetManager();
        this.arenaListener = plugin.getArenaListener();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "wand":
                handleWand(sender);
                break;
            case "create":
                handleCreate(sender, args);
                break;
            case "start":
                handleStart(sender, args);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "bet":
                handleBet(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendHelp(sender);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 擂台系统帮助 ===");
        if (sender.hasPermission("arena.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/arena wand " + ChatColor.WHITE + "- 获取圈地工具");
            sender.sendMessage(ChatColor.YELLOW + "/arena create <名称> " + ChatColor.WHITE + "- 创建擂台");
            sender.sendMessage(ChatColor.YELLOW + "/arena start <名称> " + ChatColor.WHITE + "- 开始擂台");
            sender.sendMessage(ChatColor.YELLOW + "/arena stop " + ChatColor.WHITE + "- 停止当前擂台");
        }
        sender.sendMessage(ChatColor.YELLOW + "/arena bet <A|B> <红|蓝> <金额> " + ChatColor.WHITE + "- 下注");
        sender.sendMessage(ChatColor.YELLOW + "/arena status " + ChatColor.WHITE + "- 查看状态");
        sender.sendMessage(ChatColor.YELLOW + "/arena list " + ChatColor.WHITE + "- 查看擂台列表");
    }
    
    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令");
            return;
        }
        
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限使用圈地工具");
            return;
        }
        
        Player player = (Player) sender;
        ItemStack wand = createWand();
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "✅ 你获得了擂台圈地工具");
        player.sendMessage(ChatColor.YELLOW + "使用说明：");
        player.sendMessage(ChatColor.GRAY + "- 左键点击选择第一个点");
        player.sendMessage(ChatColor.GRAY + "- 右键点击选择第二个点");
        player.sendMessage(ChatColor.GRAY + "- 选择两个点后使用 /arena create <名称> 创建擂台");
    }
    
    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令");
            return;
        }
        
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限创建擂台");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /arena create <名称>");
            return;
        }
        
        Player player = (Player) sender;
        String arenaName = args[1];
        
        // 检查是否已存在同名擂台
        if (arenaManager.getArenas().containsKey(arenaName)) {
            player.sendMessage(ChatColor.RED + "❌ 擂台名称已存在！");
            return;
        }
        
        // 获取玩家选择的两个点
        Location[] selections = arenaListener.getPlayerSelection(player);
        if (selections == null || selections[0] == null || selections[1] == null) {
            player.sendMessage(ChatColor.RED + "❌ 请先用圈地工具选择两个点！");
            player.sendMessage(ChatColor.YELLOW + "使用圈地工具左键选择第一个点，右键选择第二个点");
            return;
        }
        
        // 创建擂台
        boolean success = arenaManager.createArena(arenaName, selections[0], selections[1]);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "✅ 擂台 '" + arenaName + "' 创建成功！");
            // 清除选择点
            arenaListener.clearPlayerSelection(player);
        } else {
            player.sendMessage(ChatColor.RED + "❌ 擂台创建失败！擂台尺寸太小，最小尺寸为 " + 
                plugin.getConfig().getInt("arena.min-size") + "x" + 
                plugin.getConfig().getInt("arena.min-size"));
        }
    }
    
    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限开始擂台");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /arena start <擂台名称>");
            return;
        }
        
        String arenaName = args[1];
        Arena arena = arenaManager.getArenas().get(arenaName);
        
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "❌ 擂台不存在！");
            return;
        }
        
        arenaManager.setCurrentArena(arena);
        arena.setState(ArenaState.WAITING_FOR_PLAYERS);
        sender.sendMessage(ChatColor.GREEN + "✅ 擂台 " + arenaName + " 已启动！");
        
        // 广播消息
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.getWorld().getPlayers().forEach(p -> 
                p.sendMessage(ChatColor.YELLOW + "🏟️ 擂台 '" + arenaName + "' 已开启！进入擂台区域可成为选手"));
        }
    }
    
    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("arena.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限停止擂台");
            return;
        }
        
        if (arenaManager.getCurrentArena() == null) {
            sender.sendMessage(ChatColor.RED + "❌ 没有正在进行的擂台");
            return;
        }
        
        String arenaName = arenaManager.getCurrentArena().getName();
        arenaManager.getCurrentArena().setState(ArenaState.FINISHED);
        arenaManager.setCurrentArena(null);
        sender.sendMessage(ChatColor.GREEN + "✅ 擂台 '" + arenaName + "' 已停止");
    }
    
    private void handleBet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以下注");
            return;
        }
        
        if (!sender.hasPermission("arena.bet")) {
            sender.sendMessage(ChatColor.RED + "❌ 你没有权限下注");
            return;
        }
        
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "❌ 用法: /arena bet <A|B> <红|蓝> <金额>");
            return;
        }
        
        Player player = (Player) sender;
        
        // 检查下注是否开放
        if (!betManager.isBettingOpen()) {
            player.sendMessage(ChatColor.RED + "❌ 当前没有开放的下注！");
            return;
        }
        
        // 检查玩家是否是当前比赛的选手
        Arena bettingArena = betManager.getCurrentBettingArena();
        if (bettingArena != null && bettingArena.isFighter(player)) {
            player.sendMessage(ChatColor.RED + "❌ 你是当前比赛的选手，不能下注自己的比赛！");
            return;
        }
        
        // 解析参数
        BetTier tier;
        if (args[1].equalsIgnoreCase("A")) {
            tier = BetTier.A;
        } else if (args[1].equalsIgnoreCase("B")) {
            tier = BetTier.B;
        } else {
            player.sendMessage(ChatColor.RED + "❌ 下注档位必须是 A 或 B");
            return;
        }
        
        Team team;
        if (args[2].equalsIgnoreCase("红")) {
            team = Team.RED;
        } else if (args[2].equalsIgnoreCase("蓝")) {
            team = Team.BLUE;
        } else {
            player.sendMessage(ChatColor.RED + "❌ 队伍必须是 红 或 蓝");
            return;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "❌ 请输入有效的金额");
            return;
        }
        
        // 执行下注
        betManager.placeBet(player, tier, team, amount);
    }
    
    private void handleStatus(CommandSender sender) {
        Arena currentArena = arenaManager.getCurrentArena();
        
        if (currentArena == null) {
            sender.sendMessage(ChatColor.YELLOW + "当前没有活跃的擂台");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== 擂台状态 ===");
        sender.sendMessage(ChatColor.YELLOW + "擂台: " + currentArena.getName());
        sender.sendMessage(ChatColor.YELLOW + "状态: " + getStateString(currentArena.getState()));
        
        if (currentArena.getRedPlayer() != null && currentArena.getBluePlayer() != null) {
            sender.sendMessage(ChatColor.RED + "红队: " + currentArena.getRedPlayer().getName());
            sender.sendMessage(ChatColor.BLUE + "蓝队: " + currentArena.getBluePlayer().getName());
        }
        
        sender.sendMessage(ChatColor.YELLOW + "下注状态: " + (betManager.isBettingOpen() ? "开放" : "关闭"));
    }
    
    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 擂台列表 ===");
        if (arenaManager.getArenas().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "暂无擂台");
        } else {
            for (String arenaName : arenaManager.getArenas().keySet()) {
                Arena arena = arenaManager.getArenas().get(arenaName);
                String status = (arena == arenaManager.getCurrentArena()) ? " (活跃)" : "";
                sender.sendMessage(ChatColor.YELLOW + "- " + arenaName + status);
            }
        }
    }
    
    private ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "擂台圈地工具");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "左键点击选择第一个点",
            ChatColor.GRAY + "右键点击选择第二个点",
            ChatColor.GRAY + "然后使用 /arena create <名称>"
        ));
        wand.setItemMeta(meta);
        return wand;
    }
    
    private String getStateString(ArenaState state) {
        switch (state) {
            case WAITING_FOR_PLAYERS: return "等待选手";
            case PREPARATION: return "准备阶段";
            case IN_PROGRESS: return "比赛中";
            case FINISHED: return "已结束";
            default: return "未知";
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("wand");
            completions.add("create");
            completions.add("start");
            completions.add("stop");
            completions.add("bet");
            completions.add("status");
            completions.add("list");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start")) {
                completions.addAll(arenaManager.getArenas().keySet());
            } else if (args[0].equalsIgnoreCase("bet")) {
                completions.add("A");
                completions.add("B");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("bet")) {
                completions.add("红");
                completions.add("蓝");
            }
        }
        
        return completions;
    }
}
