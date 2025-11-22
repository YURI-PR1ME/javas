package com.yourname.assassinplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public class AssassinGUI {
    
    // 新的主菜单 - 包含AI杀手和玩家刺客两个选项
    public static void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8暗网买凶系统");
        
        // AI杀手选项
        gui.setItem(11, createAIKillerItem());
        
        // 玩家刺客选项
        gui.setItem(15, createPlayerAssassinItem());
        
        // 信息物品
        gui.setItem(4, createMainInfoItem(player));
        
        player.openInventory(gui);
    }
    
    // AI杀手主菜单 (原有的)
    public static void openAIMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§8AI杀手系统");
        
        // 添加信息物品
        gui.setItem(4, createAIInfoItem(player));
        
        // 添加玩家头颅选择
        int slot = 9;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != player) {
                gui.setItem(slot, createPlayerHead(target));
                slot++;
                if (slot >= 44) break;
            }
        }
        
        // 添加说明
        gui.setItem(49, createAIHelpItem());
        
        player.openInventory(gui);
    }
    
    // 玩家刺客主菜单
    public static void openPlayerAssassinMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8玩家刺客系统");
        
        // 注册成为刺客选项
        if (!AssassinPlugin.getInstance().getAssassinManager().isPlayerAssassin(player)) {
            gui.setItem(11, createRegisterItem());
        } else {
            gui.setItem(11, createAlreadyRegisteredItem());
        }
        
        // 作为买家选项
        gui.setItem(15, createBuyerItem());
        
        // 信息物品
        gui.setItem(4, createPlayerAssassinInfoItem(player));
        
        player.openInventory(gui);
    }
    
    // AI杀手等级选择
    public static void openTierSelection(Player player, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8选择杀手等级 - " + target.getName());
        
        // 档次1 - 普通杀手
        gui.setItem(11, createTierItem(1, 
            "§7普通杀手", 
            Arrays.asList(
                "§7价格: §e30信用点",
                "§7类型: §f近战僵尸",
                "§7属性: §a普通强度",
                "§7特性: §f基础攻击",
                "",
                "§a点击选择此档次"
            )));
        
        // 档次2 - 精英杀手
        gui.setItem(13, createTierItem(2, 
            "§6精英杀手", 
            Arrays.asList(
                "§7价格: §640信用点",
                "§7类型: §6近战僵尸",
                "§7属性: §6中等强度",
                "§7特性: §6抢夺信用点",
                "§e杀死目标后获得其所有信用点",
                "",
                "§6点击选择此档次"
            )));
        
        // 档次3 - 深海杀手
        gui.setItem(15, createTierItem(3, 
            "§4深海杀手", 
            Arrays.asList(
                "§7价格: §480信用点",
                "§7类型: §4溺尸",
                "§7属性: §c高等强度", 
                "§7特性: §4抢夺信用点 + 远程三叉戟",
                "§c杀死目标后获得其所有信用点",
                "§c拥有三叉戟远程攻击能力，极难对付",
                "",
                "§4点击选择此档次"
            )));       
        // 返回按钮
        gui.setItem(22, createBackItem());
        
        // 存储目标信息
        ItemStack targetInfo = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) targetInfo.getItemMeta();
        meta.setDisplayName("§a目标: " + target.getName());
        meta.setOwningPlayer(target);
        meta.setLore(Arrays.asList(
            "§7你正在悬赏此玩家",
            "§7选择右边的杀手等级"
        ));
        targetInfo.setItemMeta(meta);
        gui.setItem(4, targetInfo);
        
        player.openInventory(gui);
    }
    
    // AI杀手确认菜单
    public static void openConfirmation(Player player, Player target, int tier) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8确认合约");
        
        int cost = AssassinPlugin.getInstance().getAssassinManager().getTierCost(tier);
        String tierName = getTierName(tier);
        String tierColor = getTierColor(tier);
        
        // 确认物品
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a✅ 确认发布合约");
        confirmMeta.setLore(Arrays.asList(
            "§7目标: §f" + target.getName(),
            "§7等级: " + tierColor + tierName,
            "§7价格: §e" + cost + "信用点",
            "",
            "§a点击确认发布买凶合约",
            "§c⚠ 一旦发布无法撤销！"
        ));
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);
        
        // 取消物品
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c❌ 取消");
        cancelMeta.setLore(List.of("§7点击取消操作"));
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        
        // 目标信息
        ItemStack targetInfo = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta targetMeta = (SkullMeta) targetInfo.getItemMeta();
        targetMeta.setDisplayName("§6目标信息");
        targetMeta.setOwningPlayer(target);
        targetMeta.setLore(Arrays.asList(
            "§7名称: §f" + target.getName(),
            "§7等级: " + tierColor + tierName,
            "§7位置: §f" + getFormattedLocation(target.getLocation()),
            "§7世界: §f" + target.getWorld().getName(),
            "",
            "§e杀手将在40格外生成"
        ));
        targetInfo.setItemMeta(targetMeta);
        gui.setItem(4, targetInfo);
        
        player.openInventory(gui);
    }
    
    // 玩家刺客选择菜单
    public static void openAssassinSelectionMenu(Player buyer) {
        List<PlayerAssassin> assassins = AssassinPlugin.getInstance().getAssassinManager().getActivePlayerAssassins();
        
        int size = Math.max(9, (int) Math.ceil(assassins.size() / 9.0) * 9);
        size = Math.min(54, size);
        
        Inventory gui = Bukkit.createInventory(null, size, "§8选择刺客");
        
        for (PlayerAssassin assassin : assassins) {
            gui.addItem(createAssassinProfileItem(assassin));
        }
        
        // 返回按钮
        gui.setItem(size - 1, createBackItem());
        
        buyer.openInventory(gui);
    }
    
    // 玩家刺客目标选择菜单
    public static void openTargetSelectionMenu(Player buyer, PlayerAssassin selectedAssassin) {
        Inventory gui = Bukkit.createInventory(null, 54, "§8选择目标 - " + selectedAssassin.getAnonymousId());
        
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            // 排除旁观者和自己
            if (target.getGameMode() != org.bukkit.GameMode.SPECTATOR && !target.equals(buyer)) {
                gui.setItem(slot, createTargetSelectionItem(target));
                slot++;
            }
            if (slot >= 45) break;
        }
        
        // 返回按钮
        gui.setItem(53, createBackItem());
        
        // 存储选择的刺客信息
        ItemStack assassinInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = assassinInfo.getItemMeta();
        meta.setDisplayName("§6已选刺客: " + selectedAssassin.getAnonymousId());
        meta.setLore(Arrays.asList(
            "§7成功率: §a" + String.format("%.1f", selectedAssassin.getSuccessRate()) + "%",
            "§7完成合约: §e" + selectedAssassin.getCompletedContracts(),
            "§7总收益: §6" + selectedAssassin.getTotalEarnings() + "信用点"
        ));
        assassinInfo.setItemMeta(meta);
        gui.setItem(49, assassinInfo);
        
        buyer.openInventory(gui);
    }
    
    // ========== 物品创建方法 ==========
    
    private static ItemStack createAIKillerItem() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§cAI杀手系统");
        meta.setLore(Arrays.asList(
            "§7雇佣AI杀手执行暗杀任务",
            "§7三个不同等级的AI杀手",
            "",
            "§e点击打开AI杀手菜单"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createPlayerAssassinItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6玩家刺客系统");
        meta.setLore(Arrays.asList(
            "§7雇佣其他玩家执行暗杀任务",
            "§7玩家之间的暗杀合约",
            "",
            "§e点击打开玩家刺客菜单"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createMainInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6暗网买凶系统");
        meta.setLore(Arrays.asList(
            "§7欢迎来到暗网，" + player.getName(),
            "§8——————————————",
            "§7选择左侧的AI杀手系统雇佣AI杀手",
            "§7选择右侧的玩家刺客系统雇佣其他玩家",
            "",
            "§c⚠ 警告:",
            "§c• 买凶有冷却时间",
            "§c• 合约一旦发布无法取消",
            "§c• 失败不退还信用点"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createAIInfoItem(Player player) {
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        
        meta.setDisplayName("§cAI杀手系统");
        meta.setLore(Arrays.asList(
            "§7欢迎使用AI杀手系统，" + player.getName(),
            "§8——————————————",
            "§7选择左侧的玩家头颅",
            "§7作为你的暗杀目标",
            "",
            "§c⚠ 警告:",
            "§c• 买凶有冷却时间",
            "§c• 合约一旦发布无法取消",
            "§c• 失败不退还信用点"
        ));
        
        info.setItemMeta(meta);
        return info;
    }
    
    private static ItemStack createAIHelpItem() {
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta meta = help.getItemMeta();
        
        meta.setDisplayName("§eAI杀手说明");
        meta.setLore(Arrays.asList(
            "§71. 选择左侧的玩家头颅",
            "§72. 选择杀手等级",
            "§73. 确认发布合约",
            "",
            "§e档次说明:",
            "§7• §7Ⅰ级: 普通近战杀手",
            "§7• §6Ⅱ级: 精英杀手，抢夺信用点", 
            "§7• §4Ⅲ级: 深海杀手，抢夺信用点"
        ));
        
        help.setItemMeta(meta);
        return help;
    }
    
    private static ItemStack createRegisterItem() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§a注册成为刺客");
        meta.setLore(Arrays.asList(
            "§7成为暗网职业刺客",
            "§7接取玩家发布的合约",
            "",
            "§e入场费: " + AssassinPlugin.getInstance().getAssassinManager().getAssassinEntryFee() + "信用点",
            "§c要求: 入场费2倍以上的信用点",
            "",
            "§a点击注册"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createAlreadyRegisteredItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6已注册刺客");
        meta.setLore(Arrays.asList(
            "§7你已经是注册刺客",
            "§7可以接取玩家合约",
            "",
            "§e点击查看刺客面板"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createBuyerItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6雇佣刺客");
        meta.setLore(Arrays.asList(
            "§7雇佣其他玩家作为刺客",
            "§7指定目标进行暗杀",
            "",
            "§e点击选择刺客"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createPlayerAssassinInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
        boolean isAssassin = manager.isPlayerAssassin(player);
        
        meta.setDisplayName("§6玩家刺客系统");
        meta.setLore(Arrays.asList(
            "§7玩家之间的暗杀合约",
            "",
            isAssassin ? "§a✅ 你已是注册刺客" : "§c❌ 你未注册为刺客",
            "",
            "§8特点:",
            "§7• 玩家对玩家的暗杀",
            "§7• 匿名交易系统", 
            "§7• 信用点转移",
            "§7• 通讯书沟通"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createAssassinProfileItem(PlayerAssassin assassin) {
        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§8" + assassin.getAnonymousId());
        meta.setLore(Arrays.asList(
            "§7成功率: §a" + String.format("%.1f", assassin.getSuccessRate()) + "%",
            "§7完成合约: §e" + assassin.getCompletedContracts() + "次",
            "§7失败合约: §c" + assassin.getFailedContracts() + "次",
            "§7总收益: §6" + assassin.getTotalEarnings() + "信用点",
            "§7活跃时间: §7" + formatTime(System.currentTimeMillis() - assassin.getJoinTime()),
            "",
            "§a点击选择此刺客"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        meta.setDisplayName("§c" + target.getName());
        meta.setOwningPlayer(target);
        meta.setLore(Arrays.asList(
            "§7生命值: §a" + (int) target.getHealth() + "§7/§a" + (int) target.getMaxHealth(),
            "§7位置: §f" + getFormattedLocation(target.getLocation()),
            "§7世界: §f" + target.getWorld().getName(),
            "",
            "§e点击选择此玩家作为目标"
        ));
        
        head.setItemMeta(meta);
        return head;
    }
    
    private static ItemStack createTargetSelectionItem(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        meta.setDisplayName("§c" + target.getName());
        meta.setOwningPlayer(target);
        meta.setLore(Arrays.asList(
            "§7生命值: §a" + (int) target.getHealth() + "§7/§a" + (int) target.getMaxHealth(),
            "§7位置: §f" + getFormattedLocation(target.getLocation()),
            "§7世界: §f" + target.getWorld().getName(),
            "",
            "§e点击选择此玩家作为目标"
        ));
        
        head.setItemMeta(meta);
        return head;
    }
    
    private static ItemStack createTierItem(int tier, String name, List<String> lore) {
        Material material;
        switch (tier) {
            case 1: material = Material.IRON_SWORD; break;
            case 2: material = Material.DIAMOND_SWORD; break;
            case 3: material = Material.TRIDENT; break;
            default: material = Material.STONE_SWORD;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(name);
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createBackItem() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        
        meta.setDisplayName("§7返回");
        meta.setLore(List.of("§7点击返回主菜单"));
        
        back.setItemMeta(meta);
        return back;
    }
    
    // ========== 辅助方法 ==========
    
    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        
        if (days > 0) {
            return days + "天" + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时";
        } else {
            return "不到1小时";
        }
    }
    
    private static String getFormattedLocation(Location location) {
        return String.format("X:%.0f Y:%.0f Z:%.0f", 
            location.getX(), location.getY(), location.getZ());
    }
    
    private static String getTierName(int tier) {
        switch (tier) {
            case 1: return "普通杀手";
            case 2: return "精英杀手";
            case 3: return "深海杀手";
            default: return "未知";
        }
    }
    
    private static String getTierColor(int tier) {
        switch (tier) {
            case 1: return "§7";
            case 2: return "§6";
            case 3: return "§4";
            default: return "§f";
        }
    }
}
