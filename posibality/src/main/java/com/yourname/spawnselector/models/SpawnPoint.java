package com.yourname.spawnselector.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class SpawnPoint {
    private String id;
    private String name;
    private String description;
    private Location location;
    private List<ItemStack> startingItems;
    private List<String> startingCommands;
    
    public SpawnPoint(String id, String name, String description, Location location) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.location = location;
        this.startingItems = new ArrayList<>();
        this.startingCommands = new ArrayList<>();
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    
    public List<ItemStack> getStartingItems() { return startingItems; }
    public void setStartingItems(List<ItemStack> startingItems) { this.startingItems = startingItems; }
    
    public List<String> getStartingCommands() { return startingCommands; }
    public void setStartingCommands(List<String> startingCommands) { this.startingCommands = startingCommands; }
    
    public void addStartingItem(ItemStack item) {
        startingItems.add(item);
    }
    
    public void addStartingCommand(String command) {
        startingCommands.add(command);
    }
    
    // Helper method to create default spawn here point
    public static SpawnPoint createDefaultSpawnHere() {
        return new SpawnPoint("spawn_here", "就地出生", "在当前服务器出生点开始游戏", null);
    }
}
