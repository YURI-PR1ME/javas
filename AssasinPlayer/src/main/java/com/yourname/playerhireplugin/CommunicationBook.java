package com.yourname.playerhireplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

public class CommunicationBook {
    
    public static ItemStack createCommunicationBook(UUID sessionId, UUID otherPartyId, boolean isAssassin) {
        // 仍然使用 WRITTEN_BOOK，但只作为会话ID的载体
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        String role = isAssassin ? "刺客" : "雇主";
        String otherRole = isAssassin ? "雇主" : "刺客";
        Player otherPlayer = Bukkit.getPlayer(otherPartyId);
        String otherName = otherPlayer != null ? otherPlayer.getName() : "未知玩家";
        
        meta.setTitle("§6雇佣通讯书");
        meta.setAuthor("雇佣市场系统");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        
        // 设置书的内容 - 主要显示会话ID和使用说明
        java.util.List<String> pages = new java.util.ArrayList<>();
        
        // 第一页：基本信息
        pages.add("§l雇佣通讯书\n\n" +
                 "§0会话ID:\n§7" + sessionId.toString() + "\n\n" +
                 "§0你的身份:\n§7" + role + "\n\n" +
                 "§0对方身份:\n§7" + otherRole + "\n\n" +
                 "§0对方玩家:\n§7" + otherName);
        
        // 第二页：使用说明
        pages.add("§l使用说明\n\n" +
                 "§7• 本书包含会话ID\n" +
                 "§7• 使用密电指令沟通\n" +
                 "§7• 保持匿名和安全\n\n" +
                 "§0密电指令:\n" +
                 "§7/hire message <ID> <消息>\n\n" +
                 "§0示例:\n" +
                 "§7/hire message " + sessionId.toString().substring(0, 8) + " 你好");
        
        // 第三页：任务流程
        pages.add("§l任务流程\n\n" +
                 "§71. 确定目标玩家\n" +
                 "§72. 使用密电协商价格\n" +
                 "§73. 刺客使用/hire offer报价\n" +
                 "§74. 买家使用/hire accept接受\n" +
                 "§75. 刺客执行暗杀任务\n" +
                 "§76. 结算信用点");
        
        // 第四页：注意事项
        pages.add("§l注意事项\n\n" +
                 "§7• 保持专业态度\n" +
                 "§7• 明确任务要求\n" +
                 "§7• 及时沟通进展\n" +
                 "§7• 遵守市场规则\n" +
                 "§7• 尊重对方隐私\n\n" +
                 "§0会话ID:\n" +
                 "§8" + sessionId.toString().substring(0, 8) + "...");
        
        meta.setPages(pages);
        
        // 添加NBT标签
        NamespacedKey sessionKey = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_session");
        meta.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, sessionId.toString());
        
        NamespacedKey partyKey = new NamespacedKey(PlayerHirePlugin.getInstance(), "other_party");
        meta.getPersistentDataContainer().set(partyKey, PersistentDataType.STRING, otherPartyId.toString());
        
        NamespacedKey roleKey = new NamespacedKey(PlayerHirePlugin.getInstance(), "user_role");
        meta.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, isAssassin ? "assassin" : "buyer");
        
        book.setItemMeta(meta);
        return book;
    }
    
    // 其他方法保持不变...
    public static boolean isCommunicationBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_session");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }
    
    public static UUID getSessionId(ItemStack book) {
        if (!isCommunicationBook(book)) return null;
        
        BookMeta meta = (BookMeta) book.getItemMeta();
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "hire_session");
        
        try {
            return UUID.fromString(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }
    
    public static UUID getOtherPartyId(ItemStack book) {
        if (!isCommunicationBook(book)) return null;
        
        BookMeta meta = (BookMeta) book.getItemMeta();
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "other_party");
        
        try {
            return UUID.fromString(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean isUserAssassin(ItemStack book) {
        if (!isCommunicationBook(book)) return false;
        
        BookMeta meta = (BookMeta) book.getItemMeta();
        NamespacedKey key = new NamespacedKey(PlayerHirePlugin.getInstance(), "user_role");
        
        String role = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return "assassin".equals(role);
    }
}
