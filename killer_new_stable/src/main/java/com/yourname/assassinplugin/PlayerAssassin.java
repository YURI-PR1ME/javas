package com.yourname.assassinplugin;

import java.util.UUID;

public class PlayerAssassin {
    private final UUID playerId;
    private String displayName;
    private int completedContracts;
    private int failedContracts;
    private int totalEarnings;
    private double successRate;
    private long joinTime;
    private boolean isActive;
    private int entryFeePaid;
    
    public PlayerAssassin(UUID playerId, String displayName) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.completedContracts = 0;
        this.failedContracts = 0;
        this.totalEarnings = 0;
        this.successRate = 0.0;
        this.joinTime = System.currentTimeMillis();
        this.isActive = true;
        this.entryFeePaid = 0;
    }
    
    // Getters and Setters
    public UUID getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getCompletedContracts() { return completedContracts; }
    public void setCompletedContracts(int completedContracts) { 
        this.completedContracts = completedContracts; 
        updateSuccessRate();
    }
    public int getFailedContracts() { return failedContracts; }
    public void setFailedContracts(int failedContracts) { 
        this.failedContracts = failedContracts; 
        updateSuccessRate();
    }
    public int getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(int totalEarnings) { this.totalEarnings = totalEarnings; }
    public double getSuccessRate() { return successRate; }
    public long getJoinTime() { return joinTime; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public int getEntryFeePaid() { return entryFeePaid; }
    public void setEntryFeePaid(int entryFeePaid) { this.entryFeePaid = entryFeePaid; }
    
    public void addCompletedContract(int earnings) {
        this.completedContracts++;
        this.totalEarnings += earnings;
        updateSuccessRate();
    }
    
    public void addFailedContract() {
        this.failedContracts++;
        updateSuccessRate();
    }
    
    private void updateSuccessRate() {
        int total = completedContracts + failedContracts;
        if (total > 0) {
            this.successRate = (double) completedContracts / total * 100;
        } else {
            this.successRate = 0.0;
        }
    }
    
    public int getTotalContracts() {
        return completedContracts + failedContracts;
    }
    
    public String getAnonymousId() {
        return "刺客_" + playerId.toString().substring(0, 8).toUpperCase();
    }
}
