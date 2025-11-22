package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class HireListener implements Listener {
    
    private final HireManager hireManager = PlayerHirePlugin.getInstance().getHireManager();
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();
        
        // 检查是否是我们的GUI - 修复：使用startsWith来匹配动态标题
        boolean isOurGUI = title.equals("§8玩家雇佣市场") || 
                          title.equals("§8成为刺客") || 
                          title.equals("§8选择刺客") || 
                          title.startsWith("§8选择目标 - ");
        
        if (!isOurGUI) {
            return;
        }
        
        // 重要：取消所有在我们的GUI中的点击事件，防止物品被拖拽
        event.setCancelled(true);
        
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // 主菜单
        if (title.equals("§8玩家雇佣市场")) {
            handleMainMenuClick(player, clicked);
        }
        // 刺客注册菜单
        else if (title.equals("§8成为刺客")) {
            handleRegistrationClick(player, clicked);
        }
        // 刺客列表菜单
        else if (title.equals("§8选择刺客")) {
            handleAssassinListClick(player, clicked, inventory);
        }
        // 目标选择菜单 - 修复：使用startsWith匹配
        else if (title.startsWith("§8选择目标 - ")) {
            handleTargetSelectionClick(player, clicked, inventory, title);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        
        // 如果关闭的是目标选择页面，清除相关数据
        if (title.startsWith("§8选择目标 - ")) {
            HireGUI.clearTargetSelection(player);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        
        // 处理目标死亡
        hireManager.handleTargetDeath(player, killer);
        
        // 处理刺客死亡
        if (killer == null) {
            // 只有玩家杀死才不算刺客死亡，其他情况都算
            hireManager.handleAssassinDeath(player);
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        
        // 检查是否是通讯书或追踪指南针
        if (CommunicationBook.isCommunicationBook(item)) {
            event.setCancelled(true);
            // 处理通讯书交互
            handleCommunicationBook(event.getPlayer(), item);
        } else if (TrackingCompass.isTrackingCompass(item)) {
            event.setCancelled(true);
            // 处理追踪指南针交互
            handleTrackingCompass(event.getPlayer(), item);
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clicked) {
        if (clicked.getType() == Material.IRON_SWORD && clicked.getItemMeta().getDisplayName().equals("§a成为刺客")) {
            HireGUI.openAssassinRegistration(player);
        } else if (clicked.getType() == Material.GOLD_INGOT && clicked.getItemMeta().getDisplayName().equals("§b雇佣刺客")) {
            HireGUI.openAssassinList(player);
        }
    }
    
    private void handleRegistrationClick(Player player, ItemStack clicked) {
        if (clicked.getType() == Material.LIME_WOOL && clicked.getItemMeta().getDisplayName().equals("§a✅ 确认注册")) {
            // 确认注册
            if (hireManager.registerAsAssassin(player)) {
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "✅ 成功注册成为刺客！");
            }
        } else if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            HireGUI.openMainMenu(player);
        }
    }
    
    private void handleAssassinListClick(Player player, ItemStack clicked, Inventory inventory) {
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            HireGUI.openMainMenu(player);
            return;
        }
        
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null) {
                // 从描述中提取刺客ID（匿名ID）
                String anonymousId = ChatColor.stripColor(meta.getDisplayName());
                
                // 查找对应的刺客档案
                for (AssassinProfile profile : hireManager.getAssassinProfiles().values()) {
                    if (profile.getAnonymousId().equals(anonymousId)) {
                        HireGUI.openTargetSelection(player, profile.getPlayerId());
                        return;
                    }
                }
                
                player.sendMessage(ChatColor.RED + "❌ 刺客不存在或已离线");
            }
        }
    }
    
    private void handleTargetSelectionClick(Player player, ItemStack clicked, Inventory inventory, String title) {
        // 处理返回按钮
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            HireGUI.clearTargetSelection(player);
            HireGUI.openAssassinList(player);
            return;
        }
        
        // 处理玩家头颅选择
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.hasOwner()) {
                Player target = Bukkit.getPlayer(meta.getOwner());
                if (target != null) {
                    // 获取刺客ID - 修复：从GUI标题中提取刺客ID
                    UUID assassinId = extractAssassinIdFromTitle(title);
                    if (assassinId == null) {
                        // 备用方法：从存储的数据中获取
                        assassinId = HireGUI.getTargetSelectionAssassin(player);
                    }
                    
                    if (assassinId != null) {
                        // 创建雇佣会话
                        if (hireManager.createHireSession(player, assassinId, target.getUniqueId()) != null) {
                            player.closeInventory();
                            player.sendMessage(ChatColor.GREEN + "📝 雇佣会话已创建！请查看通讯书");
                            
                            // 通知刺客
                            Player assassin = Bukkit.getPlayer(assassinId);
                            if (assassin != null) {
                                assassin.sendMessage(ChatColor.GREEN + "💰 你有新的雇佣邀请！目标: " + target.getName());
                                assassin.sendMessage(ChatColor.YELLOW + "💡 请使用通讯书查看详情并报价");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "❌ 创建雇佣会话失败");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "❌ 系统错误：无法找到刺客信息");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "❌ 目标玩家已离线");
                }
            }
        }
    }
    
    // 新增方法：从标题中提取刺客ID
    private UUID extractAssassinIdFromTitle(String title) {
        try {
            // 标题格式："§8选择目标 - " + assassinId.toString().substring(0, 8)
            if (title.startsWith("§8选择目标 - ")) {
                String idPart = title.substring("§8选择目标 - ".length());
                // 查找完整的UUID
                for (UUID assassinId : hireManager.getAssassinProfiles().keySet()) {
                    if (assassinId.toString().startsWith(idPart)) {
                        return assassinId;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略异常，使用备用方法
        }
        return null;
    }
    
    private void handleCommunicationBook(Player player, ItemStack book) {
        // 处理通讯书交互 - 直接打开书本界面
        UUID sessionId = CommunicationBook.getSessionId(book);
        if (sessionId != null) {
            // 重要：需要稍微延迟打开书本，确保事件处理完成
            Bukkit.getScheduler().runTaskLater(PlayerHirePlugin.getInstance(), () -> {
                player.openBook(book);
            }, 1L);
            
            player.sendMessage(ChatColor.YELLOW + "📖 打开通讯书...");
        }
    }
    
    private void handleTrackingCompass(Player player, ItemStack compass) {
        // 处理追踪指南针交互 - 显示目标信息
        UUID contractId = TrackingCompass.getContractId(compass);
        UUID targetId = TrackingCompass.getTargetId(compass);
        
        if (contractId != null && targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                player.sendMessage(ChatColor.GREEN + "🎯 目标位置: " + 
                    String.format("X:%.0f Y:%.0f Z:%.0f", 
                        target.getLocation().getX(),
                        target.getLocation().getY(), 
                        target.getLocation().getZ()));
                player.sendMessage(ChatColor.YELLOW + "📏 距离: " + 
                    String.format("%.1f格", player.getLocation().distance(target.getLocation())));
            } else {
                player.sendMessage(ChatColor.RED + "❌ 目标不在线");
            }
        }
    }
}
