package com.yourname.arenaplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.yourname.creditplugin.CreditManager;
import com.yourname.creditplugin.CreditPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BetManager {
    
    private final Map<UUID, Bet> activeBets = new ConcurrentHashMap<>();
    private final Map<UUID, Bet> settledBets = new ConcurrentHashMap<>();
    private boolean bettingOpen = false;
    private Arena currentBettingArena;
    
    // 开启下注
    public void openBetting(Arena arena) {
        this.bettingOpen = true;
        this.currentBettingArena = arena;
        activeBets.clear();
        
        // 广播下注开放消息
        String message = ChatColor.translateAlternateColorCodes('&',
            ArenaPlugin.getInstance().getConfig().getString("messages.bet-open", 
                "&e下注已开放！使用 /arena bet <A|B> <红|蓝> <金额> 进行下注"));
        Bukkit.broadcastMessage(message);
        
        // 通知选手不能下注自己的比赛
        if (arena.getRedPlayer() != null) {
            arena.getRedPlayer().sendMessage(ChatColor.RED + "⚔️ 你是选手，不能下注自己的比赛！");
        }
        if (arena.getBluePlayer() != null) {
            arena.getBluePlayer().sendMessage(ChatColor.RED + "⚔️ 你是选手，不能下注自己的比赛！");
        }
    }
    
    // 关闭下注
    public void closeBetting() {
        this.bettingOpen = false;
    }
    
    // 下注
    public boolean placeBet(Player player, BetTier tier, Team team, int amount) {
        if (!bettingOpen) {
            player.sendMessage(ChatColor.RED + "❌ 当前无法下注！");
            return false;
        }
        
        // 检查是否是选手
        if (currentBettingArena != null && currentBettingArena.isFighter(player)) {
            player.sendMessage(ChatColor.RED + "❌ 你是选手，不能下注自己的比赛！");
            return false;
        }
        
        FileConfiguration config = ArenaPlugin.getInstance().getConfig();
        int minBet = config.getInt("betting.min-bet", 1);
        int maxBet = config.getInt("betting.max-bet", 100);
        
        // 检查下注金额
        if (amount < minBet || amount > maxBet) {
            player.sendMessage(ChatColor.RED + "❌ 下注金额必须在 " + minBet + " 到 " + maxBet + " 之间！");
            return false;
        }
        
        // 检查信用点
        CreditManager creditManager = getCreditManager();
        if (creditManager == null) {
            player.sendMessage(ChatColor.RED + "❌ 信用点系统未加载！");
            return false;
        }
        
        if (creditManager.getCredits(player) < amount) {
            player.sendMessage(ChatColor.RED + "❌ 信用点不足！");
            return false;
        }
        
        // 扣除信用点
        creditManager.removeCredits(player, amount);
        
        // 记录下注
        Bet bet = new Bet(player.getUniqueId(), tier, team, amount);
        activeBets.put(player.getUniqueId(), bet);
        
        // 发送确认消息
        String teamName = team == Team.RED ? "红队" : "蓝队";
        String tierName = tier == BetTier.A ? "A档(保守)" : "B档(激进)";
        String message = ChatColor.translateAlternateColorCodes('&',
            String.format(ArenaPlugin.getInstance().getConfig().getString("messages.bet-placed", 
                "&a下注成功！你下注 %s 点信用点到 %s 档，支持 %s 队"), amount, tierName, teamName));
        player.sendMessage(message);
        
        return true;
    }
    
    // 结算下注
    public void settleBets(Arena arena, Player winner) {
        Team winningTeam = arena.getPlayerTeam(winner);
        FileConfiguration config = ArenaPlugin.getInstance().getConfig();
        CreditManager creditManager = getCreditManager();
        
        if (creditManager == null) {
            Bukkit.getLogger().severe("信用点系统未加载，无法结算下注！");
            return;
        }
        
        for (Bet bet : activeBets.values()) {
            Player bettor = Bukkit.getPlayer(bet.getPlayerId());
            if (bettor != null && bettor.isOnline()) {
                int payout = calculatePayout(bet, winningTeam, config);
                
                if (payout > 0) {
                    // 发放奖励
                    creditManager.addCredits(bettor, payout);
                    
                    // 发送结果消息
                    String message = ChatColor.translateAlternateColorCodes('&',
                        String.format(config.getString("messages.bet-result", 
                            "&e下注结果：你获得了 %d 点信用点"), payout));
                    bettor.sendMessage(message);
                } else {
                    bettor.sendMessage(ChatColor.RED + "❌ 下注失败，未获得奖励");
                }
                
                // 移动到已结算列表
                settledBets.put(bet.getPlayerId(), bet);
            }
        }
        
        activeBets.clear();
        bettingOpen = false;
        currentBettingArena = null;
    }
    
    // 计算赔付
    private int calculatePayout(Bet bet, Team winningTeam, FileConfiguration config) {
        if (bet.getTeam() == winningTeam) {
            // 获胜
            double multiplier = bet.getTier() == BetTier.A ? 
                config.getDouble("betting.tier-a.win-multiplier") : 
                config.getDouble("betting.tier-b.win-multiplier");
            return (int) (bet.getAmount() * multiplier);
        } else {
            // 失败
            double refund = bet.getTier() == BetTier.A ? 
                config.getDouble("betting.tier-a.lose-refund") : 
                config.getDouble("betting.tier-b.lose-refund");
            return (int) (bet.getAmount() * refund);
        }
    }
    
    // 保存下注数据
    public void saveAllBets() {
        // 实现保存逻辑
    }
    
    // Getter 方法
    public boolean isBettingOpen() {
        return bettingOpen;
    }
    
    public Arena getCurrentBettingArena() {
        return currentBettingArena;
    }
    
    // 获取信用点管理器
    private CreditManager getCreditManager() {
        try {
            return CreditPlugin.getInstance().getCreditManager();
        } catch (Exception e) {
            Bukkit.getLogger().severe("无法获取信用点管理器: " + e.getMessage());
            return null;
        }
    }
}

// Bet 类定义
class Bet {
    private final UUID playerId;
    private final BetTier tier;
    private final Team team;
    private final int amount;
    
    public Bet(UUID playerId, BetTier tier, Team team, int amount) {
        this.playerId = playerId;
        this.tier = tier;
        this.team = team;
        this.amount = amount;
    }
    
    public UUID getPlayerId() { return playerId; }
    public BetTier getTier() { return tier; }
    public Team getTeam() { return team; }
    public int getAmount() { return amount; }
}

// BetTier 枚举定义
enum BetTier {
    A, B
}
