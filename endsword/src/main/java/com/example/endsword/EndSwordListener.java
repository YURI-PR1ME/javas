package com.example.endsword;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class EndSwordListener implements Listener {
    
    private final EndSwordPlugin plugin;
    private final HashMap<UUID, Integer> criticalHits = new HashMap<>();
    private final Random random = new Random();
    
    public EndSwordListener(EndSwordPlugin plugin) {
        this.plugin = plugin;
    }
    
    // 处理手持终末之剑时给予状态效果
    @EventHandler
    public void onPlayerHoldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        
        if (isEndSword(newItem)) {
            applySwordEffects(player);
        }
    }
    
    // 处理暴击检测和特效触发
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();
        ItemStack weapon = damager.getInventory().getItemInMainHand();
        
        if (!isEndSword(weapon)) return;
        
        // 检测是否为暴击（玩家正在下落且没有站在地面上）
        if (damager.getFallDistance() > 0 && !damager.isOnGround()) {
            event.setDamage(event.getDamage() * 2); // 暴击双倍伤害
            
            int hitCount = criticalHits.getOrDefault(damager.getUniqueId(), 0) + 1;
            criticalHits.put(damager.getUniqueId(), hitCount);
            
            damager.sendActionBar(ChatColor.YELLOW + "暴击计数: " + hitCount + "/5");
            
            // 每5次暴击触发特效
            if (hitCount >= 5) {
                triggerSpecialEffects(target);
                criticalHits.put(damager.getUniqueId(), 0);
                damager.sendMessage(ChatColor.GREEN + "终末之剑特效已触发！");
            }
        }
    }
    
    // 处理右键雷暴技能
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && 
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (!isEndSword(item)) return;
        
        // 检查冷却
        if (plugin.getCooldownManager().isOnCooldown(player)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(player);
            player.sendMessage(ChatColor.RED + "技能冷却中，剩余时间: " + remaining + "秒");
            event.setCancelled(true);
            return;
        }
        
        // 触发雷暴
        triggerThunderstorm(player);
        plugin.getCooldownManager().setCooldown(player);
        player.sendMessage(ChatColor.AQUA + "雷暴降临！");
        
        // 冷却提示
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(ChatColor.GREEN + "终末之剑技能已就绪！");
        }, 600); // 30秒后提示
    }
    
    private boolean isEndSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && 
               meta.hasDisplayName() && 
               meta.getDisplayName().equals("§6终末之剑");
    }
    
    private void applySwordEffects(Player player) {
        // 急迫1，力量3，速度1
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.HASTE, 100, 0, true, false)); // 急迫1
        
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.STRENGTH, 100, 2, true, false)); // 力量3（等级2表示3级）
        
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SPEED, 100, 0, true, false)); // 速度1
    }
    
    private void triggerSpecialEffects(Player target) {
        Location loc = target.getLocation();
        World world = target.getWorld();
        
        // 创建2x2蜘蛛网区域
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                Block block = world.getBlockAt(
                    loc.getBlockX() + x, 
                    loc.getBlockY(), 
                    loc.getBlockZ() + z
                );
                
                if (block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    block.setType(Material.COBWEB);
                }
            }
        }
        
        // 从5格高处发射龙息弹（向下射击）
        Location dragonBreathLoc = loc.clone().add(0, 5, 0);
        DragonFireball fireball = world.spawn(dragonBreathLoc, DragonFireball.class);
        fireball.setDirection(new Vector(0, -1, 0)); // 直接向下
        fireball.setYield(1.5f); // 设置爆炸威力
        
        // 特效和音效
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        world.spawnParticle(Particle.DRAGON_BREATH, loc, 50, 2, 2, 2);
    }
    
    private void triggerThunderstorm(Player player) {
        Location center = player.getTargetBlock(null, 20).getLocation();
        World world = player.getWorld();
        
        // 在半径5格的圆形区域内随机生成10道闪电
        for (int i = 0; i < 10; i++) {
            // 在圆形区域内随机分布
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 5; // 0-5格随机半径
            
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            
            // 获取该位置的地面高度
            Location strikeLoc = new Location(
                world, 
                x, 
                world.getHighestBlockYAt((int)x, (int)z) + 1, 
                z
            );
            
            // 立即触发闪电（瞬间多道雷）
            world.strikeLightning(strikeLoc);
            
            // 闪电伤害范围内的生物
            world.getNearbyEntities(strikeLoc, 3, 3, 3).forEach(entity -> {
                if (entity instanceof LivingEntity livingEntity && !entity.equals(player)) {
                    livingEntity.damage(5);
                }
            });
        }
        
        // 环境效果
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
        world.spawnParticle(Particle.FLASH, center, 20, 5, 5, 5);
        
        // 添加爆炸粒子效果增强视觉冲击
        for (int i = 0; i < 5; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 5;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(world, x, center.getY(), z);
            world.spawnParticle(Particle.EXPLOSION, particleLoc, 1);
        }
    }
}
