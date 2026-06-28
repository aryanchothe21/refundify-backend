package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import com.example.upi_tracker.repository.FailedTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private FailedTransactionRepository repository;

    public Map<String, Object> getSummary(UUID userId) {

        // fetch all transactions for this user
        List<FailedTransaction> all = repository.findByUserId(userId);

        // total money stuck (all non-resolved transactions)
        BigDecimal totalStuck = all.stream()
                .filter(t -> !t.getStatus().equals("RESOLVED"))
                .map(FailedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // total money recovered
        BigDecimal totalRecovered = all.stream()
                .filter(t -> t.getStatus().equals("RESOLVED"))
                .map(FailedTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // count by status
        long pending  = all.stream()
                .filter(t -> t.getStatus().equals("PENDING_RECOVERY")).count();
        long disputed = all.stream()
                .filter(t -> t.getStatus().equals("DISPUTED")).count();
        long resolved = all.stream()
                .filter(t -> t.getStatus().equals("RESOLVED")).count();

        // transactions where deadline is in next 24 hours — urgent!
        List<FailedTransaction> urgent = all.stream()
                .filter(t -> !t.getStatus().equals("RESOLVED"))
                .filter(t -> t.getDeadlineDate() != null)
                .filter(t -> !t.getDeadlineDate().isAfter(
                        LocalDate.now().plusDays(1)))
                .collect(Collectors.toList());

        // average recovery score
        double avgScore = all.stream()
                .filter(t -> t.getRecoveryScore() != null)
                .mapToInt(FailedTransaction::getRecoveryScore)
                .average()
                .orElse(0.0);

        // build the response map
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTransactions",  all.size());
        summary.put("totalMoneyStuck",    totalStuck);
        summary.put("totalMoneyRecovered", totalRecovered);
        summary.put("pendingCount",       pending);
        summary.put("disputedCount",      disputed);
        summary.put("resolvedCount",      resolved);
        summary.put("urgentDeadlines",    urgent.size());
        summary.put("averageRecoveryScore",
                Math.round(avgScore * 10.0) / 10.0);
        summary.put("urgentTransactions", urgent);

        return summary;
    }
}