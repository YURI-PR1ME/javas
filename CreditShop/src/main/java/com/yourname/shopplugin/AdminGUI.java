package com.yourname.shopplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AdminGUI implements Listener {
    
    public static void openAdminMenu(Player player) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        Inventory gui = Bukkit.createInventory(null, 54, "商店管理面板");
        
        // 添加商品管理选项
        ItemStack itemManage = new ItemStack(Material.CHEST);
        ItemMeta manageMeta = itemManage.getItemMeta();
        manageMeta.setDisplayName(ChatColor.GREEN + "商品管理");
        manageMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "查看和编辑所有商品",
            ChatColor.GRAY + "点击打开"
        ));
        itemManage.setItemMeta(manageMeta);
        gui.setItem(10, itemManage);
        
        // 添加分类管理
        ItemStack categoryManage = new ItemStack(Material.BOOKSHELF);
        ItemMeta categoryMeta = categoryManage.getItemMeta();
        categoryMeta.setDisplayName(ChatColor.BLUE + "分类管理");
        categoryMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "管理商品分类",
            ChatColor.GRAY + "点击打开"
        ));
        categoryManage.setItemMeta(categoryMeta);
        gui.setItem(12, categoryManage);
        
        // 添加统计信息
        ItemStack stats = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.setDisplayName(ChatColor.YELLOW + "统计信息");
        statsMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "总商品数: " + ChatColor.GREEN + shopManager.getShopItems().size(),
            ChatColor.GRAY + "已启用: " + ChatColor.GREEN + getEnabledCount(shopManager),
            ChatColor.GRAY + "点击刷新"
        ));
        stats.setItemMeta(statsMeta);
        gui.setItem(14, stats);
        
        // 添加重载按钮
        ItemStack reload = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta reloadMeta = reload.getItemMeta();
        reloadMeta.setDisplayName(ChatColor.RED + "重载配置");
        reloadMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "重新加载商店配置",
            ChatColor.GRAY + "点击重载"
        ));
        reload.setItemMeta(reloadMeta);
        gui.setItem(16, reload);
        
        player.openInventory(gui);
    }
    
    public static void openItemManagement(Player player) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        Inventory gui = Bukkit.createInventory(null, 54, "商品管理");
        
        // 添加返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← 返回");
        backButton.setItemMeta(backMeta);
        gui.setItem(45, backButton);
        
        // 添加添加商品按钮
        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.setDisplayName(ChatColor.GREEN + "添加商品");
        addMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "使用 /shop add 命令添加商品",
            ChatColor.GRAY + "手持物品后输入命令"
        ));
        addButton.setItemMeta(addMeta);
        gui.setItem(53, addButton);
        
        // 显示所有商品
        int slot = 0;
        for (Map.Entry<String, ShopItem> entry : shopManager.getShopItems().entrySet()) {
            if (slot >= 45) break;
            
            ShopItem shopItem = entry.getValue();
            ItemStack displayItem = createAdminDisplayItem(shopItem);
            gui.setItem(slot, displayItem);
            slot++;
        }
        
        player.openInventory(gui);
    }
    
    // 创建管理界面显示物品的辅助方法
    private static ItemStack createAdminDisplayItem(ShopItem shopItem) {
        ItemStack displayItem = shopItem.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        lore.add("");
        lore.add(ChatColor.GREEN + "价格: " + ChatColor.YELLOW + shopItem.getPrice());
        lore.add(ChatColor.BLUE + "分类: " + ChatColor.WHITE + shopItem.getCategory());
        lore.add(ChatColor.GRAY + "ID: " + shopItem.getId());
        lore.add("");
        lore.add(shopItem.isEnabled() ? 
            ChatColor.GREEN + "已启用 ✓" : 
            ChatColor.RED + "已禁用 ✗");
        lore.add(ChatColor.YELLOW + "左键: " + (shopItem.isEnabled() ? "禁用" : "启用"));
        lore.add(ChatColor.YELLOW + "右键: 移除商品");
        
        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        
        return displayItem;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // 只处理管理相关的GUI
        if (!title.equals("商店管理面板") && !title.equals("商品管理")) {
            return;
        }
        
        // 取消所有点击事件，防止玩家拿起物品
        event.setCancelled(true);
        
        // 检查点击的是否是有效槽位（不是玩家背包区域）
        if (event.getClickedInventory() == null || 
            event.getClickedInventory().getType() != InventoryType.CHEST) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        
        if (title.equals("商店管理面板")) {
            // 主管理面板
            handleAdminPanelClick(player, clickedItem, event.getSlot());
        } else if (title.equals("商品管理")) {
            // 商品管理界面
            handleItemManagementClick(player, clickedItem, event.getSlot(), event.isLeftClick());
        }
    }
    
    private void handleAdminPanelClick(Player player, ItemStack clickedItem, int slot) {
        if (clickedItem.getType() == Material.CHEST && 
            clickedItem.getItemMeta().getDisplayName().contains("商品管理")) {
            openItemManagement(player);
        } else if (clickedItem.getType() == Material.REDSTONE_TORCH && 
                   clickedItem.getItemMeta().getDisplayName().contains("重载")) {
            ShopPlugin.getInstance().reloadAllConfigs();
            player.sendMessage(ChatColor.GREEN + "✅ 配置已重载！");
            openAdminMenu(player);
        } else if (clickedItem.getType() == Material.PAPER && 
                   clickedItem.getItemMeta().getDisplayName().contains("统计")) {
            openAdminMenu(player); // 刷新
        }
    }
    
    private void handleItemManagementClick(Player player, ItemStack clickedItem, int slot, boolean isLeftClick) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        
        // 返回按钮
        if (slot == 45 && clickedItem.getType() == Material.ARROW) {
            openAdminMenu(player);
            return;
        }
        
        // 添加商品按钮
        if (slot == 53 && clickedItem.getType() == Material.EMERALD) {
            player.sendMessage(ChatColor.YELLOW + "💡 使用 /shop add <价格> <分类> 命令添加商品");
            return;
        }
        
        // 商品操作（只处理前45个槽位）
        if (slot >= 0 && slot < 45) {
            String itemId = extractItemId(clickedItem);
            if (itemId != null) {
                ShopItem shopItem = shopManager.getShopItem(itemId);
                if (shopItem != null) {
                    if (isLeftClick) {
                        // 启用/禁用
                        shopItem.setEnabled(!shopItem.isEnabled());
                        shopManager.addShopItem(itemId, shopItem);
                        player.sendMessage(ChatColor.GREEN + "✅ 商品 " + itemId + " 已" + 
                            (shopItem.isEnabled() ? "启用" : "禁用"));
                        openItemManagement(player);
                    } else {
                        // 移除
                        shopManager.removeShopItem(itemId);
                        player.sendMessage(ChatColor.GREEN + "✅ 商品 " + itemId + " 已移除");
                        openItemManagement(player);
                    }
                }
            }
        }
    }
    
    private static int getEnabledCount(ShopManager shopManager) {
        int count = 0;
        for (ShopItem item : shopManager.getShopItems().values()) {
            if (item.isEnabled()) count++;
        }
        return count;
    }
    
    private String extractItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return null;
        }
        
        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.startsWith(ChatColor.GRAY + "ID: ")) {
                return line.substring((ChatColor.GRAY + "ID: ").length());
            }
        }
        return null;
    }
}
