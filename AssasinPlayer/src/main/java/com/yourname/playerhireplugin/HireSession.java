package com.yourname.playerhireplugin;

import java.util.UUID;

public class HireSession {
    private final UUID sessionId;
    private final UUID buyerId;
    private final UUID assassinId;
    private UUID targetId;
    private final long createdTime;
    
    private SessionStatus status;
    private int offeredAmount;
    private UUID contractId;
    
    public enum SessionStatus {
        NEGOTIATING,
        OFFER_MADE,
        CONTRACT_CREATED,
        EXPIRED
    }
    
    public HireSession(UUID sessionId, UUID buyerId, UUID assassinId, long createdTime) {
        this.sessionId = sessionId;
        this.buyerId = buyerId;
        this.assassinId = assassinId;
        this.createdTime = createdTime;
        this.status = SessionStatus.NEGOTIATING;
        this.offeredAmount = 0;
    }
    
    // Getters and Setters
    public UUID getSessionId() { return sessionId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getAssassinId() { return assassinId; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public long getCreatedTime() { return createdTime; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public int getOfferedAmount() { return offeredAmount; }
    public void setOfferedAmount(int offeredAmount) { this.offeredAmount = offeredAmount; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID contractId) { this.contractId = contractId; }
}
