package com.yourname.assassinplugin;

import java.util.UUID;

public class PlayerContractSession {
    private final UUID sessionId;
    private final UUID buyerId;
    private final UUID assassinId;
    private final UUID targetId;
    private final long createdTime;
    
    private int proposedPrice;
    private boolean priceAccepted;
    private boolean active;
    private boolean completed;
    private boolean success;
    private long completionTime;
    private String communicationBookData;
    
    public PlayerContractSession(UUID sessionId, UUID buyerId, UUID assassinId, UUID targetId) {
        this.sessionId = sessionId;
        this.buyerId = buyerId;
        this.assassinId = assassinId;
        this.targetId = targetId;
        this.createdTime = System.currentTimeMillis();
        this.proposedPrice = 0;
        this.priceAccepted = false;
        this.active = false;
        this.completed = false;
        this.success = false;
        this.communicationBookData = "";
    }
    
    // Getters and Setters
    public UUID getSessionId() { return sessionId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getAssassinId() { return assassinId; }
    public UUID getTargetId() { return targetId; }
    public long getCreatedTime() { return createdTime; }
    public int getProposedPrice() { return proposedPrice; }
    public void setProposedPrice(int proposedPrice) { this.proposedPrice = proposedPrice; }
    public boolean isPriceAccepted() { return priceAccepted; }
    public void setPriceAccepted(boolean priceAccepted) { this.priceAccepted = priceAccepted; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getCompletionTime() { return completionTime; }
    public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
    public String getCommunicationBookData() { return communicationBookData; }
    public void setCommunicationBookData(String communicationBookData) { this.communicationBookData = communicationBookData; }
    
    public boolean isReadyToActivate() {
        return priceAccepted && !active && !completed;
    }
}
