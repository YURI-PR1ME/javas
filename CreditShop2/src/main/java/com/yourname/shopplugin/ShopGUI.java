package com.yourname.shopplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopGUI implements Listener {
    
    private static final Map<UUID, String> currentCategory = new HashMap<>();
    private static final Map<UUID, Integer> currentPage = new HashMap<>();
    
    public static void openMainMenu(Player player) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        String title = ChatColor.translateAlternateColorCodes('&', 
            ShopPlugin.getInstance().getConfig().getString("shop.gui.title", "&8信用点商店"));
        
        Inventory gui = Bukkit.createInventory(null, 54, title);
        
        // 添加分类物品
        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            ItemStack categoryItem = new ItemStack(category.getIcon());
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(category.getDisplayName());
            
            // 统计该分类下的商品数量
            int itemCount = shopManager.getItemsByCategory(category.getKey()).size();
            
            // 构建Lore，包含入场费信息
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "点击查看商品");
            lore.add(ChatColor.YELLOW + "商品数量: " + itemCount);
            
            // 添加入场费信息
            if (category.getEntranceFee() > 0) {
                lore.add(ChatColor.RED + "入场费: " + category.getEntranceFee() + " 信用点");
            } else {
                lore.add(ChatColor.GREEN + "入场费: 免费");
            }
            
            // 添加描述
            if (!category.getDescription().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.translateAlternateColorCodes('&', category.getDescription()));
            }
            
            meta.setLore(lore);
            categoryItem.setItemMeta(meta);
            
            gui.setItem(category.getSlot(), categoryItem);
        }
        
        // 添加玩家信息
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playerMeta = playerInfo.getItemMeta();
        playerMeta.setDisplayName(ChatColor.GREEN + player.getName());
        playerMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "当前信用点: " + ChatColor.YELLOW + CreditHook.getPlayerCredits(player),
            ChatColor.GRAY + "点击刷新"
        ));
        playerInfo.setItemMeta(playerMeta);
        gui.setItem(49, playerInfo);
        
        player.openInventory(gui);
        currentCategory.put(player.getUniqueId(), "main");
        currentPage.put(player.getUniqueId(), 1);
    }
    
    public static void openCategoryMenu(Player player, String categoryKey, int page) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        List<ShopItem> categoryItems = shopManager.getItemsByCategoryPage(categoryKey, page);
        
        if (categoryItems.isEmpty() && page > 1) {
            // 如果当前页没有商品但页码大于1，回到第一页
            openCategoryMenu(player, categoryKey, 1);
            return;
        }
        
        // 获取分类的显示名称
        String displayName = getCategoryDisplayName(categoryKey);
        
        String title = ChatColor.translateAlternateColorCodes('&', 
            ShopPlugin.getInstance().getConfig().getString("shop.gui.title", "&8信用点商店")) + " - " + displayName;
        
        Inventory gui = Bukkit.createInventory(null, 54, title);
        
        // 添加返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← 返回主菜单");
        backButton.setItemMeta(backMeta);
        gui.setItem(45, backButton);
        
        // 添加翻页按钮
        int totalPages = shopManager.getTotalPages(categoryKey);
        
        // 上一页按钮
        if (page > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "← 上一页");
            prevMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "第 " + (page - 1) + " 页",
                ChatColor.GRAY + "共 " + totalPages + " 页"
            ));
            prevButton.setItemMeta(prevMeta);
            gui.setItem(48, prevButton);
        }
        
        // 下一页按钮
        if (page < totalPages) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "下一页 →");
            nextMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "第 " + (page + 1) + " 页",
                ChatColor.GRAY + "共 " + totalPages + " 页"
            ));
            nextButton.setItemMeta(nextMeta);
            gui.setItem(50, nextButton);
        }
        
        // 页码信息
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.setDisplayName(ChatColor.WHITE + "第 " + page + " 页 / 共 " + totalPages + " 页");
        pageMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "商品总数: " + shopManager.getItemsByCategory(categoryKey).size(),
            ChatColor.GRAY + "每页显示: " + ShopManager.ITEMS_PER_PAGE + " 个商品"
        ));
        pageInfo.setItemMeta(pageMeta);
        gui.setItem(49, pageInfo);
        
        // 添加商品
        int slot = 0;
        for (ShopItem shopItem : categoryItems) {
            if (!shopItem.isEnabled()) continue;
            
            if (slot >= ShopManager.ITEMS_PER_PAGE) break; // 最多显示ITEMS_PER_PAGE个商品
            
            ItemStack displayItem = shopItem.getItem();
            gui.setItem(slot, displayItem);
            slot++;
        }
        
        player.openInventory(gui);
        currentCategory.put(player.getUniqueId(), categoryKey);
        currentPage.put(player.getUniqueId(), page);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // 只处理商店相关的GUI
        if (!title.contains("信用点商店")) {
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
        
        // 处理主菜单点击
        if (title.contains("信用点商店") && !title.contains("-")) {
            handleMainMenuClick(player, clickedItem, event.getSlot());
        } 
        // 处理分类菜单点击
        else if (title.contains("-")) {
            handleCategoryMenuClick(player, clickedItem, event.getSlot(), title, event);
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clickedItem, int slot) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        
        // 检查是否是分类物品 - 修复：通过槽位匹配
        ShopManager.ShopCategory category = shopManager.getCategoryBySlot(slot);
        if (category != null) {
            // 检查入场费
            if (shopManager.chargeEntranceFee(player, category)) {
                openCategoryMenu(player, category.getKey(), 1); // 默认打开第一页
            }
            return;
        }
        
        // 检查是否是玩家信息物品（刷新）
        if (slot == 49 && clickedItem.getType() == Material.PLAYER_HEAD) {
            openMainMenu(player); // 刷新界面
        }
    }
    
    private void handleCategoryMenuClick(Player player, ItemStack clickedItem, int slot, String title, InventoryClickEvent event) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        String currentCategoryKey = currentCategory.get(player.getUniqueId());
        int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 1);
        
        // 返回按钮
        if (slot == 45 && clickedItem.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }
        
        // 上一页按钮
        if (slot == 48 && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("上一页")) {
            openCategoryMenu(player, currentCategoryKey, currentPageNum - 1);
            return;
        }
        
        // 下一页按钮
        if (slot == 50 && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("下一页")) {
            openCategoryMenu(player, currentCategoryKey, currentPageNum + 1);
            return;
        }
        
        // 商品点击（只处理前45个槽位）
        if (slot >= 0 && slot < ShopManager.ITEMS_PER_PAGE && clickedItem != null) {
            String itemId = extractItemId(clickedItem);
            if (itemId != null) {
                int amount = event.isLeftClick() ? 1 : 64;
                shopManager.purchaseItem(player, itemId, amount);
                
                // 刷新界面
                Bukkit.getScheduler().runTaskLater(ShopPlugin.getInstance(), () -> {
                    // 重新打开当前分类和页面
                    openCategoryMenu(player, currentCategoryKey, currentPageNum);
                }, 5L);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        currentCategory.remove(player.getUniqueId());
        currentPage.remove(player.getUniqueId());
    }
    
    // 修复：获取分类显示名称
    private static String getCategoryDisplayName(String categoryKey) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            if (category.getKey().equals(categoryKey)) {
                return category.getDisplayName();
            }
        }
        return categoryKey; // 如果找不到，返回原始键
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
