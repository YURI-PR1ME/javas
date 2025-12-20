package com.yourname.playerhireplugin;

import java.util.UUID;

public class AssassinProfile {
    private final UUID playerId;
    private final String playerName;
    private final long registeredTime;
    
    private int completedContracts;
    private int successfulContracts;
    private int failedContracts;
    private int totalEarned;
    
    public AssassinProfile(UUID playerId, String playerName, long registeredTime) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.registeredTime = registeredTime;
        this.completedContracts = 0;
        this.successfulContracts = 0;
        this.failedContracts = 0;
        this.totalEarned = 0;
    }
    
    // Getters and Setters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public long getRegisteredTime() { return registeredTime; }
    public int getCompletedContracts() { return completedContracts; }
    public void setCompletedContracts(int completedContracts) { this.completedContracts = completedContracts; }
    public int getSuccessfulContracts() { return successfulContracts; }
    public void setSuccessfulContracts(int successfulContracts) { this.successfulContracts = successfulContracts; }
    public int getFailedContracts() { return failedContracts; }
    public void setFailedContracts(int failedContracts) { this.failedContracts = failedContracts; }
    public int getTotalEarned() { return totalEarned; }
    public void setTotalEarned(int totalEarned) { this.totalEarned = totalEarned; }
    
    public double getSuccessRate() {
        if (completedContracts == 0) return 0.0;
        return (double) successfulContracts / completedContracts;
    }
    
    public void addCompletedContract(boolean success) {
        completedContracts++;
        if (success) {
            successfulContracts++;
        } else {
            failedContracts++;
        }
    }
    
    public void addEarnings(int amount) {
        totalEarned += amount;
    }
    
    public String getAnonymousId() {
        // 生成匿名ID（基于玩家UUID的前8位）
        return "ASS-" + playerId.toString().substring(0, 8).toUpperCase();
    }
}
