package com.example.frauddetection.service;

import java.util.Arrays;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;
import com.example.frauddetection.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.context.annotation.Import;
import com.example.frauddetection.config.TestConfig;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.CLOUDWATCH;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;

@SpringBootTest
@Testcontainers
@Import(TestConfig.class)
class CloudWatchLoggingIntegrationTest {

    private static final String LOG_GROUP = "/test/fraud-detection";
    private static final String LOG_STREAM = "test-stream-1";
    private static final String QUEUE_NAME = "test-fraud-detection-queue";

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.1"))      
                .withServices(CLOUDWATCH)
                .withServices(SQS);
    static{
        localStack.setPortBindings(List.of("4566:4566"));
    }


    @Autowired
    private FraudDetectionService fraudDetectionService;

    @Autowired
    private AWSLogs cloudWatchLogsClient;

    @Autowired
    private AmazonSQS amazonSQS;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.sqs.endpoint",
            () -> localStack.getEndpointOverride(SQS).toString());
    }

    @BeforeEach
    void setUp() throws Exception {
        // Create log group and stream
        try {
            cloudWatchLogsClient.createLogGroup(new CreateLogGroupRequest(LOG_GROUP));
            cloudWatchLogsClient.createLogStream(new CreateLogStreamRequest(LOG_GROUP, LOG_STREAM));
            // Wait for log group and stream to be available
            Thread.sleep(5000);
            
            // Verify log group and stream creation
            DescribeLogGroupsRequest describeLogGroupsRequest = new DescribeLogGroupsRequest()
                .withLogGroupNamePrefix(LOG_GROUP);
            DescribeLogGroupsResult logGroupsResult = cloudWatchLogsClient.describeLogGroups(describeLogGroupsRequest);
            if (logGroupsResult.getLogGroups().isEmpty()) {
                throw new RuntimeException("Log group was not created successfully");
            }

            DescribeLogStreamsRequest describeLogStreamsRequest = new DescribeLogStreamsRequest()
                .withLogGroupName(LOG_GROUP)
                .withLogStreamNamePrefix(LOG_STREAM);
            DescribeLogStreamsResult logStreamsResult = cloudWatchLogsClient.describeLogStreams(describeLogStreamsRequest);
            if (logStreamsResult.getLogStreams().isEmpty()) {
                throw new RuntimeException("Log stream was not created successfully");
            }

            // Initialize log stream with a test event
            PutLogEventsRequest putLogEventsRequest = new PutLogEventsRequest()
                .withLogGroupName(LOG_GROUP)
                .withLogStreamName(LOG_STREAM)
                .withLogEvents(Arrays.asList(
                    new InputLogEvent()
                        .withTimestamp(System.currentTimeMillis())
                        .withMessage("Test log stream initialization")
                ));
            cloudWatchLogsClient.putLogEvents(putLogEventsRequest);
            Thread.sleep(2000); // Wait for test event to be processed

        } catch (ResourceAlreadyExistsException e) {
            // Ignore if already exists
        }

        // Create test queue using AWS SDK client
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(QUEUE_NAME);
        amazonSQS.createQueue(createQueueRequest);
        
        // Wait for queue to be available
        Thread.sleep(5000);
    }

    private void verifyCloudWatchLogs(String expectedMessage) throws InterruptedException {
        // Wait a bit for logs to be processed
        Thread.sleep(5000);

        GetLogEventsRequest request = new GetLogEventsRequest()
            .withLogGroupName(LOG_GROUP)
            .withLogStreamName(LOG_STREAM)
            .withStartFromHead(true)
            .withLimit(100);

        GetLogEventsResult result = cloudWatchLogsClient.getLogEvents(request);
        List<OutputLogEvent> events = result.getEvents();
        boolean found = false;

        for(OutputLogEvent event: events) {
            if(event.getMessage().contains(expectedMessage)) {
                found = true;
                System.out.println("'" + expectedMessage + "' was found in logs: " + event.getMessage());
                break;
            } else {
                System.out.println("Verifying message:[" + event.getMessage() +"]");
            }
        }

        assertTrue(found, "Expected message '" + expectedMessage + "' was not found in CloudWatch logs");
    }

    @Test
    void whenAnalyzingSuspiciousTransaction_thenLogWarning() throws InterruptedException {
        // Arrange
        Transaction transaction = new Transaction();
        transaction.setAccountId("TEST123");
        transaction.setAmount(new BigDecimal("2000")); // Above threshold
        transaction.setTimestamp(new Date());
        transaction.setLocation("TestLocation");
        transaction.setCurrency("USD");
        transaction.setStatus(Transaction.TransactionStatus.PENDING);

        // Act
        fraudDetectionService.analyzeTransaction(transaction);

        // Assert
        verifyCloudWatchLogs("High-value transaction detected");
    }
}