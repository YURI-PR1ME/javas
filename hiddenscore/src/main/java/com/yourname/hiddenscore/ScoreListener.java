package com.yourname.hiddenscore;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

public class ScoreListener implements Listener {
    
    private final ScoreManager scoreManager = HiddenScorePlugin.getInstance().getScoreManager();
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scoreManager.initializePlayer(player);
        
        // 检查玩家是否穿着钻石甲（登录时检查）
        if (scoreManager.hasDiamondArmor(player)) {
            scoreManager.handleFirstDiamondArmor(player);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        scoreManager.handlePlayerDeath(player);
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        if (killer == null) return;
        
        // 检查是否是Boss击杀
        if (isBossEntity(entity)) {
            String bossType = getBossType(entity);
            if (bossType != null) {
                scoreManager.handleBossKill(killer, bossType);
            }
        }
        // 检查是否是玩家击杀
        else if (entity instanceof Player) {
            scoreManager.handlePlayerKill(killer, (Player) entity);
        }
        // 检查是否是怪物击杀
        else if (isMonster(entity)) {
            scoreManager.handleMonsterKill(killer);
        }
    }
    
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        Player player = event.getPlayer();
        
        // 检查是否是下界合金锭
        if (item.getType() == Material.NETHERITE_INGOT) {
            scoreManager.handleNetheriteIngot(player);
        }
        
        // 检查是否是盾牌
        if (item.getType() == Material.SHIELD) {
            scoreManager.handleFirstShield(player);
        }
        
        // 检查是否是重锤
        if (scoreManager.isHeavyHammer(item)) {
            scoreManager.handleFirstHeavyHammer(player);
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) return;
        
        // 检查是否使用末影珍珠
        if (item.getType() == Material.ENDER_PEARL && event.getAction().name().contains("RIGHT")) {
            scoreManager.handleFirstEnderPearl(player);
        }
        
        // 检查是否使用锄头
        if (item.getType().name().contains("HOE") && event.getAction().name().contains("RIGHT")) {
            scoreManager.handleFirstHoeUse(player);
        }
    }
    
    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        String advancementKey = event.getAdvancement().getKey().getKey();
        
        // 检查是否是曲速泡成就
        if (advancementKey.equals("fast_travel") || advancementKey.contains("warp") || advancementKey.contains("speed")) {
            scoreManager.handleWarpSpeedAchievement(player);
        }
        
        // 检查是否是不死图腾激活相关成就
        if (advancementKey.contains("totem") || advancementKey.contains("resurrect") || 
            advancementKey.contains("postmortal") || advancementKey.contains("cheat_death")) {
            scoreManager.handleTotemActivation(player);
        }
    }
    
    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            // 检查是否使用不死图腾复活
            if (event.isCancelled()) return;
            
            scoreManager.handleTotemActivation(player);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // 检查是否点击了装备槽位
        // 在玩家库存中，装备槽位的索引是：
        // 头盔: 39, 胸甲: 38, 护腿: 37, 靴子: 36
        int slot = event.getSlot();
        if (slot == 36 || slot == 37 || slot == 38 || slot == 39) {
            // 延迟检查，确保物品已经装备
            Bukkit.getScheduler().runTaskLater(HiddenScorePlugin.getInstance(), () -> {
                if (scoreManager.hasDiamondArmor(player)) {
                    scoreManager.handleFirstDiamondArmor(player);
                }
            }, 1L);
        }
    }
    
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack result = event.getCurrentItem();
        
        if (result == null) return;
        
        // 检查是否制作盾牌
        if (result.getType() == Material.SHIELD) {
            scoreManager.handleFirstShield(player);
        }
        
        // 检查是否制作重锤
        if (scoreManager.isHeavyHammer(result)) {
            scoreManager.handleFirstHeavyHammer(player);
        }
    }
    
    // 检查是否是Boss实体
    private boolean isBossEntity(LivingEntity entity) {
        if (entity.getCustomName() == null) return false;
        
        String name = entity.getCustomName();
        return name.contains("溺尸王") || name.contains("暴君") || 
               name.contains("Drowned King") || name.contains("Tyrant");
    }
    
    // 获取Boss类型
    private String getBossType(LivingEntity entity) {
        if (entity.getCustomName() == null) return null;
        
        String name = entity.getCustomName();
        if (name.contains("溺尸王") || name.contains("Drowned King")) {
            return "drowned_king";
        } else if (name.contains("暴君") || name.contains("Tyrant")) {
            return "tyrant_boss";
        }
        
        return null;
    }
    
    // 检查是否是怪物
    private boolean isMonster(LivingEntity entity) {
        return entity instanceof Monster && 
               !(entity instanceof Player) &&
               !isBossEntity(entity);
    }
}
