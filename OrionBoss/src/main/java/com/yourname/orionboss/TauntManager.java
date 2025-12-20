package com.yourname.orionboss;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TauntManager {
    
    private final OrionBossPlugin plugin;
    private final Random random = new Random();
    
    // 嘲讽文本
    private final List<String> PHASE1_TAUNTS = Arrays.asList(
        "That's it?", "Try more boy", "Nice try,but still noob",
        "AWww is that hurt?", "You are not a god!", "You are good to kill",
        "why not try just suicide?", "You all going to die down here!", "You are done!"
    );
    
    private final List<String> PHASE2_TAUNTS = Arrays.asList(
        "You will lose,stop trying", "You still can be a good ....dead man!",
        "Nothing can beat me, by that!", "Can you stop ,that even not hurting!",
        "If you kill me Apocalypse will do what i should do", 
        "Armageddon is here", "Stop trying ,you will done"
    );
    
    // 嘲讽状态
    private boolean isTaunting = false;
    private BukkitRunnable tauntTask;
    private long lastTauntTime = 0;
    
    // 时间间隔（毫秒）
    private static final long PHASE1_TAUNT_INTERVAL = 2 * 60 * 1000; // 2分钟
    private static final long PHASE2_TAUNT_INTERVAL = 3 * 60 * 1000; // 3分钟
    
    public TauntManager(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    // 检查是否触发嘲讽（基于时间）
    public boolean checkTaunt(double healthPercent) {
        if (isTaunting) return false;
        
        long currentTime = System.currentTimeMillis();
        long tauntInterval;
        
        if (healthPercent > 0.5) {
            // 第一阶段：2分钟
            tauntInterval = PHASE1_TAUNT_INTERVAL;
        } else {
            // 第二阶段：3分钟
            tauntInterval = PHASE2_TAUNT_INTERVAL;
        }
        
        // 检查是否达到嘲讽间隔
        if (currentTime - lastTauntTime >= tauntInterval) {
            startTaunt(healthPercent <= 0.5);
            lastTauntTime = currentTime;
            return true;
        }
        
        return false;
    }
    
    // 开始嘲讽（移除结束提示）
    private void startTaunt(boolean isPhase2) {
        isTaunting = true;
        
        String tauntMessage;
        if (isPhase2) {
            tauntMessage = "§4§l[ORION] §c" + PHASE2_TAUNTS.get(random.nextInt(PHASE2_TAUNTS.size()));
        } else {
            tauntMessage = "§6§l[ORION] §e" + PHASE1_TAUNTS.get(random.nextInt(PHASE1_TAUNTS.size()));
        }
        
        final String finalMessage = tauntMessage;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(finalMessage);
            }
        });
        
        if (plugin.getActiveBosses().values().iterator().hasNext()) {
            OrionBoss boss = plugin.getActiveBosses().values().iterator().next();
            if (boss != null && boss.getBoss() != null) {
                boss.getBoss().getWorld().playSound(
                    boss.getBoss().getLocation(),
                    org.bukkit.Sound.ENTITY_WITHER_AMBIENT,
                    2.0f, 0.8f
                );
            }
        }
        
        // 3秒后结束嘲讽（移除提示信息）
        tauntTask = new BukkitRunnable() {
            @Override
            public void run() {
                isTaunting = false;
            }
        };
        
        tauntTask.runTaskLater(plugin, 60L); // 3秒
    }
    
    public boolean isTaunting() {
        return isTaunting;
    }
    
    public void endTaunt() {
        if (isTaunting && tauntTask != null) {
            tauntTask.cancel();
            isTaunting = false;
        }
    }
    
    public void cleanup() {
        endTaunt();
    }
}
