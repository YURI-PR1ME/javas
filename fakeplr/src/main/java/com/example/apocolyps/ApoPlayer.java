package com.example.apocolyps;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Random;
import java.util.UUID;

public class ApoPlayer {
    private final Zombie entity;
    private final int id;
    private final ApoManager manager;
    private final Random random = new Random();
    private final boolean isBoundAI; // 标记是否为绑定AI
    
    private boolean isAnimalAI = false;
    private boolean hasSpeedBoost = false;
    private Player attackTarget = null;
    private long lastAttackTime = 0;
    private long lastBuildTime = 0;
    
    public ApoPlayer(Location loc, int id, ApoManager manager, boolean isBoundAI) {
        this.id = id;
        this.manager = manager;
        this.isBoundAI = isBoundAI;
        
        this.entity = loc.getWorld().spawn(loc, Zombie.class);
        setupEntity();
    }
    
    private void setupEntity() {
        // 基础设置
        String displayName = isBoundAI ? "§a天启§e" + id : "§7村庄§e" + id;
        entity.setCustomName(displayName);
        entity.setCustomNameVisible(false);
        entity.setAdult();
        entity.setShouldBurnInDay(false);
        entity.setCanPickupItems(false);
        
        // 血量设置
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
        entity.setHealth(30.0);
        
        // 设置为玩家外观
        entity.setBaby(false);
        
        // 装备设置 - 绑定AI装备更好
        entity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        entity.getEquipment().setHelmet(createPlayerHead());
        
        if (isBoundAI) {
            entity.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            entity.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            entity.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
        } else {
            // 村庄AI装备差一些
            entity.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            entity.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        }
        
        // 清除默认AI目标 - 重要：让AI不会主动追击玩家
        clearDefaultAI();
    }
    
    private ItemStack createPlayerHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner("Steve");
        meta.setDisplayName("§6天启头盔");
        head.setItemMeta(meta);
        return head;
    }
    
    private void clearDefaultAI() {
        try {
            // 清除所有默认目标选择器，防止AI主动追击玩家
            entity.setTarget(null);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    public void updateBehavior() {
        // 如果AI已经死亡或无效，不执行任何行为
        if (!entity.isValid() || entity.isDead()) {
            return;
        }
        
        if (isAnimalAI) {
            // 动物AI - 随机移动
            if (random.nextDouble() < 0.1) {
                Location current = entity.getLocation();
                Location newLoc = current.clone().add(
                    random.nextInt(5) - 2,
                    0,
                    random.nextInt(5) - 2
                );
                entity.getPathfinder().moveTo(newLoc);
            }
            return;
        }
        
        // 检查3格内的玩家 - 只有进入3格范围才会攻击
        Player nearestPlayer = findNearestPlayer(3);
        
        if (nearestPlayer != null && attackTarget == null) {
            // 发现3格内的玩家，开始仇恨
            attackTarget = nearestPlayer;
            entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation(), 5, 0.5, 1, 0.5);
            
            // 重要：不设置实体目标，让AI不会主动追击
            // entity.setTarget(attackTarget); // 注释掉这行，让AI不会主动追击
        }
        
        if (attackTarget != null) {
            // 检查目标是否还在3格内
            double distance = attackTarget.getLocation().distance(entity.getLocation());
            
            if (distance <= 3) {
                // 在攻击范围内，检查攻击冷却
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastAttackTime > 1000) {
                    // 村庄AI攻击力更低
                    double damage = isBoundAI ? 3.0 : 1.5;
                    attackTarget.damage(damage, entity);
                    lastAttackTime = currentTime;
                    attackTarget.getWorld().spawnParticle(Particle.CRIT, attackTarget.getLocation(), 3);
                }
            } else if (distance > 10) {
                // 目标太远，清除仇恨
                attackTarget = null;
                // 确保清除目标
                entity.setTarget(null);
            }
        } else {
            // 绑定AI建筑频率更高
            double buildChance = isBoundAI ? 0.15 : 0.08;
            
            if (random.nextDouble() < buildChance) {
                long currentTime = System.currentTimeMillis();
                // 确保建筑行为有最小间隔（2秒）
                if (currentTime - lastBuildTime > 2000) {
                    performBuildAction();
                    lastBuildTime = currentTime;
                }
            } else if (random.nextDouble() < 0.05) {
                performSurvivalAction();
            } else if (random.nextDouble() < 0.1) {
                // 随机移动 - 增加移动频率，让AI有机会接近玩家
                Location current = entity.getLocation();
                Location newLoc = current.clone().add(
                    random.nextInt(15) - 7, // 增加移动范围
                    0,
                    random.nextInt(15) - 7
                );
                entity.getPathfinder().moveTo(newLoc);
            }
        }
        
        // 重要：确保AI不会主动追击玩家
        // 每次更新行为后都清除目标，防止AI主动追击
        if (attackTarget == null) {
            entity.setTarget(null);
        }
    }
    
    private Player findNearestPlayer(double radius) {
        // 如果AI已经死亡或无效，不寻找玩家
        if (!entity.isValid() || entity.isDead()) {
            return null;
        }
        
        Player nearest = null;
        double nearestDistance = radius + 1;
        
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE) continue;
            
            double distance = entity.getLocation().distance(player.getLocation());
            if (distance <= radius && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
    
    private void performBuildAction() {
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        
        // 村庄风格建筑材料
        Material[] buildingMaterials = {
            Material.STONE_BRICKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
            Material.BRICKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.GLASS_PANE, Material.OAK_FENCE, Material.SPRUCE_FENCE
        };
        
        Material[] decorativeMaterials = {
            Material.LANTERN, Material.TORCH, Material.FLOWER_POT,
            Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.STONE_SLAB
        };
        
        // 30%几率建造小型房屋，70%几率建造单个方块
        if (random.nextDouble() < 0.3) {
            buildSmallStructure(loc, buildingMaterials, decorativeMaterials);
        } else {
            buildRandomBlock(loc, buildingMaterials);
        }
    }
    
    private void buildSmallStructure(Location center, Material[] buildingMaterials, Material[] decorativeMaterials) {
        World world = center.getWorld();
        int size = 2 + random.nextInt(2); // 2-3大小的结构
        
        // 地基
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (x == -size || x == size || z == -size || z == size) {
                    Location blockLoc = center.clone().add(x, -1, z);
                    if (blockLoc.getBlock().getType().isAir() && 
                        blockLoc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                        blockLoc.getBlock().setType(buildingMaterials[random.nextInt(buildingMaterials.length)]);
                    }
                }
            }
        }
        
        // 放置一些装饰
        for (int i = 0; i < 2; i++) {
            Location decorLoc = center.clone().add(
                random.nextInt(size * 2) - size,
                0,
                random.nextInt(size * 2) - size
            );
            if (decorLoc.getBlock().getType().isAir() && 
                decorLoc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                decorLoc.getBlock().setType(decorativeMaterials[random.nextInt(decorativeMaterials.length)]);
            }
        }
        
        world.spawnParticle(Particle.VILLAGER_HAPPY, center, 10, size, 2, size);
    }
    
    private void buildRandomBlock(Location center, Material[] buildingMaterials) {
        for (int i = 0; i < 2; i++) {
            Location buildLoc = center.clone().add(
                random.nextInt(7) - 3,
                0,
                random.nextInt(7) - 3
            );
            
            if (buildLoc.getBlock().getType().isAir() && 
                buildLoc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                
                Material material = buildingMaterials[random.nextInt(buildingMaterials.length)];
                buildLoc.getBlock().setType(material);
                
                center.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, buildLoc, 3, 0.5, 0.5, 0.5);
            }
        }
    }
    
    private void performSurvivalAction() {
        Location loc = entity.getLocation();
        
        // 随机放置火把或种植
        if (random.nextDouble() < 0.5) {
            Location torchLoc = loc.clone().add(
                random.nextInt(5) - 2,
                0,
                random.nextInt(5) - 2
            );
            
            if (torchLoc.getBlock().getType().isAir() && 
                torchLoc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                torchLoc.getBlock().setType(Material.TORCH);
            }
        } else {
            // 种植作物
            Location farmLoc = loc.clone().add(
                random.nextInt(5) - 2,
                0,
                random.nextInt(5) - 2
            );
            
            if (farmLoc.getBlock().getType() == Material.GRASS_BLOCK || 
                farmLoc.getBlock().getType() == Material.DIRT) {
                
                Material[] crops = {Material.WHEAT, Material.CARROTS, Material.POTATOES};
                farmLoc.clone().add(0, 1, 0).getBlock().setType(crops[random.nextInt(crops.length)]);
            }
        }
        
        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 3, 0.5, 1, 0.5);
    }
    
    public void setAnimalAI(boolean animalAI) {
        this.isAnimalAI = animalAI;
        if (animalAI) {
            attackTarget = null;
            entity.setTarget(null);
            entity.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        }
    }
    
    public void setSpeedBoost(boolean boost) {
        this.hasSpeedBoost = boost;
        if (boost) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        } else {
            entity.removePotionEffect(PotionEffectType.SPEED);
        }
    }
    
    public void setMaxHealth(double health) {
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        if (entity.getHealth() > health) {
            entity.setHealth(health);
        }
    }
    
    public void heal(double amount) {
        double newHealth = Math.min(entity.getHealth() + amount, 
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        entity.setHealth(newHealth);
        entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 2, 0), 1);
    }
    
    public void teleport(Location location) {
        if (entity != null && entity.isValid()) {
            // 传送时清除攻击目标
            attackTarget = null;
            entity.setTarget(null);
            entity.teleport(location);
            
            // 传送后增加移动速度，让AI有机会接近玩家
            if (isBoundAI) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1)); // 10秒速度提升
            }
        }
    }
    
    public void remove() {
        if (entity != null && entity.isValid()) {
            // 清除攻击目标
            attackTarget = null;
            entity.setTarget(null);
            entity.remove();
        }
    }
    
    // 检查AI是否死亡或无效
    public boolean isDead() {
        return !entity.isValid() || entity.isDead();
    }
    
    // Getter方法
    public UUID getUUID() { return entity.getUniqueId(); }
    public int getId() { return id; }
    public Location getLocation() { return entity.getLocation(); }
    public boolean isAnimalAI() { return isAnimalAI; }
    public boolean isBoundAI() { return isBoundAI; }
}
