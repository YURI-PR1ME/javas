package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class HireGUI {
    
    private static final HireManager hireManager = PlayerHirePlugin.getInstance().getHireManager();
    
    // 存储目标选择页面的刺客ID - 修复：使用更可靠的数据结构
    private static final Map<UUID, UUID> targetSelectionAssassins = new HashMap<>();
    
    // 存储GUI标题与刺客ID的映射
    private static final Map<String, UUID> titleToAssassinMap = new HashMap<>();
    
    public static void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8玩家雇佣市场");
        
        // 添加信息物品
        gui.setItem(4, createInfoItem(player));
        
        // 成为刺客选项
        gui.setItem(11, createAssassinOptionItem(player));
        
        // 雇佣刺客选项
        gui.setItem(15, createHireOptionItem(player));
        
        player.openInventory(gui);
    }
    
    public static void openAssassinRegistration(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8成为刺客");
        
        // 注册信息
        gui.setItem(4, createRegistrationInfoItem(player));
        
        // 确认注册按钮
        gui.setItem(13, createConfirmRegistrationItem(player));
        
        // 返回按钮
        gui.setItem(22, createBackItem());
        
        player.openInventory(gui);
    }
    
    public static void openAssassinList(Player player) {
        List<AssassinProfile> availableAssassins = hireManager.getAvailableAssassins();
        
        int size = (int) Math.ceil(availableAssassins.size() / 9.0) * 9;
        size = Math.max(9, Math.min(54, Math.max(size, 9)));
        
        Inventory gui = Bukkit.createInventory(null, size, "§8选择刺客");
        
        for (AssassinProfile profile : availableAssassins) {
            gui.addItem(createAssassinProfileItem(profile));
        }
        
        // 返回按钮
        gui.setItem(size - 1, createBackItem());
        
        player.openInventory(gui);
    }
    
    public static void openTargetSelection(Player player, UUID assassinId) {
        List<Player> availableTargets = new ArrayList<>();
        
        // 获取所有在线玩家（包括自己），排除旁观者
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() != GameMode.SPECTATOR) {
                availableTargets.add(online);
            }
        }
        
        int size = (int) Math.ceil(availableTargets.size() / 9.0) * 9;
        size = Math.max(9, Math.min(54, Math.max(size, 9)));
        
        // 创建唯一的标题 - 修复：使用固定格式但包含刺客ID信息
        String title = "§8选择目标 - " + assassinId.toString().substring(0, 8);
        Inventory gui = Bukkit.createInventory(null, size, title);
        
        // 存储刺客ID用于后续处理 - 修复：同时存储标题映射
        targetSelectionAssassins.put(player.getUniqueId(), assassinId);
        titleToAssassinMap.put(title, assassinId);
        
        for (Player target : availableTargets) {
            gui.addItem(createTargetPlayerItem(player, target));
        }
        
        // 刺客信息 - 使用书本而不是玩家头颅，避免被误点击
        ItemStack assassinInfo = new ItemStack(Material.BOOK);
        ItemMeta meta = assassinInfo.getItemMeta();
        
        Player assassin = Bukkit.getPlayer(assassinId);
        String assassinName = assassin != null ? assassin.getName() : "未知刺客";
        
        meta.setDisplayName("§6选定的刺客");
        meta.setLore(Arrays.asList(
            "§7刺客: " + assassinName,
            "§7ID: " + assassinId.toString().substring(0, 8),
            "",
            "§e点击目标玩家头颅选择目标"
        ));
        assassinInfo.setItemMeta(meta);
        gui.setItem(4, assassinInfo);
        
        // 返回按钮
        gui.setItem(size - 1, createBackItem());
        
        player.openInventory(gui);
    }
    
    // 获取目标选择页面的刺客ID
    public static UUID getTargetSelectionAssassin(Player player) {
        return targetSelectionAssassins.get(player.getUniqueId());
    }
    
    // 通过标题获取刺客ID
    public static UUID getAssassinIdFromTitle(String title) {
        return titleToAssassinMap.get(title);
    }
    
    // 清除目标选择数据
    public static void clearTargetSelection(Player player) {
        UUID assassinId = targetSelectionAssassins.remove(player.getUniqueId());
        if (assassinId != null) {
            // 同时清理标题映射
            titleToAssassinMap.entrySet().removeIf(entry -> entry.getValue().equals(assassinId));
        }
    }
    
    private static ItemStack createInfoItem(Player player) {
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        
        meta.setDisplayName("§6玩家雇佣市场");
        meta.setLore(Arrays.asList(
            "§7欢迎来到雇佣市场，" + player.getName(),
            "§8——————————————",
            "§a成为刺客 §7- 注册成为可被雇佣的刺客",
            "§b雇佣刺客 §7- 雇佣其他玩家执行暗杀任务",
            "",
            "§e规则说明:",
            "§7• 刺客需要资格核验",
            "§7• 通过通讯书协商价格",
            "§7• 合约成功获得目标信用点",
            "§7• 合约失败返还金额"
        ));
        
        info.setItemMeta(meta);
        return info;
    }
    
    private static ItemStack createAssassinOptionItem(Player player) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        boolean isRegistered = hireManager.getAssassinProfiles().containsKey(player.getUniqueId());
        int registrationFee = hireManager.getRegistrationFee();
        
        meta.setDisplayName("§a成为刺客");
        meta.setLore(Arrays.asList(
            "§7注册成为可被雇佣的刺客",
            "§8——————————————",
            isRegistered ? 
                "§a✅ 你已注册成为刺客" :
                "§e💰 注册费用: " + registrationFee + " 信用点",
            "§e📋 资格要求: " + (registrationFee * 2) + " 点信用点",
            "",
            isRegistered ? 
                "§a点击查看刺客档案" :
                "§a点击注册成为刺客"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createHireOptionItem(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§b雇佣刺客");
        meta.setLore(Arrays.asList(
            "§7雇佣其他玩家执行暗杀任务",
            "§8——————————————",
            "§7• 浏览匿名刺客档案",
            "§7• 选择目标玩家",
            "§7• 通过通讯书协商价格",
            "§7• 获得目标信用点",
            "",
            "§b点击浏览可用刺客"
        ));
        
        item.setItemMeta(meta);
        return item;
    }
    
    private static ItemStack createRegistrationInfoItem(Player player) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        
        int registrationFee = hireManager.getRegistrationFee();
        int requiredCredits = registrationFee * 2;
        int playerCredits = getPlayerCredits(player);
        boolean canRegister = playerCredits >= requiredCredits;
        
        meta.setDisplayName("§6刺客注册信息");
        meta.setLore(Arrays.asList(
            "§7注册成为职业刺客",
            "§8——————————————",
            "§e💰 注册费用: " + registrationFee + " 信用点",
            "§e📋 资格核验: " + requiredCredits + " 点信用点",
            "",
            "§7你的信用点: " + playerCredits + " / " + requiredCredits,
            canRegister ? 
                "§a✅ 符合注册资格" :
                "§c❌ 信用点不足",
            "",
            "§7注册后你将:",
            "§7• 出现在刺客名单中",
            "§7• 可以接受雇佣任务",
            "§7• 获得追踪指南针",
            "§7• 分享任务收益"
        ));
        
        info.setItemMeta(meta);
        return info;
    }
    
    private static ItemStack createConfirmRegistrationItem(Player player) {
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = confirm.getItemMeta();
        
        int registrationFee = hireManager.getRegistrationFee();
        int requiredCredits = registrationFee * 2;
        int playerCredits = getPlayerCredits(player);
        boolean canRegister = playerCredits >= requiredCredits;
        
        meta.setDisplayName(canRegister ? "§a✅ 确认注册" : "§c❌ 无法注册");
        meta.setLore(Arrays.asList(
            canRegister ? 
                "§7点击确认注册成为刺客" :
                "§7你需要 " + requiredCredits + " 点信用点才能注册",
            "",
            canRegister ? 
                "§a费用: " + registrationFee + " 信用点" :
                "§c当前: " + playerCredits + " / " + requiredCredits
        ));
        
        confirm.setItemMeta(meta);
        return confirm;
    }
    
    private static ItemStack createAssassinProfileItem(AssassinProfile profile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        Player assassin = Bukkit.getPlayer(profile.getPlayerId());
        boolean isOnline = assassin != null && assassin.isOnline();
        
        meta.setDisplayName("§6" + profile.getAnonymousId());
        if (assassin != null) {
            meta.setOwningPlayer(assassin);
        }
        
        meta.setLore(Arrays.asList(
            "§7状态: " + (isOnline ? "§a在线" : "§c离线"),
            "§7完成合约: §e" + profile.getCompletedContracts(),
            "§7成功率: §a" + String.format("%.1f", profile.getSuccessRate() * 100) + "%",
            "§7总收入: §6" + profile.getTotalEarned() + " 信用点",
            "",
            "§7注册时间: §f" + formatTime(profile.getRegisteredTime()),
            "",
            "§a点击雇佣此刺客"
        ));
        
        head.setItemMeta(meta);
        return head;
    }
    
    private static ItemStack createTargetPlayerItem(Player buyer, Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        int targetCredits = getPlayerCredits(target);
        boolean isSelf = target.getUniqueId().equals(buyer.getUniqueId());
        
        meta.setDisplayName(isSelf ? "§c" + target.getName() + " (你自己)" : "§c" + target.getName());
        meta.setOwningPlayer(target);
        meta.setLore(Arrays.asList(
            "§7信用点: §e" + targetCredits,
            "§7生命值: §a" + (int) target.getHealth() + "§7/§a" + (int) target.getMaxHealth(),
            "§7位置: §f" + getFormattedLocation(target.getLocation()),
            "",
            isSelf ?
                "§c⚠ 警告：这将把自己设为目标！" :
                "§a点击选择此玩家作为目标"
        ));
        
        head.setItemMeta(meta);
        return head;
    }
    
    private static ItemStack createBackItem() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        
        meta.setDisplayName("§7返回");
        meta.setLore(List.of("§7点击返回上一菜单"));
        
        back.setItemMeta(meta);
        return back;
    }
    
    private static int getPlayerCredits(Player player) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return 0;
            
            java.lang.reflect.Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            java.lang.reflect.Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            return (int) getCredits.invoke(creditManager, player);
            
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static String getFormattedLocation(org.bukkit.Location location) {
        return String.format("X:%.0f Y:%.0f Z:%.0f", 
            location.getX(), location.getY(), location.getZ());
    }
    
    private static String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        
        if (days > 0) {
            return days + "天前";
        } else if (hours > 0) {
            return hours + "小时前";
        } else {
            return "刚刚";
        }
    }
}
