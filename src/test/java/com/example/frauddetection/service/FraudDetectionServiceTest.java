package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        fraudDetectionService = new FraudDetectionService(transactionRepository);
        
        // Set threshold values for testing
        ReflectionTestUtils.setField(fraudDetectionService, "amountThreshold", new BigDecimal("1000"));
        ReflectionTestUtils.setField(fraudDetectionService, "suspiciousTransactionsThreshold", 3);
        ReflectionTestUtils.setField(fraudDetectionService, "locationChangeThreshold", new BigDecimal("500"));
    }

    @Test
    void whenTransactionAmountExceedsThreshold_thenFlagAsSuspicious() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("2000"));
        transaction.setAccountId("ACC123");

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(transaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS, status);
    }

    @Test
    void whenMultipleSuspiciousTransactions_thenFlagAsSuspicious() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAccountId("ACC123");
        transaction.setAmount(new BigDecimal("500"));

        when(transactionRepository.countRecentSuspiciousTransactions(eq("ACC123"), any(), eq(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS)))
            .thenReturn(3L);

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(transaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS, status);
    }

    @Test
    void whenRapidSuccessiveTransactions_thenFlagAsSuspicious() {
        // Arrange
        Calendar now = Calendar.getInstance();
        Date currentTime = now.getTime();
        Transaction currentTransaction = new Transaction();
        currentTransaction.setAccountId("ACC123");
        currentTransaction.setAmount(new BigDecimal("100"));
        currentTransaction.setTimestamp(currentTime);

        now.add(Calendar.MINUTE, -1);
        Transaction transaction1 = new Transaction();
        transaction1.setTimestamp(now.getTime());
        now.add(Calendar.MINUTE, -1);
        Transaction transaction2 = new Transaction();
        transaction2.setTimestamp(now.getTime());
        now.add(Calendar.MINUTE, -1);
        Transaction transaction3 = new Transaction();
        transaction3.setTimestamp(now.getTime());

        when(transactionRepository.findRecentTransactionsByAccount(eq("ACC123"), any()))
            .thenReturn(Arrays.asList(transaction1, transaction2, transaction3));

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(currentTransaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS, status);
    }

    @Test
    void whenLocationChange_thenFlagAsSuspicious() {
        // Arrange
        Calendar now = Calendar.getInstance();
        Transaction currentTransaction = new Transaction();
        currentTransaction.setAccountId("ACC123");
        currentTransaction.setAmount(new BigDecimal("600"));
        currentTransaction.setLocation("London");
        currentTransaction.setTimestamp(now.getTime());

        now.add(Calendar.HOUR, -1);
        Transaction previousTransaction = new Transaction();
        previousTransaction.setLocation("Paris");
        previousTransaction.setTimestamp(now.getTime());

        when(transactionRepository.findRecentTransactionsByAccount(eq("ACC123"), any()))
            .thenReturn(Collections.singletonList(previousTransaction));

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(currentTransaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS, status);
    }

    @Test
    void whenUnusualCurrencyPattern_thenFlagAsSuspicious() {
        // Arrange
        Calendar now = Calendar.getInstance();
        Transaction currentTransaction = new Transaction();
        currentTransaction.setAccountId("ACC123");
        currentTransaction.setAmount(new BigDecimal("100"));
        currentTransaction.setCurrency("JPY");
        currentTransaction.setTimestamp(now.getTime());

        now.add(Calendar.HOUR, -2);
        Transaction transaction1 = new Transaction();
        transaction1.setCurrency("USD");
        transaction1.setTimestamp(now.getTime());
        transaction1.setLocation("London");

        now.add(Calendar.HOUR, 1);
        Transaction transaction2 = new Transaction();
        transaction2.setCurrency("EUR");
        transaction2.setTimestamp(now.getTime());
        transaction2.setLocation("London");

        when(transactionRepository.findRecentTransactionsByAccount(eq("ACC123"), any()))
            .thenReturn(Arrays.asList(transaction1, transaction2));

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(currentTransaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.FLAGGED_SUSPICIOUS, status);
    }

    @Test
    void whenNormalTransaction_thenApprove() {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAccountId("ACC123");
        transaction.setAmount(new BigDecimal("100"));
        transaction.setLocation("London");
        transaction.setCurrency("USD");

        when(transactionRepository.findRecentTransactionsByAccount(eq("ACC123"), any()))
            .thenReturn(Collections.emptyList());

        // Act
        Transaction.TransactionStatus status = fraudDetectionService.analyzeTransaction(transaction);

        // Assert
        assertEquals(Transaction.TransactionStatus.APPROVED, status);
    }
}