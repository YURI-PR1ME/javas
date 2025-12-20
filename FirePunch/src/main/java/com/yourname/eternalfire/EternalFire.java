package com.yourname.eternalfire;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class EternalFire extends JavaPlugin implements Listener {
    
    private final Set<Location> eternalFires = Collections.synchronizedSet(new HashSet<>());
    private final NamespacedKey burningArrowKey = new NamespacedKey(this, "burning_arrow");
    private final NamespacedKey zeroStoneKey = new NamespacedKey(this, "zero_stone");
    private final NamespacedKey fireWandKey = new NamespacedKey(this, "fire_wand");
    
    // 存储实体上次点燃位置
    private final Map<UUID, Location> lastIgniteLocation = new HashMap<>();
    // 存储实体碰撞传播冷却时间
    private final Map<UUID, Long> collisionCooldown = new HashMap<>();
    
    private ItemStack fireWand;
    private ItemStack zeroStone;
    
    @Override
    public void onEnable() {
        // 初始化自定义物品
        createFireWand();
        createZeroStone();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 启动火焰蔓延检测任务
        new FireSpreadTask(this).runTaskTimer(this, 20L, 20L);
        
        // 启动实体碰撞检测任务
        new EntityCollisionTask(this).runTaskTimer(this, 5L, 5L); // 每0.25秒检测一次
        
        // 注册命令
        Objects.requireNonNull(getCommand("getfirewand")).setExecutor(new FireWandCommand(this));
        Objects.requireNonNull(getCommand("getzerostone")).setExecutor(new ZeroStoneCommand(this));
        
        getLogger().info("永恒之火插件已启用！");
    }
    
    @Override
    public void onDisable() {
        eternalFires.clear();
        lastIgniteLocation.clear();
        collisionCooldown.clear();
        getLogger().info("永恒之火插件已禁用！");
    }
    
    private void createFireWand() {
        fireWand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = fireWand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "火焰魔杖");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "右键点燃路径上的所有生物和方块",
                    ChatColor.GRAY + "火焰会一直蔓延直到烧尽所有可燃物"
            ));
            
            // 添加持久化数据标记
            meta.getPersistentDataContainer().set(fireWandKey, PersistentDataType.BYTE, (byte) 1);
            fireWand.setItemMeta(meta);
        }
    }
    
    private void createZeroStone() {
        zeroStone = new ItemStack(Material.BLUE_ICE);
        ItemMeta meta = zeroStone.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "零度石");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "防止点燃走过的方块",
                    ChatColor.RED + "不能免疫燃烧伤害"
            ));
            
            // 添加持久化数据标记
            meta.getPersistentDataContainer().set(zeroStoneKey, PersistentDataType.BYTE, (byte) 1);
            zeroStone.setItemMeta(meta);
        }
    }
    
    // 获取火焰魔杖
    public ItemStack getFireWand() {
        return fireWand.clone();
    }
    
    // 获取零度石
    public ItemStack getZeroStone() {
        return zeroStone.clone();
    }
    
    // 检查方块是否可燃
    public boolean isFlammable(Block block) {
        Material type = block.getType();
        return type == Material.OAK_PLANKS || 
               type == Material.SPRUCE_PLANKS ||
               type == Material.BIRCH_PLANKS ||
               type == Material.JUNGLE_PLANKS ||
               type == Material.ACACIA_PLANKS ||
               type == Material.DARK_OAK_PLANKS ||
               type == Material.OAK_LEAVES ||
               type == Material.SPRUCE_LEAVES ||
               type == Material.BIRCH_LEAVES ||
               type == Material.JUNGLE_LEAVES ||
               type == Material.ACACIA_LEAVES ||
               type == Material.DARK_OAK_LEAVES ||
               type == Material.WHITE_WOOL ||
               type == Material.TNT ||
               type == Material.COAL_BLOCK ||
               type == Material.BOOKSHELF ||
               type == Material.LECTERN ||
               type == Material.CRAFTING_TABLE ||
               type.isFlammable();
    }
    
    // 点燃永恒之火
    public void igniteEternalFire(Block block, Entity igniter) {
        if (!isFlammable(block) && block.getType() != Material.AIR) {
            return;
        }
        
        // 在方块上方点燃火
        Block fireBlock = block.getRelative(0, 1, 0);
        if (fireBlock.getType() == Material.AIR || fireBlock.getType() == Material.FIRE) {
            if (fireBlock.getType() != Material.FIRE) {
                fireBlock.setType(Material.FIRE);
            }
            
            // 标记为永恒之火
            eternalFires.add(fireBlock.getLocation().clone());
            
            // 显示自定义火焰粒子
            fireBlock.getWorld().spawnParticle(
                    Particle.FLAME,
                    fireBlock.getLocation().add(0.5, 0.5, 0.5),
                    10, 0.2, 0.2, 0.2, 0.05
            );
            
            // 播放音效
            fireBlock.getWorld().playSound(
                    fireBlock.getLocation(),
                    Sound.ITEM_FIRECHARGE_USE,
                    1.0f, 1.0f
            );
        }
    }
    
    // 设置实体燃烧（确保燃烧至死亡）
    public void setEntityOnFire(LivingEntity entity, int ticks) {
        // 设置一个非常长的燃烧时间，确保实体死亡
        int extendedFireTicks = Math.max(ticks, 200); // 至少10秒
        entity.setFireTicks(extendedFireTicks);

        // 创建一个任务来持续刷新实体的燃烧时间，模拟永不熄灭的火焰
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid() && !entity.isDead()) {
                    // 如果实体还活着，就重置燃烧时间
                    // 但为了避免无限循环，我们检查当前火刻是否大于0
                    if (entity.getFireTicks() > 0) {
                        entity.setFireTicks(Math.max(entity.getFireTicks(), 100)); // 始终保持至少5秒火刻
                    } else {
                        // 如果火灭了，就取消这个任务
                        this.cancel();
                    }
                    
                    // 显示自定义火焰粒子，区别于原版
                    entity.getWorld().spawnParticle(
                            Particle.SOUL_FIRE_FLAME,
                            entity.getLocation().add(0, 1, 0),
                            5, 0.3, 0.5, 0.3, 0.02
                    );
                } else {
                    // 如果实体无效或已死亡，取消任务
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 每秒钟检查一次
    }
    
    // 检查实体是否持有零度石
    public boolean hasZeroStone(LivingEntity entity) {
        if (entity instanceof Player player) {
            // 检查主手、副手和装备栏
            for (ItemStack item : Arrays.asList(
                    player.getInventory().getItemInMainHand(),
                    player.getInventory().getItemInOffHand(),
                    player.getInventory().getBoots(),
                    player.getInventory().getLeggings(),
                    player.getInventory().getChestplate(),
                    player.getInventory().getHelmet()
            )) {
                if (item != null && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.getPersistentDataContainer().has(zeroStoneKey, PersistentDataType.BYTE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    // 获取永恒之火集合
    public Set<Location> getEternalFires() {
        return eternalFires;
    }
    
    // 获取命名空间键
    public NamespacedKey getFireWandKey() { return fireWandKey; }
    public NamespacedKey getBurningArrowKey() { return burningArrowKey; }
    public NamespacedKey getZeroStoneKey() { return zeroStoneKey; }
    
    // 检查碰撞冷却时间
    public boolean isCollisionOnCooldown(UUID entityId) {
        Long lastCollision = collisionCooldown.get(entityId);
        if (lastCollision == null) return false;
        
        // 2秒冷却时间
        return System.currentTimeMillis() - lastCollision < 2000;
    }
    
    // 设置碰撞冷却时间
    public void setCollisionCooldown(UUID entityId) {
        collisionCooldown.put(entityId, System.currentTimeMillis());
    }
    
    // 魔杖右键事件
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(fireWandKey, PersistentDataType.BYTE)) {
                
                if (event.getAction().toString().contains("RIGHT")) {
                    event.setCancelled(true);
                    
                    // 发射火焰射线
                    shootFireRay(player);
                    
                    // 播放音效
                    player.getWorld().playSound(player.getLocation(), 
                            Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
                }
            }
        }
    }
    
    // 发射火焰射线点燃路径
    private void shootFireRay(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        double range = 30.0; // 射程
        
        // 追踪方块和实体
        RayTraceResult blockResult = player.getWorld().rayTraceBlocks(
                start, direction, range,
                FluidCollisionMode.NEVER, true
        );
        
        double currentDistance = 0;
        double step = 0.5; // 检测步长
        
        while (currentDistance <= range) {
            Vector currentVector = direction.clone().multiply(currentDistance);
            Location currentLoc = start.clone().add(currentVector);
            
            // 检查当前点的方块
            Block block = currentLoc.getBlock();
            if (block.getType().isSolid() && block.getType() != Material.AIR) {
                igniteEternalFire(block, player);
            }
            
            // 检查当前点的实体
            for (Entity entity : player.getWorld().getNearbyEntities(currentLoc, 1, 1, 1)) {
                if (entity instanceof LivingEntity livingEntity && entity != player) {
                    setEntityOnFire(livingEntity, 200); // 点燃10秒
                }
            }
            
            // 显示射线粒子效果
            player.getWorld().spawnParticle(
                    Particle.DRAGON_BREATH,
                    currentLoc,
                    1, 0, 0, 0, 0
            );
            
            currentDistance += step;
            
            // 如果击中方块，提前结束
            if (blockResult != null && currentDistance >= blockResult.getHitPosition().distance(start.toVector())) {
                break;
            }
        }
    }
    
    // 玩家移动时点燃路径
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getFireTicks() > 0) {
            // 检查是否持有零度石
            if (hasZeroStone(player)) {
                return; // 持有零度石，不点燃方块
            }
            
            // 检查位置是否真的改变了
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
                // 计算移动的距离（忽略Y轴变化）
                Location fromFlat = new Location(from.getWorld(), from.getX(), 0, from.getZ());
                Location toFlat = new Location(to.getWorld(), to.getX(), 0, to.getZ());
                
                // 获取上次点燃的位置
                UUID playerId = player.getUniqueId();
                Location lastIgnite = lastIgniteLocation.get(playerId);
                
                // 如果上次没有记录，或者移动超过了3格，就点燃脚下的方块
                if (lastIgnite == null || toFlat.distance(lastIgnite) >= 3.0) {
                    // 点燃脚下的方块
                    Block block = to.getBlock();
                    igniteEternalFire(block, player);
                    // 更新上次点燃的位置
                    lastIgniteLocation.put(playerId, toFlat);
                }
            }
        }
    }
    
    // 实体攻击传播火焰
    @EventHandler
    public void onEntityAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity damager && 
            event.getEntity() instanceof LivingEntity target) {
            
            // 如果攻击者燃烧着，点燃目标
            if (damager.getFireTicks() > 0) {
                setEntityOnFire(target, 100); // 点燃5秒
            }
        }
    }
    
    // 骷髅发射燃烧箭
    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Skeleton skeleton) {
            if (skeleton.getFireTicks() > 0 && event.getProjectile() instanceof Arrow arrow) {
                // 标记为燃烧箭
                arrow.getPersistentDataContainer().set(burningArrowKey, PersistentDataType.BYTE, (byte) 1);
                
                // 添加火焰粒子轨迹
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (arrow.isValid() && !arrow.isOnGround()) {
                            arrow.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    arrow.getLocation(),
                                    2, 0.1, 0.1, 0.1, 0.01
                            );
                        } else {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(this, 0L, 1L);
            }
        }
    }
    
    // 燃烧箭点燃方块
    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            if (arrow.getPersistentDataContainer().has(burningArrowKey, PersistentDataType.BYTE)) {
                if (event.getHitBlock() != null) {
                    igniteEternalFire(event.getHitBlock(), arrow);
                }
                
                if (event.getHitEntity() instanceof LivingEntity hitEntity) {
                    setEntityOnFire(hitEntity, 100);
                }
            }
        }
    }
    
    // 实体碰撞传播火焰
    public void checkEntityCollisions() {
        // 获取所有燃烧的实体
        for (World world : getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getFireTicks() > 0 && !isCollisionOnCooldown(entity.getUniqueId())) {
                    // 检查周围2格范围内的其他实体
                    for (Entity nearby : entity.getNearbyEntities(2, 2, 2)) {
                        if (nearby instanceof LivingEntity nearbyLiving && nearby != entity) {
                            // 检查碰撞箱是否重叠
                            if (entity.getBoundingBox().overlaps(nearbyLiving.getBoundingBox())) {
                                // 点燃附近的实体
                                setEntityOnFire(nearbyLiving, 100);
                                // 设置冷却时间
                                setCollisionCooldown(entity.getUniqueId());
                                break; // 一次只点燃一个实体，避免性能问题
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 禁用抗火药水
    @EventHandler
    public void onPotionEffectApply(EntityPotionEffectEvent event) {
        if (event.getModifiedType() == PotionEffectType.FIRE_RESISTANCE) {
            event.setCancelled(true);
            
            // 显示提示信息
            if (event.getEntity() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "抗火药水在这个服务器上失效了！");
            }
        }
    }
    
    // 防止永恒之火被熄灭
    @EventHandler
    public void onFireExtinguish(BlockBurnEvent event) {
        if (eternalFires.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FIRE && 
            eternalFires.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
    
    // 清理死亡实体的数据
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        lastIgniteLocation.remove(entityId);
        collisionCooldown.remove(entityId);
    }
    
    // 火焰魔杖命令
    public static class FireWandCommand implements CommandExecutor {
        private final EternalFire plugin;
        
        public FireWandCommand(EternalFire plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (sender instanceof Player player) {
                if (player.hasPermission("eternalfire.admin")) {
                    player.getInventory().addItem(plugin.getFireWand());
                    player.sendMessage(ChatColor.GREEN + "已获得火焰魔杖");
                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "你没有权限使用此命令");
                }
            } else {
                sender.sendMessage("只有玩家可以使用此命令");
            }
            return false;
        }
    }
    
    // 零度石命令
    public static class ZeroStoneCommand implements CommandExecutor {
        private final EternalFire plugin;
        
        public ZeroStoneCommand(EternalFire plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (sender instanceof Player player) {
                if (player.hasPermission("eternalfire.admin")) {
                    player.getInventory().addItem(plugin.getZeroStone());
                    player.sendMessage(ChatColor.GREEN + "已获得零度石");
                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "你没有权限使用此命令");
                }
            } else {
                sender.sendMessage("只有玩家可以使用此命令");
            }
            return false;
        }
    }
}
