package com.yourname.shopplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;

public class ShopManager {
    
    private final Map<String, ShopItem> shopItems = new HashMap<>();
    private final Map<String, ShopCategory> categories = new HashMap<>();
    private ItemStack shopOpenerItem;
    
    public ShopManager() {
        loadDefaultCategories();
        createShopOpenerItem();
    }
    
    public void loadShopData() {
        shopItems.clear();
        
        // 从shops.yml加载商品
        FileConfiguration shopsConfig = ShopPlugin.getInstance().getShopsConfig();
        if (shopsConfig.contains("items")) {
            ConfigurationSection itemsSection = shopsConfig.getConfigurationSection("items");
            for (String key : itemsSection.getKeys(false)) {
                try {
                    ShopItem item = ShopItem.fromConfig(key, itemsSection.getConfigurationSection(key));
                    if (item != null) {
                        shopItems.put(key, item);
                    }
                } catch (Exception e) {
                    ShopPlugin.getInstance().getLogger().log(Level.WARNING, "加载商品 " + key + " 时出错: " + e.getMessage());
                }
            }
        }
        
        // 如果没有商品，加载默认商品
        if (shopItems.isEmpty()) {
            loadDefaultItems();
        }
        
        ShopPlugin.getInstance().getLogger().info("已加载 " + shopItems.size() + " 个商店商品");
    }
    
    public void saveShopData() {
        FileConfiguration shopsConfig = ShopPlugin.getInstance().getShopsConfig();
        
        // 清空现有数据
        shopsConfig.set("items", null);
        
        // 保存所有商品
        for (Map.Entry<String, ShopItem> entry : shopItems.entrySet()) {
            entry.getValue().saveToConfig(shopsConfig.createSection("items." + entry.getKey()));
        }
        
        ShopPlugin.getInstance().saveShopsData();
    }
    
    private void loadDefaultCategories() {
        // 从config.yml加载分类
        FileConfiguration config = ShopPlugin.getInstance().getConfig();
        for (Map<?, ?> categoryMap : config.getMapList("shop.gui.categories")) {
            String name = (String) categoryMap.get("name");
            String icon = (String) categoryMap.get("icon");
            int slot = (Integer) categoryMap.get("slot");
            
            ShopCategory category = new ShopCategory(name, Material.valueOf(icon), slot);
            categories.put(getCategoryKey(name), category);
        }
    }
    
    private void loadDefaultItems() {
        // 从config.yml加载默认商品
        FileConfiguration config = ShopPlugin.getInstance().getConfig();
        ConfigurationSection defaultItems = config.getConfigurationSection("shop.default-items");
        
        if (defaultItems != null) {
            for (String key : defaultItems.getKeys(false)) {
                try {
                    ShopItem item = ShopItem.fromConfig(key, defaultItems.getConfigurationSection(key));
                    if (item != null) {
                        shopItems.put(key, item);
                    }
                } catch (Exception e) {
                    ShopPlugin.getInstance().getLogger().log(Level.WARNING, "加载默认商品 " + key + " 时出错: " + e.getMessage());
                }
            }
        }
    }
    
    private void createShopOpenerItem() {
        FileConfiguration config = ShopPlugin.getInstance().getConfig();
        String materialName = config.getString("shop.shop-item.material", "EMERALD");
        String name = ChatColor.translateAlternateColorCodes('&', 
            config.getString("shop.shop-item.name", "&a信用点商店"));
        List<String> lore = config.getStringList("shop.shop-item.lore");
        
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            shopOpenerItem = new ItemStack(material);
            ItemMeta meta = shopOpenerItem.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(coloredLore);
            shopOpenerItem.setItemMeta(meta);
        } catch (IllegalArgumentException e) {
            shopOpenerItem = new ItemStack(Material.EMERALD);
            ShopPlugin.getInstance().getLogger().warning("无效的商店物品材料: " + materialName);
        }
    }
    
    public boolean purchaseItem(Player player, String itemId, int amount) {
        ShopItem shopItem = shopItems.get(itemId);
        if (shopItem == null) {
            player.sendMessage(ChatColor.RED + "❌ 商品不存在！");
            return false;
        }
        
        if (!shopItem.isEnabled()) {
            player.sendMessage(ChatColor.RED + "❌ 该商品已下架！");
            return false;
        }
        
        int totalCost = shopItem.getPrice() * amount;
        
        // 检查信用点是否足够
        if (!CreditHook.hasEnoughCredits(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "❌ 信用点不足！需要 " + totalCost + " 点");
            return false;
        }
        
        // 检查库存空间
        if (!hasInventorySpace(player, shopItem.getItem().getType(), amount)) {
            player.sendMessage(ChatColor.RED + "❌ 背包空间不足！");
            return false;
        }
        
        // 扣除信用点
        if (CreditHook.chargePlayer(player, totalCost)) {
            // 给予物品
            ItemStack itemToGive = shopItem.getItem().clone();
            itemToGive.setAmount(amount);
            
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);
            if (!leftover.isEmpty()) {
                // 如果背包满了，掉落物品
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            player.sendMessage(ChatColor.GREEN + "✅ 购买成功！花费 " + totalCost + " 信用点");
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "❌ 购买失败！");
            return false;
        }
    }
    
    private boolean hasInventorySpace(Player player, Material material, int amount) {
        int space = 0;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (ItemStack item : contents) {
            if (item == null) {
                space += material.getMaxStackSize();
            } else if (item.getType() == material && item.getAmount() < item.getMaxStackSize()) {
                space += item.getMaxStackSize() - item.getAmount();
            }
        }
        
        return space >= amount;
    }
    
    public void addShopItem(String id, ShopItem item) {
        shopItems.put(id, item);
        saveShopData();
    }
    
    public void removeShopItem(String id) {
        shopItems.remove(id);
        saveShopData();
    }
    
    public ShopItem getShopItem(String id) {
        return shopItems.get(id);
    }
    
    public Map<String, ShopItem> getShopItems() {
        return Collections.unmodifiableMap(shopItems);
    }
    
    public Map<String, ShopCategory> getCategories() {
        return Collections.unmodifiableMap(categories);
    }
    
    public ItemStack getShopOpenerItem() {
        return shopOpenerItem.clone();
    }
    
    public List<ShopItem> getItemsByCategory(String category) {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : shopItems.values()) {
            if (category.equals(item.getCategory())) {
                result.add(item);
            }
        }
        return result;
    }
    
    // 修复：添加公共分类键方法
    public String getCategoryKey(String displayName) {
        return ChatColor.stripColor(displayName).toLowerCase().replace(" ", "_");
    }
    
    public static class ShopCategory {
        private final String name;
        private final Material icon;
        private final int slot;
        
        public ShopCategory(String name, Material icon, int slot) {
            this.name = name;
            this.icon = icon;
            this.slot = slot;
        }
        
        public String getName() { return name; }
        public Material getIcon() { return icon; }
        public int getSlot() { return slot; }
    }
}
