package com.example.frauddetection.repository;

import com.example.frauddetection.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.timestamp >= :startTime")
    List<Transaction> findRecentTransactionsByAccount(
        @Param("accountId") String accountId,
        @Param("startTime") Date startTime
    );
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId AND t.status = :status AND t.timestamp >= :startTime")
    long countRecentSuspiciousTransactions(
        @Param("accountId") String accountId,
        @Param("startTime") Date startTime,
        @Param("status") Transaction.TransactionStatus status
    );
    
    List<Transaction> findByAccountIdAndAmountGreaterThan(
        String accountId,
        BigDecimal amount
    );
}