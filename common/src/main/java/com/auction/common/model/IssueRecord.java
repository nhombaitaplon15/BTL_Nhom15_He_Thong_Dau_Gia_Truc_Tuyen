package com.auction.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Model đại diện cho một bản ghi báo cáo sự cố trong DB.
 * Được truyền từ Server -> Admin Client qua socket.
 */
public class IssueRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int userId;
    private int auctionId;
    private String issueType;
    private String description;
    private LocalDateTime createdAt;

    public IssueRecord() {}

    public IssueRecord(int id, int userId, int auctionId, String issueType,
                       String description, LocalDateTime createdAt) {
        this.id          = id;
        this.userId      = userId;
        this.auctionId   = auctionId;
        this.issueType   = issueType;
        this.description = description;
        this.createdAt   = createdAt;
    }

    public int getId()                  { return id; }
    public void setId(int id)           { this.id = id; }

    public int getUserId()              { return userId; }
    public void setUserId(int userId)   { this.userId = userId; }

    public int getAuctionId()           { return auctionId; }
    public void setAuctionId(int v)     { this.auctionId = v; }

    public String getIssueType()        { return issueType; }
    public void setIssueType(String v)  { this.issueType = v; }

    public String getDescription()      { return description; }
    public void setDescription(String v){ this.description = v; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime v)   { this.createdAt = v; }

    @Override
    public String toString() {
        return "IssueRecord{id=" + id + ", userId=" + userId + ", auctionId=" + auctionId
                + ", issueType='" + issueType + "', createdAt=" + createdAt + "}";
    }
}

