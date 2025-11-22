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
    
    // 每页显示的商品数量
    public static final int ITEMS_PER_PAGE = 45;
    
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
            // 修复：安全地获取配置值
            String name = getStringFromMap(categoryMap, "name");
            String icon = getStringFromMap(categoryMap, "icon");
            int slot = getIntFromMap(categoryMap, "slot");
            int entranceFee = getIntFromMap(categoryMap, "entrance_fee", 0);
            String description = getStringFromMap(categoryMap, "description", "");
            
            // 修复：使用带颜色代码的名称创建分类，但键使用清理后的版本
            String cleanName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
            ShopCategory category = new ShopCategory(name, Material.valueOf(icon), slot, cleanName, entranceFee, description);
            categories.put(getCategoryKey(cleanName), category);
        }
    }
    
    // 修复：安全地从Map获取字符串值
    private String getStringFromMap(Map<?, ?> map, String key) {
        return getStringFromMap(map, key, "");
    }
    
    private String getStringFromMap(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    // 修复：安全地从Map获取整数值
    private int getIntFromMap(Map<?, ?> map, String key) {
        return getIntFromMap(map, key, 0);
    }
    
    private int getIntFromMap(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
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
    
    // 新增：检查并收取入场费
    public boolean chargeEntranceFee(Player player, ShopCategory category) {
        int entranceFee = category.getEntranceFee();
        
        if (entranceFee <= 0) {
            // 免费区域
            String freeMessage = ChatColor.translateAlternateColorCodes('&', 
                ShopPlugin.getInstance().getConfig().getString("shop.entrance_messages.free", "&a✅ 免费进入 {category}"))
                .replace("{category}", category.getDisplayName());
            player.sendMessage(freeMessage);
            return true;
        }
        
        // 检查信用点是否足够
        if (!CreditHook.hasEnoughCredits(player, entranceFee)) {
            String insufficientMessage = ChatColor.translateAlternateColorCodes('&', 
                ShopPlugin.getInstance().getConfig().getString("shop.entrance_messages.insufficient_funds", "&c❌ 信用点不足！需要 {fee} 点信用点才能进入 {category}"))
                .replace("{fee}", String.valueOf(entranceFee))
                .replace("{category}", category.getDisplayName());
            player.sendMessage(insufficientMessage);
            return false;
        }
        
        // 扣除入场费
        if (CreditHook.chargePlayer(player, entranceFee)) {
            String chargedMessage = ChatColor.translateAlternateColorCodes('&', 
                ShopPlugin.getInstance().getConfig().getString("shop.entrance_messages.charged", "&a✅ 已扣除 {fee} 点信用点进入 {category}"))
                .replace("{fee}", String.valueOf(entranceFee))
                .replace("{category}", category.getDisplayName());
            player.sendMessage(chargedMessage);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "❌ 入场费扣除失败！");
            return false;
        }
    }
    
    // 新增：获取分页商品列表
    public List<ShopItem> getItemsByCategoryPage(String category, int page) {
        List<ShopItem> allItems = getItemsByCategory(category);
        int fromIndex = (page - 1) * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, allItems.size());
        
        if (fromIndex >= allItems.size()) {
            return new ArrayList<>();
        }
        
        return allItems.subList(fromIndex, toIndex);
    }
    
    // 新增：获取管理界面分页商品列表
    public List<ShopItem> getShopItemsPage(int page) {
        List<ShopItem> allItems = new ArrayList<>(shopItems.values());
        int fromIndex = (page - 1) * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, allItems.size());
        
        if (fromIndex >= allItems.size()) {
            return new ArrayList<>();
        }
        
        return allItems.subList(fromIndex, toIndex);
    }
    
    // 新增：计算总页数
    public int getTotalPages(String category) {
        List<ShopItem> items = getItemsByCategory(category);
        return (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
    }
    
    // 新增：计算管理界面总页数
    public int getAdminTotalPages() {
        return (int) Math.ceil((double) shopItems.size() / ITEMS_PER_PAGE);
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
            // 给予物品 - 修复：使用保存的完整物品数据
            ItemStack itemToGive = shopItem.getOriginalItem().clone();
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
    
    // 修复：添加通过显示名称获取分类的方法
    public ShopCategory getCategoryByDisplayName(String displayName) {
        for (ShopCategory category : categories.values()) {
            if (category.getDisplayName().equals(displayName)) {
                return category;
            }
        }
        return null;
    }
    
    // 修复：添加通过槽位获取分类的方法
    public ShopCategory getCategoryBySlot(int slot) {
        for (ShopCategory category : categories.values()) {
            if (category.getSlot() == slot) {
                return category;
            }
        }
        return null;
    }
    
    public static class ShopCategory {
        private final String displayName; // 显示名称（带颜色）
        private final Material icon;
        private final int slot;
        private final String key; // 分类键（清理后的名称）
        private final int entranceFee; // 入场费
        private final String description; // 分类描述
        
        public ShopCategory(String displayName, Material icon, int slot, String key, int entranceFee, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.slot = slot;
            this.key = key;
            this.entranceFee = entranceFee;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public int getSlot() { return slot; }
        public String getKey() { return key; }
        public int getEntranceFee() { return entranceFee; }
        public String getDescription() { return description; }
    }
}
