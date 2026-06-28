package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import com.example.upi_tracker.repository.FailedTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class FollowUpEmailService {

    @Autowired private JavaMailSender mailSender;
    @Autowired private FailedTransactionRepository repository;
    @Autowired private EmailTemplateService templates;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.dev-mode:true}")
    private boolean devMode;

    @Scheduled(cron = "0 0 9 * * *")
    public void runDailyFollowUps() {
        System.out.println("[FollowUp] Daily check at 9 AM...");
        processAll(false);
    }

    public String triggerManually() {
        return processAll(true);
    }

    private String processAll(boolean manual) {
        List<FailedTransaction> active = repository.findByStatusNot("RESOLVED");
        int sent = 0;
        StringBuilder log = new StringBuilder();

        for (FailedTransaction txn : active) {
            long daysSinceFailed = ChronoUnit.DAYS.between(
                    txn.getFailedAt().toLocalDate(), LocalDate.now());

            long daysUntilDeadline = txn.getDeadlineDate() != null
                    ? ChronoUnit.DAYS.between(LocalDate.now(), txn.getDeadlineDate())
                    : 99;

            Set<String> sent_keys = getSentKeys(txn);

            log.append("\n[").append(txn.getUpiRefId()).append("] ")
                    .append("Days since failed: ").append(daysSinceFailed)
                    .append(", Days until deadline: ").append(daysUntilDeadline)
                    .append(", Sent: ").append(sent_keys);

            // Day 1 — initial confirmation (24 hrs after failure)
            if (daysSinceFailed >= 1 && !sent_keys.contains("D1")) {
                if (fire(txn, "D1", manual)) sent++;
            }

            // 3 days before deadline — first deadline warning
            if (daysUntilDeadline <= 3 && daysUntilDeadline > 1
                    && !sent_keys.contains("3BD")) {
                if (fire(txn, "3BD", manual)) sent++;
            }

            // 2 days before deadline — escalation nudge
            if (daysUntilDeadline <= 2 && daysUntilDeadline > 0
                    && !sent_keys.contains("2BD")) {
                if (fire(txn, "2BD", manual)) sent++;
            }

            // Deadline day — urgent action required
            if (daysUntilDeadline == 0 && !sent_keys.contains("D0")) {
                if (fire(txn, "D0", manual)) sent++;
            }

            // 1 day after deadline passed — file RBI complaint
            if (daysUntilDeadline < 0 && daysUntilDeadline >= -1
                    && !sent_keys.contains("P1")) {
                if (fire(txn, "P1", manual)) sent++;
            }

            // 7 days after deadline — consumer forum last resort
            if (daysUntilDeadline < -7 && !sent_keys.contains("P7")) {
                if (fire(txn, "P7", manual)) sent++;
            }
        }

        String summary = "Processed " + active.size() +
                " active transactions. Sent " + sent + " emails.";
        System.out.println("[FollowUp] " + summary);
        return summary + log;
    }

    private boolean fire(FailedTransaction txn, String key, boolean manual) {
        String subject = getSubject(txn, key);
        String body    = getBody(txn, key);

        if (devMode) {
            System.out.println("[FollowUp DEV] Would send [" + key + "] to "
                    + txn.getUser().getEmail() + " — " + subject);
            markSent(txn, key);
            return true;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(txn.getUser().getEmail());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            markSent(txn, key);
            System.out.println("[FollowUp] Sent [" + key + "] to "
                    + txn.getUser().getEmail());
            return true;
        } catch (Exception e) {
            System.err.println("[FollowUp] Failed [" + key + "]: " + e.getMessage());
            return false;
        }
    }

    private String getSubject(FailedTransaction txn, String key) {
        return switch (key) {
            case "D1"  -> "Your failed UPI transaction is being tracked — Ref: " + txn.getUpiRefId();
            case "3BD" -> "⏰ 3 days left: Take action on your ₹" + txn.getAmount() + " refund";
            case "2BD" -> "⚠️ 2 days left: Escalate your UPI dispute NOW — Ref: " + txn.getUpiRefId();
            case "D0"  -> "🚨 LAST DAY: RBI refund deadline TODAY for ₹" + txn.getAmount();
            case "P1"  -> "❌ Deadline passed — File RBI complaint for ₹" + txn.getAmount();
            case "P7"  -> "Consumer Forum: Last resort for ₹" + txn.getAmount() + " recovery";
            default    -> "UPI Recovery Update — Ref: " + txn.getUpiRefId();
        };
    }

    private String getBody(FailedTransaction txn, String key) {
        return switch (key) {
            case "D1"  -> templates.day1Body(txn);
            case "3BD" -> templates.threeDaysBeforeBody(txn);
            case "2BD" -> templates.twoDaysBeforeBody(txn);
            case "D0"  -> templates.deadlineDayBody(txn);
            case "P1"  -> templates.day5Body(txn);
            case "P7"  -> templates.day7Body(txn);
            default    -> "";
        };
    }

    private Set<String> getSentKeys(FailedTransaction txn) {
        Set<String> keys = new HashSet<>();
        if (txn.getFollowUpDaysSent() != null && !txn.getFollowUpDaysSent().isEmpty()) {
            for (String k : txn.getFollowUpDaysSent().split(",")) {
                if (!k.isBlank()) keys.add(k.trim());
            }
        }
        return keys;
    }

    private void markSent(FailedTransaction txn, String key) {
        String existing = txn.getFollowUpDaysSent();
        String updated = (existing == null || existing.isEmpty())
                ? key : existing + "," + key;
        txn.setFollowUpDaysSent(updated);
        repository.save(txn);
    }
}