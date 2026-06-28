package com.example.upi_tracker.dto;

import com.example.upi_tracker.entity.FailedTransaction;

public class ScanResult {
    public FailedTransaction transaction;
    public String recoveryReasoning;
    public String recommendedAction;
    public int expectedResolutionDays;
    public boolean escalationNeeded;

    public ScanResult(FailedTransaction transaction,
                      String recoveryReasoning,
                      String recommendedAction,
                      int expectedResolutionDays,
                      boolean escalationNeeded) {
        this.transaction             = transaction;
        this.recoveryReasoning       = recoveryReasoning;
        this.recommendedAction       = recommendedAction;
        this.expectedResolutionDays  = expectedResolutionDays;
        this.escalationNeeded        = escalationNeeded;
    }
}