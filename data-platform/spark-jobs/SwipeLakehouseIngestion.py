from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, col, to_timestamp, broadcast
from pyspark.sql.types import StructType, StructField, StringType, BooleanType, LongType

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

def ingest_stream(spark):
    """
    Reads from Kafka 'swipes' topic, enriches data, and writes to Iceberg.
    """
    # 1. Read Stream from Kafka
    # Using 'readStream' for continuous ingestion
    raw_stream = spark.readStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", "localhost:9092") \
        .option("subscribe", "swipes") \
        .option("startingOffsets", "earliest") \
        .option("failOnDataLoss", "false") \
        .load()

    # 2. Transform & Normalize
    # Parse JSON value and cast timestamp for time-partitioning
    parsed_stream = raw_stream.select(
        from_json(col("value").cast("string"), swipe_schema).alias("data"),
        col("timestamp").alias("kafka_ingest_time")
    ).select(
        col("data.user_id"),
        col("data.target_id"),
        col("data.is_like"),
        col("data.timestamp").alias("event_timestamp"),
        to_timestamp(col("kafka_ingest_time") / 1000).alias("ingest_time")
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
    # Resume Point: "Ensuring exactly-once delivery semantics" via checkpointing
    query = enriched_stream.writeStream \
        .format("iceberg") \
        .outputMode("append") \
        .trigger(processingTime="30 seconds") \
        .option("path", "pulse_lake.raw.swipes_cdc") \
        .option("fanout-enabled", "true") \
        .option("checkpointLocation", CHECKPOINT_PATH) \
        .start()

    print(f"🌊 Started Pulse Lakehouse Ingestion to pulse_lake.raw.swipes_cdc...")
    query.awaitTermination()

if __name__ == "__main__":
    spark = create_spark_session()
    ingest_stream(spark)
