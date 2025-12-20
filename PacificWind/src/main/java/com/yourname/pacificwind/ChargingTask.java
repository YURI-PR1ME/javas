// [file name]: ChargingTask.java
package com.yourname.pacificwind;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class ChargingTask extends BukkitRunnable {
    
    private final PacificWindPlugin plugin;
    private final PacificWindManager windManager;
    private final UUID playerId;
    private final Player player;
    
    public ChargingTask(PacificWindPlugin plugin, UUID playerId) {
        this.plugin = plugin;
        this.windManager = plugin.getWindManager();
        this.playerId = playerId;
        this.player = Bukkit.getPlayer(playerId);
    }
    
    @Override
    public void run() {
        // 检查玩家是否在线且仍在蓄力
        if (player == null || !player.isOnline() || !windManager.isCharging(playerId)) {
            windManager.stopCharging(playerId);
            this.cancel();
            return;
        }
        
        long chargingTime = windManager.getChargingTime(playerId);
        
        // 检查玩家是否仍在潜行
        if (!player.isSneaking()) {
            player.sendMessage("§c❌ 蓄力中断!");
            windManager.stopCharging(playerId);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.5f);
            this.cancel();
            return;
        }
        
        // 显示蓄力进度
        int progress = (int) (chargingTime / 3000.0 * 100); // 百分比
        progress = Math.min(100, progress);
        
        // 每0.5秒显示一次进度
        if (chargingTime % 500 < 50) {
            String progressBar = createProgressBar(progress);
            player.sendActionBar("§9蓄力中: " + progressBar + " §7" + progress + "%");
            
            // 蓄力粒子效果
            player.getWorld().spawnParticle(Particle.FLAME, 
                player.getLocation().add(0, 1, 0), 
                10, 0.5, 0.5, 0.5, 0.1);
        }
        
        // 达到3秒蓄力
        if (chargingTime >= 3000) {
            windManager.stopCharging(playerId);
            
            // 检查冷却
            if (windManager.isRainOnCooldown(playerId)) {
                long remaining = windManager.getRainCooldownRemaining(playerId);
                player.sendMessage("§c❌ 下雨技能冷却中! 剩余: " + remaining + "秒");
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            } else {
                // 召唤下雨
                summonRain(player);
                windManager.startRainCooldown(playerId);
            }
            
            this.cancel();
        }
    }
    
    private String createProgressBar(int progress) {
        int bars = 20;
        int filledBars = progress * bars / 100;
        StringBuilder bar = new StringBuilder("§8[");
        
        for (int i = 0; i < bars; i++) {
            if (i < filledBars) {
                bar.append("§9█");
            } else {
                bar.append("§7▒");
            }
        }
        
        bar.append("§8]");
        return bar.toString();
    }
    
    private void summonRain(Player player) {
        World world = player.getWorld();
        
        // 设置下雨
        world.setStorm(true);
        world.setWeatherDuration(20 * 60); // 60秒
        
        // 播放效果
        player.sendMessage("§9🌧️ 你召唤了降雨! 持续60秒");
        player.sendMessage("§6💡 下雨期间，投掷三叉戟命中敌人会触发引雷+爆炸!");
        player.getWorld().playSound(player.getLocation(), Sound.WEATHER_RAIN_ABOVE, 2.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        
        // 粒子效果
        for (int i = 0; i < 30; i++) {
            double angle = 2 * Math.PI * i / 30;
            double x = Math.cos(angle) * 5;
            double z = Math.sin(angle) * 5;
            
            player.getWorld().spawnParticle(Particle.SPLASH, 
                player.getLocation().add(x, 3, z), 
                20, 2, 5, 2, 0.5);
        }
        
        // 广播消息
        Bukkit.broadcastMessage("§9§l[天气] §b" + player.getName() + " §7使用太平洋之风召唤了降雨!");
        //Bukkit.broadcastMessage("§6");
        
        // 60秒后停止下雨
        new BukkitRunnable() {
            @Override
            public void run() {
                if (world.hasStorm()) {
                    world.setStorm(false);
                    player.sendMessage("§7☀️ 降雨已停止");
                }
            }
        }.runTaskLater(plugin, 20 * 60);
    }
}
