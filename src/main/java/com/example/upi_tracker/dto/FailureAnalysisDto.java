package com.example.upi_tracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FailureAnalysisDto {

    @JsonProperty("failure_type")
    private String failureType;

    @JsonProperty("recovery_score")
    private Integer recoveryScore;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    // ── Constructors ──────────────────────────
    public FailureAnalysisDto() {}

    // ── Getters and Setters ───────────────────
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }

    public Integer getRecoveryScore() { return recoveryScore; }
    public void setRecoveryScore(Integer recoveryScore) { this.recoveryScore = recoveryScore; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }
}
