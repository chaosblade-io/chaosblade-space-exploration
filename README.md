# ChaosBlade Space Exploration

A Spring Boot microservices project for chaos engineering space exploration.

## Overview

This project consists of multiple microservices designed to demonstrate chaos engineering principles and practices. Each service is independently deployable and provides specific functionality within the overall system architecture.

## Architecture

### Services

| Service | Port | Description | Framework |
|---------|------|-------------|-----------|
| svc-task-resource | 8101 | Task resource management service | Spring MVC |
| svc-task-executor | 8102 | Task execution service | Spring MVC |
| svc-fault-scheduler | 8103 | Fault injection scheduler | Spring MVC |
| svc-result-processor | 8104 | Result processing service | Spring MVC |
| svc-reqrsp-proxy | 8105 | Request/Response proxy service | Spring WebFlux |
| svc-topo | 8106 | Topology awareness service | Spring MVC |

### Common Modules

- **common-core**: Shared utilities and common response formats
- **common-test**: Test dependencies and utilities

## Quick Start

### Prerequisites

- Java 21
- Maven 3.6+
- Docker (optional, for infrastructure)

### Building the Project

```bash
# Build all modules
mvn clean compile

# Build and package all services
mvn clean package

# Skip tests during build
mvn clean package -DskipTests
```

### Running Services

Each service can be run independently:

```bash
# Run a specific service
cd svc-task-resource
mvn spring-boot:run

# Or run the packaged JAR
java -jar target/svc-task-resource-1.0.0-SNAPSHOT.jar
```

### Health Check

Each service provides a simple health check endpoint:

```bash
# Check service health
curl http://localhost:8101/hello  # svc-task-resource
curl http://localhost:8102/hello  # svc-task-executor
curl http://localhost:8103/hello  # svc-fault-scheduler
curl http://localhost:8104/hello  # svc-result-processor
curl http://localhost:8105/hello  # svc-reqrsp-proxy
curl http://localhost:8106/hello  # svc-topo
```

Expected response format:
```json
{
  "code": 0,
  "message": "ok",
  "data": "<service-name>: hello world"
}
```

## Development

### Project Structure

```
chaosblade-space-exploration/
├─ pom.xml                          # Parent aggregator POM
├─ common/
│  ├─ common-core/                  # Shared utilities
│  └─ common-test/                  # Test dependencies
├─ svc-task-resource/               # Task resource service
├─ svc-task-executor/               # Task executor service
├─ svc-fault-scheduler/             # Fault scheduler service
├─ svc-result-processor/            # Result processor service
├─ svc-reqrsp-proxy/                # Proxy service (WebFlux)
├─ svc-topo/                        # Topology service
└─ infra/
   ├─ docker-compose.yml            # Infrastructure setup
   └─ local/mysql-init/             # Database initialization
```

### Technology Stack

- **Java**: 21
- **Spring Boot**: 3.5.5
- **Spring Cloud**: 2024.0.0
- **Build Tool**: Maven
- **Reactive Framework**: Spring WebFlux (proxy service only)

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## Security

Please read [SECURITY.md](SECURITY.md) for information about reporting security vulnerabilities.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.