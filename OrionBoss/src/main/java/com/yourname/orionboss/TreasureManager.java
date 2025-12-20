package com.yourname.orionboss;

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

public class TreasureManager {

    private final OrionBossPlugin plugin;
    private final Random random;

    public TreasureManager(OrionBossPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void openTreasureBag(Player player) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        // Play opening sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        
        // Get rewards
        List<ItemStack> rewards = getRandomRewards(config);
        
        // Give rewards to player
        for (ItemStack reward : rewards) {
            // Check inventory space
            if (player.getInventory().firstEmpty() == -1) {
                // Inventory full, drop item
                player.getWorld().dropItemNaturally(player.getLocation(), reward);
                player.sendMessage("§cInventory full, some rewards dropped on ground!");
            } else {
                player.getInventory().addItem(reward);
            }
        }
        
        // Show reward information
        player.sendMessage("§6§lCongratulations! You received the following rewards from Orion's Treasure Bag:");
        for (ItemStack reward : rewards) {
            if (reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()) {
                player.sendMessage("§e- " + reward.getItemMeta().getDisplayName() + " §7x" + reward.getAmount());
            } else {
                player.sendMessage("§e- " + reward.getType().toString() + " §7x" + reward.getAmount());
            }
        }
        
        // Play effects
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.spawnParticle(org.bukkit.Particle.FLAME, player.getLocation(), 30, 1, 1, 1);
    }

    private List<ItemStack> getRandomRewards(FileConfiguration config) {
        List<ItemStack> rewards = new ArrayList<>();
        
        // Get reward items from config
        if (config.getConfigurationSection("rewards") != null) {
            for (String key : config.getConfigurationSection("rewards").getKeys(false)) {
                String path = "rewards." + key;
                
                // Check chance
                double chance = config.getDouble(path + ".chance", 100.0);
                if (random.nextDouble() * 100 > chance) {
                    continue; // Missed chance
                }
                
                ItemStack item = getItemFromConfig(config, path);
                if (item != null) {
                    rewards.add(item);
                }
            }
        }
        
        // Ensure at least one reward
        if (rewards.isEmpty()) {
            // Default reward
            ItemStack defaultReward = new ItemStack(Material.NETHERITE_INGOT, 3);
            ItemMeta meta = defaultReward.getItemMeta();
            meta.setDisplayName("§6Orion's Essence");
            meta.setLore(List.of("§7Crystallized power of Orion", "§7Contains cosmic energy"));
            defaultReward.setItemMeta(meta);
            rewards.add(defaultReward);
        }
        
        return rewards;
    }

    private ItemStack getItemFromConfig(FileConfiguration config, String path) {
        // Priority: serialized items
        if (config.contains(path + ".serialized")) {
            ItemStack serializedItem = getSerializedItem(config, path);
            if (serializedItem != null) {
                return serializedItem;
            } else {
                plugin.getLogger().warning("Serialized item loading failed, trying config method: " + path);
            }
        }
        
        // Fallback: configured items
        return getConfiguredItem(config, path);
    }

    private ItemStack getSerializedItem(FileConfiguration config, String path) {
        try {
            // Get serialized data
            String serializedData = config.getString(path + ".serialized");
            if (serializedData == null || serializedData.isEmpty()) {
                plugin.getLogger().warning("Serialized data empty: " + path);
                return null;
            }
            
            // Clean Base64 string
            String cleanedData = cleanBase64String(serializedData);
            if (cleanedData == null) {
                plugin.getLogger().warning("Base64 data cleaning failed: " + path);
                return null;
            }
            
            // Use Bukkit built-in serialization
            return deserializeItemStack(cleanedData);
        } catch (Exception e) {
            plugin.getLogger().warning("Error deserializing item: " + e.getMessage() + " [Path: " + path + "]");
            return null;
        }
    }

    private ItemStack getConfiguredItem(FileConfiguration config, String path) {
        // Get material type
        String materialName = config.getString(path + ".material");
        Material material = Material.getMaterial(materialName != null ? materialName.toUpperCase() : "STONE");
        if (material == null) {
            plugin.getLogger().warning("Invalid material type: " + materialName);
            material = Material.STONE;
        }
        
        // Create item
        int amount = config.getInt(path + ".amount", 1);
        int minAmount = config.getInt(path + ".min_amount", amount);
        int maxAmount = config.getInt(path + ".max_amount", amount);
        
        // Random amount
        if (minAmount != maxAmount) {
            amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
        }
        
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) {
            return item;
        }
        
        // Set display name
        if (config.contains(path + ".name")) {
            meta.setDisplayName(config.getString(path + ".name").replace('&', '§'));
        }
        
        // Set Lore
        if (config.contains(path + ".lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(path + ".lore")) {
                lore.add(line.replace('&', '§'));
            }
            meta.setLore(lore);
        }
        
        // Set enchantments
        if (config.contains(path + ".enchantments")) {
            for (String enchantKey : config.getConfigurationSection(path + ".enchantments").getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (enchantment != null) {
                    int level = config.getInt(path + ".enchantments." + enchantKey);
                    meta.addEnchant(enchantment, level, true);
                }
            }
        }
        
        // Set NBT tags
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
        
        // Set unbreakable
        if (config.getBoolean(path + ".unbreakable", false)) {
            meta.setUnbreakable(true);
        }
        
        // Set custom model data
        if (config.contains(path + ".custom_model_data")) {
            meta.setCustomModelData(config.getInt(path + ".custom_model_data"));
        }
        
        item.setItemMeta(meta);
        return item;
    }

    // Add item to treasure bag
    public boolean addItemToTreasure(Player player, String rewardId, double chance) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§cPlease hold the item you want to add!");
            return false;
        }
        
        FileConfiguration config = plugin.getTreasureConfig();
        String path = "rewards." + rewardId;
        
        // Serialize item
        String serializedData = serializeItemStack(handItem);
        if (serializedData == null) {
            player.sendMessage("§cItem serialization failed!");
            return false;
        }
        
        // Save to config
        config.set(path + ".serialized", serializedData);
        config.set(path + ".chance", chance);
        
        // Also save config version as backup
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
        
        // Save config
        plugin.saveTreasureConfig();
        
        return true;
    }

    // List all treasure bag items
    public void listTreasureItems(org.bukkit.command.CommandSender sender) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        if (config.getConfigurationSection("rewards") == null) {
            sender.sendMessage("§cNo items in treasure bag!");
            return;
        }
        
        sender.sendMessage("§6§lTreasure Bag Items:");
        for (String key : config.getConfigurationSection("rewards").getKeys(false)) {
            String path = "rewards." + key;
            double chance = config.getDouble(path + ".chance", 0.0);
            
            // Try to get item information
            String itemName = "Unknown Item";
            if (config.contains(path + ".serialized")) {
                ItemStack item = getSerializedItem(config, path);
                if (item != null) {
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        itemName = item.getItemMeta().getDisplayName();
                    } else {
                        itemName = item.getType().toString();
                    }
                } else {
                    itemName = "§cSerialized item load failed";
                }
            } else if (config.contains(path + ".name")) {
                itemName = config.getString(path + ".name").replace('&', '§');
            } else if (config.contains(path + ".material")) {
                itemName = config.getString(path + ".material");
            }
            
            sender.sendMessage("§e- " + key + " §7(Chance: " + chance + "%) -> " + itemName);
        }
    }

    // Remove treasure bag item
    public boolean removeTreasureItem(String rewardId) {
        FileConfiguration config = plugin.getTreasureConfig();
        
        if (!config.contains("rewards." + rewardId)) {
            return false;
        }
        
        config.set("rewards." + rewardId, null);
        plugin.saveTreasureConfig();
        
        return true;
    }

    // Clean Base64 string
    private String cleanBase64String(String data) {
        if (data == null) return null;
        
        // Remove all whitespace
        String cleaned = data.replaceAll("\\s+", "");
        
        // Check Base64 validity
        if (!cleaned.matches("^[a-zA-Z0-9+/]*={0,2}$")) {
            plugin.getLogger().warning("Base64 string contains illegal characters: " + cleaned);
            return null;
        }
        
        return cleaned;
    }

    // Serialize item - using Bukkit official method
    private String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        
        try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            
            // Write item
            dataOutput.writeObject(item);
            
            // Convert to Base64
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error serializing item: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Deserialize item - using Bukkit official method
    private ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        try {
            // Decode Base64
            byte[] bytes = Base64.getDecoder().decode(data);
            
            try (java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                
                // Read item
                ItemStack item = (ItemStack) dataInput.readObject();
                return item;
                
            }
        } catch (java.lang.IllegalArgumentException e) {
            plugin.getLogger().severe("Base64 decode error: " + e.getMessage() + " [Data: " + data + "]");
            return null;
        } catch (Exception e) {
            plugin.getLogger().severe("Error deserializing item: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
