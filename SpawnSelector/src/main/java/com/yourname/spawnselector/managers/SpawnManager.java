package com.yourname.spawnselector.managers;

import com.yourname.spawnselector.SpawnSelector;
import com.yourname.spawnselector.models.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SpawnManager {
    
    private final SpawnSelector plugin;
    private final Map<String, SpawnPoint> spawnPoints;
    
    public SpawnManager(SpawnSelector plugin) {
        this.plugin = plugin;
        this.spawnPoints = new HashMap<>();
    }
    
    public void loadSpawnPoints() {
        spawnPoints.clear();
        FileConfiguration config = plugin.getConfig();
        
        // Add default spawn here option
        SpawnPoint spawnHere = SpawnPoint.createDefaultSpawnHere();
        spawnPoints.put(spawnHere.getId(), spawnHere);
        
        // Load custom spawn points
        ConfigurationSection spawnsSection = config.getConfigurationSection("spawn-points");
        if (spawnsSection != null) {
            for (String key : spawnsSection.getKeys(false)) {
                String path = "spawn-points." + key;
                
                String name = config.getString(path + ".name", key);
                String description = config.getString(path + ".description", "No description");
                
                // Load location
                String worldName = config.getString(path + ".location.world", "world");
                double x = config.getDouble(path + ".location.x", 0);
                double y = config.getDouble(path + ".location.y", 0);
                double z = config.getDouble(path + ".location.z", 0);
                float yaw = (float) config.getDouble(path + ".location.yaw", 0);
                float pitch = (float) config.getDouble(path + ".location.pitch", 0);
                
                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                
                SpawnPoint spawnPoint = new SpawnPoint(key, name, description, location);
                
                // Load starting items
                List<ItemStack> startingItems = loadItems(config, path + ".starting-items");
                spawnPoint.setStartingItems(startingItems);
                
                // Load starting commands
                List<String> startingCommands = config.getStringList(path + ".starting-commands");
                spawnPoint.setStartingCommands(startingCommands);
                
                spawnPoints.put(key, spawnPoint);
            }
        }
    }
    
    private List<ItemStack> loadItems(FileConfiguration config, String path) {
        List<ItemStack> items = new ArrayList<>();
        List<Map<?, ?>> itemMaps = config.getMapList(path);
        
        for (Map<?, ?> itemMap : itemMaps) {
            try {
                String materialName = (String) itemMap.get("material");
                Object amountObj = itemMap.get("amount");
                int amount = 1;
                
                // 安全地处理 amount 值
                if (amountObj instanceof Integer) {
                    amount = (Integer) amountObj;
                } else if (amountObj instanceof String) {
                    try {
                        amount = Integer.parseInt((String) amountObj);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid amount format: " + amountObj);
                    }
                }
                
                Material material = Material.getMaterial(materialName.toUpperCase());
                if (material != null) {
                    ItemStack item = new ItemStack(material, amount);
                    items.add(item);
                } else {
                    plugin.getLogger().warning("Unknown material: " + materialName);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load item: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    public boolean addSpawnPoint(SpawnPoint spawnPoint) {
        if (spawnPoints.containsKey(spawnPoint.getId())) {
            return false;
        }
        
        spawnPoints.put(spawnPoint.getId(), spawnPoint);
        saveSpawnPointToConfig(spawnPoint);
        return true;
    }
    
    public boolean removeSpawnPoint(String id) {
        if (!spawnPoints.containsKey(id)) {
            return false;
        }
        
        spawnPoints.remove(id);
        removeSpawnPointFromConfig(id);
        return true;
    }
    
    public SpawnPoint getSpawnPoint(String id) {
        return spawnPoints.get(id);
    }
    
    public Collection<SpawnPoint> getAllSpawnPoints() {
        return spawnPoints.values();
    }
    
    private void saveSpawnPointToConfig(SpawnPoint spawnPoint) {
        FileConfiguration config = plugin.getConfig();
        String path = "spawn-points." + spawnPoint.getId();
        
        config.set(path + ".name", spawnPoint.getName());
        config.set(path + ".description", spawnPoint.getDescription());
        
        if (spawnPoint.getLocation() != null) {
            Location loc = spawnPoint.getLocation();
            config.set(path + ".location.world", loc.getWorld().getName());
            config.set(path + ".location.x", loc.getX());
            config.set(path + ".location.y", loc.getY());
            config.set(path + ".location.z", loc.getZ());
            config.set(path + ".location.yaw", loc.getYaw());
            config.set(path + ".location.pitch", loc.getPitch());
        }
        
        // Save starting items
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (ItemStack item : spawnPoint.getStartingItems()) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("material", item.getType().name());
            itemMap.put("amount", item.getAmount());
            itemList.add(itemMap);
        }
        config.set(path + ".starting-items", itemList);
        
        // Save starting commands
        config.set(path + ".starting-commands", spawnPoint.getStartingCommands());
        
        plugin.saveConfig();
    }
    
    private void removeSpawnPointFromConfig(String id) {
        FileConfiguration config = plugin.getConfig();
        config.set("spawn-points." + id, null);
        plugin.saveConfig();
    }
}
