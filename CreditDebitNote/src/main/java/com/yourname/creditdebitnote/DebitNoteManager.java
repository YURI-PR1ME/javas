package com.yourname.creditdebitnote;

import com.yourname.creditplugin.CreditPlugin;
import com.yourname.creditplugin.CreditManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

public class DebitNoteManager {
    
    private final NamespacedKey debitNoteKey;
    private final NamespacedKey amountKey;
    private final NamespacedKey issuerKey;
    private final NamespacedKey issuerNameKey;
    private final Map<UUID, Boolean> waitingForCustomAmount = new HashMap<>();
    
    public DebitNoteManager() {
        CreditDebitNote plugin = CreditDebitNote.getInstance();
        this.debitNoteKey = new NamespacedKey(plugin, "debit_note");
        this.amountKey = new NamespacedKey(plugin, "debit_amount");
        this.issuerKey = new NamespacedKey(plugin, "debit_issuer");
        this.issuerNameKey = new NamespacedKey(plugin, "debit_issuer_name");
    }
    
    // 获取主插件的CreditManager
    private CreditManager getCreditManager() {
        try {
            return CreditPlugin.getInstance().getCreditManager();
        } catch (Exception e) {
            Bukkit.getLogger().severe("无法获取CreditManager: " + e.getMessage());
            return null;
        }
    }
    
    // 设置等待自定义金额状态
    public void setWaitingForCustomAmount(Player player) {
        waitingForCustomAmount.put(player.getUniqueId(), true);
    }
    
    // 检查是否在等待自定义金额
    public boolean isWaitingForCustomAmount(Player player) {
        return waitingForCustomAmount.getOrDefault(player.getUniqueId(), false);
    }
    
    // 处理自定义金额输入
    public boolean handleCustomAmountInput(Player player, String input) {
        waitingForCustomAmount.remove(player.getUniqueId());
        
        try {
            int amount = Integer.parseInt(input);
            
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "❌ 金额必须大于0！");
                return false;
            }
            
            if (amount > 10000) {
                player.sendMessage(ChatColor.RED + "❌ 金额不能超过10000点！");
                return false;
            }
            
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (!isBlankDebitNote(mainHand)) {
                player.sendMessage(ChatColor.RED + "❌ 请手持空白借记单进行填写");
                return false;
            }
            
            return fillDebitNote(player, mainHand, amount);
            
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "❌ 请输入有效的数字！");
            return false;
        }
    }
    
    // 创建空白借记单
    public ItemStack createBlankDebitNote() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6空白信用点借记单");
        meta.setLore(Arrays.asList(
            "§7右键打开填写界面",
            "§e填写要存储的信用点数量",
            "§c填写后将从你的账户扣除相应点数"
        ));
        
        // 标记为空白借记单
        meta.getPersistentDataContainer().set(debitNoteKey, PersistentDataType.STRING, "blank");
        
        item.setItemMeta(meta);
        return item;
    }
    
    // 创建已填写的借记单
    public ItemStack createFilledDebitNote(Player issuer, int amount) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§a信用点借记单 §7[" + amount + "点]");
        meta.setLore(Arrays.asList(
            "§7签发者: " + issuer.getName(),
            "§7面额: §e" + amount + " 信用点",
            "",
            "§a手持此借记单下蹲右键",
            "§a即可将信用点拨到你的账户"
        ));
        
        // 存储借记单数据
        meta.getPersistentDataContainer().set(debitNoteKey, PersistentDataType.STRING, "filled");
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        meta.getPersistentDataContainer().set(issuerKey, PersistentDataType.STRING, issuer.getUniqueId().toString());
        meta.getPersistentDataContainer().set(issuerNameKey, PersistentDataType.STRING, issuer.getName());
        
        item.setItemMeta(meta);
        return item;
    }
    
    // 检查是否是空白借记单
    public boolean isBlankDebitNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        return meta.getPersistentDataContainer().has(debitNoteKey, PersistentDataType.STRING) &&
               "blank".equals(meta.getPersistentDataContainer().get(debitNoteKey, PersistentDataType.STRING));
    }
    
    // 检查是否是已填写的借记单
    public boolean isFilledDebitNote(ItemStack item) {
        if (item == null || item.getType() != Material.MAP) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        return meta.getPersistentDataContainer().has(debitNoteKey, PersistentDataType.STRING) &&
               "filled".equals(meta.getPersistentDataContainer().get(debitNoteKey, PersistentDataType.STRING));
    }
    
    // 获取借记单数据
    public DebitNoteData getDebitNoteData(ItemStack item) {
        if (!isFilledDebitNote(item)) return null;
        
        ItemMeta meta = item.getItemMeta();
        Integer amount = meta.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
        String issuerUuidString = meta.getPersistentDataContainer().get(issuerKey, PersistentDataType.STRING);
        String issuerName = meta.getPersistentDataContainer().get(issuerNameKey, PersistentDataType.STRING);
        
        if (amount == null || issuerUuidString == null) return null;
        
        try {
            UUID issuerUuid = UUID.fromString(issuerUuidString);
            return new DebitNoteData(amount, issuerUuid, issuerName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    // 兑现借记单
    public boolean redeemDebitNote(Player redeemer, ItemStack debitNote) {
        DebitNoteData data = getDebitNoteData(debitNote);
        if (data == null) {
            redeemer.sendMessage(ChatColor.RED + "❌ 无效的借记单！");
            return false;
        }
        
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) {
            redeemer.sendMessage(ChatColor.RED + "❌ 系统错误：无法连接到信用点系统");
            return false;
        }
        
        try {
            // 给兑现者添加信用点
            creditManager.addCredits(redeemer, data.getAmount());
            
            redeemer.sendMessage(ChatColor.GREEN + "✅ 你成功兑现了 " + data.getAmount() + " 点信用点！");
            redeemer.sendMessage(ChatColor.GRAY + "签发者: " + data.getIssuerName());
            
            return true;
            
        } catch (Exception e) {
            redeemer.sendMessage(ChatColor.RED + "❌ 兑现失败：系统错误");
            e.printStackTrace();
            return false;
        }
    }
    
    // 填写借记单
    public boolean fillDebitNote(Player issuer, ItemStack blankNote, int amount) {
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) {
            issuer.sendMessage(ChatColor.RED + "❌ 系统错误：无法连接到信用点系统");
            return false;
        }
        
        try {
            // 检查信用点是否足够
            int currentCredits = creditManager.getCredits(issuer);
            
            if (currentCredits < amount) {
                issuer.sendMessage(ChatColor.RED + "❌ 信用点不足！你只有 " + currentCredits + " 点");
                return false;
            }
            
            // 扣除信用点
            boolean success = creditManager.removeCredits(issuer, amount);
            
            if (!success) {
                issuer.sendMessage(ChatColor.RED + "❌ 扣除信用点失败！");
                return false;
            }
            
            // 将空白借记单替换为已填写的借记单
            ItemStack filledNote = createFilledDebitNote(issuer, amount);
            
            if (blankNote.getAmount() > 1) {
                blankNote.setAmount(blankNote.getAmount() - 1);
                
                // 检查背包空间
                if (issuer.getInventory().firstEmpty() == -1) {
                    issuer.getWorld().dropItemNaturally(issuer.getLocation(), filledNote);
                    issuer.sendMessage(ChatColor.YELLOW + "💡 背包已满，借记单已掉落在地面上");
                } else {
                    issuer.getInventory().addItem(filledNote);
                }
            } else {
                issuer.getInventory().setItemInMainHand(filledNote);
            }
            
            issuer.sendMessage(ChatColor.GREEN + "✅ 你成功填写了 " + amount + " 点信用点借记单");
            issuer.sendMessage(ChatColor.YELLOW + "💡 现在你可以将借记单交给其他玩家兑现");
            
            return true;
            
        } catch (Exception e) {
            issuer.sendMessage(ChatColor.RED + "❌ 填写失败：系统错误");
            e.printStackTrace();
            return false;
        }
    }
    
    // 注册合成配方
    public void registerRecipes() {
        // 空白借记单合成配方
        ItemStack blankNote = createBlankDebitNote();
        NamespacedKey blankNoteKey = new NamespacedKey(CreditDebitNote.getInstance(), "blank_debit_note");
        
        ShapedRecipe recipe = new ShapedRecipe(blankNoteKey, blankNote);
        recipe.shape(" P ", "PEP", " P ");
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('E', Material.EMERALD);
        
        Bukkit.addRecipe(recipe);
    }
    
    // 借记单数据类
    public static class DebitNoteData {
        private final int amount;
        private final UUID issuerUuid;
        private final String issuerName;
        
        public DebitNoteData(int amount, UUID issuerUuid, String issuerName) {
            this.amount = amount;
            this.issuerUuid = issuerUuid;
            this.issuerName = issuerName;
        }
        
        public int getAmount() { return amount; }
        public UUID getIssuerUuid() { return issuerUuid; }
        public String getIssuerName() { return issuerName; }
    }
}
