package com.yourname.orionboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BGMPlayer {
    
    private final OrionBossPlugin plugin;
    private BukkitRunnable globalBgmTask;
    
    // 添加同步控制字段
    private boolean isTransitioning = false; // 防止切换时的重叠
    private final Object lock = new Object(); // 同步锁
    
    // BGM曲目定义
    public enum BGMTrack {
        // 使用超大音量（例如200F），使每个玩家都能以自己为中心听到覆盖整个岛屿范围的音乐
        ORION_PHASE_1("yourplugin:orion_phase1", 120, SoundCategory.MUSIC, 200.0f),
        ORION_PHASE_2("yourplugin:orion_phase2", 120, SoundCategory.MUSIC, 200.0f),
        APOSTLE("yourplugin:apostle_bgm", 180, SoundCategory.MUSIC, 200.0f),
        FINAL_PHASE("yourplugin:final_phase", 150, SoundCategory.MUSIC, 200.0f);
        
        private final String soundName;
        private final int lengthSeconds;
        private final SoundCategory category;
        private final float volume;
        
        BGMTrack(String soundName, int lengthSeconds, SoundCategory category, float volume) {
            this.soundName = soundName;
            this.lengthSeconds = lengthSeconds;
            this.category = category;
            this.volume = volume;
        }
        
        public String getSoundName() { return soundName; }
        public int getLengthSeconds() { return lengthSeconds; }
        public SoundCategory getCategory() { return category; }
        public float getVolume() { return volume; }
    }
    
    // 战斗阶段对应的BGM
    public enum BossPhase {
        ORION_NORMAL(BGMTrack.ORION_PHASE_1),
        ORION_RAGE(BGMTrack.ORION_PHASE_2),
        APOSTLE(BGMTrack.APOSTLE),
        ORION_FINAL(BGMTrack.FINAL_PHASE);
        
        private final BGMTrack bgmTrack;
        
        BossPhase(BGMTrack bgmTrack) {
            this.bgmTrack = bgmTrack;
        }
        
        public BGMTrack getBgmTrack() { return bgmTrack; }
    }
    
    private BossPhase currentPhase = BossPhase.ORION_NORMAL;
    private boolean isPlaying = false;
    private String currentSoundName = "";
    
    public BGMPlayer(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 为所有在末地的玩家播放BGM
     */
    public void playBGMForAll(BossPhase phase) {
        synchronized (lock) {
            if (isTransitioning) {
                plugin.getLogger().info("BGM正在切换中，跳过本次播放请求");
                return;
            }
            
            isTransitioning = true;
            
            // 先完全停止当前BGM
            stopAllBGMInternal();
            
            // 短暂延迟确保完全停止
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                synchronized (lock) {
                    currentPhase = phase;
                    isPlaying = true;
                    BGMTrack track = phase.getBgmTrack();
                    currentSoundName = track.getSoundName();
                    
                    // 获取所有在末地的玩家
                    List<Player> endPlayers = getPlayersInEnd();
                    if (endPlayers.isEmpty()) {
                        plugin.getLogger().info("没有玩家在末地，跳过BGM播放");
                        isTransitioning = false;
                        return;
                    }
                    
                    // 为每个末地玩家播放BGM
                    playBGMForPlayers(endPlayers, track);
                    
                    // 创建循环播放任务
                    startGlobalBGMLoop(track);
                    
                    // 广播BGM开始
                    broadcastBGMStart(phase);
                    
                    plugin.getLogger().info("开始播放BGM: " + track.getSoundName() + "，玩家数量: " + endPlayers.size());
                    isTransitioning = false;
                }
            }, 10L); // 延迟0.5秒确保完全停止
        }
    }
    
    /**
     * 内部停止方法，使用Bukkit的stopSound方法完全停止声音
     */
    private void stopAllBGMInternal() {
        // 停止全局循环任务
        if (globalBgmTask != null) {
            globalBgmTask.cancel();
            globalBgmTask = null;
        }
        
        // 使用Bukkit的stopSound方法完全停止所有玩家的音乐
        // 这相当于执行/stopsound @a命令
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopSoundCompletely(player);
        }
        
        // 清理状态
        isPlaying = false;
        currentSoundName = "";
        
        plugin.getLogger().info("已使用stopSound方法停止所有玩家的BGM");
    }
    
    /**
     * 完全停止玩家的声音，相当于/stopsound命令
     */
    private void stopSoundCompletely(Player player) {
        try {
            // 方法1：停止特定声音（如果知道声音名称）
            if (currentSoundName != null && !currentSoundName.isEmpty()) {
                player.stopSound(currentSoundName);
            }
            
            // 方法2：停止整个音乐类别的所有声音（更彻底）
            player.stopSound(SoundCategory.MUSIC);
            
            // 方法3：停止所有类别的特定声音（如果需要）
            // 停止所有我们可能播放的BGM声音
            for (BGMTrack track : BGMTrack.values()) {
                player.stopSound(track.getSoundName());
            }
            
            plugin.getLogger().fine("已完全停止玩家 " + player.getName() + " 的BGM");
        } catch (Exception e) {
            plugin.getLogger().warning("停止玩家 " + player.getName() + " 的BGM时出错: " + e.getMessage());
        }
    }
    
    /**
     * 停止所有玩家的BGM
     */
    public void stopAllBGM() {
        synchronized (lock) {
            stopAllBGMInternal();
            
            // 广播停止
            Bukkit.broadcastMessage("§7[音乐] 战斗BGM已停止");
            plugin.getLogger().info("BGM已停止");
        }
    }
    
    /**
     * 使用 player.playSound() 为指定玩家列表播放BGM
     */
    private void playBGMForPlayers(List<Player> players, BGMTrack track) {
        for (Player player : players) {
            if (player.isOnline() && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                try {
                    // 先完全停止可能正在播放的声音
                    stopSoundCompletely(player);
                    
                    // 延迟1tick再播放，确保停止生效
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                            player.playSound(
                                player.getLocation(),     // 声源：玩家当前位置
                                track.getSoundName(),     // 声音键
                                track.getCategory(),      // 声音类别：音乐
                                track.getVolume(),        // 超大音量（200F），确保覆盖范围
                                1.0f                      // 音高
                            );
                        }
                    }, 1L);
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("为玩家 " + player.getName() + " 播放BGM时出错: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取末地中的所有玩家
     */
    private List<Player> getPlayersInEnd() {
        List<Player> endPlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                endPlayers.add(player);
            }
        }
        return endPlayers;
    }
    
    /**
     * 启动全局BGM循环播放
     */
    private void startGlobalBGMLoop(BGMTrack track) {
        if (globalBgmTask != null) {
            globalBgmTask.cancel();
            globalBgmTask = null;
        }
        
        globalBgmTask = new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    if (!isPlaying || isTransitioning) {
                        cancel();
                        return;
                    }
                    
                    // 检查是否有玩家在末地
                    List<Player> endPlayers = getPlayersInEnd();
                    if (endPlayers.isEmpty()) {
                        plugin.getLogger().info("没有玩家在末地，暂停BGM循环");
                        return;
                    }
                    
                    // 为所有末地玩家重新播放BGM
                    playBGMForPlayers(endPlayers, track);
                    
                    // 调试日志
                    plugin.getLogger().fine("BGM循环播放中... 当前阶段: " + currentPhase + 
                        ", 玩家数量: " + endPlayers.size());
                }
            }
        };
        
        // 在音乐长度后重新播放
        int delayTicks = track.getLengthSeconds() * 20;
        globalBgmTask.runTaskTimer(plugin, delayTicks, delayTicks);
        
        plugin.getLogger().info("BGM循环任务已启动，间隔: " + delayTicks + " ticks");
    }
    
    /**
     * 停止特定玩家的BGM
     */
    public void stopBGMForPlayer(Player player) {
        synchronized (lock) {
            stopSoundCompletely(player);
            plugin.getLogger().info("停止玩家 " + player.getName() + " 的BGM");
            player.sendMessage("§7[音乐] 战斗BGM已停止");
        }
    }
    
    /**
     * 更新Boss阶段和BGM
     */
    public void updateBossPhase(BossPhase newPhase) {
        synchronized (lock) {
            if (currentPhase == newPhase) return;
            
            plugin.getLogger().info("BGM阶段切换: " + currentPhase + " -> " + newPhase);
            
            // 停止当前音乐并播放新阶段的音乐
            playBGMForAll(newPhase);
        }
    }
    
    /**
     * 广播BGM开始
     */
    private void broadcastBGMStart(BossPhase phase) {
        String message;
        
        switch (phase) {
            case ORION_NORMAL:
                message = "§7[音乐] §6LET'S HAVE SOME FUN'！";
                break;
            case ORION_RAGE:
                message = "§7[音乐] §cIT'sNOT OVER ,KID'！";
                break;
            case APOSTLE:
                message = "§7[音乐] §5WE BELIEVE IN GOD!！";
                break;
            case ORION_FINAL:
                message = "§7[音乐] §4A GOD DOSE NOT FEAR DEATH！";
                break;
            default:
                message = "§7[音乐] §aIT's ONLY BEGIN!";
        }
        
        // 只向末地玩家广播
        for (Player player : getPlayersInEnd()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info("BGM已播放: " + message);
    }
    
    /**
     * 获取当前播放状态
     */
    public boolean isPlaying() {
        synchronized (lock) {
            return isPlaying && !isTransitioning;
        }
    }
    
    /**
     * 获取当前阶段
     */
    public BossPhase getCurrentPhase() {
        synchronized (lock) {
            return currentPhase;
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        synchronized (lock) {
            stopAllBGM();
            plugin.getLogger().info("BGM播放器已清理");
        }
    }
    
    /**
     * 当玩家离开末地时调用
     */
    public void onPlayerLeaveEnd(Player player) {
        synchronized (lock) {
            stopBGMForPlayer(player);
            plugin.getLogger().info("玩家 " + player.getName() + " 离开末地，停止BGM");
        }
    }
    
    /**
     * 当玩家进入末地时，如果BGM正在播放，为其单独播放
     */
    public void onPlayerEnterEnd(Player player) {
        synchronized (lock) {
            if (isPlaying && !isTransitioning) {
                // 为新进入末地的玩家播放当前BGM
                List<Player> singlePlayerList = Collections.singletonList(player);
                playBGMForPlayers(singlePlayerList, currentPhase.getBgmTrack());
                player.sendMessage("§7[音乐] 战斗BGM正在播放中...");
            }
        }
    }
    
    /**
     * 检查是否正在切换BGM
     */
    public boolean isTransitioning() {
        synchronized (lock) {
            return isTransitioning;
        }
    }
}
