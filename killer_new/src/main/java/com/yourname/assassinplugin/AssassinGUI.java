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
    
    public static void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§8暗网买凶系统");
        
        // 添加信息物品
        gui.setItem(4, createInfoItem(player));
        
        // 添加玩家头颅选择
        int slot = 9;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != player) {
                gui.setItem(slot, createPlayerHead(target));
                slot++;
                if (slot >= 44) break; // 限制显示数量
            }
        }
        
        // 添加说明
        gui.setItem(49, createHelpItem());
        
        player.openInventory(gui);
    }
    
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
        
        // 档次3 - 骷髅狙击手
        gui.setItem(15, createTierItem(3, 
            "§4骷髅狙击手", 
            Arrays.asList(
                "§7价格: §480信用点",
                "§7类型: §4骷髅",
                "§7属性: §c高等强度", 
                "§7特性: §4抢夺信用点 + 力量弓箭",
                "§c杀死目标后获得其所有信用点",
                "§c拥有力量10弓箭，3秒一发，每3发连射4秒",
                "§c穿戴全套下界合金甲，极难对付",
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
        
        // 目标信息 - 这里存储了等级信息
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
    
    private static ItemStack createInfoItem(Player player) {
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        
        meta.setDisplayName("§6暗网买凶系统");
        meta.setLore(Arrays.asList(
            "§7欢迎来到暗网，" + player.getName(),
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
    
    private static ItemStack createTierItem(int tier, String name, List<String> lore) {
        Material material;
        switch (tier) {
            case 1: material = Material.IRON_SWORD; break;
            case 2: material = Material.DIAMOND_SWORD; break;
            case 3: material = Material.BOW; break;
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
    
    private static ItemStack createHelpItem() {
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta meta = help.getItemMeta();
        
        meta.setDisplayName("§e使用说明");
        meta.setLore(Arrays.asList(
            "§71. 选择左侧的玩家头颅",
            "§72. 选择杀手等级",
            "§73. 确认发布合约",
            "",
            "§e档次说明:",
            "§7• §7Ⅰ级: 普通近战杀手",
            "§7• §6Ⅱ级: 精英杀手，抢夺信用点", 
            "§7• §4Ⅲ级: 骷髅狙击手，抢夺信用点"
        ));
        
        help.setItemMeta(meta);
        return help;
    }
    
    private static String getFormattedLocation(Location location) {
        return String.format("X:%.0f Y:%.0f Z:%.0f", 
            location.getX(), location.getY(), location.getZ());
    }
    
    private static String getTierName(int tier) {
        switch (tier) {
            case 1: return "普通杀手";
            case 2: return "精英杀手";
            case 3: return "骷髅狙击手";
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
