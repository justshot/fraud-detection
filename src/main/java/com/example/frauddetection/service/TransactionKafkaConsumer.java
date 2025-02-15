package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaConsumer.class);

    public TransactionKafkaConsumer(FraudDetectionService fraudDetectionService,
                                   TransactionRepository transactionRepository) {
        this.fraudDetectionService = fraudDetectionService;
        this.transactionRepository = transactionRepository;
    }
    private final FraudDetectionService fraudDetectionService;
    private final TransactionRepository transactionRepository;
    
    @KafkaListener(topics = "${kafka.topic.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(Transaction transaction) {
        log.info("Received transaction: {}", transaction);
        
        // Analyze the transaction for potential fraud
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(transaction);
        transaction.setStatus(status);
        
        // Save the transaction with its analysis result
        transactionRepository.save(transaction);
        
        if (status == Transaction.TransactionStatus.FLAGGED_SUSPICIOUS) {
            log.warn("Suspicious transaction detected and flagged: {}", transaction);
            // Here you would typically integrate with an alerting system
            // alertingService.sendAlert(transaction);
        }
    }
}