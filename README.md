# Pulse: High-Frequency Trading & Data Platform

![Architecture Diagram](https://github.com/user-attachments/assets/bd48c61e-3dc8-42cd-943c-351a2e86bf11)

## 🏗 Architecture Overview

Pulse is a distributed, high-performance platform designed to solve the "Double Opt-In" matching problem at massive scale. It unifies a low-latency real-time engine with a horizontally scalable data lakehouse to support both instant user interactions and deep historical analytics.

The system is composed of two core subsystems:

### 1. [Application Backend](/application-backend) (Online System)
**Goal:** Low-latency ingestion and stateful matching.
- **Tech Stack:** Java 17, Spring Boot, Apache Flink, Apache Kafka, DynamoDB.
- **Performance:** Processes 10,000+ swipe events per second with <5ms matching latency.
- **Key Mechanics:**
    - **gRPC Ingestion:** High-throughput entry point for mobile clients.
    - **Flink Stateful Processing:** In-memory matching logic avoiding DB bottlenecks.
    - **DynamoDB Persistence:** Durable storage for successful matches.
    - **Real-Time Dashboard:** WebSocket-based telemetry for system observability.

👉 **[Explore the Application Backend Code](/application-backend)**

---

### 2. [Data Platform](/data-platform) (Offline / Nearline System)
**Goal:** Lambda Architecture for historical analysis, ML training, and exact-once audits.
- **Tech Stack:** Apache Spark 3.5, Apache Iceberg, AWS S3, Python.
- **Pipeline:**
    - **Ingestion:** Spark Structured Streaming reads raw events from Kafka `swipes` topic.
    - **Storage:** Writes to **Apache Iceberg** tables on S3 with Schema Evolution support.
    - **Optimization:** Z-Order clustering and time-based partitioning for fast query performance.
- **Use Cases:**
    - **Data Lakehouse:** Unified storage for "Time Travel" queries.
    - **Machine Learning:** Feature engineering for recommendation algorithms.

👉 **[Explore the Data Platform Code](/data-platform)**

---

## 🚀 Deployment Strategy

### Application Layer
The application layer runs on a containerized infrastructure orchestrated via Docker Compose (local) or Kubernetes (production).

```bash
cd application-backend
docker-compose up -d
./gradlew :pulse-processor:run
```

### Data Layer
The data platform jobs are submitted to a Spark Cluster (EMR / Databricks) or run locally for testing.

```bash
cd data-platform/spark-jobs
spark-submit \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2 \
  SwipeLakehouseIngestion.py
```

---

## 🛠 Project Structure

```
Pulse/
├── application-backend/       # ORIGINAL: Real-time Matching Engine
│   ├── src/                   # Spring Boot & Flink Source Code
│   ├── docker-compose.yml     # Infrastructure (Kafka, Zookeeper)
│   └── flood.py               # Load Generator
│
├── data-platform/             # NEW: Data Lakehouse & Spark Pipelines
│   ├── spark-jobs/            # Spark Structured Streaming Jobs
│   ├── iceberg-configs/       # Catalog & S3 Configurations
│   └── schemas/               # Avro/JSON Schemas
│
└── README.md                  # Implementation Guide
```
