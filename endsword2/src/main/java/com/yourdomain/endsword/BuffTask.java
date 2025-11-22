package com.yourdomain.endsword;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class BuffTask extends BukkitRunnable {

    private final ItemManager itemManager;

    public BuffTask(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemManager.isEndSword(itemInHand)) {
                // 给予效果
                // PotionEffect(类型, 持续时间 (ticks), 强度 (0 = 1级, 1 = 2级, 2 = 3级))
                // 持续时间设为 40 ticks (2秒)，比任务间隔长，防止效果闪烁

                // 急迫 1 (强度 0)
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0, true, false));
                
                // 力量 2 (强度 1) <-- ***修改在这里***
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, true, false));
                
                // 速度 1 (强度 0)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
            }
        }
    }
}
