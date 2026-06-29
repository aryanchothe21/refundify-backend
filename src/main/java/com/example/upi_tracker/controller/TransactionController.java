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

            // 1. Get AI Analysis
            AiClassifierService.FullAnalysisResult preview =
                    aiClassifierService.analyzeCompletely(request.getSms());

            // 2. Extract Amount
            java.math.BigDecimal finalAmount = preview.amount;
            if (finalAmount == null || finalAmount.compareTo(java.math.BigDecimal.ZERO) == 0) {
                finalAmount = aiClassifierService.extractAmountFromSms(request.getSms());
            }

            // 3. Start building the database transaction object for ALL scenarios
            FailedTransaction txn = new FailedTransaction();
            txn.setUser(user);
            txn.setRawSms(request.getSms());
            txn.setAmount(finalAmount);
            txn.setUpiRefId(preview.upiRef == null || preview.upiRef.isBlank()
                    ? "REF-" + System.currentTimeMillis() : preview.upiRef);
            txn.setMerchantName(preview.merchantName == null || preview.merchantName.isBlank()
                    ? "Unknown Merchant" : preview.merchantName);

            // 4. Handle Reversal (THE FIX IS HERE)
            if (preview.isReversal) {
                txn.setFailureType("REFUND_COMPLETED");
                txn.setRecoveryScore(100);
                txn.setStatus("RESOLVED");
                txn.setResolved(true);
                txn.setFailedAt(java.time.LocalDateTime.now());
                txn.setDeadlineDate(java.time.LocalDate.now());

                // SAVE IT TO THE DATABASE!
                FailedTransaction saved = transactionService.saveTransaction(txn);

                Map<String, Object> reversalResponse = new HashMap<>();
                reversalResponse.put("type",               "REVERSAL");
                reversalResponse.put("message",            "Money is already being returned!");
                reversalResponse.put("transactionId",      saved.getId()); // Now we have a real DB ID!
                reversalResponse.put("amount",             saved.getAmount());
                reversalResponse.put("recoveryScore",      100);
                reversalResponse.put("recoveryReasoning",  preview.recoveryReasoning);
                reversalResponse.put("recommendedAction",  preview.recommendedAction);
                reversalResponse.put("expectedResolutionDays", 0);
                reversalResponse.put("escalationNeeded",   false);
                return ResponseEntity.ok(reversalResponse);
            }

            // 5. Kick out non-failures
            if (!preview.isFailure) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "This appears to be a successful transaction or informational message. No tracking needed."));
            }

            // 6. Handle Standard Failures
            txn.setFailureType(preview.failureType == null ? "BANK_ERROR" : preview.failureType);
            txn.setRecoveryScore(preview.recoveryScore);
            txn.setStatus("PENDING_RECOVERY");
            txn.setResolved(false);
            txn.setFailedAt(java.time.LocalDateTime.now());
            txn.setDeadlineDate(preview.rbiApplies
                    ? java.time.LocalDate.now().plusDays(5)
                    : java.time.LocalDate.now().plusDays(3));

            // SAVE IT TO THE DATABASE!
            FailedTransaction saved = transactionService.saveTransaction(txn);

            Map<String, Object> response = new HashMap<>();
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
