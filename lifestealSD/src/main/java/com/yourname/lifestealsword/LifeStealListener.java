package com.yourname.lifestealsword;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.UUID;

public class LifeStealListener implements Listener {
    
    private final LifeStealSwordPlugin plugin;
    private final NamespacedKey swordKey;
    
    public LifeStealListener() {
        this.plugin = LifeStealSwordPlugin.getInstance();
        this.swordKey = new NamespacedKey(plugin, "life_steal_sword");
    }
    
    /**
     * 创建生命窃取剑
     * 数值与锋利V下界合金剑相同
     */
    public ItemStack createLifeStealSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        
        // 设置名称和Lore
        meta.setDisplayName("§6生命窃取剑");
        meta.setLore(Arrays.asList(
            "§7每杀死一个玩家，增加持有者§c2点§7生命上限",
            "§7并获得§a4秒§7生命恢复效果",
            "§7右键发动§e坚守者音波攻击",
            "",
            "§8« §7传说中饮血的魔剑 §8»"
        ));
        
        // 添加锋利V附魔（与下界合金剑+锋利5相同）
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        
        // 设置不可破坏，因为这是特殊武器
        meta.setUnbreakable(true);
        
        // 设置持久化数据，标记为生命窃取剑
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte) 1);
        
        sword.setItemMeta(meta);
        return sword;
    }
    
    /**
     * 检查物品是否是生命窃取剑
     */
    public boolean isLifeStealSword(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        Player killer = killed.getKiller();
        
        // 只有玩家杀死才触发
        if (killer != null) {
            ItemStack mainHand = killer.getInventory().getItemInMainHand();
            if (isLifeStealSword(mainHand)) {
                // 增加最大生命值2点
                double maxHealth = killer.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                double newMaxHealth = maxHealth + 2.0;
                killer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMaxHealth);
                
                // 设置当前生命值，确保不超过新上限
                double currentHealth = killer.getHealth();
                double newHealth = Math.min(currentHealth + 2.0, newMaxHealth);
                killer.setHealth(newHealth);
                
                // 添加生命恢复效果，4秒（80 ticks），等级1
                killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0));
                
                // 视觉和声音效果
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
                killer.spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5);
                
                // 发送消息
                killer.sendMessage("§a✨ 你从杀戮中汲取了生命! 最大生命值增加了§c2点§a!");
                killer.sendMessage("§a❤ 当前最大生命值: §c" + String.format("%.1f", newMaxHealth) + "§a/§c" + String.format("%.1f", newMaxHealth));
                
                // 广播消息
                Bukkit.broadcastMessage("§6" + killer.getName() + " §c使用生命窃取剑击杀了 §6" + killed.getName() + "§c!");
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 检查右键点击且持有生命窃取剑
        if (item != null && (event.getAction().toString().contains("RIGHT_CLICK")) && isLifeStealSword(item)) {
            // 取消事件，防止放置方块等操作
            event.setCancelled(true);
            
            // 检查冷却时间（5秒）
            if (player.getCooldown(Material.NETHERITE_SWORD) > 0) {
                player.sendMessage("§c技能冷却中...");
                return;
            }
            
            // 设置冷却时间（5秒 = 100 ticks）
            player.setCooldown(Material.NETHERITE_SWORD, 100);
            
            // 发动音波攻击
            sonicBoomAttack(player);
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 检查是否是溺尸王
        if (isDrownedKing(event.getEntity())) {
            // 50%几率掉落生命窃取剑
            if (Math.random() < 0.5) {
                event.getDrops().add(createLifeStealSword());
                Bukkit.broadcastMessage("§6⚔ 溺尸王掉落了一把§c生命窃取剑§6!");
            }
        }
    }
    
    /**
     * 检查实体是否是溺尸王（通过原插件的PersistentData标识）
     */
    private boolean isDrownedKing(Entity entity) {
        if (!(entity instanceof Drowned)) return false;
        
        // 使用原插件的NamespacedKey标识
        NamespacedKey bossKey = new NamespacedKey("drowned_king", "drowned_king_boss");
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
    }
    
    /**
     * 音波攻击实现 - 类似坚守者的音波
     */
    private void sonicBoomAttack(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        World world = start.getWorld();
        
        double range = 15.0; // 最大范围
        
        // 播放起始音效
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        
        // 延迟执行音波效果
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 主要音效
            world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
            
            // 音波粒子效果
            for (double distance = 1; distance <= range; distance += 1.0) {
                Location point = start.clone().add(direction.clone().multiply(distance));
                
                // 音波前进粒子
                world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
                
                // 检查碰撞点
                if (point.getBlock().getType().isSolid()) {
                    // 击中方块的爆炸效果
                    world.spawnParticle(Particle.EXPLOSION, point, 3, 0.5, 0.5, 0.5);
                    break;
                }
                
                // 对路径上的实体造成伤害
                for (Entity entity : world.getNearbyEntities(point, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && entity != player && !(entity instanceof ArmorStand)) {
                        LivingEntity target = (LivingEntity) entity;
                        
                        // 造成伤害（基于剑的伤害13点）
                        double damage = 13.0;
                        target.damage(damage, player);
                        
                        // 击退效果
                        Vector knockback = direction.clone().multiply(1.2).setY(0.3);
                        target.setVelocity(knockback);
                        
                        // 显示伤害粒子
                        world.spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation(), 5, 0.5, 1, 0.5);
                    }
                }
            }
            
            player.sendMessage("§6⚡ 你发动了坚守者音波攻击!");
            
        }, 10L); // 0.5秒延迟，模拟蓄力
    }
}
