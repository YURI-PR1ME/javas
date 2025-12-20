package com.yourname.spawnselector.listeners;

import com.yourname.spawnselector.SpawnSelector;
import com.yourname.spawnselector.managers.ConfigManager;
import com.yourname.spawnselector.managers.PlayerDataManager;
import com.yourname.spawnselector.managers.PlayerStateManager;
import com.yourname.spawnselector.managers.SpawnManager;
import com.yourname.spawnselector.models.SpawnPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {
    
    private final SpawnSelector plugin;
    private final SpawnManager spawnManager;
    private final ConfigManager configManager;
    private final PlayerStateManager playerStateManager;
    private final PlayerDataManager playerDataManager;
    
    public PlayerListener(SpawnSelector plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.configManager = plugin.getConfigManager();
        this.playerStateManager = plugin.getPlayerStateManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 保存玩家名称到数据文件
        playerDataManager.savePlayerName(player);
        
        // 如果玩家已经选择过出生点，直接跳过
        if (playerDataManager.hasPlayerChosenSpawn(player)) {
            // 玩家已经选择过出生点，不需要冻结
            playerStateManager.setPlayerFrozen(player, false);
            return;
        }
        
        // 玩家还没有选择出生点
        playerStateManager.clearPlayerData(player);
        
        // 显示选择界面
        showSpawnSelection(player);
        
        // 冻结玩家如果配置为true
        if (configManager.getBoolean("freeze-on-join")) {
            playerStateManager.setPlayerFrozen(player, true);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // 如果玩家已经选择过出生点，不限制移动
        if (playerDataManager.hasPlayerChosenSpawn(player)) {
            return;
        }
        
        // 防止玩家在冻结状态下移动
        if (playerStateManager.isPlayerFrozen(player) &&
            !configManager.getBoolean("allow-movement-before-choice")) {
            
            // 检查玩家是否实际移动（不仅仅是旋转）
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getBlockX() != to.getBlockX() || 
                from.getBlockY() != to.getBlockY() || 
                from.getBlockZ() != to.getBlockZ()) {
                event.setTo(from);
                player.sendActionBar(Component.text("§c请先选择出生点！"));
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 只清除冻结状态，不清除选择状态
        playerStateManager.clearPlayerData(event.getPlayer());
    }
    
    public void showSpawnSelection(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("§6§l=== 选择你的出生点 ==="));
        player.sendMessage(Component.text("§7请点击下面的选项来选择你的出生点"));
        player.sendMessage(Component.text("§7§o注意: 这个选择是永久的，除非管理员重置"));
        player.sendMessage(Component.text(""));
        
        for (SpawnPoint spawnPoint : spawnManager.getAllSpawnPoints()) {
            Component message = Component.text("§e§l[ " + spawnPoint.getName() + " ]")
                    .color(NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "§7描述: " + spawnPoint.getDescription() + "\n" +
                            "§7初始装备: " + getStartingItemsDescription(spawnPoint) + "\n" +
                            "§a点击选择此出生点"
                    )))
                    .clickEvent(ClickEvent.runCommand("/choosestart " + spawnPoint.getId()));
            
            player.sendMessage(message);
        }
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(configManager.getMessage("welcome")));
        player.sendMessage(Component.text(""));
    }
    
    private String getStartingItemsDescription(SpawnPoint spawnPoint) {
        if (spawnPoint.getStartingItems().isEmpty()) {
            return "无";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ItemStack item : spawnPoint.getStartingItems()) {
            sb.append(item.getAmount()).append("x ").append(getItemName(item)).append(", ");
        }
        
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        
        return sb.toString();
    }
    
    private String getItemName(ItemStack item) {
        // Simple item name conversion - you might want to improve this
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    
    public void handleSpawnSelection(Player player, String spawnId) {
        // 检查玩家是否已经选择过出生点
        if (playerDataManager.hasPlayerChosenSpawn(player)) {
            player.sendMessage(Component.text("§c你已经选择过出生点了！如果需要重置，请联系管理员。"));
            return;
        }
        
        SpawnPoint spawnPoint = spawnManager.getSpawnPoint(spawnId);
        if (spawnPoint == null) {
            player.sendMessage(Component.text(configManager.getMessage("spawn-not-found")));
            return;
        }
        
        // 标记玩家为已选择出生点（永久保存）
        playerDataManager.setPlayerChosenSpawn(player, true);
        
        // 解除冻结
        playerStateManager.setPlayerFrozen(player, false);
        
        // 处理就地出生选项
        if ("spawn_here".equals(spawnId)) {
            // 使用服务器默认出生点
            Location spawnLocation = player.getWorld().getSpawnLocation();
            player.teleport(spawnLocation);
            player.sendMessage(Component.text(configManager.getMessage("spawn-selected").replace("%spawn%", "就地出生")));
            return;
        }
        
        // 传送玩家到选择的出生点
        if (spawnPoint.getLocation() != null) {
            player.teleport(spawnPoint.getLocation());
        }
        
        // 给予初始装备
        for (ItemStack item : spawnPoint.getStartingItems()) {
            player.getInventory().addItem(item.clone());
        }
        
        // 执行初始命令
        for (String command : spawnPoint.getStartingCommands()) {
            String formattedCommand = command.replace("%player%", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formattedCommand);
        }
        
        player.sendMessage(Component.text(configManager.getMessage("spawn-selected").replace("%spawn%", spawnPoint.getName())));
        
        // 如果处于冒险模式，设置为生存模式
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        
        // 发送永久选择提示
        player.sendMessage(Component.text("§a§l✓ §a你已永久选择此出生点！如需更改请联系管理员。"));
    }
}
