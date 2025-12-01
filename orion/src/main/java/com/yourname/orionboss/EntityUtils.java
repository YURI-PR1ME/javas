package com.yourname.orionboss;


import java.util.Objects;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

public class EntityUtils {
    
    private static final Random random = new Random();
    
    // 生成玩家镜像/克隆
    public static Husk spawnPlayerClone(Player original, Location spawnLoc, String namePrefix, OrionBossPlugin plugin) {
        Husk clone = (Husk) original.getWorld().spawnEntity(spawnLoc, EntityType.HUSK);
        
        // 设置克隆属性
        clone.setCustomName(namePrefix + original.getName());
        clone.setCustomNameVisible(true);
        Objects.requireNonNull(clone.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
        clone.setHealth(20.0);
        Objects.requireNonNull(clone.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(15.0);
        
        // 复制玩家装备
        clone.getEquipment().setHelmet(original.getInventory().getHelmet());
        clone.getEquipment().setChestplate(original.getInventory().getChestplate());
        clone.getEquipment().setLeggings(original.getInventory().getLeggings());
        clone.getEquipment().setBoots(original.getInventory().getBoots());
        clone.getEquipment().setItemInMainHand(original.getInventory().getItemInMainHand());
        
        return clone;
    }
    
    // 生成增强玩家镜像
    public static Husk spawnEnhancedPlayerClone(Player original, Location spawnLoc, OrionBossPlugin plugin) {
        Husk clone = (Husk) original.getWorld().spawnEntity(spawnLoc, EntityType.HUSK);
        
        // 增强属性
        clone.setCustomName("§4ENHANCED SHADOW OF " + original.getName());
        clone.setCustomNameVisible(true);
        Objects.requireNonNull(clone.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(40.0);
        clone.setHealth(40.0);
        Objects.requireNonNull(clone.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(20.0);
        
        // 复制玩家装备
        clone.getEquipment().setHelmet(original.getInventory().getHelmet());
        clone.getEquipment().setChestplate(original.getInventory().getChestplate());
        clone.getEquipment().setLeggings(original.getInventory().getLeggings());
        clone.getEquipment().setBoots(original.getInventory().getBoots());
        clone.getEquipment().setItemInMainHand(original.getInventory().getItemInMainHand());
        
        return clone;
    }
    
    // 为实体装备钻石装备
    public static void equipDiamondArmor(LivingEntity entity) {
        if (entity.getEquipment() == null) return;
        
        entity.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        entity.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        entity.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        entity.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        entity.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
    }
    
    // 查找生成位置
    public static Location findSpawnLocationAround(Location center, double radius) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        double y = findGroundLevel(center.getWorld(), x, z);
        
        return new Location(center.getWorld(), x, y, z);
    }
    
    // 查找地面高度
    public static double findGroundLevel(World world, double x, double z) {
        Location testLoc = new Location(world, x, 0, z);
        return world.getHighestBlockYAt(testLoc);
    }
    
    // 检查是否为有效目标
    public static boolean isValidTarget(Player player) {
        return player != null && 
               player.isOnline() && 
               !player.isDead() && 
               player.getGameMode() == GameMode.SURVIVAL;
    }
}
