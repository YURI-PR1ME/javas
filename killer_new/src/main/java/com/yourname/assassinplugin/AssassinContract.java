package com.yourname.assassinplugin;

import java.util.UUID;

public class AssassinContract {
    private final UUID contractId;
    private final UUID employerId;
    private final UUID targetId;
    private final int tier;
    private final long createdTime;
    
    private UUID assassinId;
    private boolean active;
    private boolean completed;
    private boolean success;
    private long completionTime;
    
    public AssassinContract(UUID contractId, UUID employerId, UUID targetId, int tier, long createdTime) {
        this.contractId = contractId;
        this.employerId = employerId;
        this.targetId = targetId;
        this.tier = tier;
        this.createdTime = createdTime;
        this.active = false;
        this.completed = false;
        this.success = false;
    }
    
    // Getters and Setters
    public UUID getContractId() { return contractId; }
    public UUID getEmployerId() { return employerId; }
    public UUID getTargetId() { return targetId; }
    public int getTier() { return tier; }
    public long getCreatedTime() { return createdTime; }
    public UUID getAssassinId() { return assassinId; }
    public void setAssassinId(UUID assassinId) { this.assassinId = assassinId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getCompletionTime() { return completionTime; }
    public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
}
