package com.example.apocolyps;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;

public class ApoListener implements Listener {
    private final ApoManager manager;
    private final ApocolypsPlugin plugin;
    
    public ApoListener(ApoManager manager, ApocolypsPlugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 玩家加入游戏时，初始化其绑定AI
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            manager.initializePlayerAIs(player);
        }, 100L); // 延迟5秒执行，确保玩家完全加载
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();
        
        if (item.getType() == Material.BEACON) {
            // 放置母体方块
            if (player.hasPermission("apocolyps.admin")) {
                manager.setMotherBlock(block.getLocation(), true);
                player.sendMessage("§a母体方块已放置！");
            } else {
                event.setCancelled(true);
                player.sendMessage("§c你没有权限放置母体方块！");
            }
        } else if (item.getType() == Material.LODESTONE) {
            // 放置治疗电桩
            if (player.hasPermission("apocolyps.admin")) {
                manager.setHealingPole(block.getLocation(), true);
                player.sendMessage("§a治疗电桩已放置！");
            }
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        
        if (manager.isMotherBlock(loc)) {
            manager.setMotherBlock(loc, false);
            event.getPlayer().sendMessage("§c母体方块已被摧毁！所有AI降至动物级别！");
        } else if (manager.isHealingPole(loc)) {
            manager.setHealingPole(loc, false);
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && isCommandWand(item)) {
            event.setCancelled(true);
            
            if (event.getAction().toString().contains("RIGHT_CLICK")) {
                // 右键指挥棒命令
                Location targetLoc = event.getInteractionPoint();
                if (targetLoc != null) {
                    // 命令附近AI移动到目标位置
                    manager.getAllAIPlayers().forEach(apo -> {
                        if (apo.getLocation().distance(player.getLocation()) < 15) {
                            // 这里可以扩展为更复杂的命令系统
                            player.sendMessage("§6已向天启奴隶主下达移动命令！");
                        }
                    });
                }
            }
        }
    }
    
    private boolean isCommandWand(ItemStack item) {
        if (item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && 
               meta.getDisplayName().equals("§6天启指挥棒");
    }
}
