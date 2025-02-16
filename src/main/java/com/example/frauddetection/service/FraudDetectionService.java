package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collections;
import java.util.Date;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private final TransactionRepository transactionRepository;
    private final LoadingCache<String, List<Transaction>> recentTransactionsCache;
    private final LoadingCache<String, Long> suspiciousTransactionsCache;

    public FraudDetectionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        
        // Cache for recent transactions
        this.recentTransactionsCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, List<Transaction>>() {
                @Override
                public List<Transaction> load(String accountId) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.HOUR, -24);
                    Date oneDayAgo = cal.getTime();
                    return transactionRepository.findRecentTransactionsByAccount(accountId, oneDayAgo);
                }
            });

        // Cache for suspicious transaction counts
        this.suspiciousTransactionsCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(new CacheLoader<String, Long>() {
                @Override
                public Long load(String accountId) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.HOUR, -24);
                    Date oneDayAgo = cal.getTime();
                    return transactionRepository.countRecentSuspiciousTransactions(accountId, oneDayAgo, Transaction.TransactionStatus.FLAGGED_SUSPICIOUS);
                }
            });
    }
    
    @Value("${fraud.detection.amount.threshold:1000}")
    private BigDecimal amountThreshold;
    
    @Value("${fraud.detection.suspicious.transactions.threshold:3}")
    private int suspiciousTransactionsThreshold;
    
    @Value("${fraud.detection.location.change.threshold:500}")
    private BigDecimal locationChangeThreshold;

    public Transaction.TransactionStatus analyzeTransaction(Transaction transaction) {
        log.info("Analyzing transaction: {}", transaction);
        
        // Check for high-value transactions
        if (transaction.getAmount() != null && transaction.getAmount().compareTo(amountThreshold) > 0) {
            log.warn("High-value transaction detected: {}", transaction);
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }
        
        // Check for multiple suspicious transactions in the last 24 hours using cache
        try {
            long recentSuspiciousCount = suspiciousTransactionsCache.get(transaction.getAccountId());
            if (recentSuspiciousCount >= suspiciousTransactionsThreshold) {
                log.warn("Multiple suspicious transactions detected for account: {}", transaction.getAccountId());
                return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
            }
        } catch (ExecutionException e) {
            log.error("Error retrieving suspicious transactions count from cache", e);
        }
        
        // Check for rapid successive transactions using cache
        List<Transaction> recentTransactions;
        try {
            recentTransactions = recentTransactionsCache.get(transaction.getAccountId());
        } catch (ExecutionException e) {
            log.error("Error retrieving recent transactions from cache", e);
            recentTransactions = Collections.emptyList();
        }
        
        if (isRapidSuccessiveTransactions(recentTransactions, transaction)) {
            log.warn("Rapid successive transactions detected for account: {}", transaction.getAccountId());
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }

        // Check for suspicious location changes
        if (!recentTransactions.isEmpty() && isLocationSuspicious(recentTransactions, transaction)) {
            log.warn("Suspicious location change detected for account: {}", transaction.getAccountId());
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }

        // Check for unusual currency patterns
        if (!recentTransactions.isEmpty() && isUnusualCurrencyPattern(recentTransactions, transaction)) {
            log.warn("Unusual currency pattern detected for account: {}", transaction.getAccountId());
            return Transaction.TransactionStatus.FLAGGED_SUSPICIOUS;
        }
        
        return Transaction.TransactionStatus.APPROVED;
    }
    
    private boolean isRapidSuccessiveTransactions(List<Transaction> recentTransactions, Transaction currentTransaction) {
        if (recentTransactions.size() < 3) return false;
        
        // Sort transactions by timestamp
        recentTransactions.sort((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()));
        
        // Check if there are 3 or more transactions within 5 minutes
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentTransaction.getTimestamp());
        cal.add(Calendar.MINUTE, -5);
        Date fiveMinutesAgo = cal.getTime();
        
        long rapidTransactions = recentTransactions.stream()
            .filter(t -> t.getTimestamp().after(fiveMinutesAgo))
            .count();
            
        return rapidTransactions >= 3;
    }

    private boolean isLocationSuspicious(List<Transaction> recentTransactions, Transaction currentTransaction) {
        // Get the most recent transaction
        Transaction lastTransaction = recentTransactions.stream()
            .max((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
            .orElse(null);

        if (lastTransaction == null) return false;

        // If locations are different and amount is above threshold, flag as suspicious
        return !lastTransaction.getLocation().equals(currentTransaction.getLocation()) &&
               currentTransaction.getAmount().compareTo(locationChangeThreshold) > 0;
    }

    private boolean isUnusualCurrencyPattern(List<Transaction> recentTransactions, Transaction currentTransaction) {
        // Check if the current transaction uses a different currency from recent transactions
        long distinctCurrencies = recentTransactions.stream()
            .map(Transaction::getCurrency)
            .distinct()
            .count();

        // If there are already multiple currencies used recently, and this is a new one, flag as suspicious
        if (distinctCurrencies >= 2 && 
            recentTransactions.stream().noneMatch(t -> t.getCurrency().equals(currentTransaction.getCurrency()))) {
            return true;
        }

        return false;
    }
}