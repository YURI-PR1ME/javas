package com.yourname.assassinplugin;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CommunicationBookListener implements Listener {
    
    private final AssassinManager assassinManager = AssassinPlugin.getInstance().getAssassinManager();
    
    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        ItemStack book = player.getInventory().getItemInMainHand();
        
        if (!assassinManager.isCommunicationBook(book)) {
            return;
        }
        
        NamespacedKey sessionKey = new NamespacedKey(AssassinPlugin.getInstance(), "communication_book");
        String sessionIdStr = book.getItemMeta().getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        
        NamespacedKey partnerKey = new NamespacedKey(AssassinPlugin.getInstance(), "communication_partner");
        String partnerIdStr = book.getItemMeta().getPersistentDataContainer().get(partnerKey, PersistentDataType.STRING);
        
        if (sessionIdStr == null || partnerIdStr == null || partnerIdStr.equals("self")) {
            return;
        }
        
        try {
            UUID partnerId = UUID.fromString(partnerIdStr);
            Player partner = org.bukkit.Bukkit.getPlayer(partnerId);
            
            if (partner != null && partner.isOnline()) {
                ItemStack newBook = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta newMeta = (BookMeta) newBook.getItemMeta();
                
                newMeta.setTitle("§8暗网通讯录");
                newMeta.setAuthor("匿名中介");
                newMeta.setPages(event.getNewBookMeta().getPages());
                
                newMeta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionIdStr);
                newMeta.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                
                newBook.setItemMeta(newMeta);
                
                partner.getInventory().addItem(newBook);
                partner.sendMessage("§8[通讯] §7你收到了新的通讯消息");
                
                player.sendMessage("§8[通讯] §7消息已发送");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c❌ 通讯失败");
        }
    }
}
