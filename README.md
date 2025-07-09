# Login Management Application

A comprehensive login management system built with Spring Boot, featuring distributed task processing, Hazelcast clustering, and resilient caching.

## Features

- **Distributed Task Processing**: Hazelcast-based distributed queue for login record processing
- **Network Fault Tolerance**: Automatic reconnection and health monitoring for Hazelcast cluster
- **Resilient Caching**: Hazelcast distributed cache with Caffeine fallback for high availability
- **Service Discovery**: Consul integration for dynamic service registration and discovery
- **Monitoring**: Comprehensive metrics and health checks
- **Security Analysis**: User behavior analysis and security risk assessment

## Architecture

### Core Components

1. **Distributed Queue System**
   - Hazelcast-based distributed task queue
   - Deduplication and priority handling
   - Automatic failover and recovery

2. **Network Fault Tolerance**
   - Automatic cluster health monitoring
   - Smart reconnection with exponential backoff
   - Integration with Consul service discovery

3. **Resilient Caching System**
   - Hazelcast distributed cache as primary
   - Caffeine local cache as fallback
   - Automatic failover when Hazelcast is unavailable
   - Cache monitoring and management APIs

4. **Service Layer**
   - Login record management
   - User security analysis
   - Distributed task processing

## Technology Stack

- **Framework**: Spring Boot 3 with Java 17
- **Database**: PostgreSQL with JPA/Hibernate
- **Distributed Cache**: Hazelcast with Caffeine fallback
- **Service Discovery**: Consul
- **Monitoring**: Micrometer with Prometheus/Grafana
- **Build Tool**: Maven

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- Consul (optional, for service discovery)

### Configuration

1. **Database Configuration**
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/login_management
       username: your_username
       password: your_password
   ```

2. **Hazelcast Configuration**
   ```yaml
   app:
     hazelcast:
       network:
         port: 5701
         fault-tolerance:
           enabled: true
           heartbeat-check-interval-seconds: 30
   ```

3. **Cache Configuration**
   ```yaml
   app:
     cache:
       hazelcast:
         enabled: true
       fallback:
         enabled: true
         max-size: 1000
         expire-after-write-seconds: 300
   ```

### Running the Application

```bash
# Clone the repository
git clone <repository-url>
cd login-management-app

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

## API Endpoints

### Login Records

- `POST /api/login-records` - Create login record
- `GET /api/login-records/{id}` - Get login record by ID
- `GET /api/login-records/user/{email}` - Get records by email
- `GET /api/login-records/ip/{ipAddress}` - Get records by IP
- `GET /api/login-records/recent` - Get recent records
- `PUT /api/login-records/{id}` - Update login record
- `DELETE /api/login-records/{id}` - Delete login record

### Cache Management

- `GET /api/cache/status` - Get cache status
- `POST /api/cache/reset-to-primary` - Reset to Hazelcast cache
- `POST /api/cache/force-fallback` - Force Caffeine fallback
- `DELETE /api/cache/{cacheName}` - Clear specific cache
- `DELETE /api/cache/clear-all` - Clear all caches

### Network Monitoring

- `GET /api/network/health` - Get network health status
- `POST /api/network/reconnect` - Trigger manual reconnection
- `POST /api/network/reset` - Reset health status
- `GET /api/network/config` - Get network configuration

### Queue Monitoring

- `GET /api/queue/status` - Get queue status
- `GET /api/queue/metrics` - Get queue metrics
- `POST /api/queue/clear` - Clear queue
- `GET /api/queue/tasks` - Get pending tasks

## Caching Strategy

### Cache Types

1. **Hazelcast Distributed Cache** (Primary)
   - Shared across cluster nodes
   - High availability and scalability
   - Automatic data replication

2. **Caffeine Local Cache** (Fallback)
   - Fast local access
   - Automatic fallback when Hazelcast fails
   - Configurable TTL and size limits

### Cache Usage

```java
@Service
public class LoginRecordService {
    
    @Cacheable(value = "login-records", key = "#id", unless = "#result == null")
    public LoginRecordResponse getLoginRecordById(Long id) {
        // Automatically cached with fallback support
        return repository.findById(id).map(this::mapToResponse).orElse(null);
    }
    
    @CachePut(value = "login-records", key = "#result.id")
    @CacheEvict(value = {"user-login-records", "ip-login-records"}, allEntries = true)
    public LoginRecordResponse createLoginRecord(LoginRecordRequest request) {
        // Update cache and evict related caches
        return repository.save(mapToEntity(request));
    }
}
```

## Monitoring and Health Checks

### Health Endpoints

- `GET /actuator/health` - Application health
- `GET /actuator/metrics` - Application metrics
- `GET /api/cache/status` - Cache health
- `GET /api/network/health` - Network health

### Key Metrics

- Cache hit/miss rates
- Queue processing rates
- Network connectivity status
- Hazelcast cluster health

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/wilsonkeh/loginmanagement/
│   │       ├── config/           # Configuration classes
│   │       ├── controller/       # REST controllers
│   │       ├── dto/             # Data transfer objects
│   │       ├── entity/          # JPA entities
│   │       ├── queue/           # Distributed queue components
│   │       ├── repository/      # Data access layer
│   │       └── service/         # Business logic
│   └── resources/
│       ├── application.yml      # Application configuration
│       └── schema.sql          # Database schema
└── test/
    └── java/
        └── com/wilsonkeh/loginmanagement/
            ├── queue/           # Queue tests
            └── service/         # Service tests
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CacheIntegrationTest

# Run with coverage
mvn test jacoco:report
```

## Deployment

### Docker

```dockerfile
FROM openjdk:17-jre-slim
COPY target/login-management-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: login-management-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: login-management-app
  template:
    metadata:
      labels:
        app: login-management-app
    spec:
      containers:
      - name: login-management-app
        image: login-management-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
```

## Documentation

- [Cache Usage Guide](CACHE_USAGE_GUIDE.md) - Detailed caching documentation
- [Network Fault Tolerance](HAZELCAST_ADVANCED_CONFIG.md) - Network resilience guide
- [Queue Optimization](QUEUE_OPTIMIZATION.md) - Distributed queue optimization
- [Monitoring Guide](MONITORING.md) - Monitoring and metrics guide

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 