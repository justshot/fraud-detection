package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@Service
public class AlertingService {
    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);
    private final Counter fraudulentTransactionsCounter;
    private final Counter highValueTransactionsCounter;
    private final Counter suspiciousLocationCounter;
    private final Counter unusualCurrencyCounter;

    public AlertingService(MeterRegistry registry) {
        this.fraudulentTransactionsCounter = registry.counter("fraud.transactions.total");
        this.highValueTransactionsCounter = registry.counter("fraud.transactions.high_value");
        this.suspiciousLocationCounter = registry.counter("fraud.transactions.suspicious_location");
        this.unusualCurrencyCounter = registry.counter("fraud.transactions.unusual_currency");
    }

    public void sendAlert(Transaction transaction, String reason) {
        // Increment the total fraudulent transactions counter
        fraudulentTransactionsCounter.increment();

        // Increment specific counters based on the fraud reason
        switch (reason) {
            case "HIGH_VALUE":
                highValueTransactionsCounter.increment();
                break;
            case "SUSPICIOUS_LOCATION":
                suspiciousLocationCounter.increment();
                break;
            case "UNUSUAL_CURRENCY":
                unusualCurrencyCounter.increment();
                break;
        }

        // Log detailed alert information
        log.error("FRAUD ALERT - {} - Transaction Details: ID={}, Account={}, Amount={}, Location={}", 
            reason,
            transaction.getId(),
            transaction.getAccountId(),
            transaction.getAmount(),
            transaction.getLocation()
        );

        // Here you would typically integrate with external alerting systems
        // For example:
        // - Send to CloudWatch/Stackdriver
        // - Push to PagerDuty
        // - Send email notifications
        // - Push to Slack channel
    }
}