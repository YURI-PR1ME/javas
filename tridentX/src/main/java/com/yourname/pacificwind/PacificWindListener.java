// [file name]: PacificWindListener.java
package com.yourname.pacificwind;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PacificWindListener implements Listener {
    
    private final PacificWindPlugin plugin;
    private final PacificWindManager windManager;
    private final Set<UUID> activeSummons;
    
    public PacificWindListener(PacificWindPlugin plugin) {
        this.plugin = plugin;
        this.windManager = plugin.getWindManager();
        this.activeSummons = new HashSet<>();
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();
        
        // 检查是否手持太平洋之风三叉戟
        if (item == null || !windManager.isPacificWind(item)) {
            return;
        }
        
        // 检查是否右键末地传送门框架
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && 
            block != null && 
            block.getType() == Material.END_PORTAL_FRAME) {
            
            event.setCancelled(true);
            
            // 检查暴君是否已被召唤
            if (plugin.isTyrantSummoned()) {
                player.sendMessage("§c❌ 暴君已被召唤，无法再次召唤！");
                player.sendMessage("§7请联系管理员使用 §6/pacificwind reset §7重置召唤限制");
                return;
            }
            
            // 检查世界是否为地狱
            if (block.getWorld().getEnvironment() != World.Environment.NETHER) {
                player.sendMessage("§c❌ 只能在地狱的末地传送门框架上召唤暴君！");
                player.sendMessage("§7请前往地狱寻找由管理员放置的末地传送门框架");
                return;
            }
            
            // 防止重复召唤
            if (activeSummons.contains(player.getUniqueId())) {
                player.sendMessage("§c❌ 召唤仪式正在进行中，请稍候...");
                return;
            }
            
            // 开始召唤仪式
            startSummonRitual(player, block.getLocation());
        }
    }
    
    private void startSummonRitual(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        activeSummons.add(playerId);
        
        // 第一阶段提示
        player.sendMessage("§8[§9太平洋之风§8] §7Drowned bOy? yoU ArE bAcK?");
        player.getWorld().playSound(location, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.0f, 0.7f);
        
        // 播放粒子效果
        playSummonParticles(location, 1);
        
        // 10秒后召唤暴君
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeSummons.remove(playerId);
                    return;
                }
                
                // 第二阶段提示
                player.sendMessage("§4§l[§c暴君§4§l] §cyOu ALL gOinG tO DiE dOwN Here!!!");
                player.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
                
                // 最终粒子效果
                playSummonParticles(location, 2);
                
                // 召唤暴君
                summonTyrantBoss(player, location);
                
                // 移除召唤状态
                activeSummons.remove(playerId);
            }
        }.runTaskLater(plugin, 200L); // 10秒 = 200 ticks
    }
    
    private void playSummonParticles(Location location, int stage) {
        if (stage == 1) {
            // 第一阶段粒子 - 蓝色水粒子
            for (int i = 0; i < 20; i++) {
                double angle = 2 * Math.PI * i / 20;
                double x = Math.cos(angle) * 2;
                double z = Math.sin(angle) * 2;
                
                Location particleLoc = location.clone().add(x, 1, z);
                location.getWorld().spawnParticle(Particle.SPLASH, particleLoc, 5, 0.2, 0.5, 0.2, 0.1);
                location.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 3, 0.1, 0.3, 0.1, 0.05);
            }
        } else {
            // 第二阶段粒子 - 红色警告粒子
            for (int i = 0; i < 30; i++) {
                double angle = 2 * Math.PI * i / 30;
                double x = Math.cos(angle) * 3;
                double z = Math.sin(angle) * 3;
                
                Location particleLoc = location.clone().add(x, 2, z);
                location.getWorld().spawnParticle(Particle.FLAME, particleLoc, 8, 0.3, 0.8, 0.3, 0.02);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 5, 0.2, 0.6, 0.2, 0.01);
            }
            
            // 闪电效果
            location.getWorld().strikeLightningEffect(location.clone().add(2, 0, 2));
            location.getWorld().strikeLightningEffect(location.clone().add(-2, 0, 2));
            location.getWorld().strikeLightningEffect(location.clone().add(2, 0, -2));
            location.getWorld().strikeLightningEffect(location.clone().add(-2, 0, -2));
        }
    }
    
    private void summonTyrantBoss(Player player, Location location) {
        try {
            // 标记暴君已被召唤
            plugin.setTyrantSummoned(true);
            
            // 方法1: 尝试通过反射调用暴君插件的API
            if (tryReflectiveSpawn(location)) {
                player.sendMessage("§4⚡ 暴君已被召唤! 准备战斗!");
                
                // 广播消息给所有玩家
                Bukkit.broadcastMessage("§4§l⚠ 警告! §c暴君已被 " + player.getName() + " 召唤!");
                Bukkit.broadcastMessage("§6所有玩家请做好战斗准备!");
                
                // 最终音效
                player.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
                player.getWorld().playSound(location, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.8f);
                return;
            }
            
            // 方法2: 尝试直接执行命令（使用控制台）
            if (tryCommandSpawn(player, location)) {
                return;
            }
            
            // 方法3: 备用方案 - 生成一个自定义的暴君实体
            createCustomTyrantBoss(player, location);
            
        } catch (Exception e) {
            // 如果召唤失败，重置召唤状态
            plugin.resetTyrantSummoned();
            player.sendMessage("§c❌ 召唤过程中发生错误!");
            plugin.getLogger().severe("召唤暴君时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
   private boolean tryReflectiveSpawn(Location location) {
    try {
        // 获取暴君插件 - 尝试多个可能的名称
        org.bukkit.plugin.Plugin tyrantPlugin = Bukkit.getPluginManager().getPlugin("TyrantBoss");
        
        // 如果没找到，尝试其他可能的名称
        if (tyrantPlugin == null) {
            tyrantPlugin = Bukkit.getPluginManager().getPlugin("TyrantBossPlugin");
        }
        if (tyrantPlugin == null) {
            tyrantPlugin = Bukkit.getPluginManager().getPlugin("tyrantboss");
        }
        
        if (tyrantPlugin == null) {
            plugin.getLogger().warning("暴君插件未找到，尝试的插件名: TyrantBoss, TyrantBossPlugin, tyrantboss");
            return false;
        }
        
        plugin.getLogger().info("找到暴君插件: " + tyrantPlugin.getName() + " v" + tyrantPlugin.getDescription().getVersion());
        
        // 使用反射调用 spawnTyrantBoss 方法
        java.lang.reflect.Method spawnMethod = tyrantPlugin.getClass().getMethod("spawnTyrantBoss", Location.class);
        spawnMethod.invoke(tyrantPlugin, location);
        
        plugin.getLogger().info("通过反射成功召唤暴君");
        return true;
        
    } catch (Exception e) {
        plugin.getLogger().warning("反射调用失败: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
} 
    private boolean tryCommandSpawn(Player player, Location location) {
        try {
            // 通过控制台执行命令，这样就不需要处理玩家权限
            String command = "execute as " + player.getName() + " at " + player.getName() + " run spawntyrant";
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            if (success) {
                player.sendMessage("§4⚡ 暴君已被召唤! 准备战斗!");
                Bukkit.broadcastMessage("§4§l⚠ 警告! §c暴君已被 " + player.getName() + " 召唤!");
                return true;
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("命令召唤失败: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    private void createCustomTyrantBoss(Player player, Location location) {
        try {
            // 创建凋零骷髅作为暴君
            location.getWorld().strikeLightningEffect(location);
            
            // 生成凋零骷髅
            org.bukkit.entity.WitherSkeleton boss = (org.bukkit.entity.WitherSkeleton) location.getWorld().spawnEntity(location, org.bukkit.entity.EntityType.WITHER_SKELETON);
            
            // 设置暴君属性
            boss.setCustomName("§6§l暴君 §c§lTyrant");
            boss.setCustomNameVisible(true);
            boss.setPersistent(true);
            boss.setRemoveWhenFarAway(false);
            
            // 设置属性
            org.bukkit.attribute.AttributeInstance healthAttr = boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(150.0);
            }
            boss.setHealth(150.0);
            
            org.bukkit.attribute.AttributeInstance damageAttr = boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.setBaseValue(15.0);
            }
            
            // 装备
            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
            
            // 设置装备不掉落
            boss.getEquipment().setHelmetDropChance(0.0f);
            boss.getEquipment().setChestplateDropChance(0.0f);
            boss.getEquipment().setLeggingsDropChance(0.0f);
            boss.getEquipment().setBootsDropChance(0.0f);
            boss.getEquipment().setItemInMainHandDropChance(0.0f);
            
            // 添加药水效果
            boss.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            boss.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1));
            boss.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1));
            
            player.sendMessage("§4⚡ 暴君已被召唤! 准备战斗!");
            Bukkit.broadcastMessage("§4§l⚠ 警告! §c暴君已被 " + player.getName() + " 召唤!");
            Bukkit.broadcastMessage("§6所有玩家请做好战斗准备!");
            
            // 最终音效
            player.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
            player.getWorld().playSound(location, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.8f);
            
            plugin.getLogger().info("使用备用方案成功召唤自定义暴君");
            
        } catch (Exception e) {
            player.sendMessage("§c❌ 召唤暴君失败! 请检查服务器配置");
            plugin.getLogger().severe("创建自定义暴君时出错: " + e.getMessage());
        }
    }
}
