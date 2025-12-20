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
            meta.setDisplayName(category.getName());
            
            // 统计该分类下的商品数量
            int itemCount = shopManager.getItemsByCategory(getCategoryKey(category.getName())).size();
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "点击查看商品",
                ChatColor.YELLOW + "商品数量: " + itemCount
            ));
            
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
    
    public static void openCategoryMenu(Player player, String categoryName) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        List<ShopItem> categoryItems = shopManager.getItemsByCategory(getCategoryKey(categoryName));
        
        if (categoryItems.isEmpty()) {
            player.sendMessage(ChatColor.RED + "❌ 该分类下没有商品！");
            return;
        }
        
        String title = ChatColor.translateAlternateColorCodes('&', 
            ShopPlugin.getInstance().getConfig().getString("shop.gui.title", "&8信用点商店")) + " - " + categoryName;
        
        Inventory gui = Bukkit.createInventory(null, 54, title);
        
        // 添加返回按钮
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← 返回主菜单");
        backButton.setItemMeta(backMeta);
        gui.setItem(45, backButton);
        
        // 添加商品
        int slot = 0;
        for (ShopItem shopItem : categoryItems) {
            if (!shopItem.isEnabled()) continue;
            
            if (slot >= 45) break; // 最多显示45个商品
            
            ItemStack displayItem = createShopDisplayItem(shopItem);
            gui.setItem(slot, displayItem);
            slot++;
        }
        
        player.openInventory(gui);
        currentCategory.put(player.getUniqueId(), getCategoryKey(categoryName));
        currentPage.put(player.getUniqueId(), 1);
    }
    
    // 创建商店显示物品的辅助方法
    private static ItemStack createShopDisplayItem(ShopItem shopItem) {
        ItemStack displayItem = shopItem.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        lore.add("");
        lore.add(ChatColor.GREEN + "价格: " + ChatColor.YELLOW + shopItem.getPrice() + " 信用点");
        lore.add(ChatColor.YELLOW + "左键购买1个");
        lore.add(ChatColor.YELLOW + "右键购买64个");
        lore.add(ChatColor.GRAY + "ID: " + shopItem.getId());
        
        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        
        return displayItem;
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
            handleCategoryMenuClick(player, clickedItem, event.getSlot(), title);
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clickedItem, int slot) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        
        // 检查是否是分类物品
        for (ShopManager.ShopCategory category : shopManager.getCategories().values()) {
            if (slot == category.getSlot()) {
                openCategoryMenu(player, ChatColor.stripColor(category.getName()));
                return;
            }
        }
        
        // 检查是否是玩家信息物品（刷新）
        if (slot == 49 && clickedItem.getType() == Material.PLAYER_HEAD) {
            openMainMenu(player); // 刷新界面
        }
    }
    
    private void handleCategoryMenuClick(Player player, ItemStack clickedItem, int slot, String title) {
        ShopManager shopManager = ShopPlugin.getInstance().getShopManager();
        
        // 返回按钮
        if (slot == 45 && clickedItem.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }
        
        // 商品点击（只处理前45个槽位）
        if (slot >= 0 && slot < 45 && clickedItem != null) {
            String itemId = extractItemId(clickedItem);
            if (itemId != null) {
                int amount = 1; // 默认购买1个
                shopManager.purchaseItem(player, itemId, amount);
                
                // 刷新界面
                Bukkit.getScheduler().runTaskLater(ShopPlugin.getInstance(), () -> {
                    // 重新打开当前分类
                    String categoryName = title.split("-")[1].trim();
                    openCategoryMenu(player, categoryName);
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
    
    private static String getCategoryKey(String displayName) {
        return ChatColor.stripColor(displayName).toLowerCase().replace(" ", "_");
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
