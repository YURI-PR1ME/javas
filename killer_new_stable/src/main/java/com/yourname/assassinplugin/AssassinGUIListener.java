package com.yourname.assassinplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class AssassinGUIListener implements Listener {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();
        
        // 新的主菜单
        if (title.equals("§8暗网买凶系统")) {
            event.setCancelled(true);
            handleMainMenuClick(player, clicked);
            return;
        }
        
        // 玩家刺客系统GUI
        if (title.equals("§8玩家刺客系统") || 
            title.equals("§8选择刺客") ||
            title.startsWith("§8选择目标 - ")) {
            event.setCancelled(true);
            handlePlayerAssassinGUI(player, title, clicked, inventory);
            return;
        }
        
        // AI杀手系统GUI
        if (title.equals("§8AI杀手系统") || 
            title.startsWith("§8选择杀手等级 - ") || 
            title.equals("§8确认合约")) {
            
            if (event.getRawSlot() >= inventory.getSize()) {
                return;
            }
            
            event.setCancelled(true);
            
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            if (title.equals("§8AI杀手系统")) {
                handleAIMenuClick(player, clicked);
            } else if (title.startsWith("§8选择杀手等级 - ")) {
                handleTierSelectionClick(player, title, clicked);
            } else if (title.equals("§8确认合约")) {
                handleConfirmationClick(player, clicked, inventory);
            }
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        if (clicked.getType() == Material.IRON_SWORD && clicked.getItemMeta().getDisplayName().equals("§cAI杀手系统")) {
            // 打开AI杀手菜单
            AssassinGUI.openAIMenu(player);
        } else if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta().getDisplayName().equals("§6玩家刺客系统")) {
            // 打开玩家刺客菜单
            AssassinGUI.openPlayerAssassinMenu(player);
        }
    }
    
    private void handleAIMenuClick(Player player, ItemStack clicked) {
        // 点击玩家头颅
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.hasOwner()) {
                Player target = Bukkit.getPlayer(meta.getOwner());
                if (target != null && target != player) {
                    AssassinGUI.openTierSelection(player, target);
                } else {
                    player.sendMessage("§c❌ 目标玩家不在线");
                    player.closeInventory();
                }
            }
        }
    }
    
    private void handlePlayerAssassinGUI(Player player, String title, ItemStack clicked, Inventory inventory) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
        
        if (title.equals("§8玩家刺客系统")) {
            handleMainPlayerAssassinMenu(player, clicked);
        } else if (title.equals("§8选择刺客")) {
            handleAssassinSelectionMenu(player, clicked);
        } else if (title.startsWith("§8选择目标 - ")) {
            handleTargetSelectionMenu(player, title, clicked, inventory);
        }
    }
    
    private void handleMainPlayerAssassinMenu(Player player, ItemStack clicked) {
        AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
        
        if (clicked.getType() == Material.IRON_SWORD && clicked.getItemMeta().getDisplayName().equals("§a注册成为刺客")) {
            // 注册成为刺客
            int entryFee = manager.getAssassinEntryFee();
            if (manager.registerPlayerAssassin(player, entryFee)) {
                player.sendMessage("§a✅ 注册成功！成为暗网刺客");
                player.closeInventory();
            } else {
                player.sendMessage("§c❌ 注册失败！请检查信用点是否足够");
            }
        } else if (clicked.getType() == Material.DIAMOND_SWORD && clicked.getItemMeta().getDisplayName().equals("§6已注册刺客")) {
            // 查看刺客面板
            PlayerAssassin assassin = manager.getPlayerAssassin(player.getUniqueId());
            if (assassin != null) {
                player.sendMessage("§8=== §6你的刺客档案 §8===");
                player.sendMessage("§7匿名ID: §8" + assassin.getAnonymousId());
                player.sendMessage("§7成功率: §a" + String.format("%.1f", assassin.getSuccessRate()) + "%");
                player.sendMessage("§7完成合约: §e" + assassin.getCompletedContracts() + "次");
                player.sendMessage("§7失败合约: §c" + assassin.getFailedContracts() + "次");
                player.sendMessage("§7总收益: §6" + assassin.getTotalEarnings() + "信用点");
            }
            player.closeInventory();
        } else if (clicked.getType() == Material.GOLD_INGOT && clicked.getItemMeta().getDisplayName().equals("§6雇佣刺客")) {
            // 打开刺客选择界面
            AssassinGUI.openAssassinSelectionMenu(player);
        }
    }
    
    private void handleAssassinSelectionMenu(Player player, ItemStack clicked) {
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            AssassinGUI.openPlayerAssassinMenu(player);
            return;
        }
        
        if (clicked.getType() == Material.SKELETON_SKULL) {
            // 选择刺客
            String assassinId = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
            
            // 找到对应的刺客
            for (PlayerAssassin assassin : manager.getActivePlayerAssassins()) {
                if (assassin.getAnonymousId().equals(assassinId)) {
                    AssassinGUI.openTargetSelectionMenu(player, assassin);
                    break;
                }
            }
        }
    }
    
    private void handleTargetSelectionMenu(Player player, String title, ItemStack clicked, Inventory inventory) {
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            AssassinGUI.openAssassinSelectionMenu(player);
            return;
        }
        
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.hasOwner()) {
                Player target = Bukkit.getPlayer(meta.getOwner());
                Player assassin = getSelectedAssassin(inventory);
                
                if (target != null && assassin != null && !target.equals(player)) {
                    // 创建玩家合约会话
                    AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
                    PlayerContractSession session = manager.createPlayerContractSession(player, assassin, target);
                    
                    player.sendMessage("§8[暗网] §a✅ 合约请求已发送给 " + assassin.getName());
                    player.sendMessage("§8[暗网] §7等待刺客报价...");
                    player.closeInventory();
                }
            }
        }
    }
    
    private void handleTierSelectionClick(Player player, String title, ItemStack clicked) {
        String targetName = title.replace("§8选择杀手等级 - ", "");
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage("§c❌ 目标玩家已离线");
            player.closeInventory();
            return;
        }
        
        // 点击返回按钮
        if (clicked.getType() == Material.ARROW && clicked.getItemMeta().getDisplayName().equals("§7返回")) {
            AssassinGUI.openAIMenu(player);
            return;
        }
        
        // 点击等级选项
        int tier = -1;
        if (clicked.getType() == Material.IRON_SWORD) tier = 1;
        else if (clicked.getType() == Material.DIAMOND_SWORD) tier = 2;
        else if (clicked.getType() == Material.TRIDENT) tier = 3;
        
        if (tier != -1) {
            AssassinGUI.openConfirmation(player, target, tier);
        }
    }
    
    private void handleConfirmationClick(Player player, ItemStack clicked, Inventory inventory) {
        if (clicked.getType() == Material.LIME_WOOL && clicked.getItemMeta().getDisplayName().equals("§a✅ 确认发布合约")) {
            // 确认发布合约
            ItemStack targetInfo = inventory.getItem(4);
            if (targetInfo != null && targetInfo.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) targetInfo.getItemMeta();
                if (meta != null && meta.hasOwner()) {
                    Player target = Bukkit.getPlayer(meta.getOwner());
                    if (target != null) {
                        // 从物品描述中提取等级信息
                        String lore = meta.getLore().toString();
                        int tier = extractTierFromLore(lore);
                        
                        if (tier != -1) {
                            boolean success = assassinManager.createContract(player, target, tier);
                            player.closeInventory();
                            
                            if (success) {
                                player.sendMessage("§a✅ AI杀手合约已发布！");
                            } else {
                                player.sendMessage("§c❌ 发布合约失败");
                            }
                        } else {
                            player.sendMessage("§c❌ 无法确定杀手等级");
                        }
                    } else {
                        player.sendMessage("§c❌ 目标玩家已离线");
                    }
                }
            }
        } else if (clicked.getType() == Material.RED_WOOL && clicked.getItemMeta().getDisplayName().equals("§c❌ 取消")) {
            // 取消操作
            player.closeInventory();
            player.sendMessage("§7❌ 已取消买凶操作");
        }
    }
    
    private int extractTierFromLore(String lore) {
        if (lore.contains("普通杀手")) return 1;
        if (lore.contains("精英杀手")) return 2;
        if (lore.contains("深海杀手")) return 3;
        return -1;
    }
    
    private Player getSelectedAssassin(Inventory inventory) {
        ItemStack assassinInfo = inventory.getItem(49);
        if (assassinInfo != null && assassinInfo.getType() == Material.PLAYER_HEAD) {
            String displayName = assassinInfo.getItemMeta().getDisplayName();
            String assassinId = ChatColor.stripColor(displayName).replace("已选刺客: ", "");
            
            AssassinManager manager = AssassinPlugin.getInstance().getAssassinManager();
            for (PlayerAssassin assassin : manager.getActivePlayerAssassins()) {
                if (assassin.getAnonymousId().equals(assassinId)) {
                    return Bukkit.getPlayer(assassin.getPlayerId());
                }
            }
        }
        return null;
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 确保玩家库存正确更新
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Bukkit.getScheduler().runTaskLater(AssassinPlugin.getInstance(), () -> {
                player.updateInventory();
            }, 1L);
        }
    }
}
