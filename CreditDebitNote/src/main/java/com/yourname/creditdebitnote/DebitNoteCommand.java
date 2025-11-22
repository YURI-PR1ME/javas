package com.yourname.creditdebitnote;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DebitNoteCommand implements CommandExecutor {
    
    private final DebitNoteManager debitNoteManager;
    
    public DebitNoteCommand(DebitNoteManager debitNoteManager) {
        this.debitNoteManager = debitNoteManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ 只有玩家可以使用此命令");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 1) {
            sendHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "set":
                handleSet(player, args);
                break;
            case "help":
            default:
                sendHelp(player);
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 信用点借记单指令帮助 ===");
        player.sendMessage(ChatColor.YELLOW + "/debit set <金额> " + ChatColor.WHITE + "- 设置手持空白借记单的金额");
        player.sendMessage(ChatColor.YELLOW + "/debit help " + ChatColor.WHITE + "- 显示此帮助信息");
        player.sendMessage(ChatColor.GRAY + "提示: 也可以右键空白借记单打开GUI选择预设金额");
    }
    
    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "❌ 用法: /debit set <金额>");
            return;
        }
        
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        
        if (!debitNoteManager.isBlankDebitNote(mainHand)) {
            player.sendMessage(ChatColor.RED + "❌ 请手持空白借记单使用此命令");
            return;
        }
        
        try {
            int amount = Integer.parseInt(args[1]);
            
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "❌ 金额必须大于0！");
                return;
            }
            
            if (amount > 10000) {
                player.sendMessage(ChatColor.RED + "❌ 金额不能超过10000点！");
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
                
                player.sendMessage(ChatColor.GREEN + "✅ 成功设置借记单金额为 " + amount + " 点");
            }
            
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "❌ 请输入有效的数字！");
        }
    }
}
