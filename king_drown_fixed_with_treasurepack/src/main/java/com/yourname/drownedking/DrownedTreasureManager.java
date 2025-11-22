package com.yourname.drownedking;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.util.*;

public class DrownedTreasureManager {

    private final DrownedKingPlugin plugin;
    private final Random random;

    public DrownedTreasureManager(DrownedKingPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void openTreasureBag(Player player) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        // 播放开箱音效
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        
        // 获取奖励列表
        List<ItemStack> rewards = getRandomRewards(config);
        
        // 给玩家奖励
        for (ItemStack reward : rewards) {
            // 检查背包空间
            if (player.getInventory().firstEmpty() == -1) {
                // 背包已满，掉落物品
                player.getWorld().dropItemNaturally(player.getLocation(), reward);
                player.sendMessage("§c背包已满，部分奖励已掉落在地上!");
            } else {
                player.getInventory().addItem(reward);
            }
        }
        
        // 显示奖励信息
        player.sendMessage("§b§l恭喜! 你从溺尸王宝藏袋中获得了以下奖励:");
        for (ItemStack reward : rewards) {
            if (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()) {
                player.sendMessage("§3- " + reward.getItemMeta().getDisplayName() + " §7x" + reward.getAmount());
            } else {
                player.sendMessage("§3- " + reward.getType().toString() + " §7x" + reward.getAmount());
            }
        }
        
        // 播放特效
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.spawnParticle(org.bukkit.Particle.SPLASH, player.getLocation(), 30, 1, 1, 1);
    }

    private List<ItemStack> getRandomRewards(FileConfiguration config) {
        List<ItemStack> rewards = new ArrayList<>();
        
        // 获取配置中的奖励项
        if (config.getConfigurationSection("rewards") != null) {
            for (String key : config.getConfigurationSection("rewards").getKeys(false)) {
                String path = "rewards." + key;
                
                // 检查概率
                double chance = config.getDouble(path + ".chance", 100.0);
                if (random.nextDouble() * 100 > chance) {
                    continue; // 未命中概率
                }
                
                ItemStack item = getItemFromConfig(config, path);
                if (item != null) {
                    rewards.add(item);
                }
            }
        }
        
        // 确保至少有一个奖励
        if (rewards.isEmpty()) {
            // 默认奖励
            ItemStack defaultReward = new ItemStack(Material.TRIDENT, 1);
            ItemMeta meta = defaultReward.getItemMeta();
            meta.setDisplayName("§b深渊碎片");
            meta.setLore(List.of("§7溺尸王力量的结晶", "§7蕴含着海洋的能量"));
            defaultReward.setItemMeta(meta);
            rewards.add(defaultReward);
        }
        
        return rewards;
    }

    private ItemStack getItemFromConfig(FileConfiguration config, String path) {
        // 优先使用序列化物品
        if (config.contains(path + ".serialized")) {
            ItemStack serializedItem = getSerializedItem(config, path);
            if (serializedItem != null) {
                return serializedItem;
            } else {
                plugin.getLogger().warning("序列化物品加载失败，尝试使用配置方式: " + path);
            }
        }
        
        // 如果序列化失败或不存在，使用配置物品
        return getConfiguredItem(config, path);
    }

    private ItemStack getSerializedItem(FileConfiguration config, String path) {
        try {
            // 获取序列化数据
            String serializedData = config.getString(path + ".serialized");
            if (serializedData == null || serializedData.isEmpty()) {
                plugin.getLogger().warning("序列化数据为空: " + path);
                return null;
            }
            
            // 清理Base64字符串（移除可能的非法字符）
            String cleanedData = cleanBase64String(serializedData);
            if (cleanedData == null) {
                plugin.getLogger().warning("Base64数据清理失败: " + path);
                return null;
            }
            
            // 使用Bukkit内置的序列化方法
            return deserializeItemStack(cleanedData);
        } catch (Exception e) {
            plugin.getLogger().warning("反序列化物品时出错: " + e.getMessage() + " [路径: " + path + "]");
            return null;
        }
    }

    private ItemStack getConfiguredItem(FileConfiguration config, String path) {
        // 获取物品类型
        String materialName = config.getString(path + ".material");
        Material material = Material.getMaterial(materialName != null ? materialName.toUpperCase() : "STONE");
        if (material == null) {
            plugin.getLogger().warning("无效的物品类型: " + materialName);
            material = Material.STONE;
        }
        
        // 创建物品
        int amount = config.getInt(path + ".amount", 1);
        int minAmount = config.getInt(path + ".min_amount", amount);
        int maxAmount = config.getInt(path + ".max_amount", amount);
        
        // 随机数量
        if (minAmount != maxAmount) {
            amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
        }
        
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) {
            return item;
        }
        
        // 设置显示名称
        if (config.contains(path + ".name")) {
            meta.setDisplayName(config.getString(path + ".name").replace('&', '§'));
        }
        
        // 设置Lore
        if (config.contains(path + ".lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(path + ".lore")) {
                lore.add(line.replace('&', '§'));
            }
            meta.setLore(lore);
        }
        
        // 设置附魔
        if (config.contains(path + ".enchantments")) {
            for (String enchantKey : config.getConfigurationSection(path + ".enchantments").getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (enchantment != null) {
                    int level = config.getInt(path + ".enchantments." + enchantKey);
                    meta.addEnchant(enchantment, level, true);
                }
            }
        }
        
        // 设置NBT标签
        if (config.contains(path + ".nbt")) {
            for (String nbtKey : config.getConfigurationSection(path + ".nbt").getKeys(false)) {
                String value = config.getString(path + ".nbt." + nbtKey);
                if (value != null) {
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, nbtKey),
                        PersistentDataType.STRING,
                        value
                    );
                }
            }
        }
        
        // 设置不可破坏
        if (config.getBoolean(path + ".unbreakable", false)) {
            meta.setUnbreakable(true);
        }
        
        // 设置自定义模型数据
        if (config.contains(path + ".custom_model_data")) {
            meta.setCustomModelData(config.getInt(path + ".custom_model_data"));
        }
        
        item.setItemMeta(meta);
        return item;
    }

    // 添加物品到宝藏袋
    public boolean addItemToTreasure(Player player, String rewardId, double chance) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c请手持要添加的物品!");
            return false;
        }
        
        FileConfiguration config = plugin.getTreasureConfig();
        String path = "rewards." + rewardId;
        
        // 序列化物品
        String serializedData = serializeItemStack(handItem);
        if (serializedData == null) {
            player.sendMessage("§c物品序列化失败!");
            return false;
        }
        
        // 保存到配置
        config.set(path + ".serialized", serializedData);
        config.set(path + ".chance", chance);
        
        // 同时保存配置版本作为备用
        config.set(path + ".material", handItem.getType().name());
        config.set(path + ".amount", handItem.getAmount());
        
        if (handItem.hasItemMeta()) {
            ItemMeta meta = handItem.getItemMeta();
            if (meta.hasDisplayName()) {
                config.set(path + ".name", meta.getDisplayName().replace('§', '&'));
            }
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>();
                for (String line : meta.getLore()) {
                    lore.add(line.replace('§', '&'));
                }
                config.set(path + ".lore", lore);
            }
            if (meta.hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    config.set(path + ".enchantments." + entry.getKey().getKey().getKey(), entry.getValue());
                }
            }
            config.set(path + ".unbreakable", meta.isUnbreakable());
            if (meta.hasCustomModelData()) {
                config.set(path + ".custom_model_data", meta.getCustomModelData());
            }
        }
        
        // 保存配置
        plugin.saveTreasureConfig();
        
        return true;
    }

    // 列出所有宝藏袋物品
    public void listTreasureItems(org.bukkit.command.CommandSender sender) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        if (config.getConfigurationSection("rewards") == null) {
            sender.sendMessage("§c宝藏袋中没有物品!");
            return;
        }
        
        sender.sendMessage("§b§l溺尸王宝藏袋物品列表:");
        for (String key : config.getConfigurationSection("rewards").getKeys(false)) {
            String path = "rewards." + key;
            double chance = config.getDouble(path + ".chance", 0.0);
            
            // 尝试获取物品信息
            String itemName = "未知物品";
            if (config.contains(path + ".serialized")) {
                ItemStack item = getSerializedItem(config, path);
                if (item != null) {
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        itemName = item.getItemMeta().getDisplayName();
                    } else {
                        itemName = item.getType().toString();
                    }
                } else {
                    itemName = "§c序列化物品加载失败";
                }
            } else if (config.contains(path + ".name")) {
                itemName = config.getString(path + ".name").replace('&', '§');
            } else if (config.contains(path + ".material")) {
                itemName = config.getString(path + ".material");
            }
            
            sender.sendMessage("§3- " + key + " §7(概率: " + chance + "%) -> " + itemName);
        }
    }

    // 移除宝藏袋物品
    public boolean removeTreasureItem(String rewardId) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        if (!config.contains("rewards." + rewardId)) {
            return false;
        }
        
        config.set("rewards." + rewardId, null);
        plugin.saveTreasureConfig();
        
        return true;
    }

    // 清理Base64字符串
    private String cleanBase64String(String data) {
        if (data == null) return null;
        
        // 移除所有空白字符
        String cleaned = data.replaceAll("\\s+", "");
        
        // 检查Base64有效性
        if (!cleaned.matches("^[a-zA-Z0-9+/]*={0,2}$")) {
            plugin.getLogger().warning("Base64字符串包含非法字符: " + cleaned);
            return null;
        }
        
        return cleaned;
    }

    // 序列化物品 - 使用Bukkit官方方法
    private String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        
        try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            
            // 写入物品
            dataOutput.writeObject(item);
            
            // 转换为Base64
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            
        } catch (Exception e) {
            plugin.getLogger().severe("序列化物品时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 反序列化物品 - 使用Bukkit官方方法
    private ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        try {
            // 解码Base64
            byte[] bytes = Base64.getDecoder().decode(data);
            
            try (java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                
                // 读取物品
                ItemStack item = (ItemStack) dataInput.readObject();
                return item;
                
            }
        } catch (java.lang.IllegalArgumentException e) {
            plugin.getLogger().severe("Base64解码错误: " + e.getMessage() + " [数据: " + data + "]");
            return null;
        } catch (Exception e) {
            plugin.getLogger().severe("反序列化物品时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
