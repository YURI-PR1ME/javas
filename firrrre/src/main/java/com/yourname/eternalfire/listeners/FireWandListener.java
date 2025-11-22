package com.yourname.eternalfire.listeners;

import com.yourname.eternalfire.EternalFire;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class FireWandListener implements Listener {
    
    private final EternalFire plugin;
    
    public FireWandListener(EternalFire plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(plugin.getFireWandKey(), PersistentDataType.BYTE)) {
                
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
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
                plugin.igniteEternalFire(block, player);
            }
            
            // 检查当前点的实体
            player.getWorld().getNearbyEntities(currentLoc, 1, 1, 1).forEach(entity -> {
                if (entity instanceof LivingEntity livingEntity && entity != player) {
                    plugin.setEntityOnFire(livingEntity, 100); // 点燃5秒
                }
            });
            
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
}
