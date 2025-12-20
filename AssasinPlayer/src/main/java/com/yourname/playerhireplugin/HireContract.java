package com.yourname.playerhireplugin;

import java.util.UUID;

public class HireContract {
    private final UUID contractId;
    private final UUID buyerId;
    private final UUID assassinId;
    private final UUID targetId;
    private final int amount;
    private final long createdTime;
    
    private boolean completed;
    private boolean success;
    private long completionTime;
    
    public enum ContractStatus {
        ACTIVE,
        COMPLETED,
        FAILED
    }
    
    public HireContract(UUID contractId, UUID buyerId, UUID assassinId, UUID targetId, int amount, long createdTime) {
        this.contractId = contractId;
        this.buyerId = buyerId;
        this.assassinId = assassinId;
        this.targetId = targetId;
        this.amount = amount;
        this.createdTime = createdTime;
        this.completed = false;
        this.success = false;
    }
    
    // Getters and Setters
    public UUID getContractId() { return contractId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getAssassinId() { return assassinId; }
    public UUID getTargetId() { return targetId; }
    public int getAmount() { return amount; }
    public long getCreatedTime() { return createdTime; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getCompletionTime() { return completionTime; }
    public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
    
    public ContractStatus getStatus() {
        if (!completed) return ContractStatus.ACTIVE;
        return success ? ContractStatus.COMPLETED : ContractStatus.FAILED;
    }
}
