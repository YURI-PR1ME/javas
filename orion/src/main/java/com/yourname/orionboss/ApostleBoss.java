package com.yourname.orionboss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.*;

public class ApostleBoss {

    private LivingEntity apostle;
    private final Location spawnLocation;
    private final OrionBossPlugin plugin;
    private BukkitRunnable behaviorTask;
    private BossBar bossBar;
    private int currentPhase = 1; // 1: 幻术师, 2: 尸壳
    private final Random random = new Random();

    // 技能冷却
    private long lastFlameAttack = 0;
    private long lastPotionRain = 0;
    private long lastCosmicStorm = 0; // 新增：宇宙雷暴冷却
    private static final long FLAME_COOLDOWN = 15000;
    private static final long POTION_RAIN_COOLDOWN = 20000;
    private static final long COSMIC_STORM_COOLDOWN = 35000; // 35秒冷却

    // 镜像管理
    private final List<Husk> playerMirrors = new ArrayList<>();
    private BukkitRunnable swapTask;
    private final Map<UUID, Integer> playerMirrorCount = new HashMap<>();
    
    // 宇宙雷暴技能状态
    private boolean isCastingCosmicStorm = false;
    private BukkitRunnable cosmicStormTask;
    private int cosmicStormTicks = 0; // 新增：宇宙雷暴计时器

    // 传送相关
    private static final double TELEPORT_DISTANCE_THRESHOLD = 30.0; // 传送距离阈值
    private static final double TELEPORT_RADIUS = 5.0; // 传送半径（在目标周围5格内传送）

    public ApostleBoss(Location spawnLocation, OrionBossPlugin plugin) {
        this.spawnLocation = spawnLocation;
        this.plugin = plugin;
    }

    public void startFight() {
        spawnPhaseOne();
        createBossBar();
        startBehavior();
    }

    private void spawnPhaseOne() {
        // 生成幻术师（唤魔者）
        apostle = (LivingEntity) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.EVOKER);
        apostle.setCustomName("§6§lApostle - Phase 1");
        apostle.setCustomNameVisible(true);
        Objects.requireNonNull(apostle.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(300.0);
        apostle.setHealth(300.0);
        apostle.setPersistent(true);
        apostle.setRemoveWhenFarAway(false);

        // 给予保护效果
        apostle.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
    }

    private void switchToPhaseTwo() {
        currentPhase = 2;
        
        // 移除幻术师
        apostle.remove();

        // 生成尸壳
        apostle = (LivingEntity) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.HUSK);
        apostle.setCustomName("§4§lApostle");
        apostle.setCustomNameVisible(true);
        Objects.requireNonNull(apostle.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(300.0);
        apostle.setHealth(apostle.getMaxHealth() * 0.8); // 80%血量进入二阶段
        apostle.setPersistent(true);
        apostle.setRemoveWhenFarAway(false);

        // 给予效果
        apostle.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        apostle.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));
        
        // 复制最近玩家的装备
        copyNearestPlayerEquipment();
        
        // 召唤镜像
        summonMirrors();
        
        // 开始位置交换
        startSwapTask();

        // 更新BossBar
        updateBossBar();
    }

    private void copyNearestPlayerEquipment() {
        Player nearestPlayer = findNearestPlayer();
        if (nearestPlayer != null) {
            // 复制玩家装备
            apostle.getEquipment().setHelmet(nearestPlayer.getInventory().getHelmet());
            apostle.getEquipment().setChestplate(nearestPlayer.getInventory().getChestplate());
            apostle.getEquipment().setLeggings(nearestPlayer.getInventory().getLeggings());
            apostle.getEquipment().setBoots(nearestPlayer.getInventory().getBoots());
            apostle.getEquipment().setItemInMainHand(nearestPlayer.getInventory().getItemInMainHand());
            apostle.getEquipment().setItemInOffHand(nearestPlayer.getInventory().getItemInOffHand());
            
            // 复制装备的附魔和耐久
            copyEquipmentEnchantments(nearestPlayer);
        }
    }
    
    private void copyEquipmentEnchantments(Player player) {
        // 复制主手物品的附魔
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.hasItemMeta()) {
            ItemMeta meta = mainHand.getItemMeta();
            if (meta.hasEnchants()) {
                ItemStack apostleWeapon = apostle.getEquipment().getItemInMainHand();
                if (apostleWeapon != null) {
                    ItemMeta apostleMeta = apostleWeapon.getItemMeta();
                    for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
                        apostleMeta.addEnchant(enchant.getKey(), enchant.getValue(), true);
                    }
                    apostleWeapon.setItemMeta(apostleMeta);
                }
            }
        }
    }

    private void summonMirrors() {
        // 清理现有镜像
        cleanupMirrors();
        
        // 为每个玩家生成3个镜像
        for (Player player : getNearbyPlayers(50)) {
            for (int i = 0; i < 3; i++) {
                spawnMirror(player);
            }
        }
        
        Bukkit.broadcastMessage("§5§lApostle summons shadow mirrors of all players!");
    }

    private void spawnMirror(Player player) {
        // 使用EntityUtils生成镜像
        Location spawnLoc = EntityUtils.findSpawnLocationAround(apostle.getLocation(), 10);
        Husk mirror = EntityUtils.spawnPlayerClone(player, spawnLoc, "§8SHADOW OF ", plugin);
        
        playerMirrors.add(mirror);
        
        // 添加爆炸攻击AI
        addExplosiveAI(mirror);
        
        // 记录玩家镜像数量
        UUID playerId = player.getUniqueId();
        playerMirrorCount.put(playerId, playerMirrorCount.getOrDefault(playerId, 0) + 1);
    }
    
    private void addExplosiveAI(Husk mirror) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (mirror.isDead() || !mirror.isValid()) {
                    cancel();
                    return;
                }
                
                Player target = findNearestPlayerTo(mirror.getLocation());
                if (target != null && target.getLocation().distance(mirror.getLocation()) < 3) {
                    // 创建爆炸
                    Location explosionLoc = mirror.getLocation();
                    explosionLoc.getWorld().createExplosion(explosionLoc, 4.0f, true, true, mirror);
                    target.damage(20.0, mirror);
                    mirror.remove();
                    cancel();
                    
                    // 爆炸特效
                    explosionLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
                    explosionLoc.getWorld().spawnParticle(Particle.EXPLOSION, explosionLoc, 10, 1, 1, 1);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startSwapTask() {
        swapTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 如果正在释放宇宙雷暴，跳过位置交换
                if (isCastingCosmicStorm) {
                    return;
                }
                
                if (apostle.isDead() || !apostle.isValid() || playerMirrors.isEmpty()) {
                    cancel();
                    return;
                }

                // 与随机镜像交换位置
                Husk randomMirror = playerMirrors.get(random.nextInt(playerMirrors.size()));
                if (randomMirror.isValid() && !randomMirror.isDead()) {
                    Location apostleLoc = apostle.getLocation().clone();
                    Location mirrorLoc = randomMirror.getLocation().clone();

                    apostle.teleport(mirrorLoc);
                    randomMirror.teleport(apostleLoc);

                    // 交换效果
                    apostle.getWorld().spawnParticle(Particle.PORTAL, apostleLoc, 10, 0.5, 0.5, 0.5);
                    apostle.getWorld().spawnParticle(Particle.PORTAL, mirrorLoc, 10, 0.5, 0.5, 0.5);
                    apostle.getWorld().playSound(apostle.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    
                    // 给玩家提示
                    Bukkit.broadcastMessage("§5Apostle swaps position with a mirror!");
                }
            }
        };
        swapTask.runTaskTimer(plugin, 20L, 20L); // 1秒（20 ticks * 1）
    }

    private void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }

        String title = (currentPhase == 1) ? "§6§lApostle (Phase 1)" : "§4§lApostle (Phase 2)";
        bossBar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_12);
        bossBar.setProgress(apostle.getHealth() / apostle.getMaxHealth());
        bossBar.setVisible(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    private void updateBossBar() {
        if (bossBar == null || apostle == null || apostle.isDead()) return;

        String title = (currentPhase == 1) ? "§6§lApostle (Phase 1)" : "§4§lApostle (Phase 2)";
        bossBar.setTitle(title + " §7- §c❤ " + (int) apostle.getHealth() + "/" + (int) apostle.getMaxHealth());
        bossBar.setProgress(apostle.getHealth() / apostle.getMaxHealth());
    }

    private void startBehavior() {
        behaviorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!apostle.isValid() || apostle.isDead()) {
                    onDeath();
                    cancel();
                    return;
                }

                // 检查阶段转换
                if (currentPhase == 1 && apostle.getHealth() <= apostle.getMaxHealth() * 0.8) {
                    switchToPhaseTwo();
                }

                // 更新BossBar
                updateBossBar();

                // 执行阶段行为
                if (currentPhase == 1) {
                    phaseOneBehavior();
                } else if (currentPhase == 2) {
                    // 检查距离并传送
                    checkDistanceAndTeleport();
                    phaseTwoBehavior(); // 新增：二阶段行为
                }
                
                // 检查并补充死亡的镜像
                if (currentPhase == 2) {
                    checkAndReplenishMirrors();
                }
            }
        };
        behaviorTask.runTaskTimer(plugin, 0L, 20L); // 每秒执行一次
    }
    
    // 新增：检查距离并传送的方法
    private void checkDistanceAndTeleport() {
        // 如果正在释放宇宙雷暴，不执行传送
        if (isCastingCosmicStorm) {
            return;
        }
        
        Player target = findNearestPlayer();
        if (target == null || !target.isOnline() || target.isDead()) {
            return;
        }
        
        // 检查距离是否大于阈值
        double distance = apostle.getLocation().distance(target.getLocation());
        if (distance > TELEPORT_DISTANCE_THRESHOLD) {
            // 传送到目标相同高度（保持Y坐标相同）
            Location targetLoc = target.getLocation();
            
            // 在目标周围随机选择一个点（保持相同高度）
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * TELEPORT_RADIUS;
            double x = targetLoc.getX() + radius * Math.cos(angle);
            double z = targetLoc.getZ() + radius * Math.sin(angle);
            double y = targetLoc.getY(); // 保持相同高度
            
            Location teleportLocation = new Location(target.getWorld(), x, y, z);
            
            // 确保传送位置是安全的（不是方块内部）
            if (teleportLocation.getBlock().getType() != Material.AIR || 
                teleportLocation.clone().add(0, 1, 0).getBlock().getType() != Material.AIR) {
                // 如果不安全，尝试调整高度
                teleportLocation.setY(teleportLocation.getWorld().getHighestBlockYAt(teleportLocation) + 1);
            }
            
            // 执行传送
            apostle.teleport(teleportLocation);
            
            // 传送特效
            apostle.getWorld().playSound(apostle.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.8f);
            apostle.getWorld().spawnParticle(Particle.PORTAL, apostle.getLocation(), 30, 1, 1, 1);
            
            // 检查宇宙雷暴技能是否在冷却中
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCosmicStorm > COSMIC_STORM_COOLDOWN) {
                // 不在冷却中，释放宇宙雷暴技能
                useCosmicStorm(target);
                lastCosmicStorm = currentTime;
                
                // 广播消息
                Bukkit.broadcastMessage("§4§lApostle teleports and unleashes Cosmic Storm!");
            } else {
                // 在冷却中，只传送不释放技能
                Bukkit.broadcastMessage("§4§lApostle teleports to close the distance!");
            }
        }
    }

    private void phaseOneBehavior() {
        Player target = findNearestPlayer();
        if (target == null) return;

        long currentTime = System.currentTimeMillis();

        // 随机使用技能或普通攻击
        if (currentTime - lastFlameAttack > FLAME_COOLDOWN && random.nextDouble() < 0.3) {
            useFlameAttack(target);
            lastFlameAttack = currentTime;
        } else if (currentTime - lastPotionRain > POTION_RAIN_COOLDOWN && random.nextDouble() < 0.2) {
            usePotionRain(target);
            lastPotionRain = currentTime;
        } else {
            // 普通弓箭攻击
            shootArrow(target);
        }
    }
    
    // 新增：二阶段行为
    private void phaseTwoBehavior() {
        Player target = findNearestPlayer();
        if (target == null) return;
        
        long currentTime = System.currentTimeMillis();
        
        // 二阶段技能：宇宙雷暴
        if (currentTime - lastCosmicStorm > COSMIC_STORM_COOLDOWN && random.nextDouble() < 0.2) {
            useCosmicStorm(target);
            lastCosmicStorm = currentTime;
        }
        // 二阶段也可以使用一阶段的技能
        else if (currentTime - lastFlameAttack > FLAME_COOLDOWN && random.nextDouble() < 0.25) {
            useFlameAttack(target);
            lastFlameAttack = currentTime;
        } else if (currentTime - lastPotionRain > POTION_RAIN_COOLDOWN && random.nextDouble() < 0.15) {
            usePotionRain(target);
            lastPotionRain = currentTime;
        } else {
            // 普通攻击
            shootArrow(target);
        }
    }

    private void useFlameAttack(Player target) {
        // 发射3个缓慢跟踪的火焰弹
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                private final Fireball fireball;
                private int lifespan = 0;
                private final int maxLifespan = 200; // 10秒

                {
                    Location spawnLoc = apostle.getEyeLocation();
                    fireball = apostle.getWorld().spawn(spawnLoc, Fireball.class);
                    fireball.setDirection(target.getLocation().toVector().subtract(spawnLoc.toVector()).normalize());
                    fireball.setYield(0.5f);
                    fireball.setIsIncendiary(false);
                    fireball.setShooter(apostle);
                }

                @Override
                public void run() {
                    if (lifespan >= maxLifespan || fireball.isDead() || !fireball.isValid()) {
                        fireball.remove();
                        cancel();
                        return;
                    }

                    // 缓慢追踪
                    Vector toTarget = target.getLocation().toVector().subtract(fireball.getLocation().toVector()).normalize();
                    fireball.setDirection(fireball.getDirection().multiply(0.7).add(toTarget.multiply(0.3)).normalize());

                    // 检查是否击中玩家
                    if (fireball.getLocation().distance(target.getLocation()) < 2) {
                        fireball.remove();
                        // 命中效果：失明3秒 + 持续伤害
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true)); // 3秒
                        // 持续伤害效果，每秒10点伤害，持续3秒
                        new BukkitRunnable() {
                            private int ticks = 0;

                            @Override
                            public void run() {
                                if (ticks >= 60 || target.isDead() || !target.isOnline()) {
                                    cancel();
                                    return;
                                }

                                if (ticks % 20 == 0) { // 每秒
                                    target.damage(10.0, apostle);
                                }

                                ticks++;
                            }
                        }.runTaskTimer(plugin, 0L, 1L);

                        cancel();
                        return;
                    }

                    lifespan++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        // 效果
        apostle.getWorld().playSound(apostle.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
        apostle.getWorld().spawnParticle(Particle.FLAME, apostle.getLocation(), 20, 1, 1, 1);
        target.sendMessage("§cApostle launches tracking fireballs at you!");
    }

    private void usePotionRain(Player target) {
        Location center = target.getLocation();
        int duration = 100; // 5秒
        int radius = 5;

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration || apostle.isDead()) {
                    cancel();
                    return;
                }

                // 在随机位置生成伤害药水
                for (int i = 0; i < 3; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = random.nextDouble() * radius;
                    double x = center.getX() + distance * Math.cos(angle);
                    double z = center.getZ() + distance * Math.sin(angle);
                    double y = center.getY() + 10;

                    Location spawnLoc = new Location(center.getWorld(), x, y, z);
                    ThrownPotion potion = center.getWorld().spawn(spawnLoc, ThrownPotion.class);
                    potion.setItem(new ItemStack(Material.SPLASH_POTION));
                    // 设置药水效果为瞬间伤害 II
                    org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItem().getItemMeta();
                    meta.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE, 1, 1), true);
                    potion.getItem().setItemMeta(meta);
                    potion.setVelocity(new Vector(0, -1, 0));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L); // 每0.5秒一次

        apostle.getWorld().playSound(apostle.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 1.0f);
        target.sendMessage("§cApostle summons a rain of harming potions!");
    }
    
    // 新增：宇宙雷暴技能
    private void useCosmicStorm(Player target) {
        if (isCastingCosmicStorm) return;
        
        isCastingCosmicStorm = true;
        cosmicStormTicks = 0;
        
        // 广播消息
        Bukkit.broadcastMessage("§4§lApostle unleashes COSMIC STORM!");
        Bukkit.broadcastMessage("§cBeware of continuous lightning and crystal explosions!");
        
        // 对所有附近玩家显示警告
        for (Player player : getNearbyPlayers(50)) {
            player.sendTitle("§4§lCOSMIC STORM", "§cContinuous lightning for 15 seconds!", 10, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.7f);
        }
        
        // 技能特效：使徒周围特效
        Location apostleLoc = apostle.getLocation();
        apostleLoc.getWorld().playSound(apostleLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
        apostleLoc.getWorld().spawnParticle(Particle.PORTAL, apostleLoc, 50, 2, 2, 2);
        
        // 使徒免疫伤害并浮空（技能期间无敌）
        apostle.setInvulnerable(true);
        apostle.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 15, 0, false, false)); // 15秒浮空
        
        // 开始宇宙雷暴任务
        cosmicStormTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (cosmicStormTicks >= 20 * 15 || apostle.isDead() || !apostle.isValid()) {
                    // 技能结束
                    endCosmicStorm();
                    cancel();
                    return;
                }
                
                // 3倍频率：每3ticks执行一次攻击（原来是每10ticks一次）
                if (cosmicStormTicks % 3 == 0) {
                    performCosmicStormAttack();
                }
                
                // 每2秒广播一次警告
                if (cosmicStormTicks % 40 == 0) {
                    Bukkit.broadcastMessage("§4§lCOSMIC STORM continues! Take cover!");
                }
                
                cosmicStormTicks++;
            }
        };
        
        cosmicStormTask.runTaskTimer(plugin, 0L, 1L);
    }

    // 宇宙雷暴单次攻击
    private void performCosmicStormAttack() {
        Location center = apostle.getLocation();
        World world = center.getWorld();
        
        // 3倍密度：每次攻击生成9-15个闪电爆炸点（原来是3-5个）
        int attacks = 9 + random.nextInt(7); // 9-15个攻击点
        
        for (int attackIndex = 0; attackIndex < attacks; attackIndex++) {
            // 在使徒周围圆面内均匀分布（不是只在圆周上）
            // 使用 reject sampling 确保在整个圆面均匀分布
            double x, z;
            
            // 通过随机选择正方形内的点，并检查是否在圆内
            do {
                x = (random.nextDouble() * 2 - 1) * 30; // -30 到 30
                z = (random.nextDouble() * 2 - 1) * 30; // -30 到 30
            } while (x * x + z * z > 30 * 30); // 确保在半径30的圆内
            
            // 计算最终位置
            double finalX = center.getX() + x;
            double finalZ = center.getZ() + z;
            
            // 找到地面高度
            Location targetLoc = new Location(world, finalX, center.getY(), finalZ);
            double groundY = world.getHighestBlockYAt(targetLoc) + 1;
            
            // 确保Y坐标在合理范围内
            double adjustedY = groundY;
            if (adjustedY < center.getY() - 5) {
                adjustedY = center.getY() - 5;
            } else if (adjustedY > center.getY() + 20) {
                adjustedY = center.getY() + 20;
            }
            
            final Location finalTargetLoc = new Location(world, finalX, adjustedY, finalZ);
            
            // 第一阶段：闪电打击
            world.strikeLightning(finalTargetLoc);
            
            // 闪电特效
            world.playSound(finalTargetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.9f);
            world.spawnParticle(Particle.FLASH, finalTargetLoc, 3, 0.5, 0.5, 0.5);
            
            // 第二阶段：0.3秒后生成水晶并爆炸
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 生成1-2个末影水晶
                    int crystalCount = 1 + random.nextInt(2);
                    for (int crystalIndex = 0; crystalIndex < crystalCount; crystalIndex++) {
                        // 在水晶位置周围随机偏移
                        double offsetX = (random.nextDouble() - 0.5) * 3;
                        double offsetZ = (random.nextDouble() - 0.5) * 3;
                        
                        Location crystalLoc = finalTargetLoc.clone().add(offsetX, 1, offsetZ);
                        
                        // 确保水晶位置合理
                        crystalLoc.setY(world.getHighestBlockYAt(crystalLoc) + 1);
                        
                        EnderCrystal crystal = world.spawn(crystalLoc, EnderCrystal.class);
                        crystal.setShowingBottom(false);
                        
                        // 水晶生成特效
                        world.playSound(crystalLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);
                        world.spawnParticle(Particle.PORTAL, crystalLoc, 8, 0.4, 0.4, 0.4);
                        
                        // 0.3秒后水晶爆炸
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // 爆炸伤害（水晶伤害）- 威力略微降低以平衡性能
                                world.createExplosion(crystalLoc, 5.5f, true, true, apostle);
                                crystal.remove();
                                
                                // 爆炸特效
                                world.playSound(crystalLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.8f);
                                world.spawnParticle(Particle.EXPLOSION, crystalLoc, 1);
                                world.spawnParticle(Particle.FLAME, crystalLoc, 15, 1, 1, 1);
                            }
                        }.runTaskLater(plugin, 6L); // 0.3秒后
                    }
                }
            }.runTaskLater(plugin, 6L); // 0.3秒后
            
            // 在闪电位置周围添加一些随机电火花
            for (int sparkIndex = 0; sparkIndex < 3; sparkIndex++) {
                Location sparkLoc = finalTargetLoc.clone().add(
                    (random.nextDouble() - 0.5) * 5,
                    random.nextDouble() * 2,
                    (random.nextDouble() - 0.5) * 5
                );
                world.spawnParticle(Particle.ELECTRIC_SPARK, sparkLoc, 2, 0.2, 0.2, 0.2);
            }
        }
        
        // 使徒周围的持续特效
        world.spawnParticle(Particle.CLOUD, center, 5, 1, 1, 1);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 8, 0.8, 0.8, 0.8);
    }
    
    // 结束宇宙雷暴技能
    private void endCosmicStorm() {
        isCastingCosmicStorm = false;
        cosmicStormTicks = 0;
        
        // 移除使徒的无敌状态
        apostle.setInvulnerable(false);
        apostle.removePotionEffect(PotionEffectType.LEVITATION);
        
        // 广播结束消息
        Bukkit.broadcastMessage("§aCosmic Storm has ended!");
        
        // 结束特效
        Location apostleLoc = apostle.getLocation();
        apostleLoc.getWorld().playSound(apostleLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        apostleLoc.getWorld().spawnParticle(Particle.CLOUD, apostleLoc, 50, 2, 2, 2);
        
        // 使徒缓慢降落
        new BukkitRunnable() {
            private int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= 20 || apostle.isDead() || !apostle.isValid()) {
                    cancel();
                    return;
                }
                
                // 缓慢下降
                Location currentLoc = apostle.getLocation();
                if (currentLoc.getBlock().getRelative(0, -1, 0).getType() != Material.AIR) {
                    cancel();
                    return;
                }
                
                apostle.teleport(currentLoc.clone().subtract(0, 0.1, 0));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void shootArrow(Player target) {
        Location eyeLoc = apostle.getEyeLocation();
        Vector direction = target.getLocation().add(0, 1, 0).subtract(eyeLoc).toVector().normalize();

        Arrow arrow = apostle.getWorld().spawnArrow(eyeLoc, direction, 1.0f, 12.0f); // 伤害12
        arrow.setShooter(apostle);
        arrow.setCritical(true);

        apostle.getWorld().playSound(apostle.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    private void checkAndReplenishMirrors() {
        // 检查死亡的镜像并在1秒后补充
        Iterator<Husk> iterator = playerMirrors.iterator();
        while (iterator.hasNext()) {
            Husk mirror = iterator.next();
            if (mirror.isDead() || !mirror.isValid()) {
                iterator.remove();
                
                // 找到对应的玩家（通过名字匹配）
                String playerName = mirror.getCustomName();
                if (playerName != null) {
                    Player player = plugin.getServer().getPlayerExact(playerName.replace("§8SHADOW OF ", ""));
                    if (player != null && player.isOnline() && player.getGameMode() == GameMode.SURVIVAL) {
                        // 1秒后补充镜像
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // 确保仍然需要补充（每位玩家保持3个镜像）
                                int count = countMirrorsForPlayer(player);
                                if (count < 3) {
                                    spawnMirror(player);
                                }
                            }
                        }.runTaskLater(plugin, 20L); // 1秒后
                    }
                }
            }
        }
    }

    private int countMirrorsForPlayer(Player player) {
        int count = 0;
        for (Husk mirror : playerMirrors) {
            if (mirror.getCustomName() != null && mirror.getCustomName().equals("§8SHADOW OF " + player.getName())) {
                count++;
            }
        }
        return count;
    }

    private Player findNearestPlayer() {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : apostle.getWorld().getPlayers()) {
            if (EntityUtils.isValidTarget(player)) {
                double distance = player.getLocation().distance(apostle.getLocation());
                if (distance < nearestDistance && distance <= 50) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }
        }

        return nearest;
    }
    
    private Player findNearestPlayerTo(Location location) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            if (EntityUtils.isValidTarget(player)) {
                double distance = player.getLocation().distance(location);
                if (distance < nearestDistance) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }
        }

        return nearest;
    }

    private List<Player> getNearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Player player : apostle.getWorld().getPlayers()) {
            if (EntityUtils.isValidTarget(player) && player.getLocation().distance(apostle.getLocation()) <= radius) {
                players.add(player);
            }
        }
        return players;
    }

    private void onDeath() {
        // 清理
        cleanup();

        // 通知插件使徒死亡
        plugin.onApostleDeath();

        // 广播消息
        Bukkit.broadcastMessage("§6§lApostle has been defeated! Orion returns to battle!");
    }

    public void cleanup() {
        if (behaviorTask != null) {
            behaviorTask.cancel();
        }

        if (swapTask != null) {
            swapTask.cancel();
        }
        
        if (cosmicStormTask != null) {
            cosmicStormTask.cancel();
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        cleanupMirrors();

        if (apostle != null && apostle.isValid()) {
            apostle.remove();
        }
    }

    private void cleanupMirrors() {
        for (Husk mirror : playerMirrors) {
            if (mirror.isValid()) {
                mirror.remove();
            }
        }
        playerMirrors.clear();
        playerMirrorCount.clear();
    }

    public LivingEntity getApostle() {
        return apostle;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }
}
