package com.example.upi_tracker.repository;

import com.example.upi_tracker.entity.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FailedTransactionRepository
        extends JpaRepository<FailedTransaction, UUID> {

    List<FailedTransaction> findByUserId(UUID userId);

    List<FailedTransaction> findByUserIdAndStatus(UUID userId, String status);

    List<FailedTransaction> findByDeadlineDateBeforeAndStatus(
            LocalDate date, String status);

    List<FailedTransaction> findByStatus(String status);
    List<FailedTransaction> findByStatusNot(String status);
}