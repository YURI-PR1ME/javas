package com.yourname.creditexchange;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import com.yourname.creditplugin.CreditPlugin;
import com.yourname.creditplugin.CreditManager;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ExchangeManager {
    
    private final CreditManager creditManager;
    private final CreditExchangePlugin plugin;
    
    // 食物兑换配置
    private final Map<Material, FoodExchangeRule> foodRules = new HashMap<>();
    // 珍贵物品兑换配置
    private final Map<Material, PreciousExchangeRule> preciousRules = new HashMap<>();
    
    public ExchangeManager() {
        this.plugin = CreditExchangePlugin.getInstance();
        this.creditManager = CreditPlugin.getInstance().getCreditManager();
        initializeRules();
    }
    
    private void initializeRules() {
        // 初始化食物兑换规则
        foodRules.put(Material.COOKED_BEEF, new FoodExchangeRule(Material.COOKED_BEEF, 64, 5, 2));
        foodRules.put(Material.COOKED_PORKCHOP, new FoodExchangeRule(Material.COOKED_PORKCHOP, 64, 5, 2));
        foodRules.put(Material.COOKED_CHICKEN, new FoodExchangeRule(Material.COOKED_CHICKEN, 64, 5, 2));
        foodRules.put(Material.BREAD, new FoodExchangeRule(Material.BREAD, 64, 5, 2));
        foodRules.put(Material.GOLDEN_CARROT, new FoodExchangeRule(Material.GOLDEN_CARROT, 32, 5, 2));
        foodRules.put(Material.GOLDEN_APPLE, new FoodExchangeRule(Material.GOLDEN_APPLE, 16, 5, 2));
        foodRules.put(Material.ENCHANTED_GOLDEN_APPLE, new FoodExchangeRule(Material.ENCHANTED_GOLDEN_APPLE, 16, 5, 2));
        
        // 初始化珍贵物品兑换规则
        preciousRules.put(Material.IRON_INGOT, new PreciousExchangeRule(Material.IRON_INGOT, 64, 5, 2, "iron"));
        preciousRules.put(Material.GOLD_INGOT, new PreciousExchangeRule(Material.GOLD_INGOT, 32, 5, 2, "gold"));
        preciousRules.put(Material.DIAMOND, new PreciousExchangeRule(Material.DIAMOND, 8, 5, 2, "diamond"));
        preciousRules.put(Material.ENDER_PEARL, new PreciousExchangeRule(Material.ENDER_PEARL, 16, 10, 3, "ender_pearl"));
        preciousRules.put(Material.TOTEM_OF_UNDYING, new PreciousExchangeRule(Material.TOTEM_OF_UNDYING, 1, 2, 1, "totem"));
        preciousRules.put(Material.BLAZE_ROD, new PreciousExchangeRule(Material.BLAZE_ROD, 16, 10, 2, "blaze_rod"));
        preciousRules.put(Material.GLOWSTONE_DUST, new PreciousExchangeRule(Material.GLOWSTONE_DUST, 32, 10, 1, "glowstone_dust"));
    }
    
    public ExchangeResult exchangeItems(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return new ExchangeResult(false, "❌ 请手持要兑换的物品！");
        }
        
        Material material = itemInHand.getType();
        int amount = itemInHand.getAmount();
        
        // 检查是否是食物
        if (foodRules.containsKey(material)) {
            return exchangeFood(player, material, amount);
        }
        
        // 检查是否是珍贵物品
        if (preciousRules.containsKey(material)) {
            return exchangePrecious(player, material, amount);
        }
        
        return new ExchangeResult(false, "❌ 该物品不可兑换信用点！");
    }
    
    private ExchangeResult exchangeFood(Player player, Material material, int amount) {
        FoodExchangeRule rule = foodRules.get(material);
        
        // 检查数量是否足够
        if (amount < rule.getRequiredAmount()) {
            return new ExchangeResult(false, "❌ 需要至少 " + rule.getRequiredAmount() + " 个" + getItemDisplayName(material) + "！");
        }
        
        // 检查玩家信用点是否小于0，如果是则取消所有冷却
        boolean negativeCredits = creditManager.getCredits(player) < 0;
        
        // 如果不是负信用点，检查冷却时间
        if (!negativeCredits && isFoodCooldownActive(player)) {
            long remainingTime = getFoodCooldownRemaining(player);
            return new ExchangeResult(false, "❌ 食物兑换冷却中，剩余: " + formatTime(remainingTime));
        }
        
        // 如果不是负信用点，检查今日兑换上限
        if (!negativeCredits) {
            int todayExchanged = getTodayFoodExchange(player);
            if (todayExchanged >= 100) {
                return new ExchangeResult(false, "❌ 今日食物兑换已达上限（100点）！");
            }
            
            int exchangePoints = rule.getPoints();
            if (todayExchanged + exchangePoints > 100) {
                exchangePoints = 100 - todayExchanged;
                if (exchangePoints <= 0) {
                    return new ExchangeResult(false, "❌ 今日食物兑换已达上限（100点）！");
                }
            }
            
            // 执行兑换（非负信用点）
            removeItemsFromHand(player, rule.getRequiredAmount());
            creditManager.addCredits(player, exchangePoints);
            updateFoodCooldown(player);
            updateTodayFoodExchange(player, exchangePoints);
            
            String message = "✅ 成功兑换 " + exchangePoints + " 点信用点！";
            if (negativeCredits) {
                int newCredits = creditManager.getCredits(player);
                if (newCredits >= 0) {
                    message += " 你的信用点已恢复正数！";
                }
            }
            
            return new ExchangeResult(true, message);
        } else {
            // 负信用点状态：无冷却无上限
            int exchangePoints = rule.getPoints();
            
            // 执行兑换（负信用点）
            removeItemsFromHand(player, rule.getRequiredAmount());
            creditManager.addCredits(player, exchangePoints);
            
            String message = "✅ 成功兑换 " + exchangePoints + " 点信用点！";
            int newCredits = creditManager.getCredits(player);
            if (newCredits >= 0) {
                message += " 你的信用点已恢复正数！";
            }
            
            return new ExchangeResult(true, message);
        }
    }
    
    private ExchangeResult exchangePrecious(Player player, Material material, int amount) {
        PreciousExchangeRule rule = preciousRules.get(material);
        
        // 检查数量是否足够
        if (amount < rule.getRequiredAmount()) {
            return new ExchangeResult(false, "❌ 需要至少 " + rule.getRequiredAmount() + " 个" + getItemDisplayName(material) + "！");
        }
        
        // 检查玩家信用点是否小于0，如果是则取消所有冷却
        boolean negativeCredits = creditManager.getCredits(player) < 0;
        
        // 如果不是负信用点，检查冷却时间
        if (!negativeCredits && isPreciousCooldownActive(player, rule.getType())) {
            long remainingTime = getPreciousCooldownRemaining(player, rule.getType());
            return new ExchangeResult(false, "❌ " + getItemDisplayName(material) + " 兑换冷却中，剩余: " + formatTime(remainingTime));
        }
        
        // 执行兑换
        removeItemsFromHand(player, rule.getRequiredAmount());
        creditManager.addCredits(player, rule.getPoints());
        
        // 如果不是负信用点，更新冷却时间
        if (!negativeCredits) {
            updatePreciousCooldown(player, rule.getType(), rule.getCooldownDays());
        }
        
        String message = "✅ 成功兑换 " + rule.getPoints() + " 点信用点！";
        if (negativeCredits) {
            int newCredits = creditManager.getCredits(player);
            if (newCredits >= 0) {
                message += " 你的信用点已恢复正数！";
            }
        }
        
        return new ExchangeResult(true, message);
    }
    
    private void removeItemsFromHand(Player player, int amount) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        int newAmount = itemInHand.getAmount() - amount;
        
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            itemInHand.setAmount(newAmount);
        }
    }
    
    private boolean isFoodCooldownActive(Player player) {
        long lastExchange = plugin.getDataConfig().getLong("food_cooldown." + player.getUniqueId(), 0);
        if (lastExchange == 0) return false;
        
        long currentTime = System.currentTimeMillis();
        long cooldownTime = TimeUnit.DAYS.toMillis(2);
        
        return currentTime - lastExchange < cooldownTime;
    }
    
    private long getFoodCooldownRemaining(Player player) {
        long lastExchange = plugin.getDataConfig().getLong("food_cooldown." + player.getUniqueId(), 0);
        long currentTime = System.currentTimeMillis();
        long cooldownTime = TimeUnit.DAYS.toMillis(2);
        
        return cooldownTime - (currentTime - lastExchange);
    }
    
    private void updateFoodCooldown(Player player) {
        plugin.getDataConfig().set("food_cooldown." + player.getUniqueId(), System.currentTimeMillis());
        plugin.saveData();
    }
    
    private int getTodayFoodExchange(Player player) {
        long lastReset = plugin.getDataConfig().getLong("food_reset." + player.getUniqueId(), 0);
        long currentTime = System.currentTimeMillis();
        
        // 如果超过2天，重置计数
        if (currentTime - lastReset >= TimeUnit.DAYS.toMillis(2)) {
            plugin.getDataConfig().set("food_exchange." + player.getUniqueId(), 0);
            plugin.getDataConfig().set("food_reset." + player.getUniqueId(), currentTime);
            plugin.saveData();
            return 0;
        }
        
        return plugin.getDataConfig().getInt("food_exchange." + player.getUniqueId(), 0);
    }
    
    private void updateTodayFoodExchange(Player player, int points) {
        int current = getTodayFoodExchange(player);
        plugin.getDataConfig().set("food_exchange." + player.getUniqueId(), current + points);
        plugin.saveData();
    }
    
    private boolean isPreciousCooldownActive(Player player, String itemType) {
        long lastExchange = plugin.getDataConfig().getLong("precious_cooldown." + player.getUniqueId() + "." + itemType, 0);
        if (lastExchange == 0) return false;
        
        long currentTime = System.currentTimeMillis();
        long cooldownTime = TimeUnit.DAYS.toMillis(getPreciousCooldownDays(itemType));
        
        return currentTime - lastExchange < cooldownTime;
    }
    
    private long getPreciousCooldownRemaining(Player player, String itemType) {
        long lastExchange = plugin.getDataConfig().getLong("precious_cooldown." + player.getUniqueId() + "." + itemType, 0);
        long currentTime = System.currentTimeMillis();
        long cooldownTime = TimeUnit.DAYS.toMillis(getPreciousCooldownDays(itemType));
        
        return cooldownTime - (currentTime - lastExchange);
    }
    
    private void updatePreciousCooldown(Player player, String itemType, int cooldownDays) {
        plugin.getDataConfig().set("precious_cooldown." + player.getUniqueId() + "." + itemType, System.currentTimeMillis());
        plugin.saveData();
    }
    
    private int getPreciousCooldownDays(String itemType) {
        switch (itemType) {
            case "ender_pearl": return 3;
            case "totem": return 1;
            case "blaze_rod": return 2;
            case "glowstone_dust": return 1;
            default: return 2; // iron, gold, diamond
        }
    }
    
    private String formatTime(long milliseconds) {
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;
        
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }
    
    private String getItemDisplayName(Material material) {
        switch (material) {
            case COOKED_BEEF: return "牛排";
            case COOKED_PORKCHOP: return "猪排";
            case COOKED_CHICKEN: return "鸡肉";
            case BREAD: return "面包";
            case GOLDEN_CARROT: return "金胡萝卜";
            case GOLDEN_APPLE: return "金苹果";
            case ENCHANTED_GOLDEN_APPLE: return "附魔金苹果";
            case IRON_INGOT: return "铁锭";
            case GOLD_INGOT: return "金锭";
            case DIAMOND: return "钻石";
            case ENDER_PEARL: return "末影珍珠";
            case TOTEM_OF_UNDYING: return "不死图腾";
            case BLAZE_ROD: return "烈焰棒";
            case GLOWSTONE_DUST: return "荧石粉";
            default: return material.toString();
        }
    }
    
    // 获取玩家当前兑换状态信息
    public String getExchangeStatus(Player player) {
        int credits = creditManager.getCredits(player);
        StringBuilder status = new StringBuilder();
        
        status.append(ChatColor.GOLD + "=== 你的兑换状态 ===\n");
        status.append(ChatColor.WHITE + "当前信用点: " + 
                     (credits < 0 ? ChatColor.RED : ChatColor.GREEN) + credits + "\n");
        
        if (credits < 0) {
            status.append(ChatColor.GREEN + "✨ 负信用点状态：所有兑换冷却已取消！\n");
            status.append(ChatColor.YELLOW + "💡 你可以无限制兑换物品来恢复信用点\n");
        } else {
            // 食物兑换状态
            int foodExchanged = getTodayFoodExchange(player);
            status.append(ChatColor.WHITE + "今日食物兑换: " + 
                         (foodExchanged >= 100 ? ChatColor.RED : ChatColor.GREEN) + 
                         foodExchanged + "/100 点\n");
            
            if (isFoodCooldownActive(player)) {
                long remaining = getFoodCooldownRemaining(player);
                status.append(ChatColor.RED + "⏰ 食物兑换冷却中，剩余: " + formatTime(remaining) + "\n");
            } else {
                status.append(ChatColor.GREEN + "✅ 食物兑换可用\n");
            }
            
            // 珍贵物品冷却状态
            status.append(ChatColor.WHITE + "珍贵物品冷却状态:\n");
            for (PreciousExchangeRule rule : preciousRules.values()) {
                if (isPreciousCooldownActive(player, rule.getType())) {
                    long remaining = getPreciousCooldownRemaining(player, rule.getType());
                    status.append(ChatColor.RED + "  • " + getItemDisplayName(rule.getMaterial()) + 
                                 ": " + formatTime(remaining) + "\n");
                } else {
                    status.append(ChatColor.GREEN + "  • " + getItemDisplayName(rule.getMaterial()) + 
                                 ": 可用\n");
                }
            }
        }
        
        return status.toString();
    }
    
    public Map<Material, FoodExchangeRule> getFoodRules() {
        return foodRules;
    }
    
    public Map<Material, PreciousExchangeRule> getPreciousRules() {
        return preciousRules;
    }
    
    // 内部类：食物兑换规则
    public static class FoodExchangeRule {
        private final Material material;
        private final int requiredAmount;
        private final int points;
        private final int cooldownDays;
        
        public FoodExchangeRule(Material material, int requiredAmount, int points, int cooldownDays) {
            this.material = material;
            this.requiredAmount = requiredAmount;
            this.points = points;
            this.cooldownDays = cooldownDays;
        }
        
        public Material getMaterial() { return material; }
        public int getRequiredAmount() { return requiredAmount; }
        public int getPoints() { return points; }
        public int getCooldownDays() { return cooldownDays; }
    }
    
    // 内部类：珍贵物品兑换规则
    public static class PreciousExchangeRule {
        private final Material material;
        private final int requiredAmount;
        private final int points;
        private final int cooldownDays;
        private final String type;
        
        public PreciousExchangeRule(Material material, int requiredAmount, int points, int cooldownDays, String type) {
            this.material = material;
            this.requiredAmount = requiredAmount;
            this.points = points;
            this.cooldownDays = cooldownDays;
            this.type = type;
        }
        
        public Material getMaterial() { return material; }
        public int getRequiredAmount() { return requiredAmount; }
        public int getPoints() { return points; }
        public int getCooldownDays() { return cooldownDays; }
        public String getType() { return type; }
    }
    
    // 内部类：兑换结果
    public static class ExchangeResult {
        private final boolean success;
        private final String message;
        
        public ExchangeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
