package com.yourname.eternalfire;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FireSpreadTask extends BukkitRunnable {
    
    private final EternalFire plugin;
    
    public FireSpreadTask(EternalFire plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        Set<Location> eternalFires = plugin.getEternalFires();
        Iterator<Location> iterator = eternalFires.iterator();
        Set<Location> newFires = new HashSet<>();
        
        while (iterator.hasNext()) {
            Location fireLoc = iterator.next();
            Block fireBlock = fireLoc.getBlock();
            
            // 检查火是否还存在
            if (fireBlock.getType() != Material.FIRE) {
                iterator.remove();
                continue;
            }
            
            // 蔓延到周围的方块
            spreadFire(fireBlock, newFires);
        }
        
        // 添加新点燃的火
        eternalFires.addAll(newFires);
    }
    
    private void spreadFire(Block fireBlock, Set<Location> newFires) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block nearby = fireBlock.getRelative(x, y, z);
                    
                    if (plugin.isFlammable(nearby)) {
                        Block fireLocation = nearby.getRelative(0, 1, 0);
                        
                        if (fireLocation.getType() == Material.AIR) {
                            fireLocation.setType(Material.FIRE);
                            newFires.add(fireLocation.getLocation().clone());
                            
                            // 显示蔓延粒子
                            fireLocation.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    fireLocation.getLocation().add(0.5, 0.5, 0.5),
                                    5, 0.2, 0.2, 0.2, 0.02
                            );
                        }
                    }
                }
            }
        }
    }
}
