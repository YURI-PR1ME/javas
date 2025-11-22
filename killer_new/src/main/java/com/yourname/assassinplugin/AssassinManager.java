package com.yourname.assassinplugin;

import org.bukkit.entity.Mob;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AssassinManager {
    
    private final Map<UUID, AssassinContract> activeContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private long COOLDOWN_TIME; // 从配置读取
    
    // 存储狙击手的AI任务
    private final Map<UUID, BukkitRunnable> sniperAITasks = new ConcurrentHashMap<>();
    
    public AssassinManager() {
        loadConfig();
        loadActiveContracts();
        registerDarkWebAccessItemRecipe();
        registerRecipeBookRecipe();
    }
    
    // 从配置加载设置
    private void loadConfig() {
        COOLDOWN_TIME = AssassinPlugin.getInstance().getConfig().getLong("cooldown", 600000);
    }
    
    // 重新加载配置
    public void reloadConfig() {
        AssassinPlugin.getInstance().reloadConfig();
        loadConfig();
    }
    
    // 获取冷却时间
    public long getCooldownTime() {
        return COOLDOWN_TIME;
    }
    
    // 设置冷却时间
    public void setCooldownTime(long cooldown) {
        this.COOLDOWN_TIME = cooldown;
        // 保存到配置文件
        AssassinPlugin.getInstance().getConfig().set("cooldown", cooldown);
        AssassinPlugin.getInstance().saveConfig();
    }
    
    // 清除玩家冷却
    public boolean clearPlayerCooldown(Player player) {
        if (playerCooldowns.containsKey(player.getUniqueId())) {
            playerCooldowns.remove(player.getUniqueId());
            return true;
        }
        return false;
    }
    
    // 清除所有玩家冷却
    public void clearAllCooldowns() {
        playerCooldowns.clear();
    }
    
    // 获取玩家剩余冷却时间（毫秒）
    public long getPlayerCooldownRemaining(Player player) {
        if (!playerCooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        long lastUse = playerCooldowns.get(player.getUniqueId());
        long elapsed = System.currentTimeMillis() - lastUse;
        return Math.max(0, COOLDOWN_TIME - elapsed);
    }
    
    // 创建暗网接入口物品
    public ItemStack createDarkWebAccessItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§8暗网接入口");
        meta.setLore(Arrays.asList(
            "§7右键打开买凶界面",
            "§8————————————",
            "§c⚠ 非法物品",
            "§e造价昂贵，谨慎使用"
        ));
        
        // 添加NBT标签
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "dark_web_access");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        item.setItemMeta(meta);
        return item;
    }
    
    // 检查是否是暗网接入口
    public boolean isDarkWebAccessItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "dark_web_access");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    // 注册合成配方（昂贵的造价）
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
    
    // 创建暗网配方书
    public ItemStack createRecipeBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        
        meta.setTitle("§8暗网接入指南");
        meta.setAuthor("匿名黑客");
        meta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
        
        // 设置书的内容
        java.util.List<String> pages = new java.util.ArrayList<>();
        pages.add("§0暗网接入指南\n\n§7这本书记载了如何\n制造暗网接入口的方法\n\n§8警告：\n§4使用此技术可能触犯法律\n后果自负！");
        pages.add("§0合成配方\n\n§6暗网接入口\n\n需要材料：\n§7黑曜石 x6\n§5末影之眼 x1\n§b下界合金锭 x2\n§b钻石块 x1\n§6信标 x1");
        pages.add("§0合成布局\n\n§8O E O\n§8N D N\n§8O B O\n\n§7O=黑曜石\n§5E=末影之眼\n§bN=下界合金锭\n§bD=钻石块\n§6B=信标");
        pages.add("§0使用说明\n\n§7手持暗网接入口\n右键打开买凶界面\n\n§8功能：\n§7- 选择目标玩家\n§7- 选择杀手等级\n§7- 发布暗杀合约");
        pages.add("§0杀手等级\n\n§7Ⅰ级 - 30信用点\n普通近战杀手\n\n§6Ⅱ级 - 40信用点\n精英卫道士，抢夺信用点\n\n§4Ⅲ级 - 80信用点\n骷髅狙击手，抢夺信用点");
        pages.add("§0注意事项\n\n§7- 买凶有冷却时间\n§7- 合约一旦发布无法取消\n§7- 失败不退还信用点\n§7- 小心被反杀！\n\n§8保持匿名，注意安全");
        
        meta.setPages(pages);
        
        // 添加NBT标签
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "recipe_book");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        
        book.setItemMeta(meta);
        return book;
    }
    
    // 检查是否是配方书
    public boolean isRecipeBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
        if (meta == null) return false;
        
        NamespacedKey key = new NamespacedKey(AssassinPlugin.getInstance(), "recipe_book");
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    
    // 注册配方书合成（非常昂贵！）
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
    
    // 创建买凶合约
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
        
        // 检查信用点 - 实际扣除信用点
        int cost = getTierCost(tier);
        
        // 尝试通过反射调用CreditPlugin扣除信用点
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
        
        // 延迟生成杀手（给目标反应时间）
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnAssassin(contract);
            }
        }.runTaskLater(AssassinPlugin.getInstance(), 100L); // 5秒后生成
        
        saveContract(contract);
        return true;
    }
    
    // 通过反射调用CreditPlugin扣除信用点
    private boolean deductCredits(Player player, int amount) {
        try {
            // 获取CreditPlugin实例
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) {
                player.sendMessage("§c❌ 信用点插件未找到！");
                return false;
            }
            
            // 获取CreditManager
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            // 获取当前信用点
            Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            int currentCredits = (int) getCredits.invoke(creditManager, player);
            
            // 检查信用点是否足够
            if (currentCredits < amount) {
                return false;
            }
            
            // 扣除信用点
            Method removeCredits = creditManager.getClass().getMethod("removeCredits", Player.class, int.class);
            return (boolean) removeCredits.invoke(creditManager, player, amount);
            
        } catch (Exception e) {
            player.sendMessage("§c❌ 信用点系统错误，请联系管理员");
            AssassinPlugin.getInstance().getLogger().warning("扣除信用点时出错: " + e.getMessage());
            return false;
        }
    }
    
    // 生成杀手
    private void spawnAssassin(AssassinContract contract) {
        Player target = Bukkit.getPlayer(contract.getTargetId());
        if (target == null || !target.isOnline()) {
            // 目标离线，返还部分信用点
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
        
        // 设置杀手的目标玩家UUID
        assassin.getPersistentDataContainer().set(
            new NamespacedKey(AssassinPlugin.getInstance(), "assassin_target"),
            PersistentDataType.STRING,
            target.getUniqueId().toString()
        );
        
        contract.setAssassinId(assassin.getUniqueId());
        contract.setActive(true);
        
        // 发送警告给目标
        target.sendMessage("§c⚔️ 你感受到了杀气！有人买凶要你的命！");
        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        
        updateContract(contract);
    }
    
    // 生成第一档杀手（普通近战）
    private LivingEntity spawnTier1Assassin(Location location, Player target) {
        Zombie assassin = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        
        // 设置属性
        assassin.setCustomName("§8刺客 §7(Ⅰ级)");
        assassin.setCustomNameVisible(true);
        assassin.setAdult();
        
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
        assassin.setHealth(30.0);
        assassin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(6.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        
        // 设置装备
        assassin.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        assassin.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        assassin.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        
        // 设置目标 - 只攻击目标玩家
        setAssassinTarget(assassin, target);
        
        return assassin;
    }
    
    // 生成第二档杀手（精英卫道士，可抢夺信用点）
    private LivingEntity spawnTier2Assassin(Location location, Player target) {
        Vindicator assassin = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);
        
        // 设置属性
        assassin.setCustomName("§6精英卫道士 §6(Ⅱ级)");
        assassin.setCustomNameVisible(true);
        
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0);
        assassin.setHealth(50.0);
        assassin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(8.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
        
        // 添加药水效果 - 使用兼容性更好的方法
        addPotionEffectSafely(assassin, "SPEED", 1);
        addPotionEffectSafely(assassin, "INCREASE_DAMAGE", 0); // 力量效果
        
        // 设置装备
        assassin.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        
        // 设置目标 - 只攻击目标玩家
        setAssassinTarget(assassin, target);
        
        return assassin;
    }
    
    // 生成第三档杀手（骷髅狙击手，力量弓箭+下界合金甲+抢夺信用点）
    private LivingEntity spawnTier3Assassin(Location location, Player target) {
        Skeleton assassin = (Skeleton) location.getWorld().spawnEntity(location, EntityType.SKELETON);
        
        // 设置属性
        assassin.setCustomName("§4骷髅狙击手 §4(Ⅲ级)");
        assassin.setCustomNameVisible(true);
        
        // 通过直接设置属性来提高能力
        assassin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        assassin.setHealth(80.0);
        assassin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.25);
        
        // 设置装备 - 力量10的弓
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.addEnchant(Enchantment.POWER, 10, true); // 力量10
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true); // 无限
        bowMeta.addEnchant(Enchantment.FLAME, 1, true); // 火焰
        bowMeta.setDisplayName("§b狙击弓");
        bow.setItemMeta(bowMeta);
        assassin.getEquipment().setItemInMainHand(bow);
        assassin.getEquipment().setItemInMainHandDropChance(0.0f);
        
        // 设置全套保护4下界合金甲
        assassin.getEquipment().setHelmet(createProtection4NetheriteHelmet());
        assassin.getEquipment().setChestplate(createProtection4NetheriteChestplate());
        assassin.getEquipment().setLeggings(createProtection4NetheriteLeggings());
        assassin.getEquipment().setBoots(createProtection4NetheriteBoots());
        
        // 设置目标并使用新的狙击AI系统
        setSniperTarget(assassin, target);
        
        return assassin;
    }
    
    // 创建保护4下界合金头盔
    private ItemStack createProtection4NetheriteHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金头盔");
        helmet.setItemMeta(meta);
        return helmet;
    }
    
    // 创建保护4下界合金胸甲
    private ItemStack createProtection4NetheriteChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chestplate.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金胸甲");
        chestplate.setItemMeta(meta);
        return chestplate;
    }
    
    // 创建保护4下界合金护腿
    private ItemStack createProtection4NetheriteLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = leggings.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金护腿");
        leggings.setItemMeta(meta);
        return leggings;
    }
    
    // 创建保护4下界合金靴子
    private ItemStack createProtection4NetheriteBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        addProtectionEnchantment(meta, 4);
        meta.setDisplayName("§5下界合金靴子");
        boots.setItemMeta(meta);
        return boots;
    }
    
    // 安全添加保护附魔的方法
    private void addProtectionEnchantment(ItemMeta meta, int level) {
        try {
            // 尝试不同的保护附魔名称
            String[] protectionNames = {"PROTECTION_ENVIRONMENTAL", "PROTECTION"};
            
            for (String enchantName : protectionNames) {
                try {
                    Enchantment protection = Enchantment.getByName(enchantName);
                    if (protection != null) {
                        meta.addEnchant(protection, level, true);
                        return; // 成功添加后返回
                    }
                } catch (Exception e) {
                    // 继续尝试下一个名称
                }
            }
            
            // 如果所有方法都失败，记录警告
            AssassinPlugin.getInstance().getLogger().warning("无法添加保护附魔，将使用未附魔的装备");
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("添加保护附魔时出错: " + e.getMessage());
        }
    }
    
    // 安全添加药水效果的方法
    private void addPotionEffectSafely(LivingEntity entity, String effectName, int amplifier) {
        try {
            PotionEffectType effectType = PotionEffectType.getByName(effectName);
            if (effectType != null) {
                entity.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, amplifier));
            }
        } catch (Exception e) {
            // 忽略错误，不添加效果
            AssassinPlugin.getInstance().getLogger().warning("无法添加药水效果: " + effectName);
        }
    }
   
    // 专门为骷髅狙击手设置目标和狙击AI系统
    private void setSniperTarget(LivingEntity assassin, Player target) {
        if (!(assassin instanceof Skeleton)) return;
        
        Skeleton sniper = (Skeleton) assassin;
        
        // 立即设置目标
        sniper.setTarget(target);
        
        // 创建狙击AI任务
        BukkitRunnable aiTask = new BukkitRunnable() {
            private int shotCount = 0;
            private long lastShotTime = 0;
            private boolean inRapidFireMode = false;
            private int rapidFireShots = 0;
            private long rapidFireStartTime = 0;
            
            private final long NORMAL_SHOT_COOLDOWN = 60L; // 3秒 = 60 ticks
            private final long RAPID_FIRE_DURATION = 80L; // 4秒 = 80 ticks
            private final int RAPID_FIRE_SHOT_COUNT = 4; // 连射4箭
            private final long RAPID_FIRE_SHOT_COOLDOWN = 5L; // 0.25秒 = 5 ticks
            
            @Override
            public void run() {
                // 检查狙击手是否有效
                if (!sniper.isValid() || sniper.isDead()) {
                    cleanupSniperAI(sniper.getUniqueId());
                    this.cancel();
                    return;
                }
                
                // 检查目标是否存在和状态
                if (target == null || !target.isOnline() || target.isDead()) {
                    // 目标离线或死亡，立即退场
                    cleanupSniperAI(sniper.getUniqueId());
                    sniper.remove();
                    this.cancel();
                    return;
                }
                
                // 强制设置目标，确保始终追踪目标玩家
                sniper.setTarget(target);
                
                // 每 tick 检查距离并调整行为
                double distance = sniper.getLocation().distance(target.getLocation());
                
                // 如果距离过远，传送到合理位置（避免卡住）
                if (distance > 50) {
                    Location newLocation = findSpawnLocation(target.getLocation(), 20, 35);
                    if (newLocation != null) {
                        sniper.teleport(newLocation);
                    }
                }
                
                long currentTime = System.currentTimeMillis();
                
                if (inRapidFireMode) {
                    // 连射模式
                    handleRapidFireMode(sniper, target, currentTime);
                } else {
                    // 普通射击模式
                    handleNormalFireMode(sniper, target, currentTime);
                }
                
                // 确保狙击手始终持有弓
                if (sniper.getEquipment().getItemInMainHand().getType() != Material.BOW) {
                    ItemStack bow = createSniperBow();
                    sniper.getEquipment().setItemInMainHand(bow);
                }
            }
            
            private void handleNormalFireMode(Skeleton sniper, Player target, long currentTime) {
                if (currentTime - lastShotTime > NORMAL_SHOT_COOLDOWN * 50) { // 转换为毫秒
                    // 检查视线是否畅通
                    if (sniper.hasLineOfSight(target)) {
                        // 发射强力箭矢
                        shootSniperArrow(sniper, target);
                        lastShotTime = currentTime;
                        shotCount++;
                        
                        // 每3发进入连射模式
                        if (shotCount >= 3) {
                            shotCount = 0;
                            inRapidFireMode = true;
                            rapidFireShots = 0;
                            rapidFireStartTime = currentTime;
                            target.sendMessage("§c💥 狙击手进入连射模式！");
                        }
                    }
                }
            }
            
            private void handleRapidFireMode(Skeleton sniper, Player target, long currentTime) {
                long timeInRapidFire = currentTime - rapidFireStartTime;
                
                // 检查连射模式是否结束
                if (timeInRapidFire > RAPID_FIRE_DURATION * 50) {
                    inRapidFireMode = false;
                    return;
                }
                
                // 连射模式中的射击逻辑
                if (rapidFireShots < RAPID_FIRE_SHOT_COUNT) {
                    long timeSinceLastRapidShot = currentTime - lastShotTime;
                    if (timeSinceLastRapidShot > RAPID_FIRE_SHOT_COOLDOWN * 50) {
                        if (sniper.hasLineOfSight(target)) {
                            shootSniperArrow(sniper, target);
                            lastShotTime = currentTime;
                            rapidFireShots++;
                        }
                    }
                }
            }
            
            private void shootSniperArrow(Skeleton sniper, Player target) {
                // 创建箭矢
                Arrow arrow = sniper.launchProjectile(Arrow.class);
                
                // 设置箭矢属性
                arrow.setDamage(15.0); // 基础伤害
                arrow.setKnockbackStrength(2); // 击退效果
                arrow.setFireTicks(100); // 火焰效果
                
                // 设置箭矢速度为普通箭矢的1.5倍
                Vector direction = target.getEyeLocation().toVector().subtract(sniper.getEyeLocation().toVector()).normalize();
                arrow.setVelocity(direction.multiply(2.5));
                
                // 播放音效
                sniper.getWorld().playSound(sniper.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);
                
                // 给目标发送警告
                if (inRapidFireMode) {
                    target.sendMessage("§c🏹 狙击手连射中！");
                } else {
                    target.sendMessage("§c🎯 被狙击手锁定！");
                }
            }
        };
        
        // 存储AI任务以便后续管理
        sniperAITasks.put(sniper.getUniqueId(), aiTask);
        aiTask.runTaskTimer(AssassinPlugin.getInstance(), 0L, 1L);
    }
    
    // 创建狙击手专用弓
    private ItemStack createSniperBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.addEnchant(Enchantment.POWER, 10, true); // 力量10
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true); // 无限
        bowMeta.addEnchant(Enchantment.FLAME, 1, true); // 火焰
        bowMeta.setDisplayName("§b狙击弓");
        bow.setItemMeta(bowMeta);
        return bow;
    }
    
    // 清理狙击手的AI资源
    private void cleanupSniperAI(UUID sniperId) {
        // 移除AI任务
        BukkitRunnable aiTask = sniperAITasks.remove(sniperId);
        if (aiTask != null) {
            aiTask.cancel();
        }
    }
    
    // 设置杀手目标，确保只攻击目标玩家
    private void setAssassinTarget(LivingEntity assassin, Player target) {
        // 如果是骷髅狙击手，使用专门的方法
        if (assassin instanceof Skeleton) {
            setSniperTarget(assassin, target);
            return;
        }
        
        // 其他杀手使用原来的方法，但改进目标保持
        if (assassin instanceof Mob) {
            ((Mob) assassin).setTarget(target);
        }
        
        // 定期检查并重置目标，确保只攻击目标玩家
        new BukkitRunnable() {
            private int checkCount = 0;
            
            @Override
            public void run() {
                if (!assassin.isValid() || assassin.isDead()) {
                    this.cancel();
                    return;
                }
                
                // 如果杀手的目标不是目标玩家，重新设置目标
                if (assassin instanceof Mob) {
                    Mob mobAssassin = (Mob) assassin;
                    LivingEntity currentTarget = mobAssassin.getTarget();
                    
                    // 每5次检查（10秒）强制重置一次目标，防止目标丢失
                    if (checkCount % 5 == 0 || currentTarget == null || !currentTarget.equals(target)) {
                        mobAssassin.setTarget(target);
                    }
                }
                
                // 如果目标玩家死亡或离线，取消任务
                if (target == null || !target.isOnline() || target.isDead()) {
                    this.cancel();
                }
                
                checkCount++;
            }
        }.runTaskTimer(AssassinPlugin.getInstance(), 20L, 40L); // 每2秒检查一次
    }
    
    // 寻找生成位置（至少40格远）
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
    
    // 寻找安全位置
    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 从最高点往下找
        int y = world.getHighestBlockYAt(x, z);
        Location testLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
        
        // 检查位置是否安全（不是液体，有站立空间）
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
    
    // 处理杀手成功击杀
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
            
            // 根据档次处理信用点转移
            if (contract.getTier() >= 2) {
                // 实际转移信用点
                transferCredits(target, employer);
            }
            
            if (employer != null) {
                employer.sendMessage("§8[暗网] §a✅ 合约完成！目标 " + target.getName() + " 已被清除");
            }
            
            target.sendMessage("§c💀 你被职业杀手终结了...");
            
            // 移除AI任务
            if (assassin instanceof Skeleton) {
                cleanupSniperAI(assassin.getUniqueId());
            }
            
            // 移除杀手
            assassin.remove();
            updateContract(contract);
            
            // 从活跃合约中移除
            activeContracts.remove(contractId);
        }
    }
    
    // 处理杀手死亡
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
            
            // 移除AI任务
            if (assassin instanceof Skeleton) {
                cleanupSniperAI(assassin.getUniqueId());
            }
            
            updateContract(contract);
            
            // 从活跃合约中移除
            activeContracts.remove(contractId);
        }
    }
    
    // 转移信用点（从目标到雇主）
    private void transferCredits(Player from, Player to) {
        try {
            // 获取CreditPlugin实例
            Object creditPlugin = Bukkit.getPluginManager().getPlugin("CreditPlugin");
            if (creditPlugin == null) return;
            
            // 获取CreditManager
            Method getCreditManager = creditPlugin.getClass().getMethod("getCreditManager");
            Object creditManager = getCreditManager.invoke(creditPlugin);
            
            // 获取目标信用点
            Method getCredits = creditManager.getClass().getMethod("getCredits", Player.class);
            int targetCredits = (int) getCredits.invoke(creditManager, from);
            
            if (targetCredits > 0) {
                // 转移所有信用点
                Method removeCredits = creditManager.getClass().getMethod("removeCredits", Player.class, int.class);
                removeCredits.invoke(creditManager, from, targetCredits);
                
                Method addCredits = creditManager.getClass().getMethod("addCredits", Player.class, int.class);
                addCredits.invoke(creditManager, to, targetCredits);
                
                to.sendMessage("§8[暗网] §6💰 你获得了目标的所有信用点: " + targetCredits + " 点");
                from.sendMessage("§c💸 你的信用点被杀手抢走了！");
            }
        } catch (Exception e) {
            AssassinPlugin.getInstance().getLogger().warning("转移信用点时出错: " + e.getMessage());
        }
    }
    
    // 检查活跃合约
    public void checkActiveContracts() {
        Iterator<Map.Entry<UUID, AssassinContract>> iterator = activeContracts.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, AssassinContract> entry = iterator.next();
            AssassinContract contract = entry.getValue();
            
            // 移除已完成的合约
            if (contract.isCompleted()) {
                iterator.remove();
                removeContract(contract.getContractId());
                continue;
            }
            
            // 检查目标是否离线
            Player target = Bukkit.getPlayer(contract.getTargetId());
            if (target == null || !target.isOnline()) {
                contract.setCompleted(true);
                contract.setSuccess(false);
                updateContract(contract);
                iterator.remove();
                continue;
            }
            
            // 检查杀手是否存在
            if (contract.isActive() && contract.getAssassinId() != null) {
                Entity assassin = Bukkit.getEntity(contract.getAssassinId());
                if (assassin == null || assassin.isDead()) {
                    contract.setCompleted(true);
                    contract.setSuccess(false);
                    
                    // 移除AI任务
                    if (assassin instanceof Skeleton) {
                        cleanupSniperAI(assassin.getUniqueId());
                    }
                    
                    updateContract(contract);
                    iterator.remove();
                }
            }
        }
    }
    
    // 获取档次价格
    public int getTierCost(int tier) {
        switch (tier) {
            case 1: return 30;
            case 2: return 40;
            case 3: return 80;
            default: return 30;
        }
    }
    
    // 退还合约（目标离线等情况）
    private void refundContract(AssassinContract contract) {
        Player employer = Bukkit.getPlayer(contract.getEmployerId());
        if (employer != null) {
            int cost = getTierCost(contract.getTier());
            employer.sendMessage("§8[暗网] §e⚠ 目标离线，退还 " + (cost / 2) + " 信用点");
            
            // 实际退还信用点
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
    
    // 数据保存和加载
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
    
    private void updateContract(AssassinContract contract) {
        saveContract(contract);
    }
    
    private void removeContract(UUID contractId) {
        FileConfiguration config = AssassinPlugin.getInstance().getDataConfig();
        config.set("contracts." + contractId.toString(), null);
        AssassinPlugin.getInstance().saveData();
    }
    
    public void saveAllContracts() {
        for (AssassinContract contract : activeContracts.values()) {
            saveContract(contract);
        }
    }
    
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
            
            // 只加载未完成的合约
            if (!contract.isCompleted()) {
                activeContracts.put(contractId, contract);
            }
        }
    }
    
    // 获取玩家的活跃合约
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
