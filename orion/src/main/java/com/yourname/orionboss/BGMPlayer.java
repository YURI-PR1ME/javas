package com.yourname.orionboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BGMPlayer {
    
    private final OrionBossPlugin plugin;
    private BukkitRunnable globalBgmTask;
    
    // BGM曲目定义
    public enum BGMTrack {
        ORION_PHASE_1("yourplugin:orion_phase1", 120, SoundCategory.VOICE, 1.0f),
        ORION_PHASE_2("yourplugin:orion_phase2", 120, SoundCategory.VOICE, 1.0f),
        APOSTLE("yourplugin:apostle_bgm", 180, SoundCategory.VOICE, 1.0f),
        FINAL_PHASE("yourplugin:final_phase", 150, SoundCategory.VOICE, 1.2f);
        
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
    private Location lastSoundLocation = null;
    
    public BGMPlayer(OrionBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 为所有在末地的玩家播放BGM（全局播放）
     */
    public void playBGMForAll(BossPhase phase) {
        stopAllBGM(); // 先停止当前播放
        
        currentPhase = phase;
        isPlaying = true;
        BGMTrack track = phase.getBgmTrack();
        currentSoundName = track.getSoundName();
        
        // 获取Boss位置作为音源中心
        Location bossLocation = getBossLocation();
        if (bossLocation == null) {
            // 如果没有Boss，使用默认位置
            bossLocation = new Location(Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.THE_END)
                .findFirst()
                .orElse(Bukkit.getWorlds().get(0)), 0, 65, 0);
        }
        
        lastSoundLocation = bossLocation.clone();
        
        // 检查世界是否为末地
        boolean isInEnd = bossLocation.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
        
        if (!isInEnd) {
            // 如果不是末地，就不播放
            return;
        }
        
        // 为末地中的所有玩家播放BGM
        playBGMForEndPlayers(track, bossLocation);
        
        // 创建循环播放任务
        startGlobalBGMLoop(track, bossLocation);
        
        // 广播BGM开始
        broadcastBGMStart(phase);
    }
    
    /**
     * 为末地中的所有玩家播放BGM
     */
    private void playBGMForEndPlayers(BGMTrack track, Location soundLocation) {
        // 获取末地中的所有玩家
        List<Player> endPlayers = getPlayersInEnd();
        
        // 为每个末地玩家播放BGM
        for (Player player : endPlayers) {
            playSoundForPlayer(player, track, soundLocation);
        }
    }
    
    /**
     * 为单个玩家播放声音
     */
    private void playSoundForPlayer(Player player, BGMTrack track, Location soundLocation) {
        String command = String.format(
            "playsound %s voice %s %f %f %f %f",
            track.getSoundName(),
            player.getName(),
            soundLocation.getX(),
            soundLocation.getY(),
            soundLocation.getZ(),
            track.getVolume() * 200 // 大音量确保全岛可听
        );
        
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
    
    /**
     * 获取末地中的所有玩家
     */
    private List<Player> getPlayersInEnd() {
        List<Player> endPlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
                endPlayers.add(player);
            }
        }
        return endPlayers;
    }
    
    /**
     * 启动全局BGM循环播放
     */
    private void startGlobalBGMLoop(BGMTrack track, Location soundSource) {
        if (globalBgmTask != null) {
            globalBgmTask.cancel();
        }
        
        globalBgmTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isPlaying) {
                    cancel();
                    return;
                }
                
                // 检查声音源是否在末地
                boolean isInEnd = soundSource.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
                if (!isInEnd) {
                    cancel();
                    return;
                }
                
                // 为末地中的所有玩家重新播放BGM
                playBGMForEndPlayers(track, soundSource);
            }
        };
        
        // 在音乐长度后重新播放
        globalBgmTask.runTaskTimer(plugin, track.getLengthSeconds() * 20L, track.getLengthSeconds() * 20L);
    }
    
    /**
     * 停止所有玩家的BGM
     */
    public void stopAllBGM() {
        // 停止全局循环任务
        if (globalBgmTask != null) {
            globalBgmTask.cancel();
            globalBgmTask = null;
        }
        
        // 停止所有玩家的音乐
        if (currentSoundName != null && !currentSoundName.isEmpty()) {
            // 为所有在线玩家停止音乐
            for (Player player : Bukkit.getOnlinePlayers()) {
                String command = String.format("stopsound %s voice %s", player.getName(), currentSoundName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
        
        // 清理状态
        isPlaying = false;
        currentSoundName = "";
        lastSoundLocation = null;
        
        // 广播停止
        Bukkit.broadcastMessage("§7[音乐] 战斗BGM已停止");
    }
    
    /**
     * 停止特定玩家的BGM
     */
    public void stopBGMForPlayer(Player player) {
        // 停止特定玩家的音乐
        if (currentSoundName != null && !currentSoundName.isEmpty()) {
            String command = String.format("stopsound %s voice %s", player.getName(), currentSoundName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        
        // 发送停止提示（可选）
        player.sendMessage("§7[音乐] 战斗BGM已停止");
    }
    
    /**
     * 停止特定玩家的所有BGM
     */
    public void stopAllBGMForPlayer(Player player) {
        // 停止玩家所有声音
        String command = String.format("stopsound %s", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
    
    /**
     * 更新Boss阶段和BGM
     */
    public void updateBossPhase(BossPhase newPhase) {
        if (currentPhase == newPhase) return;
        
        currentPhase = newPhase;
        
        // 停止当前音乐
        stopAllBGM();
        
        // 播放新阶段的音乐
        playBGMForAll(newPhase);
        
        // 广播阶段切换
        broadcastPhaseChange(newPhase);
    }
    
    /**
     * 获取Boss位置
     */
    private Location getBossLocation() {
        // 检查Orion Boss
        if (!plugin.getActiveBosses().isEmpty()) {
            for (OrionBoss boss : plugin.getActiveBosses().values()) {
                if (boss.getBoss() != null && !boss.getBoss().isDead()) {
                    return boss.getBoss().getLocation();
                }
            }
        }
        
        return null;
    }
    
    /**
     * 广播BGM开始
     */
    private void broadcastBGMStart(BossPhase phase) {
        String message;
        
        switch (phase) {
            case ORION_NORMAL:
                message = "§7[音乐] §6史诗般的BGM响起，猎户座的战斗开始了！";
                break;
            case ORION_RAGE:
                message = "§7[音乐] §c音乐变得激昂！猎户座进入狂怒状态！";
                break;
            case APOSTLE:
                message = "§7[音乐] §5神秘的旋律响起，使徒降临战场！";
                break;
            case ORION_FINAL:
                message = "§7[音乐] §4最终决战的音乐响起，胜负即将揭晓！";
                break;
            default:
                message = "§7[音乐] §a战斗BGM开始播放";
        }
        
        Bukkit.broadcastMessage(message);
    }
    
    /**
     * 广播阶段切换
     */
    private void broadcastPhaseChange(BossPhase newPhase) {
        String message;
        
        switch (newPhase) {
            case ORION_RAGE:
                message = "§7[音乐] §6BGM切换：猎户座进入第二阶段！";
                break;
            case APOSTLE:
                message = "§7[音乐] §5BGM切换：使徒降临！";
                break;
            case ORION_FINAL:
                message = "§7[音乐] §4BGM切换：最终决战开始！";
                break;
            default:
                return;
        }
        
        Bukkit.broadcastMessage(message);
    }
    
    /**
     * 获取当前播放状态
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * 获取当前阶段
     */
    public BossPhase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopAllBGM();
    }
    
    /**
     * 为新进入末地的玩家播放BGM
     */
    public void playForNewEndPlayer(Player player) {
        if (isPlaying && isInEnd(player) && lastSoundLocation != null) {
            // 为新玩家播放当前BGM
            BGMTrack track = currentPhase.getBgmTrack();
            playSoundForPlayer(player, track, lastSoundLocation);
        }
    }
    
    /**
     * 检查玩家是否在末地
     */
    private boolean isInEnd(Player player) {
        return player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
    }
    
    /**
     * 当玩家离开末地时调用
     */
    public void onPlayerLeaveEnd(Player player) {
        stopBGMForPlayer(player);
    }
}
