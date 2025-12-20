// EventManager.java
package com.yourname.eventpart1;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import com.yourname.creditplugin.CreditPlugin;
import com.yourname.creditplugin.CreditManager;
import java.util.*;

public class EventManager {
    
    private final Map<UUID, Long> taxCheckedPlayers = new HashMap<>();
    private final Set<UUID> taxSurveillancePlayers = new HashSet<>();
    private long lastTaxCheckTime = 0;
    private long socialPurificationEndTime = 0;
    private boolean isSocialPurificationActive = false;
    
    public EventManager() {
        loadData();
    }
    
    // 检查资源税
    public void checkResourceTax() {
        World world = Bukkit.getWorlds().get(0);
        long time = world.getTime();
        
        // 每天检查一次（游戏时间0 tick）
        if (time == 0) {
            long currentTime = System.currentTimeMillis();
            // 防止重复检查
            if (currentTime - lastTaxCheckTime < 23000) { // 23秒冷却
                return;
            }
            lastTaxCheckTime = currentTime;
            
            processResourceTax();
        }
    }
    
    // 处理资源税
    private void processResourceTax() {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        boolean autoStart = config.getBoolean("resource-tax.auto-start", false);
        
        if (!autoStart && !isResourceTaxActive()) {
            return;
        }
        
        Bukkit.broadcastMessage(ChatColor.YELLOW + "📊 基础生存资源税检查开始！");
        Bukkit.broadcastMessage(ChatColor.GRAY + "要求: 64个小麦 + 64个小麦种子");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerTax(player);
        }
        
        // 更新监管状态
        updateTaxSurveillance();
    }
    
    // 检查单个玩家的税收
    private void checkPlayerTax(Player player) {
        PlayerInventory inventory = player.getInventory();
        
        // 检查小麦数量
        int wheatCount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.WHEAT) {
                wheatCount += item.getAmount();
            }
        }
        
        // 检查小麦种子数量
        int seedCount = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.WHEAT_SEEDS) {
                seedCount += item.getAmount();
            }
        }
        
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) {
            player.sendMessage(ChatColor.RED + "❌ 信用点系统未找到，税收检查失败！");
            return;
        }
        
        if (wheatCount >= 64 && seedCount >= 64) {
            // 达标：获得4信用点，收走全部小麦和种子
            removeAllWheatAndSeeds(player);
            creditManager.addCredits(player, 4);
            player.sendMessage(ChatColor.GREEN + "✅ 你已成功缴纳资源税，获得4点信用点！");
            
            // 移除监管状态
            taxSurveillancePlayers.remove(player.getUniqueId());
        } else {
            // 未达标：扣除2信用点
            creditManager.removeCredits(player, 2);
            player.sendMessage(ChatColor.RED + "❌ 你未满足资源税要求，扣除2点信用点！");
            player.sendMessage(ChatColor.YELLOW + "📦 当前: " + wheatCount + "小麦, " + seedCount + "种子");
            
            // 添加监管状态
            taxSurveillancePlayers.add(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "👁 你受到一级监管，直到信用点大于6！");
        }
        
        taxCheckedPlayers.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    // 移除所有小麦和种子
    private void removeAllWheatAndSeeds(Player player) {
        PlayerInventory inventory = player.getInventory();
        
        // 移除小麦
        for (ItemStack item : new ArrayList<>(Arrays.asList(inventory.getContents()))) {
            if (item != null && item.getType() == org.bukkit.Material.WHEAT) {
                inventory.remove(item);
            }
        }
        
        // 移除小麦种子
        for (ItemStack item : new ArrayList<>(Arrays.asList(inventory.getContents()))) {
            if (item != null && item.getType() == org.bukkit.Material.WHEAT_SEEDS) {
                inventory.remove(item);
            }
        }
    }
    
    // 检查社会净化演习
    public void checkSocialPurification() {
        if (isSocialPurificationActive && System.currentTimeMillis() > socialPurificationEndTime) {
            stopSocialPurification();
        }
    }
    
    // 开始社会净化演习
    public void startSocialPurification() {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        int durationDays = config.getInt("social-purification.duration-days", 3);
        
        isSocialPurificationActive = true;
        socialPurificationEndTime = System.currentTimeMillis() + (durationDays * 20 * 60 * 1000); // 3个游戏日
        
        Bukkit.broadcastMessage(ChatColor.RED + "⚔️ 社会净化演习开始！");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "⏰ 持续时间: " + durationDays + "个游戏日");
        Bukkit.broadcastMessage(ChatColor.GOLD + "💰 效果: 杀人将分走受害者50%信用点！");
    }
    
    // 停止社会净化演习
    public void stopSocialPurification() {
        isSocialPurificationActive = false;
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ 社会净化演习已结束！");
    }
    
    // 处理社会净化演习的杀人事件
    public void handleSocialPurificationKill(Player killer, Player victim) {
        if (!isSocialPurificationActive) return;
        
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) return;
        
        int victimCredits = creditManager.getCredits(victim);
        if (victimCredits <= 0) return;
        
        // 计算50%信用点（向下取整）
        int stolenCredits = victimCredits / 2;
        
        if (stolenCredits > 0) {
            // 从受害者扣除，给予杀人者
            creditManager.removeCredits(victim, stolenCredits);
            creditManager.addCredits(killer, stolenCredits);
            
            killer.sendMessage(ChatColor.GOLD + "⚡ 社会净化演习: 你抢夺了 " + victim.getName() + " 的 " + stolenCredits + " 点信用点！");
            victim.sendMessage(ChatColor.RED + "💸 社会净化演习: 你被 " + killer.getName() + " 抢走了 " + stolenCredits + " 点信用点！");
        }
    }
    
    // 更新税收监管状态
    private void updateTaxSurveillance() {
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) return;
        
        Iterator<UUID> iterator = taxSurveillancePlayers.iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            Player player = Bukkit.getPlayer(playerId);
            
            if (player != null && player.isOnline()) {
                int credits = creditManager.getCredits(player);
                if (credits > 6) {
                    // 信用点大于6，解除监管
                    iterator.remove();
                    player.sendMessage(ChatColor.GREEN + "✅ 你已解除资源税监管！");
                }
            } else {
                // 玩家离线，移除监管
                iterator.remove();
            }
        }
    }
    
    // 检查玩家是否处于税收监管
    public boolean isPlayerUnderTaxSurveillance(Player player) {
        return taxSurveillancePlayers.contains(player.getUniqueId());
    }
    
    // 获取信用点管理器
    private CreditManager getCreditManager() {
        try {
            Plugin creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin instanceof CreditPlugin) {
                return ((CreditPlugin) creditPlugin).getCreditManager();
            }
        } catch (Exception e) {
            EventPart1.getInstance().getLogger().warning("无法获取信用点管理器: " + e.getMessage());
        }
        return null;
    }
    
    public boolean isSocialPurificationActive() {
        return isSocialPurificationActive;
    }
    
    public boolean isResourceTaxActive() {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        return config.getBoolean("resource-tax.active", false);
    }
    
    public void setResourceTaxActive(boolean active) {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        config.set("resource-tax.active", active);
        EventPart1.getInstance().saveConfig();
    }
    
    public long getSocialPurificationTimeLeft() {
        if (!isSocialPurificationActive) return 0;
        return Math.max(0, socialPurificationEndTime - System.currentTimeMillis());
    }
    
    // 数据保存和加载
    private void loadData() {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        
        // 加载社会净化演习状态
        isSocialPurificationActive = config.getBoolean("social-purification.active", false);
        socialPurificationEndTime = config.getLong("social-purification.end-time", 0);
        
        // 加载税收监管玩家
        if (config.contains("tax-surveillance-players")) {
            for (String uuidStr : config.getStringList("tax-surveillance-players")) {
                try {
                    taxSurveillancePlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    // 忽略无效UUID
                }
            }
        }
    }
    
    public void saveAllData() {
        FileConfiguration config = EventPart1.getInstance().getConfig();
        
        // 保存社会净化演习状态
        config.set("social-purification.active", isSocialPurificationActive);
        config.set("social-purification.end-time", socialPurificationEndTime);
        
        // 保存税收监管玩家
        List<String> uuidList = new ArrayList<>();
        for (UUID uuid : taxSurveillancePlayers) {
            uuidList.add(uuid.toString());
        }
        config.set("tax-surveillance-players", uuidList);
        
        EventPart1.getInstance().saveConfig();
    }
}
