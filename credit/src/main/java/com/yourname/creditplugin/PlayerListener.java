package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class PlayerListener implements Listener {
    
    private final CreditManager creditManager = CreditPlugin.getInstance().getCreditManager();
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        creditManager.initializePlayer(player);
        
        // 确保信用点书在背包中
        ensureCreditBook(player);
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        
        // 处理死亡逻辑
        creditManager.handlePlayerDeath(player);
        
        // 如果是被玩家杀死，处理杀人逻辑
        if (killer != null && killer != player) {
            creditManager.handleKill(killer, player);
        }
        
        // 处理死亡不掉落物品
        handleKeepItems(event);
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // 确保复活的玩家有信用点书
        Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
            ensureCreditBook(player);
            // 同步状态
            creditManager.syncPlayerState(player);
        }, 5L);
    }
    
    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode newGameMode = event.getNewGameMode();
        
        // 如果玩家从观察者模式变为生存模式（被复活）
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR && newGameMode == GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                // 检查信用点，如果为0则给予2点
                if (creditManager.getCredits(player) <= 0) {
                    creditManager.setCredits(player, 2);
                    player.sendMessage(ChatColor.GREEN + "✨ 你已被复活，获得2点信用点！");
                    creditManager.giveCreditBook(player);
                }
            }, 1L);
        }
        
        // 如果玩家从生存模式变为观察者模式
        if (event.getPlayer().getGameMode() == GameMode.SURVIVAL && newGameMode == GameMode.SPECTATOR) {
            // 同步状态，确保信用点逻辑正确
            creditManager.syncPlayerState(player);
        }
    }
    
    // 处理死亡不掉落物品
    private void handleKeepItems(PlayerDeathEvent event) {
        List<ItemStack> keptItems = new ArrayList<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        
        for (ItemStack item : event.getDrops()) {
            if (creditManager.shouldKeepOnDeath(item)) {
                keptItems.add(item);
                itemsToRemove.add(item);
            }
        }
        
        // 移除不掉落的物品
        event.getDrops().removeAll(itemsToRemove);
        
        // 将不掉落的物品存回玩家背包
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
            if (player.isOnline()) {
                for (ItemStack item : keptItems) {
                    // 如果背包已满，掉落物品
                    if (player.getInventory().firstEmpty() == -1) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                }
            }
        }, 1L);
    }
    
    // 确保玩家有信用点书
    private void ensureCreditBook(Player player) {
        boolean hasBook = false;
        
        // 检查背包中是否有信用点书
        for (ItemStack item : player.getInventory().getContents()) {
            if (creditManager.isCreditBook(item)) {
                hasBook = true;
                // 更新书本显示
                creditManager.updateBookDisplay(player, item);
                break;
            }
        }
        
        // 如果没有信用点书，给予一本
        if (!hasBook) {
            creditManager.giveCreditBook(player);
        }
    }
}
