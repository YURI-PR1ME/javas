package com.yourname.creditdebitnote;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import java.util.Arrays;

public class DebitNoteListener implements Listener {
    
    private final DebitNoteManager debitNoteManager = CreditDebitNote.getInstance().getDebitNoteManager();
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) return;
        
        // 检查是否是空白借记单右键
        if (debitNoteManager.isBlankDebitNote(item) && 
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            openFillGUI(player);
            return;
        }
        
        // 检查是否是已填写借记单右键 - 修复bug：无论是否潜行都取消事件
        if (debitNoteManager.isFilledDebitNote(item) && 
            (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // 取消事件防止变成普通地图
            event.setCancelled(true);
            
            // 只有潜行右键才执行兑现逻辑
            if (player.isSneaking()) {
                // 兑现借记单
                if (debitNoteManager.redeemDebitNote(player, item)) {
                    // 减少物品数量
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    }
                }
            } else {
                // 非潜行右键时显示信息
                DebitNoteManager.DebitNoteData data = debitNoteManager.getDebitNoteData(item);
                if (data != null) {
                    player.sendMessage(ChatColor.YELLOW + "💡 这张借记单包含 " + data.getAmount() + " 点信用点");
                    player.sendMessage(ChatColor.GRAY + "签发者: " + data.getIssuerName());
                    player.sendMessage(ChatColor.GREEN + "提示: 下蹲+右键可兑现信用点");
                }
            }
            return;
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();
        String title = view.getTitle();
        
        if (title.equals("填写信用点借记单")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null) return;
            
            int amount = getAmountFromSlot(event.getRawSlot());
            if (amount > 0) {
                fillDebitNote(player, amount);
                player.closeInventory();
            }
        }
    }
    
    private void openFillGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "填写信用点借记单");
        
        // 添加面额选项 - 调整为50,100,300,500,800
        int[] amounts = {50, 100, 300, 500, 800};
        Material[] materials = {Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.BEACON};
        String[] colors = {"§6", "§b", "§a", "§8", "§5"};
        
        for (int i = 0; i < amounts.length; i++) {
            ItemStack option = new ItemStack(materials[i]);
            ItemMeta meta = option.getItemMeta();
            
            meta.setDisplayName(colors[i] + amounts[i] + " 点信用点");
            meta.setLore(Arrays.asList(
                "§7点击填写 " + amounts[i] + " 点借记单",
                "§e将从你的账户扣除 " + amounts[i] + " 点信用点"
            ));
            
            option.setItemMeta(meta);
            gui.setItem(10 + i, option);
        }
        
        // 添加信息说明
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6信用点借记单说明");
        infoMeta.setLore(Arrays.asList(
            "§7填写后将从你的账户扣除相应信用点",
            "§7其他玩家持借记单下蹲右键即可兑现",
            "§7使用 /debit set <金额> 设置自定义金额"
        ));
        info.setItemMeta(infoMeta);
        gui.setItem(22, info);
        
        player.openInventory(gui);
    }
    
    private int getAmountFromSlot(int slot) {
        switch (slot) {
            case 10: return 50;
            case 11: return 100;
            case 12: return 300;
            case 13: return 500;
            case 14: return 800;
            default: return 0;
        }
    }
    
    private void fillDebitNote(Player player, int amount) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        
        if (!debitNoteManager.isBlankDebitNote(mainHand)) {
            player.sendMessage(ChatColor.RED + "❌ 请手持空白借记单进行填写");
            return;
        }
        
        // 填写借记单
        if (debitNoteManager.fillDebitNote(player, mainHand, amount)) {
            // 将空白借记单替换为已填写的借记单
            ItemStack filledNote = debitNoteManager.createFilledDebitNote(player, amount);
            
            if (mainHand.getAmount() > 1) {
                mainHand.setAmount(mainHand.getAmount() - 1);
                
                // 检查背包空间
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), filledNote);
                    player.sendMessage(ChatColor.YELLOW + "💡 背包已满，借记单已掉落在地面上");
                } else {
                    player.getInventory().addItem(filledNote);
                }
            } else {
                player.getInventory().setItemInMainHand(filledNote);
            }
        }
    }
}
