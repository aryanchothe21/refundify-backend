package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class RecoveryScoreService {

    public int calculate(FailedTransaction txn) {
        int score = baseScoreForType(txn.getFailureType());
        score += referenceBonus(txn);
        score += amountPenalty(txn.getAmount());
        score += deadlineBonus(txn);
        score += merchantBonus(txn.getMerchantName());
        return clamp(score);
    }

    // ── Base score per failure type ──────────────────────────────────────
    // This is the most important factor — failure type directly determines
    // how likely the bank is legally required to refund
    private int baseScoreForType(String failureType) {
        if (failureType == null) return 50;
        return switch (failureType) {
            case "DUPLICATE"       -> 93; // charged twice — almost certain refund
            case "TIMEOUT"         -> 85; // auto-refunded in 24-48 hrs usually
            case "BANK_ERROR"      -> 80; // bank is liable under RBI rules
            case "LIMIT_EXCEEDED"  -> 62; // user can fix and retry — partial recovery
            case "GHOST_MERCHANT"  -> 48; // needs NPCI, harder to prove
            case "USER_ERROR"      -> 22; // bank not liable, user mistake
            default                -> 50;
        };
    }

    // ── Bonus for having a UPI reference number ──────────────────────────
    // A reference number means the bank can trace the transaction.
    // Without it, recovery is much harder — can't prove what happened
    private int referenceBonus(FailedTransaction txn) {
        if (txn.getUpiRefId() == null) return -10;
        if (txn.getUpiRefId().startsWith("REF-")) return -8; // our placeholder
        if (txn.getUpiRefId().length() >= 10) return 8;      // real ref number
        return 0;
    }

    // ── Penalty for very large amounts ───────────────────────────────────
    // Banks scrutinize large refund requests more heavily
    // Small amounts are often auto-refunded without question
    private int amountPenalty(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return 0;
        double amt = amount.doubleValue();
        if (amt <= 500)    return  5;  // small — easy auto-refund
        if (amt <= 5000)   return  0;  // standard — no adjustment
        if (amt <= 25000)  return -3;  // larger — slightly more scrutiny
        if (amt <= 100000) return -6;  // high value — needs documentation
        return -10;                    // very large — bank will investigate
    }

    // ── Bonus for acting within the RBI deadline window ──────────────────
    // RBI Circular 2019-20/142 gives banks 5 business days to refund.
    // Acting early maximizes pressure on the bank
    private int deadlineBonus(FailedTransaction txn) {
        if (txn.getFailedAt() == null) return 0;
        long daysElapsed = ChronoUnit.DAYS.between(
                txn.getFailedAt().toLocalDate(),
                LocalDate.now()
        );
        if (daysElapsed == 0) return  6;  // same day — best position
        if (daysElapsed <= 2) return  4;  // within 2 days — strong position
        if (daysElapsed <= 5) return  2;  // within RBI window — still valid
        if (daysElapsed <= 15) return -5; // window passed — harder
        if (daysElapsed <= 30) return -12; // a month later — difficult
        return -20;                        // very late — very hard
    }

    // ── Bonus for known legitimate merchants ─────────────────────────────
    // Transactions with known merchants (Swiggy, Amazon, etc.) are easier
    // to dispute because the merchant can confirm no payment received
    private int merchantBonus(String merchantName) {
        if (merchantName == null || merchantName.equalsIgnoreCase("Unknown Merchant")) {
            return -3;
        }
        String upper = merchantName.toUpperCase();
        String[] knownMerchants = {
                "SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "MYNTRA",
                "NETFLIX", "SPOTIFY", "PHONEPE", "PAYTM", "GPAY",
                "GOOGLE", "APPLE", "UBER", "OLA", "ZEPTO", "BLINKIT",
                "CRED", "RAZORPAY", "INSTAMOJO", "JUSPAY"
        };
        for (String merchant : knownMerchants) {
            if (upper.contains(merchant)) return 4;
        }
        return 0;
    }

    private int clamp(int score) {
        return Math.max(5, Math.min(98, score));
    }
}