package com.yourname.creditplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
            playerCredits.put(uuid, 11); // 每人进服务器自带11个点数
            giveCreditBook(player);
            savePlayerData(player);
        }
        
        // 同步游戏状态和信用点状态
        syncPlayerState(player);
    }
    
    // 同步玩家状态：确保游戏模式和信用点一致
    public void syncPlayerState(Player player) {
        int credits = getCredits(player);
        
        // 如果信用点为负数且玩家不在观察者模式，强制传送到地狱
        if (credits < 0 && player.getGameMode() != GameMode.SPECTATOR) {
            // 确保玩家在地狱
            if (!isInNether(player)) {
                teleportToNether(player);
                player.sendMessage(ChatColor.RED + "🔥 你的信用点为负数，只能待在地狱！");
                player.sendMessage(ChatColor.YELLOW + "💡 你需要通过交易或完成任务来恢复信用点");
            }
        }
        
        // 如果玩家信用点恢复到正数且在地狱，允许离开
        if (credits >= 0 && isInNether(player)) {
            player.sendMessage(ChatColor.GREEN + "✨ 你的信用点已恢复正数，现在可以离开地狱了！");
        }
    }
    
    // 处理玩家死亡
    public void handlePlayerDeath(Player player) {
        int credits = getCredits(player);
        
        // 根据信用点决定复活位置
        if (credits >= 6) {
            // 信用点>=6，扣除6点，在主世界复活
            removeCredits(player, 6);
            
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage(ChatColor.GREEN + "✨ 你消耗6点信用点自动复活了！");
                }
            }, 1L);
        } else {
            // 信用点<6，扣除6点（可能变为负数），在地狱复活
            removeCredits(player, 6);
            
            Bukkit.getScheduler().runTaskLater(CreditPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SURVIVAL);
                    
                    // 不再在这里强制传送，让PlayerRespawnEvent处理重生位置
                    player.sendMessage(ChatColor.RED + "🔥 由于信用点不足，你在地狱复活了！");
                }
            }, 1L);
        }
    }
    
    // 检查玩家是否在地狱
    public boolean isInNether(Player player) {
        return player.getWorld().getEnvironment() == World.Environment.NETHER;
    }
    
    // 传送玩家到地狱
    public void teleportToNether(Player player) {
        World nether = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                .findFirst()
                .orElse(null);
        
        if (nether != null) {
            // 传送到地狱的安全位置
            Location netherSpawn = nether.getSpawnLocation();
            // 寻找安全的重生点
            Location safeLocation = findSafeLocation(nether, netherSpawn);
            
            player.teleport(safeLocation);
            player.sendMessage(ChatColor.RED + "🔥 你被传送到了地狱！");
        }
    }
    
    // 寻找安全的位置
    public Location findSafeLocation(World world, Location center) {
        // 首先检查中心位置是否安全
        if (isLocationSafe(center)) {
            return center.clone().add(0, 1, 0); // 在安全方块上方一格
        }
        
        // 在周围寻找安全位置
        for (int radius = 1; radius <= 10; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // 只检查最外层
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    
                    Location checkLoc = center.clone().add(x, 0, z);
                    int y = world.getHighestBlockYAt(checkLoc);
                    Location safeLoc = new Location(world, checkLoc.getX(), y + 1, checkLoc.getZ());
                    
                    // 检查位置是否安全
                    if (isLocationSafe(safeLoc)) {
                        return safeLoc;
                    }
                }
            }
        }
        
        // 如果没找到安全位置，返回原始位置上方
        return center.clone().add(0, 10, 0);
    }
    
    // 检查位置是否安全
    private boolean isLocationSafe(Location location) {
        Material blockType = location.getBlock().getType();
        Material belowType = location.clone().subtract(0, 1, 0).getBlock().getType();
        Material aboveType = location.clone().add(0, 1, 0).getBlock().getType();
        
        return blockType == Material.AIR && 
               aboveType == Material.AIR &&
               belowType.isSolid() && 
               belowType != Material.LAVA && 
               belowType != Material.FIRE &&
               belowType != Material.MAGMA_BLOCK &&
               belowType != Material.CAMPFIRE;
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
                 ChatColor.BLACK + "信用点为负数时将\n被流放至地狱！");
        
        pages.add(ChatColor.DARK_BLUE + "使用说明:\n\n" +
                 ChatColor.BLACK + "• 潜行+右键玩家交易\n" +
                 ChatColor.BLACK + "• 杀人扣除2点\n" +
                 ChatColor.BLACK + "• 杀人日可抢夺点数\n" +
                 ChatColor.BLACK + "• 负数时困在地狱\n" +
                 ChatColor.BLACK + "• 死亡扣除6点\n" +
                 ChatColor.BLACK + "• <6点则地狱复活");
        
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
                 ChatColor.BLACK + "信用点为负数时将\n被流放至地狱！");
        
        pages.add(ChatColor.DARK_BLUE + "使用说明:\n\n" +
                 ChatColor.BLACK + "• 潜行+右键玩家交易\n" +
                 ChatColor.BLACK + "• 杀人扣除2点\n" +
                 ChatColor.BLACK + "• 杀人日可抢夺点数\n" +
                 ChatColor.BLACK + "• 负数时困在地狱\n" +
                 ChatColor.BLACK + "• 死亡扣除6点\n" +
                 ChatColor.BLACK + "• <6点则地狱复活");
        
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
        int oldCredits = getCredits(player);
        playerCredits.put(uuid, credits);
        updatePlayerBook(player);
        
        // 同步状态
        syncPlayerState(player);
        
        // 如果信用点从负数变为正数，发送通知
        if (oldCredits < 0 && credits >= 0) {
            player.sendMessage(ChatColor.GREEN + "✨ 恭喜！你的信用点已恢复正数！");
            player.sendMessage(ChatColor.GREEN + "🎉 你现在可以自由穿越地狱门了！");
        }
        // 如果信用点从正数变为负数，发送通知
        else if (oldCredits >= 0 && credits < 0) {
            player.sendMessage(ChatColor.RED + "💀 警告！你的信用点已变为负数！");
            player.sendMessage(ChatColor.RED + "🔥 你将被流放至地狱，无法穿越地狱门！");
        }
        
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
            // 如果点数不足，允许变为负数
            setCredits(player, current - amount);
            return true;
        }
    }
    
    // 处理杀人事件 - 点数在杀人后自动扣2
    public void handleKill(Player killer, Player victim) {
        int killerCredits = getCredits(killer);
        int victimCredits = getCredits(victim);
        
        // 杀人扣除2点
        removeCredits(killer, 2);
        
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
        if (getCredits(from) >= amount) {
            removeCredits(from, amount);
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
    
    // 复活玩家 - 将地狱玩家带回主世界
    public boolean revivePlayer(Player reviver, Player target) {
        // 只允许复活在地狱的玩家
        if (!isInNether(target)) {
            reviver.sendMessage(ChatColor.RED + "❌ 该玩家不需要复活！");
            return false;
        }
        
        int targetCredits = getCredits(target);
        
        // 计算复活花费：将负数信用点补正到0
        int reviveCost = Math.max(0, -targetCredits);
        
        if (reviveCost <= 0) {
            // 如果目标点数已经足够，直接传送回主世界
            World overworld = Bukkit.getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                    .findFirst()
                    .orElse(null);
            
            if (overworld != null) {
                Location safeLocation = findSafeLocation(overworld, overworld.getSpawnLocation());
                target.teleport(safeLocation);
                target.sendMessage(ChatColor.GREEN + "✨ 你已被传送回主世界！");
                reviver.sendMessage(ChatColor.GREEN + "✅ 目标玩家点数足够，已传送回主世界！");
                return true;
            }
        }
        
        if (removeCredits(reviver, reviveCost)) {
            // 将目标玩家信用点补正到0
            if (targetCredits < 0) {
                addCredits(target, -targetCredits);
            }
            
            // 传送目标玩家回主世界
            World overworld = Bukkit.getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                    .findFirst()
                    .orElse(null);
            
            if (overworld != null) {
                Location safeLocation = findSafeLocation(overworld, overworld.getSpawnLocation());
                target.teleport(safeLocation);
                target.sendMessage(ChatColor.GREEN + "✨ 你已被 " + reviver.getName() + " 救回主世界！");
                reviver.sendMessage(ChatColor.GREEN + "✅ 你成功将 " + target.getName() + " 救回主世界，花费了 " + reviveCost + " 点信用点！");
                return true;
            }
        } else {
            reviver.sendMessage(ChatColor.RED + "❌ 信用点不足！复活需要 " + reviveCost + " 点信用点");
            return false;
        }
        
        return false;
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
