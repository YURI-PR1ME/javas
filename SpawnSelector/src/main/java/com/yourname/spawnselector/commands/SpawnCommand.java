package com.yourname.spawnselector.commands;

import com.yourname.spawnselector.SpawnSelector;
import com.yourname.spawnselector.managers.ConfigManager;
import com.yourname.spawnselector.managers.SpawnManager;
import com.yourname.spawnselector.models.SpawnPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class SpawnCommand implements CommandExecutor {
    
    private final SpawnSelector plugin;
    private final SpawnManager spawnManager;
    private final ConfigManager configManager;
    
    public SpawnCommand(SpawnSelector plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.configManager = plugin.getConfigManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("spawnselector.admin")) {
            sender.sendMessage(Component.text(configManager.getMessage("no-permission")));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== SpawnSelector 帮助 ==="));
        sender.sendMessage(Component.text("§7/spawn add <id> <名称> <描述> §8- 添加出生点"));
        sender.sendMessage(Component.text("§7/spawn remove <id> §8- 移除出生点"));
        sender.sendMessage(Component.text("§7/spawn list §8- 列出所有出生点"));
        sender.sendMessage(Component.text("§7/spawn reload §8- 重载配置"));
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("§c只有玩家可以使用此命令！"));
            return;
        }
        
        if (args.length < 4) {
            sender.sendMessage(Component.text(configManager.getMessage("invalid-usage")));
            sender.sendMessage(Component.text("§c用法: /spawn add <id> <名称> <描述>"));
            return;
        }
        
        String id = args[1];
        String name = args[2];
        String description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        
        Player player = (Player) sender;
        Location location = player.getLocation();
        
        SpawnPoint spawnPoint = new SpawnPoint(id, name, description, location.clone());
        
        // Add some default starting items
        spawnPoint.addStartingItem(new ItemStack(Material.WOODEN_SWORD, 1));
        spawnPoint.addStartingItem(new ItemStack(Material.BREAD, 16));
        
        if (spawnManager.addSpawnPoint(spawnPoint)) {
            sender.sendMessage(Component.text(configManager.getMessage("spawn-added").replace("%spawn%", name)));
        } else {
            sender.sendMessage(Component.text("§c出生点ID已存在！"));
        }
    }
    
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("§c用法: /spawn remove <id>"));
            return;
        }
        
        String id = args[1];
        SpawnPoint spawnPoint = spawnManager.getSpawnPoint(id);
        
        if (spawnPoint != null && spawnManager.removeSpawnPoint(id)) {
            sender.sendMessage(Component.text(configManager.getMessage("spawn-removed").replace("%spawn%", spawnPoint.getName())));
        } else {
            sender.sendMessage(Component.text(configManager.getMessage("spawn-not-found")));
        }
    }
    
    private void handleList(CommandSender sender) {
        sender.sendMessage(Component.text("§6=== 出生点列表 ==="));
        
        for (SpawnPoint spawnPoint : spawnManager.getAllSpawnPoints()) {
            Component message = Component.text("§7- " + spawnPoint.getName() + " §8(ID: " + spawnPoint.getId() + ")")
                    .hoverEvent(HoverEvent.showText(Component.text("§7描述: " + spawnPoint.getDescription())))
                    .clickEvent(ClickEvent.suggestCommand("/tp " + 
                            (spawnPoint.getLocation() != null ? 
                                    spawnPoint.getLocation().getBlockX() + " " + 
                                    spawnPoint.getLocation().getBlockY() + " " + 
                                    spawnPoint.getLocation().getBlockZ() : "0 0 0")));
            
            sender.sendMessage(message);
        }
    }
    
    private void handleReload(CommandSender sender) {
        configManager.reloadConfig();
        spawnManager.loadSpawnPoints();
        sender.sendMessage(Component.text("§a配置已重载！"));
    }
}
