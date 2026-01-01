from pyspark.sql import SparkSession
from pyspark.sql.functions import udf, col, to_timestamp, broadcast, to_date
from pyspark.sql.types import StructType, StructField, StringType, BooleanType, LongType, BinaryType
import swipe_pb2

# -------------------------------------------------------------------------
# Schema Definition
# Matches the Protobuf 'SwipeRequest' event payload
# -------------------------------------------------------------------------
swipe_schema = StructType([
    StructField("user_id", StringType(), True),
    StructField("target_id", StringType(), True),
    StructField("is_like", BooleanType(), True),
    StructField("timestamp", LongType(), True)
])

import os

# Configuration: Support Local Testing vs Production S3
ENV = os.getenv("ENV", "PROD")
if ENV == "LOCAL":
    WAREHOUSE_PATH = os.path.abspath("iceberg_warehouse")
    CHECKPOINT_PATH = os.path.abspath("checkpoints")
    print(f"🔧 Running in LOCAL mode. Warehouse: {WAREHOUSE_PATH}")
else:
    WAREHOUSE_PATH = "s3a://pulse-lakehouse-data/warehouse"
    CHECKPOINT_PATH = "s3a://pulse-lakehouse-data/checkpoints/swipes_ingest"

def create_spark_session():
    """
    Initializes SparkSession with Iceberg catalog configurations.
    """
    return SparkSession.builder \
        .appName("PulseLakehouseIngestion") \
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions") \
        .config("spark.sql.catalog.pulse_lake", "org.apache.iceberg.spark.SparkCatalog") \
        .config("spark.sql.catalog.pulse_lake.type", "hadoop") \
        .config("spark.sql.catalog.pulse_lake.warehouse", WAREHOUSE_PATH) \
        .getOrCreate()

def get_user_metadata(spark):
    """
    Simulates loading a User Dimension table for enrichment.
    In production, this would read from pulse_lake.dims.users
    """
    return spark.createDataFrame([
        ("user_123", "premium", "US_CA"),
        ("user_456", "free", "US_NY"),
    ], ["user_id", "subscription_tier", "region"])

def deserialize_swipe_bytes(payload):
    """
    Deserializes raw Protobuf bytes into a tuple matching swipe_schema.
    Returns None if parsing fails.
    """
    if not payload:
        return None
    try:
        req = swipe_pb2.SwipeRequest()
        req.ParseFromString(payload)
        return (req.user_id, req.target_id, req.is_like, req.timestamp)
    except Exception as e:
        # In a real pipe, might want to output to a DLQ
        return None

def ingest_stream(spark):
    """
    Reads from Kafka 'swipes' topic, enriches data, and writes to Iceberg.
    """
    # Determine Kafka bootstrap servers based on environment
    if ENV == "LOCAL":
        kafka_bootstrap_servers = "127.0.0.1:9094"
    else:
        kafka_bootstrap_servers = "localhost:9092"

    # Register UDF
    deserialize_udf = udf(deserialize_swipe_bytes, swipe_schema)

    # 1. Read Stream from Kafka
    # Using 'readStream' for continuous ingestion
    raw_stream = spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", kafka_bootstrap_servers) \
        .option("subscribe", "swipes") \
        .option("startingOffsets", "earliest") \
        .option("failOnDataLoss", "false") \
        .load()

    # 2. Transform & Normalize
    # Parse Protobuf bytes and cast timestamp for time-partitioning
    parsed_stream = raw_stream.select(
        deserialize_udf(col("value")).alias("data"),
        col("timestamp").alias("kafka_ingest_time")
    ).select(
        col("data.user_id"),
        col("data.target_id"),
        col("data.is_like"),
        col("data.timestamp").alias("event_timestamp"),
        col("kafka_ingest_time").alias("ingest_time"),
        to_date(col("kafka_ingest_time")).alias("ingest_date")
    )

    # 3. Enrichment (Resume Point: "Enrich raw event streams with user metadata")
    # Join with User Dimension to add subscription tier and region
    # Using broadcast join for efficiency
    user_dim = get_user_metadata(spark)
    enriched_stream = parsed_stream.join(
        broadcast(user_dim), 
        on="user_id", 
        how="left_outer"
    )

    # 4. Write Stream to Apache Iceberg
    # Ensure Table Exists
    spark.sql("CREATE DATABASE IF NOT EXISTS pulse_lake.raw")
    
    # In LOCAL mode, drop the table to ensure fresh schema (resolves "Field not found" errors)
    if ENV == "LOCAL":
        print("🔧 LOCAL mode: Dropping existing table to enforce new schema...")
        spark.sql("DROP TABLE IF EXISTS pulse_lake.raw.swipes_cdc")
        
        # Clean up checkpoints to ensure fresh start in LOCAL mode
        if os.path.exists(CHECKPOINT_PATH):
            import shutil
            shutil.rmtree(CHECKPOINT_PATH)
            print(f"🧹 Removed checkpoint directory: {CHECKPOINT_PATH}")

    spark.sql("""
        CREATE TABLE IF NOT EXISTS pulse_lake.raw.swipes_cdc (
            user_id STRING,
            target_id STRING,
            is_like BOOLEAN,
            event_timestamp LONG,
            ingest_time TIMESTAMP,
            ingest_date DATE,
            subscription_tier STRING,
            region STRING
        )
        USING iceberg
        PARTITIONED BY (ingest_date, region)
    """)

    # Resume Point: "Ensuring exactly-once delivery semantics" via checkpointing
    query = enriched_stream.writeStream \
        .format("iceberg") \
        .outputMode("append") \
        .trigger(processingTime="5 seconds") \
        .option("path", "pulse_lake.raw.swipes_cdc") \
        .option("fanout-enabled", "true") \
        .option("checkpointLocation", CHECKPOINT_PATH) \
        .start()

    print(f"🌊 Started Pulse Lakehouse Ingestion to pulse_lake.raw.swipes_cdc...")
    query.awaitTermination()

if __name__ == "__main__":
    spark = create_spark_session()
    ingest_stream(spark)
