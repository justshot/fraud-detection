package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.math.BigDecimal;

@Service
@EnableAsync
public class TransactionSQSConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransactionSQSConsumer.class);
    private final Executor asyncExecutor;

    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;
    private final AlertingService alertingService;

    public TransactionSQSConsumer(FraudDetectionService fraudDetectionService,
                                TransactionRepository transactionRepository,
                                AlertingService alertingService) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionRepository = transactionRepository;
        this.alertingService = alertingService;
        this.asyncExecutor = asyncExecutor();
    }

    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("AsyncThread-");
        executor.initialize();
        return executor;
    }

    @SqsListener("${aws.sqs.queue.transactions}")
    @Async("asyncExecutor")
    public CompletableFuture<Void> receiveMessage(Transaction transaction) {
        log.info("Received transaction from SQS: {}", transaction);
        
        // Analyze the transaction for potential fraud
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(transaction);
        transaction.setStatus(status);
        
        // Save the transaction with its analysis result
        transactionRepository.save(transaction);
        
        if (status == Transaction.TransactionStatus.FLAGGED_SUSPICIOUS) {
            log.warn("Suspicious transaction detected and flagged: {}", transaction);
            String reason = determineFraudReason(transaction);
            alertingService.sendAlert(transaction, reason);
        }

        return CompletableFuture.completedFuture(null);
    }

    private String determineFraudReason(Transaction transaction) {
        if (transaction.getAmount().compareTo(new BigDecimal(1000)) > 0) {
            return "HIGH_VALUE";
        } else if (!transaction.getLocation().equals(transaction.getPreviousLocation())) {
            return "SUSPICIOUS_LOCATION";
        } else if (transaction.getCurrency() != null && !transaction.getCurrency().equals(transaction.getPreviousCurrency())) {
            return "UNUSUAL_CURRENCY";
        }
        return "UNKNOWN";
    }
}