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
        System.out.println("isFailure = " + analysis.isFailure);
        System.out.println("isReversal = " + analysis.isReversal);
        System.out.println("failureType = " + analysis.failureType);
        System.out.println("score = " + analysis.recoveryScore);

        // Step 2: Handle reversal
        if (analysis.isReversal) {
            throw new RuntimeException(
                    "Good news! This SMS shows a refund or reversal — " +
                            "your money is already being returned. No dispute needed."
            );
        }

        // Step 3: Handle non-failure SMS
        if (!analysis.isFailure) {
            throw new RuntimeException(
                    "This doesn't appear to be a failed transaction SMS. " +
                            "Please paste an SMS where a payment was declined, " +
                            "failed, or could not be processed."
            );
        }

        // Step 4: Amount extraction with fallback
        BigDecimal finalAmount = analysis.amount;

        if (finalAmount == null ||
                finalAmount.compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("[TransactionService] AI returned 0 amount, " +
                    "trying fallback extraction...");
            finalAmount = aiClassifierService.extractAmountFromSms(sms);
        }

        if (finalAmount.compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("[TransactionService] Fallback also returned 0. " +
                    "Saving with amount=0. SMS: " + sms);
        }

        // Step 5: Build the transaction object
        FailedTransaction txn = new FailedTransaction();
        txn.setUser(user);
        txn.setRawSms(sms);
        txn.setAmount(finalAmount);

        txn.setUpiRefId(
                analysis.upiRef == null || analysis.upiRef.isBlank()
                        ? "REF-" + System.currentTimeMillis()
                        : analysis.upiRef
        );

        txn.setMerchantName(
                analysis.merchantName == null || analysis.merchantName.isBlank()
                        ? "Unknown Merchant"
                        : analysis.merchantName
        );

        txn.setFailureType(
                analysis.failureType == null || analysis.failureType.isBlank()
                        ? "BANK_ERROR"
                        : analysis.failureType
        );

        txn.setRecoveryScore(analysis.recoveryScore);
        txn.setStatus("PENDING_RECOVERY");
        txn.setFailedAt(LocalDateTime.now());
        txn.setResolved(false);

        txn.setDeadlineDate(
                analysis.rbiApplies
                        ? LocalDate.now().plusDays(5)
                        : LocalDate.now().plusDays(3)
        );

        // Step 6: Save to database
        FailedTransaction saved = repository.save(txn);

        // Step 7: Return transaction + AI reasoning
        return new ScanResult(
                saved,
                analysis.recoveryReasoning,
                analysis.recommendedAction,
                analysis.expectedResolutionDays,
                analysis.escalationNeeded
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

