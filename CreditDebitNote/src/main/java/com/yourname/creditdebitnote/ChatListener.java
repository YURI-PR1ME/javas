package com.yourname.creditdebitnote;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    
    private final DebitNoteManager debitNoteManager = CreditDebitNote.getInstance().getDebitNoteManager();
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        if (debitNoteManager.isWaitingForCustomAmount(player)) {
            event.setCancelled(true);
            
            if (message.equalsIgnoreCase("取消")) {
                player.sendMessage(ChatColor.YELLOW + "❌ 已取消填写借记单");
                debitNoteManager.handleCustomAmountInput(player, "0"); // 清理状态
                return;
            }
            
            // 在主线程中处理
            org.bukkit.Bukkit.getScheduler().runTask(CreditDebitNote.getInstance(), () -> {
                debitNoteManager.handleCustomAmountInput(player, message);
            });
        }
    }
}
