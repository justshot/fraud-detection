package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FraudDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private final TransactionRepository transactionRepository;

    public FraudDetectionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }
    
    @Value("${fraud.detection.amount.threshold:1000}")
    private BigDecimal amountThreshold;
    
    @Value("${fraud.detection.suspicious.transactions.threshold:3}")
    private int suspiciousTransactionsThreshold;
    
    public Transaction.TransactionStatus analyzeTransaction(Transaction transaction) {
        log.info("Analyzing transaction: {}", transaction);
        
        // Check for high-value transactions
        if (transaction.getAmount().compareTo(amountThreshold) > 0) {
            log.warn("High-value transaction detected: {}", transaction);
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }
        
        // Check for multiple suspicious transactions in the last 24 hours
        LocalDateTime oneDayAgo = transaction.getTimestamp().minus(24, ChronoUnit.HOURS);
        long recentSuspiciousCount = transactionRepository.countRecentSuspiciousTransactions(
            transaction.getAccountId(),
            oneDayAgo
        );
        
        if (recentSuspiciousCount >= suspiciousTransactionsThreshold) {
            log.warn("Multiple suspicious transactions detected for account: {}", transaction.getAccountId());
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }
        
        // Check for rapid successive transactions
        List<Transaction> recentTransactions = transactionRepository.findRecentTransactionsByAccount(
            transaction.getAccountId(),
            oneDayAgo
        );
        
        if (isRapidSuccessiveTransactions(recentTransactions, transaction)) {
            log.warn("Rapid successive transactions detected for account: {}", transaction.getAccountId());
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }
        
        return Transaction.TransactionStatus.APPROVED;
    }
    
    private boolean isRapidSuccessiveTransactions(List<Transaction> recentTransactions, Transaction currentTransaction) {
        if (recentTransactions.size() < 3) return false;
        
        // Sort transactions by timestamp
        recentTransactions.sort((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()));
        
        // Check if there are 3 or more transactions within 5 minutes
        LocalDateTime fiveMinutesAgo = currentTransaction.getTimestamp().minus(5, ChronoUnit.MINUTES);
        long rapidTransactions = recentTransactions.stream()
            .filter(t -> t.getTimestamp().isAfter(fiveMinutesAgo))
            .count();
            
        return rapidTransactions >= 3;
    }
}