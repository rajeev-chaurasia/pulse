# Pulse Engine

![Status](https://img.shields.io/badge/Status-Active-success)
![Java](https://img.shields.io/badge/Java-17-orange)
![Flink](https://img.shields.io/badge/Apache_Flink-1.18-d71e00)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.6-black)
![React](https://img.shields.io/badge/React-18-blue)

**Pulse** is a high-frequency, real-time matching engine designed to process high-throughput user interactions with low latency. It leverages event-driven architecture to ingest millions of events, process them statefully, and deliver instant feedback to clients.

## 🏗 Architecture

Pulse adopts a modern, decoupled microservices architecture:

```mermaid
graph LR
    Client(Web/Mobile Client) -->|gRPC/Proto| Ingestion[Ingestion Service]
    Ingestion -->|Writes| KafkaSwipes[(Kafka: swipes)]
    KafkaSwipes -->|Consumes| Flink[Flink Processor]
    Flink -->|Stateful Match Logic| Flink
    Flink -->|Writes| KafkaMatches[(Kafka: matches)]
    KafkaMatches -->|Consumes| Ingestion
    Ingestion -->|WebSocket| Client
    Ingestion -->|Persist| DynamoDB[(DynamoDB)]
```

## 🚀 Tech Stack

- **Core Backend**: Java 17, Spring Boot 3
- **Stream Processing**: Apache Flink 1.18
- **Messaging**: Apache Kafka (Confluent Platform)
- **Protocol**: gRPC (Ingestion), WebSockets (Real-time updates)
- **Frontend**: React, Vite (Dashboard)
- **Infrastructure**: Docker, Docker Compose

## ⚡️ Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)
- Python 3.9+ (for load testing)

### Run the Engine
We provide a unified startup script that handles build, infrastructure initialization, and service orchestration.

```bash
./scripts/start_dev.sh
```

This will:
1. Clean up existing containers and artifacts.
2. Build the backend services and Flink jobs.
3. Start Kafka, Zookeeper, Flink Cluster, and DynamoDB.
4. Launch the Ingestion Service and Dashboard.

**Access Points:**
- **Dashboard**: [http://localhost:3000](http://localhost:3000)
- **Flink Dashboard**: [http://localhost:8081](http://localhost:8081)
- **API (gRPC)**: `localhost:50051`

### Load Testing
To simulate high-concurrency traffic:

```bash
python3 scripts/load_test.py
```

## 🛠 Development

### Project Structure
```
pulse/
├── application-backend/      # Core services
│   ├── pulse-ingestion/      # Spring Boot gRPC + WebSocket Service
│   ├── pulse-processor/      # Flink Job
│   └── pulse-dashboard/      # React Frontend
├── scripts/                  # Operational scripts
└── docker-compose.yml        # Infrastructure definition
```

### Configuration
Environment variables can be tuned in `docker-compose.yml`:
- `FLINK_PARALLELISM`: Controls Flink job scaling.
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka connectivity.

## 📚 API Reference

### gRPC Service
Send swipes via gRPC to `localhost:50051`.

```bash
grpcurl -plaintext -d '{
  "user_id": "alice",
  "target_id": "bob",
  "is_like": true,
  "timestamp": 1733600000
}' localhost:50051 com.pulse.grpc.SwipeService/Swipe
```

## 💾 Database Schema

**DynamoDB Table:** `pulse_core`

| Key | Type | Example | Description |
|-----|------|---------|-------------|
| PK | String | `USER#alice` | Partition key (user ID) |
| SK | String | `MATCH#1733600000` | Sort key (timestamp) |
| partner_id | String | `bob` | The other user in match |
| match_id | String | `uuid-xxx` | Unique match identifier |
