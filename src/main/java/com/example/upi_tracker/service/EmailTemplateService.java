package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class EmailTemplateService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Day 1 — confirmation that dispute was raised
    public String day1Subject(FailedTransaction txn) {
        return "Your UPI dispute is being tracked — Ref: " + txn.getUpiRefId();
    }

    public String day1Body(FailedTransaction txn) {
        return """
            Hi %s,

            Your failed UPI transaction has been logged in UPI Recovery Desk.

            ── Transaction Summary ───────────────────────────────
            Amount          : Rs. %s
            UPI Reference   : %s
            Failure Type    : %s
            Recovery Score  : %s / 100
            RBI Deadline    : %s
            ─────────────────────────────────────────────────────

            What happens next:
            • Your bank has 5 business days from the transaction date
              to process the refund automatically (RBI Circular 2019-20/142)
            • We will remind you at Day 3 if no response is received
            • We will send escalation steps at Day 5 if still unresolved

            If you want to take action now, log in to your UPI Recovery Desk
            dashboard and generate a dispute email to send to your bank directly.

            Stay patient — most TIMEOUT and BANK_ERROR failures are refunded
            automatically within 2-3 business days.

            — UPI Recovery Desk
            """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getUpiRefId(),
                txn.getFailureType(),
                txn.getRecoveryScore(),
                txn.getDeadlineDate().format(FMT)
        );
    }

    // Day 3 — nudge + NPCI escalation
    public String day3Subject(FailedTransaction txn) {
        return "No refund yet? Escalate to NPCI — Ref: " + txn.getUpiRefId();
    }

    public String day3Body(FailedTransaction txn) {
        return """
            Hi %s,

            3 days have passed since your UPI transaction failed and
            we haven't detected a refund yet.

            ── Still Pending ─────────────────────────────────────
            Amount        : Rs. %s
            UPI Reference : %s
            RBI Deadline  : %s (in 2 days)
            ─────────────────────────────────────────────────────

            Time to escalate. Copy and send this email to NPCI:

            ══════════════════════════════════════════════════════
            TO: npci.escalation@npci.org.in
            SUBJECT: Escalation — Failed UPI Refund Pending | Ref: %s

            Dear NPCI Team,

            I am writing to escalate a failed UPI transaction where my bank
            has not processed the refund within the expected timeframe.

            Amount         : Rs. %s
            UPI Reference  : %s
            Transaction Date: %s
            My Bank        : %s
            RBI Deadline   : %s

            Despite waiting 3 business days, the refund has not been credited.
            Kindly intervene and ensure the refund is processed before the
            RBI-mandated deadline of %s.

            Name    : %s
            Phone   : %s
            ══════════════════════════════════════════════════════

            — UPI Recovery Desk
            """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getUpiRefId(),
                txn.getDeadlineDate().format(FMT),
                txn.getUpiRefId(),
                txn.getAmount(),
                txn.getUpiRefId(),
                txn.getFailedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                txn.getUser().getBankName(),
                txn.getDeadlineDate().format(FMT),
                txn.getDeadlineDate().format(FMT),
                txn.getUser().getName(),
                txn.getUser().getPhone()
        );
    }

    // Day 5 — deadline today + RBI Ombudsman
    public String day5Subject(FailedTransaction txn) {
        return "URGENT: RBI refund deadline is TODAY — Ref: " + txn.getUpiRefId();
    }

    public String day5Body(FailedTransaction txn) {
        return """
            Hi %s,

            TODAY is the last day your bank is legally required to refund
            Rs. %s under RBI Circular 2019-20/142.

            If the money has not been credited by end of day, file an
            RBI Banking Ombudsman complaint immediately:

            ── File Online ────────────────────────────────────────
            RBI Ombudsman Portal: https://cms.rbi.org.in
            ──────────────────────────────────────────────────────

            When filing, use these details:
            Amount         : Rs. %s
            UPI Reference  : %s
            Your Bank      : %s
            Failure Date   : %s
            Deadline Missed: %s

            An RBI complaint is free, takes 10 minutes, and banks are
            legally obligated to respond within 30 days. Most complaints
            are resolved in 7-10 days after filing.

            ── Next Steps ─────────────────────────────────────────
            1. File at https://cms.rbi.org.in today
            2. Keep the complaint reference number
            3. Mark this transaction as resolved in your dashboard
               once the money is credited
            ──────────────────────────────────────────────────────

            — UPI Recovery Desk
            """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getAmount(),
                txn.getUpiRefId(),
                txn.getUser().getBankName(),
                txn.getFailedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                txn.getDeadlineDate().format(FMT)
        );
    }

    // Day 7 — deadline passed + consumer forum
    public String day7Subject(FailedTransaction txn) {
        return "Deadline passed — here's your consumer forum option | Ref: " + txn.getUpiRefId();
    }

    public String day7Body(FailedTransaction txn) {
        return """
            Hi %s,

            The RBI refund deadline for your Rs. %s transaction has passed
            without resolution. Here's your next option:

            ── Consumer Forum ─────────────────────────────────────
            File at: https://consumerhelpline.gov.in
            This is free, online, and legally binding on the bank.
            ──────────────────────────────────────────────────────

            What to mention in your complaint:
            • Bank name        : %s
            • UPI Reference    : %s
            • Amount           : Rs. %s
            • Transaction date : %s
            • RBI deadline     : %s (now passed)
            • Actions taken    : Dispute raised, NPCI escalated, RBI complaint filed

            Consumer forums typically resolve banking complaints in 30-45 days.
            Banks rarely contest cases where UPI reference numbers are clear.

            If the money has already been credited, please log in to your
            UPI Recovery Desk and mark this transaction as resolved.

            — UPI Recovery Desk
            """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getUser().getBankName(),
                txn.getUpiRefId(),
                txn.getAmount(),
                txn.getFailedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                txn.getDeadlineDate().format(FMT)
        );
    }
    public String threeDaysBeforeBody(FailedTransaction txn) {
        return """
        Hi %s,

        Your UPI refund deadline is in 3 days — %s.

        ── Transaction Details ────────────────────────────────
        Amount        : Rs. %s
        UPI Reference : %s
        Failure Type  : %s
        Deadline      : %s
        ──────────────────────────────────────────────────────

        Has the money been credited back? If YES — log in to UPI Recovery
        Desk and mark this transaction as Resolved.

        If NO — it's time to act. Send this dispute email to your bank NOW:

        ══════════════════════════════════════════════════════
        TO: %s
        SUBJECT: Urgent: Pending UPI Refund — Ref: %s

        Dear %s Customer Care,

        I raised a dispute for failed UPI transaction Ref: %s
        for Rs. %s on %s. The RBI-mandated 5-day refund window
        expires on %s. I have not received the refund yet.

        Please process this immediately or I will escalate to NPCI.

        Name: %s | Phone: %s
        ══════════════════════════════════════════════════════

        — UPI Recovery Desk
        """.formatted(
                txn.getUser().getName(),
                txn.getDeadlineDate(),
                txn.getAmount(), txn.getUpiRefId(),
                txn.getFailureType(), txn.getDeadlineDate(),
                getBankEmail(txn.getUser().getBankName()),
                txn.getUpiRefId(),
                txn.getUser().getBankName(),
                txn.getUpiRefId(), txn.getAmount(),
                txn.getFailedAt().toLocalDate(),
                txn.getDeadlineDate(),
                txn.getUser().getName(), txn.getUser().getPhone()
        );
    }

    public String twoDaysBeforeBody(FailedTransaction txn) {
        return """
        Hi %s,

        ⚠️ URGENT: Only 2 days left to claim your Rs. %s refund.

        After %s, your claim becomes significantly harder to enforce.

        ── Immediate Action Required ─────────────────────────
        1. Call your bank helpline RIGHT NOW: %s
        2. Quote UPI Reference: %s
        3. Demand refund under RBI Circular 2019-20/142
        4. Get a complaint reference number from the agent
        ──────────────────────────────────────────────────────

        Also send an email simultaneously to %s with subject:
        "URGENT: Refund Demanded Before RBI Deadline — Ref: %s"

        If bank refuses or delays — escalate to NPCI at:
        https://www.npci.org.in/what-we-do/upi/dispute-redressal-mechanism

        — UPI Recovery Desk
        """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getDeadlineDate(),
                getBankHelpline(txn.getUser().getBankName()),
                txn.getUpiRefId(),
                getBankEmail(txn.getUser().getBankName()),
                txn.getUpiRefId()
        );
    }

    public String deadlineDayBody(FailedTransaction txn) {
        return """
        Hi %s,

        🚨 TODAY IS THE LAST DAY for your Rs. %s refund.

        RBI Circular 2019-20/142 mandates refund by %s (today).
        After today, the legal obligation weakens significantly.

        DO ALL OF THESE RIGHT NOW:

        1. CALL YOUR BANK: %s
           Say: "I demand refund for UPI Ref %s under RBI mandate.
           Today is my deadline. I need this resolved today."

        2. EMAIL YOUR BANK: %s
           Subject: "Final Demand: Refund by RBI Deadline — Ref: %s"

        3. FILE NPCI COMPLAINT (takes 5 minutes):
           https://www.npci.org.in/what-we-do/upi/dispute-redressal-mechanism

        4. SMS your bank's dispute number with:
           "DISPUTE %s" (if your bank supports SMS disputes)

        If money is credited today — mark as resolved in your dashboard.
        If not — file RBI Ombudsman complaint tomorrow at:
        https://cms.rbi.org.in

        — UPI Recovery Desk
        """.formatted(
                txn.getUser().getName(),
                txn.getAmount(),
                txn.getDeadlineDate(),
                getBankHelpline(txn.getUser().getBankName()),
                txn.getUpiRefId(),
                getBankEmail(txn.getUser().getBankName()),
                txn.getUpiRefId(),
                txn.getUpiRefId()
        );
    }

    private String getBankHelpline(String bankName) {
        if (bankName == null) return "1800-XXX-XXXX";
        return switch (bankName.toUpperCase()) {
            case "HDFC"  -> "1800-202-6161";
            case "SBI"   -> "1800-425-3800";
            case "ICICI" -> "1800-102-4242";
            case "AXIS"  -> "1800-419-5577";
            case "KOTAK" -> "1860-266-2666";
            default      -> "check your bank's official website";
        };
    }

    private String getBankEmail(String bankName) {
        if (bankName == null) return "support@yourbank.com";
        return switch (bankName.toUpperCase()) {
            case "HDFC"  -> "support@hdfcbank.com";
            case "SBI"   -> "customercare@sbi.co.in";
            case "ICICI" -> "customer.care@icicibank.com";
            case "AXIS"  -> "support@axisbank.com";
            case "KOTAK" -> "customerservice@kotak.com";
            default      -> "support@yourbank.com";
        };
    }
}