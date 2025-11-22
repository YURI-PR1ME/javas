package com.yourname.assassinplugin;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AssassinManager {
    
    private final Map<UUID, AssassinContract> activeContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private long COOLDOWN_TIME;
    
    // 新增玩家刺客相关字段
    private final Map<UUID, PlayerAssassin> playerAssassins = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerContractSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePlayerContracts = new ConcurrentHashMap<>();
    private int assassinEntryFee = 50;
    
    // 溺尸王相关字段
    private final Map<UUID, World> tier3ContractWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> drownedShockCounters = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> drownedAITasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> drownedTridents = new ConcurrentHashMap<>();
    
    public AssassinManager() {
        loadConfig();
        loadActiveContracts();
        loadPlayerAssassins();
        loadPlayerSessions();
        registerDarkWebAccessItemRecipe();
        registerRecipeBookRecipe();
    }
    
    private void loadConfig() {
        COOLDOWN_TIME = AssassinPlugin.getInstance().getConfig().getLong("cooldown", 600000);
        assassinEntryFee = AssassinPlugin.getInstance().getConfig().getInt("player-assassin.entry-fee", 50);
    }
    
    public void reloadConfig() {
        AssassinPlugin.getInstance().reloadConfig();
        loadConfig();
    }
    
    // ========== 玩家刺客管理方法 ==========
    
    public boolean registerPlayerAssassin(Player player, int entryFee) {
        if (playerAssassins.containsKey(player.getUniqueId())) {
            return false;
        }
        
        if (!hasSufficientCredits(player, entryFee * 2)) {
            return false;
        }
        
        if (!deductCredits(player, entryFee)) {
            return false;
        }
        
        PlayerAssassin assassin = new PlayerAssassin(player.getUniqueId(), player.getName());
        assassin.setEntryFeePaid(entryFee);
        playerAssassins.put(player.getUniqueId(), assassin);
        
        savePlayerAssassin(assassin);
        return true;
    }
    
    public boolean isPlayerAssassin(Player player) {
        return playerAssassins.containsKey(player.getUniqueId());
    }
    
    public PlayerAssassin getPlayerAssassin(UUID playerId) {
        return playerAssassins.get(playerId);
    }
    
    public List<PlayerAssassin> getActivePlayerAssassins() {
        return playerAssassins.values().stream()
                .filter(PlayerAssassin::isActive)
                .collect(Collectors.toList());
    }
    
    public PlayerContractSession createPlayerContractSession(Player buyer, Player assassin, Player target) {
        UUID sessionId = UUID.randomUUID();
        PlayerContractSession session = new PlayerContractSession(sessionId, buyer.getUniqueId(), assassin.getUniqueId(), target.getUniqueId());
        playerSessions.put(sessionId, session);
        
        giveCommunicationBook(buyer, assassin, sessionId);
        giveCommunicationBook(assassin, buyer, sessionId);
        
        savePlayerSession(session);
        return session;
    }
    
    public PlayerContractSession getPlayerSession(UUID sessionId) {
        return playerSessions.get(sessionId);
    }
    
    public boolean handleAssassinOffer(Player assassin, UUID sessionId, int price) {
        PlayerContractSession session = playerSessions.get(sessionId);
        if (session == null || !session.getAssassinId().equals(assassin.getUniqueId())) {
            return false;
        }
        
        session.setProposedPrice(price);
        updatePlayerSession(session);
        
        Player buyer = Bukkit.getPlayer(session.getBuyerId());
        if (buyer != null) {
            buyer.sendMessage("§8[暗网] §6刺客报价: " + price + " 信用点");
            buyer.sendMessage("§8[暗网] §7使用 §e/assassin accept " + sessionId + " §7接受报价");
        }
        
        return true;
    }
    
    public boolean acceptContractOffer(Player buyer, UUID sessionId) {
        PlayerContractSession session = playerSessions.get(sessionId);
        if (session == null || !session.getBuyerId().equals(buyer.getUniqueId())) {
            return false;
        }
        
        if (!hasSufficientCredits(buyer, session.getProposedPrice())) {
            buyer.sendMessage("§c❌ 信用点不足！需要 " + session.getProposedPrice() + " 点信用点");
            return false;
        }
        
        session.setPriceAccepted(true);
        session.setActive(true);
        
        Player assassin = Bukkit.getPlayer(session.getAssassinId());
        if (assassin != null) {
            giveTrackingCompass(assassin, session.getTargetId(), sessionId);
            assassin.sendMessage("§8[暗网] §a✅ 合约已激活！目标: " + getTargetName(session.getTargetId()));
            assassin.sendMessage("§8[暗网] §7报酬: " + session.getProposedPrice() + " 信用点");
        }
        
        activePlayerContracts.put(session.getAssassinId(), sessionId);
        updatePlayerSession(session);
        return true;
    }
    
    public void handlePlayerAssassinKill(Player assassin, Player target) {
        UUID sessionId = activePlayerContracts.get(assassin.getUniqueId());
        if (sessionId == null) return;
        
        PlayerContractSession session = playerSessions.get(sessionId);
        if (session == null || !session.getTargetId().equals(target.getUniqueId())) return;
        
        completePlayerContract(session, true, assassin, target);
    }
    
    public void handlePlayerAssassinDeath(Player assassin) {
        UUID sessionId = activePlayerContracts.get(assassin.getUniqueId());
        if (sessionId == null) return;
        
        PlayerContractSession session = playerSessions.get(sessionId);
        if (session == null) return;
        
        completePlayerContract(session, false, assassin, null);
    }
    
    private void completePlayerContract(PlayerContractSession session, boolean success, Player assassin, Player target) {
        session.setCompleted(true);
        session.setSuccess(success);
        session.setCompletionTime(System.currentTimeMillis());
        
        PlayerAssassin playerAssassin = playerAssassins.get(session.getAssassinId());
        Player buyer = Bukkit.getPlayer(session.getBuyerId());
        
        if (success) {
            int targetCredits = getPlayerCredits(target);
            if (targetCredits > 0) {
                transferCredits(target, buyer, targetCredits);
                buyer.sendMessage("§8[暗网] §6💰 获得目标信用点: " + targetCredits);
            }
            
            if (deductCredits(buyer, session.getProposedPrice())) {
                addCredits(assassin, session.getProposedPrice());
                assassin.sendMessage("§8[暗网] §6💰 获得报酬: " + session.getProposedPrice() + " 信用点");
            }
            
            playerAssassin.addCompletedContract(session.getProposedPrice());
            buyer.sendMessage("§8[暗网] §a✅ 合约完成！目标已被清除");
            assassin.sendMessage("§8[暗网] §a✅ 合约完成！");
        } else {
            playerAssassin.addFailedContract();
            if (buyer != null) {
                buyer.sendMessage("§8[暗网] §c❌ 合约失败！刺客被反杀");
            }
            assassin.sendMessage("§8[暗网] §c❌ 合约失败！");
        }
        
        activePlayerContracts.remove(session.getAssassinId());
        updatePlayerSession(session);
        savePlayerAssassin(playerAssassin);
        removeTrackingCompass(assassin);
    }
    
    // ========== 物品相关方法 ==========
    
    private void giveCommunicationBook(Player from, Player to, UUID sessionId) {
        ItemStack book = createCommunicationBook(from, to, sessionId);
        from.getInventory().addItem(book);
        from.sendMessage("§8[暗网] §7你获得了与 " + to.getName() + " 的通讯书");
    }
    
    private ItemStack createCommunicationBook(Player from, Player to, UUID sessionId) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("§8暗网通讯录");
        meta.setAuthor("匿名中介");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        
        List<String> pages = new ArrayList<>();
        pages.add("§0暗网通讯录\n\n§7会话ID: " + sessionId.toString().substring(0, 8) + "\n\n§8来自: 匿名\n§8目标: 匿名\n\n§7使用此书进行沟通");
        pages.add("§0使用说明\n\n§7右键书写信息\n§7潜行+右键发送\n\n§8保持匿名\n§8注意安全");
        
        meta.setPages(pages);
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "communication_book");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, sessionId.toString());
        
        NamespacedKey partnerKey = new NamespacedKey(AssassinPlugin.getInstance(), "communication_partner");
        meta.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, 
            from.getUniqueId().equals(to.getUniqueId()) ? "self" : to.getUniqueId().toString());
        
        book.setItemMeta(meta);
        return book;
    }
    
    private void giveTrackingCompass(Player assassin, UUID targetId, UUID sessionId) {
        ItemStack compass = createTrackingCompass(targetId, sessionId);
        assassin.getInventory().addItem(compass);
    }
    
    private ItemStack createTrackingCompass(UUID targetId, UUID sessionId) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        
        meta.setDisplayName("§c目标追踪指南针");
        meta.setLore(Arrays.asList(
            "§7指向合约目标",
            "§8会话: " + sessionId.toString().substring(0, 8),
            "",
            "§c右键更新位置"
        ));
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "tracking_compass");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, sessionId.toString());
        
        NamespacedKey targetKey = new NamespacedKey(AssassinPlugin.getInstance(), "tracking_target");
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetId.toString());
        
        compass.setItemMeta(meta);
        return compass;
    }
    
    private void removeTrackingCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTrackingCompass(item)) {
                player.getInventory().remove(item);
                break;
            }
        }
    }
    
    public boolean isCommunicationBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "communication_book");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }
    
    public boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "tracking_compass");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }
    
    // ========== AI杀手物品方法 ==========
    
    public ItemStack createDarkWebAccessItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§8暗网接入口");
        meta.setLore(Arrays.asList("§7右键打开买凶界面", "§8————————————", "§c⚠ 非法物品", "§e造价昂贵，谨慎使用"));
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "dark_web_access");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public boolean isDarkWebAccessItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "dark_web_access");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    public ItemStack createRecipeBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle("§8暗网接入指南");
        meta.setAuthor("匿名黑客");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        
        List<String> pages = new ArrayList<>();
        pages.add("§0暗网接入指南\n\n§7这本书记载了如何\n制造暗网接入口的方法\n\n§8警告：\n§4使用此技术可能触犯法律\n后果自负！");
        pages.add("§0合成配方\n\n§6暗网接入口\n\n需要材料：\n§7黑曜石 x6\n§5末影之眼 x1\n§b下界合金锭 x2\n§b钻石块 x1\n§6信标 x1");
        pages.add("§0合成布局\n\n§8O E O\n§8N D N\n§8O B O\n\n§7O=黑曜石\n§5E=末影之眼\n§bN=下界合金锭\n§bD=钻石块\n§6B=信标");
        pages.add("§0使用说明\n\n§7手持暗网接入口\n右键打开买凶界面\n\n§8功能：\n§7- 选择目标玩家\n§7- 选择杀手等级\n§7- 发布暗杀合约");
        pages.add("§0杀手等级\n\n§7Ⅰ级 - 30信用点\n普通近战杀手\n\n§6Ⅱ级 - 40信用点\n精英卫道士，抢夺信用点\n\n§4Ⅲ级 - 80信用点\n溺尸王，抢夺信用点");
        pages.add("§0注意事项\n\n§7- 买凶有冷却时间\n§7- 合约一旦发布无法取消\n§7- 失败不退还信用点\n§7- 小心被反杀！\n\n§8保持匿名，注意安全");
        
        meta.setPages(pages);
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "recipe_book");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        book.setItemMeta(meta);
        return book;
    }
    
    public boolean isRecipeBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "recipe_book");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    // ========== 合成配方注册 ==========
    
    private void registerDarkWebAccessItemRecipe() {
        try {
            ItemStack darkWebItem = createDarkWebAccessItem();
            NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "dark_web_access_item");
            
            ShapedRecipe recipe = new ShapedRecipe(key, darkWebItem);
            recipe.shape("OEO", "NDN", "OBO");
            recipe.setIngredient('O', Material.OBSIDIAN);
            recipe.setIngredient('E', Material.ENDER_EYE);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            recipe.setIngredient('B', Material.BEACON);
            
            Bukkit.addRecipe(recipe);
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("注册暗网接入口合成配方失败: " + e.getMessage());
        }
    }
    
    private void registerRecipeBookRecipe() {
        try {
            ItemStack recipeBook = createRecipeBook();
            NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "recipe_book_item");
            
            ShapedRecipe recipe = new ShapedRecipe(key, recipeBook);
            recipe.shape("BEB", "NDN", "BEB");
            recipe.setIngredient('B', Material.BOOK);
            recipe.setIngredient('E', Material.ENDER_EYE);
            recipe.setIngredient('N', Material.NETHERITE_INGOT);
            recipe.setIngredient('D', Material.DIAMOND_BLOCK);
            
            Bukkit.addRecipe(recipe);
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("注册配方书合成失败: " + e.getMessage());
        }
    }
    
    // ========== AI杀手合约方法 ==========
    
    public boolean createContract(Player employer, Player target, int tier) {
        // 检查冷却
        if (playerCooldowns.containsKey(employer.getUniqueId())) {
            long lastUse = playerCooldowns.get(employer.getUniqueId());
            long remaining = getPlayerCooldownRemaining(employer);
            if (remaining > 0) {
                long remainingSeconds = remaining / 1000;
                employer.sendMessage("§c❌ 买凶冷却中，请等待 " + remainingSeconds + " 秒");
                return false;
            }
        }
        
        // 检查信用点
        int cost = getTierCost(tier);
        if (!deductCredits(employer, cost)) {
            employer.sendMessage("§c❌ 信用点不足！需要 " + cost + " 点信用点");
            return false;
        }
        
        employer.sendMessage("§e💰 买凶花费: " + cost + " 信用点");
        
        // 创建合约
        AssassinContract contract = new AssassinContract(
            UUID.randomUUID(),
            employer.getUniqueId(),
            target.getUniqueId(),
            tier,
            System.currentTimeMillis()
        );
        
        activeContracts.put(contract.getContractId(), contract);
        playerCooldowns.put(employer.getUniqueId(), System.currentTimeMillis());
        
        // 发送消息
        employer.sendMessage("§8[暗网] §a✅ 合约已发布！目标: " + target.getName() + " | 等级: " + tier);
        employer.sendMessage("§8[暗网] §7杀手正在路上...");
        
        // 延迟生成杀手
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnAssassin(contract);
            }
        }.runTaskLater(AssassinPlugin.getInstance(), 100L);
        
        saveContract(contract);
        return true;
    }
    
    private void spawnAssassin(AssassinContract contract) {
        Player target = Bukkit.getPlayer(contract.getTargetId());
        if (target == null || !target.isOnline()) {
            refundContract(contract);
            return;
        }
        
        Location spawnLocation = findSpawnLocation(target.getLocation(), 40, 60);
        if (spawnLocation == null) {
            spawnLocation = target.getLocation().add(40, 0, 0);
        }
        
        LivingEntity assassin;
        
        switch (contract.getTier()) {
            case 3:
                assassin = spawnTier3Assassin(spawnLocation, target);
                break;
            case 2:
                assassin = spawnTier2Assassin(spawnLocation, target);
                break;
            case 1:
            default:
                assassin = spawnTier1Assassin(spawnLocation, target);
                break;
        }
        
        // 设置杀手的元数据
        assassin.getPersistentDataContainer().set(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_contract"),
            PersistentDataType.STRING,
            contract.getContractId().toString()
        );
        
        assassin.getPersistentDataContainer().set(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_target"),
            PersistentDataType.STRING,
            target.getUniqueId().toString()
        );
        
        contract.setAssassinId(assassin.getUniqueId());
        contract.setActive(true);
        
        // 第三档合约开始下雨
        if (contract.getTier() == 3) {
            World world = target.getWorld();
            tier3ContractWorlds.put(contract.getContractId(), world);
            world.setStorm(true);
            world.setThundering(true);
            target.sendMessage("§9☔ 天空突然阴沉下来，暴雨倾盆而下...");
        }
        
        // 发送警告给目标
        target.sendMessage("§c⚔️ 你感受到了杀气！有人买凶要你的命！");
        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        
        updateContract(contract);
    }
    
    private LivingEntity spawnTier1Assassin(Location location, Player target) {
        Zombie assassin = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        
        assassin.setCustomName("§8刺客 §7(Ⅰ级)");
        assassin.setCustomNameVisible(true);
        assassin.setAdult();
        
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
        assassin.setHealth(30.0);
        assassin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(6.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        
        assassin.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        assassin.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        assassin.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        
        setAssassinTarget(assassin, target);
        
        return assassin;
    }
    
    private LivingEntity spawnTier2Assassin(Location location, Player target) {
        Vindicator assassin = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);
        
        assassin.setCustomName("§6精英卫道士 §6(Ⅱ级)");
        assassin.setCustomNameVisible(true);
        
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0);
        assassin.setHealth(50.0);
        assassin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(8.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
        
        addPotionEffectSafely(assassin, "SPEED", 1);
        addPotionEffectSafely(assassin, "INCREASE_DAMAGE", 0);
        
        assassin.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        
        setAssassinTarget(assassin, target);
        
        return assassin;
    }
    
    private LivingEntity spawnTier3Assassin(Location location, Player target) {
        Drowned assassin = (Drowned) location.getWorld().spawnEntity(location, EntityType.DROWNED);
        
        assassin.setCustomName("§4溺尸王 §4(Ⅲ级)");
        assassin.setCustomNameVisible(true);
        
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        assassin.setHealth(80.0);
        assassin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(8.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
        
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta tridentMeta = trident.getItemMeta();
        tridentMeta.addEnchant(Enchantment.CHANNELING, 1, true);
        tridentMeta.addEnchant(Enchantment.IMPALING, 3, true);
        tridentMeta.setDisplayName("§b雷霆三叉戟");
        trident.setItemMeta(tridentMeta);
        assassin.getEquipment().setItemInMainHand(trident);
        assassin.getEquipment().setItemInMainHandDropChance(0.0f);
        
        assassin.getEquipment().setHelmet(createProtection4NetheriteHelmet());
        assassin.getEquipment().setChestplate(createProtection4NetheriteChestplate());
        assassin.getEquipment().setLeggings(createProtection4NetheriteLeggings());
        assassin.getEquipment().setBoots(createProtection4NetheriteBoots());
        
        assassin.setCanPickupItems(false);
        
        drownedShockCounters.put(assassin.getUniqueId(), 0);
        drownedTridents.put(assassin.getUniqueId(), new ArrayList<>());
        
        setDrownedTarget(assassin, target);
        
        return assassin;
    }
    
    private ItemStack createProtection4NetheriteHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金头盔");
        helmet.setItemMeta(meta);
        return helmet;
    }
    
    private ItemStack createProtection4NetheriteChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金胸甲");
        chestplate.setItemMeta(meta);
        return chestplate;
    }
    
    private ItemStack createProtection4NetheriteLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金护腿");
        leggings.setItemMeta(meta);
        return leggings;
    }
    
    private ItemStack createProtection4NetheriteBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金靴子");
        boots.setItemMeta(meta);
        return boots;
    }
    
    private void addProtectionEnchantment(ItemMeta meta, int level) {
        try {
            String[] protectionNames = {"PROTECTION_ENVIRONMENTAL", "PROTECTION"};
            for (String enchantName : protectionNames) {
                try {
                    Enchantment protection = Enchantment.getByName(enchantName);
                    if (protection != null) {
                        meta.addEnchant(protection, level, true);
                        return;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个名称
                }
            }
            AssassinPlugin.getInstance().getLogger().warning("无法添加保护附魔，将使用未附魔的装备");
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("添加保护附魔时出错: " + e.getMessage());
        }
    }
    
    private void addPotionEffectSafely(LivingEntity entity, String effectName, int amplifier) {
        try {
            PotionEffectType effectType = PotionEffectType.getByName(effectName);
            if (effectType != null) {
                entity.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, amplifier));
            }
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("无法添加药水效果: " + effectName);
        }
    }
    
    private void setDrownedTarget(LivingEntity assassin, Player target) {
        if (!(assassin instanceof Drowned)) return;
        
        Drowned drowned = (Drowned) assassin;
        drowned.setTarget(target);
        
        // 简化版的溺尸AI（完整版太复杂，这里提供基础版本）
        BukkitRunnable aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!drowned.isValid() || drowned.isDead()) {
                    this.cancel();
                    return;
                }
                
                if (target == null || !target.isOnline()) {
                    this.cancel();
                    drowned.remove();
                    return;
                }
                
                drowned.setTarget(target);
                
                double distance = drowned.getLocation().distance(target.getLocation());
                
                if (distance > 50) {
                    Location newLocation = findSpawnLocation(target.getLocation(), 15, 25);
                    if (newLocation != null) {
                        drowned.teleport(newLocation);
                    }
                }
                
                if (drowned.getEquipment().getItemInMainHand().getType() != Material.TRIDENT) {
                    ItemStack trident = createEnchantedTrident();
                    drowned.getEquipment().setItemInMainHand(trident);
                }
            }
        };
        
        drownedAITasks.put(drowned.getUniqueId(), aiTask);
        aiTask.runTaskTimer(AssassinPlugin.getInstance(), 0L, 20L);
    }
    
    private ItemStack createEnchantedTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta tridentMeta = trident.getItemMeta();
        tridentMeta.addEnchant(Enchantment.CHANNELING, 1, true);
        tridentMeta.addEnchant(Enchantment.IMPALING, 3, true);
        tridentMeta.setDisplayName("§b雷霆三叉戟");
        trident.setItemMeta(tridentMeta);
        return trident;
    }
    
    private void setAssassinTarget(LivingEntity assassin, Player target) {
        if (assassin instanceof Mob) {
            ((Mob) assassin).setTarget(target);
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!assassin.isValid() || assassin.isDead()) {
                    this.cancel();
                    return;
                }
                
                if (assassin instanceof Mob) {
                    Mob mobAssassin = (Mob) assassin;
                    LivingEntity currentTarget = mobAssassin.getTarget();
                    if (currentTarget == null || !currentTarget.equals(target)) {
                        mobAssassin.setTarget(target);
                    }
                }
                
                if (target == null || !target.isOnline() || target.isDead()) {
                    this.cancel();
                }
            }
        }.runTaskTimer(AssassinPlugin.getInstance(), 20L, 40L);
    }
    
    private Location findSpawnLocation(Location center, int minDistance, int maxDistance) {
        Random random = new Random();
        
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            
            Location testLocation = new Location(center.getWorld(), x, center.getY(), z);
            Location safeLocation = findSafeLocation(testLocation);
            
            if (safeLocation != null && safeLocation.distance(center) >= minDistance) {
                return safeLocation;
            }
        }
        
        return null;
    }
    
    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        int y = world.getHighestBlockYAt(x, z);
        Location testLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
        
        if (testLocation.getBlock().getType().isSolid() || 
            testLocation.getBlock().isLiquid()) {
            return null;
        }
        
        Location below = testLocation.clone().add(0, -1, 0);
        if (!below.getBlock().getType().isSolid()) {
            return null;
        }
        
        return testLocation;
    }
    
    // ========== AI杀手事件处理 ==========
    
    public void handleKill(Player killer, Player victim) {
        int killerCredits = getCredits(killer);
        int victimCredits = getCredits(victim);
        
        if (!deductCredits(killer, 2)) {
            setCredits(killer, 0);
        }
        
        if (isKillingDay()) {
            if (victimCredits > 0) {
                addCredits(killer, victimCredits);
                setCredits(victim, 0);
                killer.sendMessage("§c⚡ 你抢夺了 " + victim.getName() + " 的 " + victimCredits + " 点信用点！");
            }
        }
        
        killer.sendMessage("§c⚠ 你因杀人被扣除2点信用点！");
        
        handlePlayerDeath(victim);
    }
    
    public void handlePlayerDeath(Player player) {
        int credits = getCredits(player);
        
        if (credits >= 6) {
            deductCredits(player, 6);
            
            Bukkit.getScheduler().runTaskLater(AssassinPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage("§a✨ 你消耗6点信用点自动复活了！");
                }
            }, 1L);
        } else {
            Bukkit.getScheduler().runTaskLater(AssassinPlugin.getInstance(), () -> {
                if (player.isOnline() && player.isDead()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage("§c👻 你已死亡，需要他人复活你");
                    
                    int reviveCost = 6 - credits;
                    player.sendMessage("§e💡 复活需要花费 " + reviveCost + " 点信用点");
                }
            }, 1L);
        }
    }
    
    public void handleAssassinKill(LivingEntity assassin, Player target) {
        String contractIdStr = assassin.getPersistentDataContainer().get(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_contract"),
            PersistentDataType.STRING
        );
        
        if (contractIdStr == null) return;
        
        UUID contractId = UUID.fromString(contractIdStr);
        AssassinContract contract = activeContracts.get(contractId);
        
        if (contract != null && contract.isActive()) {
            contract.setCompleted(true);
            contract.setSuccess(true);
            contract.setCompletionTime(System.currentTimeMillis());
            
            Player employer = Bukkit.getPlayer(contract.getEmployerId());
            
           if (contract.getTier() >= 2) {
int targetCredits = getPlayerCredits(target);
if (targetCredits > 0) {
transferCredits(target, employer, targetCredits);
}
} 
            if (employer != null) {
                employer.sendMessage("§8[暗网] §a✅ 合约完成！目标 " + target.getName() + " 已被清除");
            }
            
            target.sendMessage("§c💀 你被职业杀手终结了...");
            
            if (contract.getTier() == 3) {
                stopRainForContract(contractId);
            }
            
            if (assassin instanceof Drowned) {
                cleanupDrownedAI(assassin.getUniqueId());
            }
            
            assassin.remove();
            updateContract(contract);
            activeContracts.remove(contractId);
        }
    }
    
    public void handleAssassinDeath(LivingEntity assassin) {
        String contractIdStr = assassin.getPersistentDataContainer().get(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_contract"),
            PersistentDataType.STRING
        );
        
        if (contractIdStr == null) return;
        
        UUID contractId = UUID.fromString(contractIdStr);
        AssassinContract contract = activeContracts.get(contractId);
        
        if (contract != null && contract.isActive()) {
            contract.setCompleted(true);
            contract.setSuccess(false);
            contract.setCompletionTime(System.currentTimeMillis());
            
            Player employer = Bukkit.getPlayer(contract.getEmployerId());
            if (employer != null) {
                employer.sendMessage("§8[暗网] §c❌ 你的杀手被反杀了！合约失败");
            }
            
            if (contract.getTier() == 3) {
                stopRainForContract(contractId);
            }
            
            if (assassin instanceof Drowned) {
                cleanupDrownedAI(assassin.getUniqueId());
            }
            
            updateContract(contract);
            activeContracts.remove(contractId);
        }
    }
    
    private void stopRainForContract(UUID contractId) {
        World world = tier3ContractWorlds.remove(contractId);
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(world)) {
                    player.sendMessage("§9☀️ 暴雨突然停止了，天空逐渐放晴...");
                }
            }
        }
    }
    
    private void cleanupDrownedAI(UUID drownedId) {
        BukkitRunnable aiTask = drownedAITasks.remove(drownedId);
        if (aiTask != null) {
            aiTask.cancel();
        }
        
        drownedShockCounters.remove(drownedId);
        
        List<UUID> tridents = drownedTridents.remove(drownedId);
        if (tridents != null) {
            for (UUID tridentId : tridents) {
                Entity trident = Bukkit.getEntity(tridentId);
                if (trident != null && trident.isValid()) {
                    trident.remove();
                }
            }
        }
    }
    
    // ========== 数据保存和加载 ==========
    
    private void loadActiveContracts() {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        
        if (!config.contains("contracts")) return;
        
        for (String contractIdStr : config.getConfigurationSection("contracts").getKeys(false)) {
            String path = "contracts." + contractIdStr;
            
            UUID contractId = UUID.fromString(contractIdStr);
            UUID employerId = UUID.fromString(config.getString(path + ".employer"));
            UUID targetId = UUID.fromString(config.getString(path + ".target"));
            int tier = config.getInt(path + ".tier");
            long created = config.getLong(path + ".created");
            
            AssassinContract contract = new AssassinContract(contractId, employerId, targetId, tier, created);
            contract.setActive(config.getBoolean(path + ".active"));
            contract.setCompleted(config.getBoolean(path + ".completed"));
            contract.setSuccess(config.getBoolean(path + ".success"));
            
            if (config.contains(path + ".assassin")) {
                contract.setAssassinId(UUID.fromString(config.getString(path + ".assassin")));
            }
            if (config.contains(path + ".completedTime")) {
                contract.setCompletionTime(config.getLong(path + ".completedTime"));
            }
            
            if (!contract.isCompleted()) {
                activeContracts.put(contractId, contract);
            }
        }
    }
    
    private void loadPlayerAssassins() {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        
        if (!config.contains("player_assassins")) return;
        
        for (String playerIdStr : config.getConfigurationSection("player_assassins").getKeys(false)) {
            String path = "player_assassins." + playerIdStr;
            
            UUID playerId = UUID.fromString(playerIdStr);
            String displayName = config.getString(path + ".display_name");
            
            PlayerAssassin assassin = new PlayerAssassin(playerId, displayName);
            assassin.setCompletedContracts(config.getInt(path + ".completed_contracts"));
            assassin.setFailedContracts(config.getInt(path + ".failed_contracts"));
            assassin.setTotalEarnings(config.getInt(path + ".total_earnings"));
            assassin.setActive(config.getBoolean(path + ".active"));
            assassin.setEntryFeePaid(config.getInt(path + ".entry_fee"));
            
            playerAssassins.put(playerId, assassin);
        }
    }
    
    private void loadPlayerSessions() {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        
        if (!config.contains("player_sessions")) return;
        
        for (String sessionIdStr : config.getConfigurationSection("player_sessions").getKeys(false)) {
            String path = "player_sessions." + sessionIdStr;
            
            UUID sessionId = UUID.fromString(sessionIdStr);
            UUID buyerId = UUID.fromString(config.getString(path + ".buyer"));
            UUID assassinId = UUID.fromString(config.getString(path + ".assassin"));
            UUID targetId = UUID.fromString(config.getString(path + ".target"));
            
            PlayerContractSession session = new PlayerContractSession(sessionId, buyerId, assassinId, targetId);
            session.setProposedPrice(config.getInt(path + ".proposed_price"));
            session.setPriceAccepted(config.getBoolean(path + ".price_accepted"));
            session.setActive(config.getBoolean(path + ".active"));
            session.setCompleted(config.getBoolean(path + ".completed"));
            session.setSuccess(config.getBoolean(path + ".success"));
            session.setCompletionTime(config.getLong(path + ".completion_time"));
            
            if (!session.isCompleted()) {
                playerSessions.put(sessionId, session);
            }
        }
    }
    
    private void saveContract(AssassinContract contract) {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        String path = "contracts." + contract.getContractId().toString();
        
        config.set(path + ".employer", contract.getEmployerId().toString());
        config.set(path + ".target", contract.getTargetId().toString());
        config.set(path + ".tier", contract.getTier());
        config.set(path + ".created", contract.getCreatedTime());
        config.set(path + ".active", contract.isActive());
        config.set(path + ".completed", contract.isCompleted());
        config.set(path + ".success", contract.isSuccess());
        
        if (contract.getAssassinId() != null) {
            config.set(path + ".assassin", contract.getAssassinId().toString());
        }
        if (contract.getCompletionTime() > 0) {
            config.set(path + ".completedTime", contract.getCompletionTime());
        }
        
        AssassinPlugin.getInstance().saveData();
    }
    
    private void savePlayerAssassin(PlayerAssassin assassin) {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        String path = "player_assassins." + assassin.getPlayerId().toString();
        
        config.set(path + ".display_name", assassin.getDisplayName());
        config.set(path + ".completed_contracts", assassin.getCompletedContracts());
        config.set(path + ".failed_contracts", assassin.getFailedContracts());
        config.set(path + ".total_earnings", assassin.getTotalEarnings());
        config.set(path + ".active", assassin.isActive());
        config.set(path + ".entry_fee", assassin.getEntryFeePaid());
        
        AssassinPlugin.getInstance().saveData();
    }
    
    private void savePlayerSession(PlayerContractSession session) {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        String path = "player_sessions." + session.getSessionId().toString();
        
        config.set(path + ".buyer", session.getBuyerId().toString());
        config.set(path + ".assassin", session.getAssassinId().toString());
        config.set(path + ".target", session.getTargetId().toString());
        config.set(path + ".proposed_price", session.getProposedPrice());
        config.set(path + ".price_accepted", session.isPriceAccepted());
        config.set(path + ".active", session.isActive());
        config.set(path + ".completed", session.isCompleted());
        config.set(path + ".success", session.isSuccess());
        config.set(path + ".completion_time", session.getCompletionTime());
        
        AssassinPlugin.getInstance().saveData();
    }
    
    private void updateContract(AssassinContract contract) {
        saveContract(contract);
    }
    
    private void updatePlayerSession(PlayerContractSession session) {
        savePlayerSession(session);
    }
    
    public void saveAllContracts() {
        for (AssassinContract contract : activeContracts.values()) {
            saveContract(contract);
        }
        for (PlayerContractSession session : playerSessions.values()) {
            savePlayerSession(session);
        }
    }
    
    // ========== 合约检查任务 ==========
    
    public void checkActiveContracts() {
        // 检查AI杀手合约
        Iterator<Map.Entry<UUID, AssassinContract>> iterator = activeContracts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AssassinContract> entry = iterator.next();
            AssassinContract contract = entry.getValue();
            
            if (contract.isCompleted()) {
                if (contract.getTier() == 3) {
                    stopRainForContract(contract.getContractId());
                }
                iterator.remove();
                continue;
            }
            
            Player target = Bukkit.getPlayer(contract.getTargetId());
            if (target == null || !target.isOnline()) {
                contract.setCompleted(true);
                contract.setSuccess(false);
                
                if (contract.getTier() == 3) {
                    stopRainForContract(contract.getContractId());
                }
                
                updateContract(contract);
                iterator.remove();
                continue;
            }
            
            if (contract.isActive() && contract.getAssassinId() != null) {
                Entity assassin = Bukkit.getEntity(contract.getAssassinId());
                if (assassin == null || assassin.isDead()) {
                    contract.setCompleted(true);
                    contract.setSuccess(false);
                    
                    if (contract.getTier() == 3) {
                        stopRainForContract(contract.getContractId());
                    }
                    
                    if (assassin instanceof Drowned) {
                        cleanupDrownedAI(assassin.getUniqueId());
                    }
                    
                    updateContract(contract);
                    iterator.remove();
                }
            }
        }
        
        // 检查玩家刺客合约
        Iterator<Map.Entry<UUID, PlayerContractSession>> playerIterator = playerSessions.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<UUID, PlayerContractSession> entry = playerIterator.next();
            PlayerContractSession session = entry.getValue();
            
            if (session.isCompleted()) {
                playerIterator.remove();
                continue;
            }
            
            Player target = Bukkit.getPlayer(session.getTargetId());
            if (target == null || !target.isOnline()) {
                session.setCompleted(true);
                session.setSuccess(false);
                updatePlayerSession(session);
                playerIterator.remove();
                continue;
            }
            
            if (session.isActive()) {
                Player assassin = Bukkit.getPlayer(session.getAssassinId());
                if (assassin == null || !assassin.isOnline()) {
                    session.setCompleted(true);
                    session.setSuccess(false);
                    updatePlayerSession(session);
                    playerIterator.remove();
                }
            }
        }
    }
    
    // ========== 辅助方法 ==========
    
    private boolean hasSufficientCredits(Player player, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return false;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            int currentCredits = (int) getCredits.invoke(creditManager, player);
            
            return currentCredits >= amount;
        } catch (Exception e) {
            return false;
        }
    }
    
    private int getPlayerCredits(Player player) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return 0;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            return (int) getCredits.invoke(creditManager, player);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private int getCredits(Player player) {
        return getPlayerCredits(player);
    }
    
    private void setCredits(Player player, int credits) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method setCredits = creditManager.getClass().getMethod("setCredits", Player.class, int.class);
            setCredits.invoke(creditManager, player, credits);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private boolean deductCredits(Player player, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return false;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method removeCredits = creditManager.getClass().getMethod("removeCredits", Player.class, int.class);
            return (boolean) removeCredits.invoke(creditManager, player, amount);
        } catch (Exception e) {
            return false;
        }
    }
    
    private void addCredits(Player player, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method addCredits = creditManager.getClass().getMethod("addCredits", Player.class, int.class);
            addCredits.invoke(creditManager, player, amount);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private void transferCredits(Player from, Player to, int amount) {
        try {
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return;
            
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            Method removeCredits = creditManager.getClass().getMethod("removeCredits", Player.class, int.class);
            removeCredits.invoke(creditManager, from, amount);
            
            Method addCredits = creditManager.getClass().getMethod("addCredits", Player.class, int.class);
            addCredits.invoke(creditManager, to, amount);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private String getTargetName(UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        return target != null ? target.getName() : "未知";
    }
    
    private void refundContract(AssassinContract contract) {
        Player employer = Bukkit.getPlayer(contract.getEmployerId());
        if (employer != null) {
            int cost = getTierCost(contract.getTier());
            employer.sendMessage("§8[暗网] §e⚠ 目标离线，退还 " + (cost / 2) + " 信用点");
            
            try {
                Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
                if (creditPlugin != null) {
                    Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
                    Object creditManager = getCreditManager.invoke(creditPlugin);
                    
                    Method addCredits = creditManager.getClass().getMethod("addCredits", Player.class, int.class);
                    addCredits.invoke(creditManager, employer, cost / 2);
                }
            } catch (Exception e) {
                AssassinPlugin.getInstance().getLogger().warning("退还信用点时出错: " + e.getMessage());
            }
        }
    }
    
    // ========== 公共方法 ==========
    
    public int getAssassinEntryFee() {
        return assassinEntryFee;
    }
    
    public long getCooldownTime() { 
        return COOLDOWN_TIME; 
    }
    
    public void setCooldownTime(long cooldown) { 
        this.COOLDOWN_TIME = cooldown;
        AssassinPlugin.getInstance().getConfig().set("cooldown", cooldown);
        AssassinPlugin.getInstance().saveConfig();
    }
    
    public boolean clearPlayerCooldown(Player player) {
        if (playerCooldowns.containsKey(player.getUniqueId())) {
            playerCooldowns.remove(player.getUniqueId());
            return true;
        }
        return false;
    }
    
    public void clearAllCooldowns() { 
        playerCooldowns.clear(); 
    }
    
    public long getPlayerCooldownRemaining(Player player) {
        if (!playerCooldowns.containsKey(player.getUniqueId())) return 0;
        long lastUse = playerCooldowns.get(player.getUniqueId());
        long elapsed = System.currentTimeMillis() - lastUse;
        return Math.max(0, COOLDOWN_TIME - elapsed);
    }
    
    public int getTierCost(int tier) {
        switch (tier) {
            case 1: return 30;
            case 2: return 40;
            case 3: return 80;
            default: return 30;
        }
    }
    
    public boolean isKillingDay() {
        // 简化实现，实际应该从配置或状态获取
        return false;
    }
    
    public List<AssassinContract> getPlayerContracts(UUID playerId) {
        List<AssassinContract> result = new ArrayList<>();
        for (AssassinContract contract : activeContracts.values()) {
            if (contract.getEmployerId().equals(playerId) || contract.getTargetId().equals(playerId)) {
                result.add(contract);
            }
        }
        return result;
    }
}
