package com.example.upi_tracker.controller;

import com.example.upi_tracker.dto.ScanResult;
import com.example.upi_tracker.dto.SmsRequest;
import com.example.upi_tracker.entity.FailedTransaction;
import com.example.upi_tracker.entity.User;
import com.example.upi_tracker.service.TransactionService;
import com.example.upi_tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.upi_tracker.service.AiClassifierService;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired private TransactionService transactionService;
    @Autowired private UserService userService;
    @Autowired
    private AiClassifierService aiClassifierService;

    @PostMapping("/scan-sms")
    public ResponseEntity<?> scanSms(@RequestBody SmsRequest request,
                                     Authentication auth) {
        try {
            User user = userService.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check reversal BEFORE processing
            AiClassifierService.FullAnalysisResult preview =
                    aiClassifierService.analyzeCompletely(request.getSms());

            if (preview.isReversal) {
                java.util.Map<String, Object> reversalResponse = new java.util.HashMap<>();
                reversalResponse.put("type",               "REVERSAL");
                reversalResponse.put("message",            "Money is being returned!");
                reversalResponse.put("amount",             preview.amount);
                reversalResponse.put("recoveryScore",      100);
                reversalResponse.put("recoveryReasoning",  preview.recoveryReasoning);
                reversalResponse.put("recommendedAction",  preview.recommendedAction);
                reversalResponse.put("expectedResolutionDays", 1);
                reversalResponse.put("escalationNeeded",   false);
                return ResponseEntity.ok(reversalResponse);
            }

            if (!preview.isFailure) {
                return ResponseEntity.badRequest().body(
                        java.util.Map.of("error",
                                "This appears to be a successful transaction or " +
                                        "informational message. No tracking needed."));
            }

            ScanResult result = new ScanResult(null, null, null, 0, false);

            // Build transaction from existing analysis
            java.math.BigDecimal finalAmount = preview.amount;
            if (finalAmount == null || finalAmount.compareTo(java.math.BigDecimal.ZERO) == 0) {
                finalAmount = aiClassifierService.extractAmountFromSms(request.getSms());
            }

            com.example.upi_tracker.entity.FailedTransaction txn =
                    new com.example.upi_tracker.entity.FailedTransaction();
            txn.setUser(user);
            txn.setRawSms(request.getSms());
            txn.setAmount(finalAmount);
            txn.setUpiRefId(preview.upiRef == null || preview.upiRef.isBlank()
                    ? "REF-" + System.currentTimeMillis() : preview.upiRef);
            txn.setMerchantName(preview.merchantName == null || preview.merchantName.isBlank()
                    ? "Unknown Merchant" : preview.merchantName);
            txn.setFailureType(preview.failureType == null
                    ? "BANK_ERROR" : preview.failureType);
            txn.setRecoveryScore(preview.recoveryScore);
            txn.setStatus("PENDING_RECOVERY");
            txn.setFailedAt(java.time.LocalDateTime.now());
            txn.setResolved(false);
            txn.setDeadlineDate(preview.rbiApplies
                    ? java.time.LocalDate.now().plusDays(5)
                    : java.time.LocalDate.now().plusDays(3));

            com.example.upi_tracker.entity.FailedTransaction saved =
                    transactionService.saveTransaction(txn);

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("type",                  "FAILURE");
            response.put("message",               "Failed transaction detected and saved");
            response.put("transactionId",         saved.getId());
            response.put("amount",                saved.getAmount());
            response.put("merchantName",          saved.getMerchantName());
            response.put("upiRefId",              saved.getUpiRefId());
            response.put("failureType",           saved.getFailureType());
            response.put("recoveryScore",         saved.getRecoveryScore());
            response.put("status",                saved.getStatus());
            response.put("deadlineDate",          saved.getDeadlineDate().toString());
            response.put("recoveryReasoning",     preview.recoveryReasoning);
            response.put("recommendedAction",     preview.recommendedAction);
            response.put("expectedResolutionDays", preview.expectedResolutionDays);
            response.put("escalationNeeded",      preview.escalationNeeded);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<List<FailedTransaction>> getMyTransactions(
            Authentication auth) {
        User user = userService.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(
                transactionService.getUserTransactions(user.getId()));
    }

    @GetMapping("/me/pending")
    public ResponseEntity<List<FailedTransaction>> getMyPending(
            Authentication auth) {
        User user = userService.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(
                transactionService.getPendingTransactions(user.getId()));
    }

    @PostMapping("/{transactionId}/resolve")
    public ResponseEntity<?> markResolved(
            @PathVariable UUID transactionId, Authentication auth) {
        try {
            User user = userService.findByEmail(auth.getName()).orElseThrow();
            FailedTransaction txn = transactionService.markResolved(
                    transactionId, user);
            return ResponseEntity.ok(Map.of(
                    "message",       "Transaction marked as resolved",
                    "transactionId", txn.getId(),
                    "status",        txn.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage()));
        }
    }
}