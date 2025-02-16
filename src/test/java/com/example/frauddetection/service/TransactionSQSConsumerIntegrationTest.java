package com.example.frauddetection.service;

import com.example.frauddetection.model.Transaction;
import com.example.frauddetection.repository.TransactionRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.context.annotation.Import;
import com.example.frauddetection.config.TestConfig;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;

@SpringBootTest
@Testcontainers
@Import(TestConfig.class)
class TransactionSQSConsumerIntegrationTest {

    private static final String QUEUE_NAME = "test-fraud-detection-queue";

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.1"))
            .withServices(SQS);

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private TransactionSQSConsumer transactionSQSConsumer;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private AlertingService alertingService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localStack.getEndpointOverride(SQS).toString());
        registry.add("aws.sqs.queue.transactions", () -> QUEUE_NAME);
    }

    @Autowired
    private AmazonSQS amazonSQS;

    @BeforeEach
    void setUp() throws Exception {
        // Create test queue using AWS SDK client
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(QUEUE_NAME);
        amazonSQS.createQueue(createQueueRequest);
        
        // Wait for queue to be available
        Thread.sleep(5000);
    }

    @Test
    void whenReceivingValidTransaction_thenProcessAndSave() throws InterruptedException {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAccountId("TEST123");
        transaction.setAmount(new BigDecimal("500"));
        transaction.setTimestamp(new Date());
        transaction.setLocation("TestLocation");
        transaction.setCurrency("USD");
        transaction.setStatus(Transaction.TransactionStatus.PENDING);

        // Act
        sqsTemplate.send(QUEUE_NAME, transaction);
        Thread.sleep(1000); // Wait for message to be processed

        // Assert
        verify(transactionRepository, timeout(5000)).save(any(Transaction.class));
    }

    @Test
    void whenReceivingSuspiciousTransaction_thenAlertAndSave() throws InterruptedException {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAccountId("TEST123");
        transaction.setAmount(new BigDecimal("2000")); // Above threshold
        transaction.setTimestamp(new Date());
        transaction.setLocation("TestLocation");
        transaction.setCurrency("USD");
        transaction.setStatus(Transaction.TransactionStatus.PENDING);

        // Act
        sqsTemplate.send(QUEUE_NAME, transaction);
        Thread.sleep(1000); // Wait for message to be processed

        // Assert
        verify(transactionRepository, timeout(5000)).save(any(Transaction.class));
        verify(alertingService, timeout(5000)).sendAlert(any(Transaction.class), any(String.class));
    }

    @Test
    void whenReceivingMultipleTransactions_thenProcessAllSuccessfully() throws Exception {
        // Arrange
        for (int i = 0; i < 5; i++) {
            Transaction transaction = new Transaction();
            transaction.setAccountId("BULK" + i);
            transaction.setAmount(new BigDecimal("100"));
            transaction.setTimestamp(new Date());
            transaction.setLocation("TestLocation");
            transaction.setCurrency("USD");
            
            // Act
            sqsTemplate.send(QUEUE_NAME, transaction);
        Thread.sleep(1000); // Wait for message to be processed
        }

        // Assert - verify all 5 transactions were processed
        verify(transactionRepository, timeout(10000).times(5)).save(any(Transaction.class));
    }
}