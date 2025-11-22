package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CreditManager {
    
    private final Map<UUID, Integer> playerCredits = new HashMap<>();
    private boolean isKillingDay = false;
    private long lastKillingDayCheck = 0;
    
    public CreditManager() {
        loadAllData();
    }
    
    // 初始化玩家信用点
    public void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerCredits.containsKey(uuid)) {
            playerCredits.put(uuid, 11); // 每人进服务器自带5个点数
            giveCreditBook(player);
            savePlayerData(player);
        }
        
        // 同步游戏状态和信用点状态
        syncPlayerState(player);
    }
    
    // 同步玩家状态：确保游戏模式和信用点一致
    public void syncPlayerState(Player player) {
        int credits = getCredits(player);
        
        // 如果信用点为0且玩家是生存模式，强制杀死并设为观察者
        if (credits <= 0 && player.getGameMode() == GameMode.SURVIVAL && player.isOnline()) {
            player.setHealth(0);
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                if (player.isOnline() && player.isDead()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(ChatColor.RED + "💀 你的信用点已归零！");
                    player.sendMessage(ChatColor.RED + "👻 你已成为观察者，需要他人用信用点复活你");
                }
            }, 1L);
        }
        
        // 如果玩家突然活了（比如管理员复活），给予2点并复活
        if (credits <= 0 && player.getGameMode() == GameMode.SURVIVAL && player.isOnline()) {
            setCredits(player, 2);
            player.sendMessage(ChatColor.GREEN + "✨ 你已被复活，获得2点信用点！");
            giveCreditBook(player);
        }
    }
    
    // 处理玩家死亡
    public void handlePlayerDeath(Player player) {
        int credits = getCredits(player);
        
        // 死前判定：如果点数>=6，直接复活并扣除6点
        if (credits >= 6) {
            // 直接复活，扣除6点
            removeCredits(player, 6);
            
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage(ChatColor.GREEN + "✨ 你消耗6点信用点自动复活了！");
                }
            }, 1L);
        } else {
            // 点数不足，进入观察者模式
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                if (player.isOnline() && player.isDead()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(ChatColor.RED + "👻 你已死亡，需要他人复活你");
                    
                    // 计算复活花费：6 - 当前点数
                    int reviveCost = 6 - credits;
                    player.sendMessage(ChatColor.YELLOW + "💡 复活需要花费 " + reviveCost + " 点信用点");
                }
            }, 1L);
        }
    }
    
    // 检查杀人日（基于游戏时间和概率）
    public void checkKillingDay() {
        // 防止频繁检查
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastKillingDayCheck < 60000) { // 每分钟最多检查一次
            return;
        }
        lastKillingDayCheck = currentTime;
        
        // 获取主世界时间
        World world = Bukkit.getWorlds().get(0);
        long time = world.getTime();
        
        // 检查是否是日出时间（游戏时间0tick）
        if (time == 0) {
            double killingDayChance = CreditPlugin.getInstance().getConfig().getDouble("killing-day-chance", 0.3);
            
            // 随机决定是否是杀人日
            if (ThreadLocalRandom.current().nextDouble() < killingDayChance) {
                startKillingDay();
            } else if (isKillingDay) {
                stopKillingDay();
            }
        }
    }
    
    // 手动开启杀人日
    public void startKillingDay() {
        if (!isKillingDay) {
            isKillingDay = true;
            Bukkit.broadcastMessage(ChatColor.RED + "⚔️ 杀人日已开启！今天杀人可以抢夺对方所有信用点！");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "⚠ 注意：杀人仍然会扣除2点信用点！");
        }
    }
    
    // 手动关闭杀人日
    public void stopKillingDay() {
        if (isKillingDay) {
            isKillingDay = false;
            Bukkit.broadcastMessage(ChatColor.GREEN + "✅ 杀人日已结束，恢复正常规则。");
        }
    }
    
    // 设置杀人日状态
    public void setKillingDay(boolean state) {
        if (state) {
            startKillingDay();
        } else {
            stopKillingDay();
        }
    }
    
    public boolean isKillingDay() {
        return isKillingDay;
    }
    
    // 给予信用点书
    public void giveCreditBook(Player player) {
        ItemStack creditBook = createCreditBook(player);
        
        // 尝试添加到背包，如果满了就掉落
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(creditBook);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), creditBook);
        }
        
        updateBookDisplay(player, creditBook);
    }
    
    // 创建信用点书
    private ItemStack createCreditBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        // 设置书的基本信息
        meta.setTitle("公民信用点证书");
        meta.setAuthor("信用管理局");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        
        // 设置书的内容
        List<String> pages = new ArrayList<>();
        pages.add(ChatColor.DARK_BLUE + "公民信用点证书\n\n" +
                 ChatColor.BLACK + "持有者: " + player.getName() + "\n" +
                 ChatColor.BLACK + "当前点数: " + getCredits(player) + "\n\n" +
                 ChatColor.RED + "警告: \n" +
                 ChatColor.BLACK + "信用点为0时将死亡!");
        
        pages.add(ChatColor.DARK_BLUE + "使用说明:\n\n" +
                 ChatColor.BLACK + "• 潜行+右键玩家交易\n" +
                 ChatColor.BLACK + "• 杀人扣除2点\n" +
                 ChatColor.BLACK + "• 杀人日可抢夺点数\n" +
                 ChatColor.BLACK + "• 复活需要6点");
        
        meta.setPages(pages);
        
        // 添加NBT标签标识这是信用点书
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "credit_book");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        // 添加死亡不掉落标签
        NamespacedKey keepKey = new NamespacedKey(CreditPlugin.getInstance(), "keep_on_death");
        meta.getPersistentDataContainer().set(keepKey, PersistentDataType.BYTE, (byte) 1);
        
        book.setItemMeta(meta);
        return book;
    }
    
    // 更新书显示
    public void updateBookDisplay(Player player, ItemStack book) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK) return;
        
        BookMeta meta = (BookMeta) book.getItemMeta();
        List<String> pages = new ArrayList<>();
        
        pages.add(ChatColor.DARK_BLUE + "公民信用点证书\n\n" +
                 ChatColor.BLACK + "持有者: " + player.getName() + "\n" +
                 ChatColor.BLACK + "当前点数: " + getCredits(player) + "\n\n" +
                 ChatColor.RED + "警告: \n" +
                 ChatColor.BLACK + "信用点为0时将死亡!");
        
        pages.add(ChatColor.DARK_BLUE + "使用说明:\n\n" +
                 ChatColor.BLACK + "• 潜行+右键玩家交易\n" +
                 ChatColor.BLACK + "• 杀人扣除2点\n" +
                 ChatColor.BLACK + "• 杀人日可抢夺点数\n" +
                 ChatColor.BLACK + "• 复活需要6点");
        
        meta.setPages(pages);
        book.setItemMeta(meta);
    }
    
    // 获取信用点
    public int getCredits(Player player) {
        return playerCredits.getOrDefault(player.getUniqueId(), 0);
    }
    
    // 设置信用点
    public void setCredits(Player player, int credits) {
        UUID uuid = player.getUniqueId();
        playerCredits.put(uuid, Math.max(0, credits));
        updatePlayerBook(player);
        
        // 同步状态
        syncPlayerState(player);
        
        savePlayerData(player);
    }
    
    // 添加信用点
    public void addCredits(Player player, int amount) {
        setCredits(player, getCredits(player) + amount);
    }
    
    // 扣除信用点
    public boolean removeCredits(Player player, int amount) {
        int current = getCredits(player);
        if (current >= amount) {
            setCredits(player, current - amount);
            return true;
        } else {
            // 如果点数不足，直接设为0
            setCredits(player, 0);
            return false;
        }
    }
    
    // 处理杀人事件 - 点数在杀人后自动扣2
    public void handleKill(Player killer, Player victim) {
        int killerCredits = getCredits(killer);
        int victimCredits = getCredits(victim);
        
        // 杀人扣除2点
        if (!removeCredits(killer, 2)) {
            // 如果点数不足，直接设为0
            setCredits(killer, 0);
        }
        
        if (isKillingDay) {
            // 杀人日：抢夺所有点数
            if (victimCredits > 0) {
                addCredits(killer, victimCredits);
                setCredits(victim, 0);
                killer.sendMessage(ChatColor.GOLD + "⚡ 你抢夺了 " + victim.getName() + " 的 " + victimCredits + " 点信用点！");
            }
        }
        
        killer.sendMessage(ChatColor.RED + "⚠ 你因杀人被扣除2点信用点！");
        updatePlayerBook(killer);
        updatePlayerBook(victim);
        
        // 处理受害者死亡
        handlePlayerDeath(victim);
    }
    
    // 交易信用点 - 拿书下蹲右键别人支付1点
    public boolean transferCredits(Player from, Player to, int amount) {
        if (removeCredits(from, amount)) {
            addCredits(to, amount);
            from.sendMessage(ChatColor.GREEN + "✅ 你成功向 " + to.getName() + " 支付了 " + amount + " 点信用点");
            to.sendMessage(ChatColor.GREEN + "✅ 你收到了 " + from.getName() + " 支付的 " + amount + " 点信用点");
            updatePlayerBook(from);
            updatePlayerBook(to);
            return true;
        } else {
            from.sendMessage(ChatColor.RED + "❌ 信用点不足！");
            return false;
        }
    }
    
    // 复活玩家 - 必须支付6个点数才能复活
    public boolean revivePlayer(Player reviver, Player target) {
        // 只允许复活观察者模式的玩家
        if (target.getGameMode() != GameMode.SPECTATOR) {
            reviver.sendMessage(ChatColor.RED + "❌ 该玩家不需要复活！");
            return false;
        }
        
        int targetCredits = getCredits(target);
        int reviveCost = 6 - targetCredits; // 计算实际复活花费
        
        if (reviveCost <= 0) {
            // 如果目标点数已经足够，直接复活
            target.setGameMode(GameMode.SURVIVAL);
            target.teleport(reviver.getLocation());
            target.sendMessage(ChatColor.GREEN + "✨ 你已被自动复活！");
            reviver.sendMessage(ChatColor.GREEN + "✅ 目标玩家点数足够，已自动复活！");
            return true;
        }
        
        if (removeCredits(reviver, reviveCost)) {
            // 复活目标玩家
            target.setGameMode(GameMode.SURVIVAL);
            target.teleport(reviver.getLocation());
            target.sendMessage(ChatColor.GREEN + "✨ 你已被 " + reviver.getName() + " 复活！");
            reviver.sendMessage(ChatColor.GREEN + "✅ 你成功复活了 " + target.getName() + "，花费了 " + reviveCost + " 点信用点！");
            
            // 给予复活的玩家2点信用点
            setCredits(target, 2);
            giveCreditBook(target);
            
            return true;
        } else {
            reviver.sendMessage(ChatColor.RED + "❌ 信用点不足！复活需要 " + reviveCost + " 点信用点");
            return false;
        }
    }
    
    // 更新玩家的信用点书
    private void updatePlayerBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCreditBook(item)) {
                updateBookDisplay(player, item);
                break;
            }
        }
    }
    
    // 检查是否是信用点书
    public boolean isCreditBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "credit_book");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    // 检查物品是否死亡不掉落
    public boolean shouldKeepOnDeath(ItemStack item) {
        if (item == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "keep_on_death");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    // 注册复活选择台合成配方
    public void registerReviveStationRecipe() {
        ItemStack reviveStation = ReviveItem.createReviveStation();
        NamespacedKey key = new NamespacedKey(CreditPlugin.getInstance(), "revive_station");
        
        ShapedRecipe recipe = new ShapedRecipe(key, reviveStation);
        recipe.shape("ODO", "DRD", "DDD");
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('R', Material.RESPAWN_ANCHOR);
        
        Bukkit.addRecipe(recipe);
    }
    
    // 数据保存和加载
    public void saveAllData() {
        for (Map.Entry<UUID, Integer> entry : playerCredits.entrySet()) {
            CreditPlugin.getInstance().getDataConfig().set("credits." + entry.getKey().toString(), entry.getValue());
        }
        CreditPlugin.getInstance().saveData();
    }
    
    private void loadAllData() {
        // 加载信用点数据
        if (CreditPlugin.getInstance().getDataConfig().contains("credits")) {
            for (String key : CreditPlugin.getInstance().getDataConfig().getConfigurationSection("credits").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                int credits = CreditPlugin.getInstance().getDataConfig().getInt("credits." + key);
                playerCredits.put(uuid, credits);
            }
        }
    }
    
    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        CreditPlugin.getInstance().getDataConfig().set("credits." + uuid.toString(), getCredits(player));
        CreditPlugin.getInstance().saveData();
    }
}
