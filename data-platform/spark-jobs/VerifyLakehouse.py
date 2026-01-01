from pyspark.sql import SparkSession
import os

# Define Warehouse Path (Match the Ingestion Job)
# In LOCAL mode, the ingestion job uses: /Users/maverickrajeev/Desktop/pulse/iceberg_warehouse
# We should use the same absolute path or relative if running from the same cwd.
# For safety, let's use the explicit path we saw in logs: /Users/maverickrajeev/Desktop/pulse/iceberg_warehouse
WAREHOUSE_PATH = "/Users/maverickrajeev/Desktop/pulse/iceberg_warehouse"

def verify_lakehouse():
    spark = SparkSession.builder \
        .appName("VerifyPulseLakehouse") \
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions") \
        .config("spark.sql.catalog.pulse_lake", "org.apache.iceberg.spark.SparkCatalog") \
        .config("spark.sql.catalog.pulse_lake.type", "hadoop") \
        .config("spark.sql.catalog.pulse_lake.warehouse", WAREHOUSE_PATH) \
        .getOrCreate()

    print(f"🌊 Connecting to Warehouse at: {WAREHOUSE_PATH}")
    print("🔍 Reading from table: pulse_lake.raw.swipes_cdc")
    
    try:
        df = spark.read.format("iceberg").load("pulse_lake.raw.swipes_cdc")
        count = df.count()
        print(f"✅ Total rows in pulse_lake.raw.swipes_cdc: {count}")
        
        if count > 0:
            print("📊 Sample Data:")
            df.show(5, truncate=False)
        else:
            print("⚠️ Table exists but is empty. Check if the streaming job has processed any batches yet.")
            
    except Exception as e:
        print(f"❌ Error reading table: {e}")

    spark.stop()

if __name__ == "__main__":
    verify_lakehouse()
