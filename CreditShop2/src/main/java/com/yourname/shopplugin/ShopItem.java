package com.yourname.shopplugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopItem {
    
    private final String id;
    private final ItemStack item;
    private final ItemStack originalItem; // 保存原始物品的完整数据
    private final int price;
    private final String category;
    private boolean enabled;
    
    public ShopItem(String id, ItemStack item, int price, String category) {
        this.id = id;
        this.item = createDisplayItem(item, price); // 创建显示用物品
        this.originalItem = item.clone(); // 保存原始物品的完整数据
        this.price = price;
        this.category = category;
        this.enabled = true;
    }
    
    // 创建显示用物品（带价格信息）
    private ItemStack createDisplayItem(ItemStack originalItem, int price) {
        ItemStack displayItem = originalItem.clone();
        ItemMeta meta = displayItem.getItemMeta();
        
        // 保存原始Lore
        List<String> originalLore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        // 添加价格信息
        List<String> newLore = new ArrayList<>(originalLore);
        newLore.add("");
        newLore.add(ChatColor.GREEN + "价格: " + ChatColor.YELLOW + price + " 信用点");
        newLore.add(ChatColor.YELLOW + "左键购买1个");
        newLore.add(ChatColor.YELLOW + "右键购买64个");
        newLore.add(ChatColor.GRAY + "ID: " + id);
        
        meta.setLore(newLore);
        displayItem.setItemMeta(meta);
        
        return displayItem;
    }
    
    public static ShopItem fromConfig(String id, ConfigurationSection config) {
        try {
            String materialName = config.getString("material");
            String name = config.getString("name");
            int price = config.getInt("price");
            String category = config.getString("category");
            
            Material material = Material.valueOf(materialName.toUpperCase());
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            // 设置显示名称
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            
            // 设置Lore
            if (config.contains("lore")) {
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lore);
            }
            
            // 设置附魔
            if (config.contains("enchantments")) {
                ConfigurationSection enchantSection = config.getConfigurationSection("enchantments");
                for (String enchantKey : enchantSection.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByName(enchantKey.toUpperCase());
                    if (enchantment != null) {
                        int level = enchantSection.getInt(enchantKey);
                        meta.addEnchant(enchantment, level, true);
                    }
                }
            }
            
            item.setItemMeta(meta);
            
            ShopItem shopItem = new ShopItem(id, item, price, category);
            shopItem.setEnabled(config.getBoolean("enabled", true));
            
            return shopItem;
        } catch (Exception e) {
            ShopPlugin.getInstance().getLogger().warning("创建商品 " + id + " 失败: " + e.getMessage());
            return null;
        }
    }
    
    public void saveToConfig(ConfigurationSection config) {
        config.set("material", originalItem.getType().toString());
        config.set("price", price);
        config.set("category", category);
        config.set("enabled", enabled);
        
        ItemMeta meta = originalItem.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                config.set("name", meta.getDisplayName());
            }
            
            if (meta.hasLore()) {
                config.set("lore", meta.getLore());
            }
            
            if (meta.hasEnchants()) {
                ConfigurationSection enchantSection = config.createSection("enchantments");
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    enchantSection.set(entry.getKey().getKey().getKey(), entry.getValue());
                }
            }
        }
    }
    
    // Getters
    public String getId() { return id; }
    public ItemStack getItem() { return item.clone(); } // 返回显示用物品
    public ItemStack getOriginalItem() { return originalItem.clone(); } // 返回原始完整物品
    public int getPrice() { return price; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
