package com.yourname.tyrantboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TyrantBGMPlayer {
    
    private final TyrantBossPlugin plugin;
    private BukkitRunnable globalBgmTask;
    
    // 添加同步控制字段
    private boolean isTransitioning = false; // 防止切换时的重叠
    private final Object lock = new Object(); // 同步锁
    
    // BGM曲目定义
    public enum BGMTrack {
        TYRANT_PHASE_1("yourplugin:tyrant_phase1", 140, SoundCategory.MASTER, 3200.0f),
        TYRANT_PHASE_2("yourplugin:tyrant_phase2", 180, SoundCategory.MASTER, 3200.0f),
        LOST_GHOST("yourplugin:lost_ghost", 140, SoundCategory.MASTER, 3200.0f);
        
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
        TYRANT_NORMAL(BGMTrack.TYRANT_PHASE_1),
        TYRANT_RAGE(BGMTrack.TYRANT_PHASE_2),
        GHOST_PHASE(BGMTrack.LOST_GHOST);
        
        private final BGMTrack bgmTrack;
        
        BossPhase(BGMTrack bgmTrack) {
            this.bgmTrack = bgmTrack;
        }
        
        public BGMTrack getBgmTrack() { return bgmTrack; }
    }
    
    private BossPhase currentPhase = BossPhase.TYRANT_NORMAL;
    private boolean isPlaying = false;
    private String currentSoundName = "";
    
    public TyrantBGMPlayer(TyrantBossPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 为所有在暴君所在世界的玩家播放BGM
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
                    
                    // 获取暴君所在世界的玩家
                    List<Player> bossWorldPlayers = getPlayersInBossWorld();
                    if (bossWorldPlayers.isEmpty()) {
                        plugin.getLogger().info("没有玩家在暴君世界，跳过BGM播放");
                        isTransitioning = false;
                        return;
                    }
                    
                    // 为每个玩家播放BGM
                    playBGMForPlayers(bossWorldPlayers, track);
                    
                    // 创建循环播放任务
                    startGlobalBGMLoop(track);
                    
                    // 广播BGM开始
                    broadcastBGMStart(phase);
                    
                    plugin.getLogger().info("开始播放BGM: " + track.getSoundName() + "，玩家数量: " + bossWorldPlayers.size());
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
            // 停止特定声音（如果知道声音名称）
            if (currentSoundName != null && !currentSoundName.isEmpty()) {
                player.stopSound(currentSoundName);
            }
            
            // 停止整个音乐类别的所有声音（更彻底）
            player.stopSound(SoundCategory.MUSIC);
            
            // 停止所有类别的特定声音
            for (BGMTrack track : BGMTrack.values()) {
                player.stopSound(track.getSoundName());
            }
            
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
            Bukkit.broadcastMessage("§7[音乐] 暴君战斗BGM已停止");
            plugin.getLogger().info("暴君BGM已停止");
        }
    }
    
    /**
     * 获取暴君所在世界的玩家
     */
    private List<Player> getPlayersInBossWorld() {
        List<Player> bossWorldPlayers = new ArrayList<>();
        
        // 优先检查暴君Boss
        World bossWorld = null;
        
        // 查找暴君所在的世界
        for (TyrantBoss boss : plugin.getActiveBosses().values()) {
            if (boss != null && boss.getBoss() != null && !boss.getBoss().isDead()) {
                bossWorld = boss.getBoss().getWorld();
                break;
            }
        }
        
        // 如果暴君不存在，检查残魂
        if (bossWorld == null) {
            for (TyrantGhostBoss ghostBoss : plugin.getActiveGhostBosses().values()) {
                if (ghostBoss != null && ghostBoss.getGhost() != null && !ghostBoss.getGhost().isDead()) {
                    bossWorld = ghostBoss.getGhost().getWorld();
                    break;
                }
            }
        }
        
        // 如果找到了暴君或残魂的世界，收集该世界的玩家
        if (bossWorld != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(bossWorld)) {
                    bossWorldPlayers.add(player);
                }
            }
        }
        
        return bossWorldPlayers;
    }
    
    /**
     * 为指定玩家列表播放BGM
     */
    private void playBGMForPlayers(List<Player> players, BGMTrack track) {
        for (Player player : players) {
            if (player.isOnline()) {
                try {
                    // 先完全停止可能正在播放的声音
                    stopSoundCompletely(player);
                    
                    // 延迟1tick再播放，确保停止生效
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.playSound(
                                player.getLocation(),     // 声源：玩家当前位置
                                track.getSoundName(),     // 声音键
                                track.getCategory(),      // 声音类别：音乐
                                track.getVolume(),        // 超大音量，确保覆盖范围
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
                    
                    // 检查暴君世界是否有玩家
                    List<Player> bossWorldPlayers = getPlayersInBossWorld();
                    if (bossWorldPlayers.isEmpty()) {
                        plugin.getLogger().info("没有玩家在暴君世界，暂停BGM循环");
                        return;
                    }
                    
                    // 为所有暴君世界玩家重新播放BGM
                    playBGMForPlayers(bossWorldPlayers, track);
                    
                    // 调试日志
                    plugin.getLogger().fine("暴君BGM循环播放中... 当前阶段: " + currentPhase + 
                        ", 玩家数量: " + bossWorldPlayers.size());
                }
            }
        };
        
        // 在音乐长度后重新播放
        int delayTicks = track.getLengthSeconds() * 20;
        globalBgmTask.runTaskTimer(plugin, delayTicks, delayTicks);
        
        plugin.getLogger().info("暴君BGM循环任务已启动，间隔: " + delayTicks + " ticks");
    }
    
    /**
     * 停止特定玩家的BGM
     */
    public void stopBGMForPlayer(Player player) {
        synchronized (lock) {
            stopSoundCompletely(player);
            plugin.getLogger().info("停止玩家 " + player.getName() + " 的BGM");
            player.sendMessage("§7[音乐] 暴君战斗BGM已停止");
        }
    }
    
    /**
     * 更新Boss阶段和BGM
     */
    public void updateBossPhase(BossPhase newPhase) {
        synchronized (lock) {
            if (currentPhase == newPhase) return;
            
            plugin.getLogger().info("暴君BGM阶段切换: " + currentPhase + " -> " + newPhase);
            
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
            case TYRANT_NORMAL:
                message = "§7[音乐] §6DEAD GOD";
                break;
            case TYRANT_RAGE:
                message = "§7[音乐] §cANGER OF NETHER";
                break;
            case GHOST_PHASE:
                message = "§7[音乐] §5LOST GHOST";
                break;
            default:
                message = "§7[音乐] §aONE MORE STEP";
        }
        
        // 只向暴君所在世界的玩家广播
        for (Player player : getPlayersInBossWorld()) {
            player.sendMessage(message);
        }
        plugin.getLogger().info("暴君BGM已播放: " + message);
    }
    
    /**
     * 当玩家离开暴君世界时调用
     */
    public void onPlayerLeaveBossWorld(Player player, World leftWorld) {
        synchronized (lock) {
            // 检查离开的世界是否是暴君所在的世界
            World bossWorld = null;
            for (TyrantBoss boss : plugin.getActiveBosses().values()) {
                if (boss != null && boss.getBoss() != null && !boss.getBoss().isDead()) {
                    bossWorld = boss.getBoss().getWorld();
                    break;
                }
            }
            
            if (bossWorld == null) {
                for (TyrantGhostBoss ghostBoss : plugin.getActiveGhostBosses().values()) {
                    if (ghostBoss != null && ghostBoss.getGhost() != null && !ghostBoss.getGhost().isDead()) {
                        bossWorld = ghostBoss.getGhost().getWorld();
                        break;
                    }
                }
            }
            
            if (bossWorld != null && leftWorld.equals(bossWorld)) {
                stopBGMForPlayer(player);
                plugin.getLogger().info("玩家 " + player.getName() + " 离开暴君世界，停止BGM");
            }
        }
    }
    
    /**
     * 当玩家进入暴君世界时，如果BGM正在播放，为其单独播放
     */
    public void onPlayerEnterBossWorld(Player player, World enteredWorld) {
        synchronized (lock) {
            if (isPlaying && !isTransitioning) {
                // 检查进入的世界是否是暴君所在的世界
                World bossWorld = null;
                for (TyrantBoss boss : plugin.getActiveBosses().values()) {
                    if (boss != null && boss.getBoss() != null && !boss.getBoss().isDead()) {
                        bossWorld = boss.getBoss().getWorld();
                        break;
                    }
                }
                
                if (bossWorld == null) {
                    for (TyrantGhostBoss ghostBoss : plugin.getActiveGhostBosses().values()) {
                        if (ghostBoss != null && ghostBoss.getGhost() != null && !ghostBoss.getGhost().isDead()) {
                            bossWorld = ghostBoss.getGhost().getWorld();
                            break;
                        }
                    }
                }
                
                if (bossWorld != null && enteredWorld.equals(bossWorld)) {
                    // 为新进入暴君世界的玩家播放当前BGM
                    List<Player> singlePlayerList = Collections.singletonList(player);
                    playBGMForPlayers(singlePlayerList, currentPhase.getBgmTrack());
                    player.sendMessage("§7[音乐] 暴君战斗BGM正在播放中...");
                }
            }
        }
    }
    
    // Getter方法
    public boolean isPlaying() {
        synchronized (lock) {
            return isPlaying && !isTransitioning;
        }
    }
    
    public BossPhase getCurrentPhase() {
        synchronized (lock) {
            return currentPhase;
        }
    }
    
    public void cleanup() {
        synchronized (lock) {
            stopAllBGM();
            plugin.getLogger().info("暴君BGM播放器已清理");
        }
    }
}
