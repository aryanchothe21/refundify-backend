package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import com.example.upi_tracker.repository.FailedTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class DisputeService {

    @Autowired
    private FailedTransactionRepository repository;

    // Bank name → official dispute email mapping
    private static final Map<String, String> BANK_EMAILS = Map.of(
            "HDFC",   "support@hdfcbank.com",
            "SBI",    "customercare@sbi.co.in",
            "ICICI",  "customer.care@icicibank.com",
            "AXIS",   "support@axisbank.com",
            "KOTAK",  "customerservice@kotak.com",
            "PNB",    "care@pnb.co.in",
            "BOB",    "customercare@bankofbaroda.com"
    );

    public String generateDisputeEmail(UUID transactionId, String requesterEmail) {

        FailedTransaction txn = repository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // ownership check — stop user A from disputing user B's transaction
        if (!txn.getUser().getEmail().equals(requesterEmail)) {
            throw new RuntimeException("You do not have permission to access this transaction");
        }

        // ... rest of the method stays exactly the same
        // Step 2: figure out which bank to address
        String bankName = txn.getUser().getBankName() != null
                ? txn.getUser().getBankName().toUpperCase()
                : "YOUR BANK";

        String bankEmail = BANK_EMAILS.getOrDefault(bankName,
                "support@yourbank.com");

        // Step 3: format the date nicely
        String formattedDate = txn.getFailedAt()
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));

        String deadlineFormatted = txn.getDeadlineDate()
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        // Step 4: build the email
        String email = buildEmail(txn, bankName, bankEmail,
                formattedDate, deadlineFormatted);

        // Step 5: update status to DISPUTED
        txn.setStatus("DISPUTED");
        repository.save(txn);

        return email;
    }

    private String buildEmail(FailedTransaction txn, String bankName,
                              String bankEmail, String txnDate, String deadline) {

        return """
            TO: %s
            SUBJECT: Urgent Dispute — Failed UPI Transaction | Ref: %s

            Dear %s Customer Care Team,

            I am writing to formally dispute a failed UPI transaction where
            my account has been debited but the payment was not processed.

            ── Transaction Details ──────────────────────────────
            Account Holder : %s
            Account Number : XXXXXXXXXX (registered mobile: %s)
            Amount Debited : Rs. %s
            UPI Reference  : %s
            Date & Time    : %s
            Failure Type   : %s
            ────────────────────────────────────────────────────

            Despite the debit of Rs. %s from my account, the transaction
            shows as failed/pending and the merchant has not received the
            payment. As per RBI Circular RBI/2019-20/142 on Failed
            Transactions, I am entitled to a full refund within 5 business
            days of the failed transaction.

            ── RBI Refund Deadline: %s ──────────────────────────

            I request you to:
            1. Immediately investigate this transaction using the UPI
               Reference ID: %s
            2. Process the refund of Rs. %s to my account within
               the RBI-mandated 5 business day window
            3. Send a confirmation SMS/email once the refund is processed

            Please note that if this matter is not resolved by %s,
            I will be compelled to escalate this to:
            - NPCI (National Payments Corporation of India)
            - RBI Banking Ombudsman
            - Consumer Forum

            I trust this matter will receive your urgent attention.

            Yours sincerely,
            %s
            Mobile: %s
            Email: %s
            Date of Complaint: %s

            ────────────────────────────────────────────────────
            This complaint is filed under RBI/2019-20/142 circular
            regarding customer protection in failed UPI transactions.
            """.formatted(
                bankEmail, txn.getUpiRefId(),
                bankName,
                txn.getUser().getName(), txn.getUser().getPhone(),
                txn.getAmount(), txn.getUpiRefId(), txnDate,
                txn.getFailureType(),
                txn.getAmount(),
                deadline,
                txn.getUpiRefId(), txn.getAmount(),
                deadline,
                txn.getUser().getName(),
                txn.getUser().getPhone(),
                txn.getUser().getEmail(),
                java.time.LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        );
    }
}
