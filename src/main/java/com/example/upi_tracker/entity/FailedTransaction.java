package com.example.upi_tracker.entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "failed_transactions")
public class FailedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String upiRefId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String merchantName;

    private String failureType;

    private Integer recoveryScore;

    @Column(nullable = false)
    private String status;

    private LocalDate deadlineDate;

    private LocalDateTime failedAt;

    @Column(columnDefinition = "TEXT")
    private String rawSms;

    // ── Constructors ──────────────────────────────
    public FailedTransaction() {
    }

    // ── Getters and Setters ───────────────────────
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getUpiRefId() {
        return upiRefId;
    }

    public void setUpiRefId(String upiRefId) {
        this.upiRefId = upiRefId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public Integer getRecoveryScore() {
        return recoveryScore;
    }

    public void setRecoveryScore(Integer recoveryScore) {
        this.recoveryScore = recoveryScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDeadlineDate() {
        return deadlineDate;
    }

    public void setDeadlineDate(LocalDate deadlineDate) {
        this.deadlineDate = deadlineDate;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public String getRawSms() {
        return rawSms;
    }

    public void setRawSms(String rawSms) {
        this.rawSms = rawSms;
    }

    @Column(name = "follow_up_days_sent", columnDefinition = "TEXT")
    private String followUpDaysSent; // stores "1,3,5" as days already emailed

    private Boolean resolved;

    public String getFollowUpDaysSent() {
        return followUpDaysSent;
    }

    public void setFollowUpDaysSent(String followUpDaysSent) {
        this.followUpDaysSent = followUpDaysSent;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }
}