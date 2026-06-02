package com.auction.common.network;

import java.io.Serializable;

/**
 * DTO chứa thông tin báo cáo sự cố từ Bidder gửi lên Server.
 * Payload của RequestCode.REPORT_ISSUE
 */
public class ReportIssueDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private int auctionId;
    private String issueType;
    private String description;

    public ReportIssueDTO() {}

    public ReportIssueDTO(int userId, int auctionId, String issueType, String description) {
        this.userId      = userId;
        this.auctionId   = auctionId;
        this.issueType   = issueType;
        this.description = description;
    }

    public int getUserId()           { return userId; }
    public void setUserId(int v)     { this.userId = v; }

    public int getAuctionId()        { return auctionId; }
    public void setAuctionId(int v)  { this.auctionId = v; }

    public String getIssueType()              { return issueType; }
    public void setIssueType(String v)        { this.issueType = v; }

    public String getDescription()            { return description; }
    public void setDescription(String v)      { this.description = v; }

    @Override
    public String toString() {
        return "ReportIssueDTO{userId=" + userId + ", auctionId=" + auctionId
                + ", issueType='" + issueType + "', description='" + description + "'}";
    }
}

