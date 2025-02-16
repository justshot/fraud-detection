# Real-Time Fraud Detection Service

## Overview
This service provides real-time fraud detection for financial transactions using a rule-based detection mechanism. It is designed to be highly available and scalable, deployed on Kubernetes, and integrated with AWS services.

## Architecture

### System Components
- **Core Service**: Spring Boot application implementing fraud detection logic
- **Message Queue**: AWS SQS for transaction ingestion
- **Database**: H2 Database (in-memory for development)
- **Monitoring**: AWS CloudWatch for logging and metrics
- **Container Orchestration**: Kubernetes (AWS EKS)

### Design Choices
1. **Rule-Based Detection**:
   - High-value transaction monitoring
   - Location-based fraud detection
   - Rapid successive transaction detection
   - Unusual currency pattern detection

2. **Caching Strategy**:
   - In-memory caching for recent transactions
   - Cache expiration after 1 hour
   - Maximum cache size of 10,000 entries

3. **High Availability**:
   - Minimum 3 pod replicas
   - Pod anti-affinity rules
   - Horizontal Pod Autoscaling
   - Load balancer integration

## Prerequisites
- Java 21
- Maven
- Docker
- kubectl
- AWS CLI configured with appropriate credentials

## Local Development Setup

1. Clone the repository:
```bash
git clone [repository-url]
cd fraud-detection
```

2. Build the application:
```bash
./mvnw clean package
```

3. Run locally:
```bash
./mvnw spring-boot:run
```

## Deployment to AWS EKS

1. Build and push Docker image:
```bash
docker build -t fraud-detection:1.0.0 .
docker tag fraud-detection:1.0.0 [your-registry]/fraud-detection:1.0.0
docker push [your-registry]/fraud-detection:1.0.0
```

2. Update image in k8s/deployment.yaml if needed.

3. Deploy to Kubernetes:
```bash
kubectl apply -f k8s/deployment.yaml
```

4. Verify deployment:
```bash
kubectl get pods -l app=fraud-detection
kubectl get svc fraud-detection-service
```

## Configuration

Key configuration parameters in `application.yml`:

```yaml
fraud:
  detection:
    amount:
      threshold: 1000    # High-value transaction threshold
    suspicious:
      transactions:
        threshold: 3     # Suspicious transaction count threshold

aws:
  sqs:
    queue:
      transactions: fraud-detection-queue
```

## Testing

### Running Tests

1. Unit Tests:
```bash
./mvnw test
```

2. Integration Tests:
```bash
./mvnw verify -P integration-test
```

### Test Coverage
Test coverage report is generated in `jacoco/index.html`

### Resilience Testing

1. Pod Failure Recovery:
```bash
# Delete a pod and verify automatic recovery
kubectl delete pod -l app=fraud-detection
kubectl get pods -w
```

2. Load Testing:
```bash
# Use tools like Apache JMeter or k6 for load testing
# Example k6 test configuration provided in /tests/load/
```

## Monitoring

1. Access Metrics:
```bash
curl http://[service-url]:8080/actuator/metrics
```

2. View Logs:
```bash
# Access CloudWatch logs
aws logs get-log-events --log-group-name /aws/fraud-detection
```