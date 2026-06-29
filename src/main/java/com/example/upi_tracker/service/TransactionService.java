package com.example.upi_tracker.service;

import com.example.upi_tracker.dto.ScanResult;
import com.example.upi_tracker.entity.FailedTransaction;
import com.example.upi_tracker.entity.User;
import com.example.upi_tracker.repository.FailedTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    @Autowired private AiClassifierService aiClassifierService;
    @Autowired private FailedTransactionRepository repository;
    @Autowired private UserService userService;

   public ScanResult processSms(String sms, User user) {

    // Step 1: Send full SMS to Gemini AI
    AiClassifierService.FullAnalysisResult analysis =
            aiClassifierService.analyzeCompletely(sms);
    
    // Step 2: Handle non-failure SMS first (Kick out actual junk SMS)
    if (!analysis.isFailure && !analysis.isReversal) {
        throw new RuntimeException(
                "This doesn't appear to be a failed or reversed transaction SMS. " +
                        "Please paste an SMS regarding a payment issue."
        );
    }

    // Step 3: Amount extraction with fallback
    BigDecimal finalAmount = analysis.amount;
    if (finalAmount == null || finalAmount.compareTo(BigDecimal.ZERO) == 0) {
        finalAmount = aiClassifierService.extractAmountFromSms(sms);
    }

    // Step 4: Build the transaction object
    FailedTransaction txn = new FailedTransaction();
    txn.setUser(user);
    txn.setRawSms(sms);
    txn.setAmount(finalAmount);
    txn.setUpiRefId(analysis.upiRef == null || analysis.upiRef.isBlank() ? "REF-" + System.currentTimeMillis() : analysis.upiRef);
    txn.setMerchantName(analysis.merchantName == null || analysis.merchantName.isBlank() ? "Unknown Merchant" : analysis.merchantName);
    
    // Fallback logic for failure type
    txn.setFailureType(analysis.failureType == null || analysis.failureType.isBlank() ? "BANK_ERROR" : analysis.failureType);

    // Step 5: The Reversal Fix
    if (analysis.isReversal) {
        // If it's a reversal, it is already successful. Save it as RESOLVED with a 100 score.
        txn.setRecoveryScore(100);
        txn.setStatus("RESOLVED"); 
        txn.setResolved(true);
        txn.setFailedAt(LocalDateTime.now());
        txn.setDeadlineDate(LocalDate.now()); // No deadline needed
    } else {
        // Standard pending failure
        txn.setRecoveryScore(analysis.recoveryScore);
        txn.setStatus("PENDING_RECOVERY");
        txn.setResolved(false);
        txn.setFailedAt(LocalDateTime.now());
        txn.setDeadlineDate(analysis.rbiApplies ? LocalDate.now().plusDays(5) : LocalDate.now().plusDays(3));
    }

    // Step 6: Save to database
    FailedTransaction saved = repository.save(txn);

    // Step 7: Return transaction + AI reasoning
    return new ScanResult(
            saved,
            analysis.isReversal ? "Funds have already been reversed." : analysis.recoveryReasoning,
            analysis.isReversal ? "No action needed." : analysis.recommendedAction,
            analysis.isReversal ? 0 : analysis.expectedResolutionDays,
            !analysis.isReversal && analysis.escalationNeeded
    );
}

    public List<FailedTransaction> getUserTransactions(UUID userId) {
        return repository.findByUserId(userId);
    }

    public List<FailedTransaction> getPendingTransactions(UUID userId) {
        return repository.findByUserIdAndStatus(userId, "PENDING_RECOVERY");
    }

    public FailedTransaction markResolved(UUID transactionId, User user) {
        FailedTransaction txn = repository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction not found with ID: " + transactionId));

        if (!txn.getUser().getId().equals(user.getId())) {
            throw new RuntimeException(
                    "You don't have permission to update this transaction.");
        }

        txn.setStatus("RESOLVED");
        txn.setResolved(true);
        return repository.save(txn);

        }
    public FailedTransaction saveTransaction(FailedTransaction txn) {
        return repository.save(txn);
    }
        }

