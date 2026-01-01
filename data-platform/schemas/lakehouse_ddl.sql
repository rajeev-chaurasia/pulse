-- Pulse Data Lakehouse DDL
-- Demonstates "Efficient Data Lakehouse schema with time-based partitioning and Z-order clustering"

-- 1. Create the Raw Swipes Table in Iceberg
CREATE TABLE pulse_lake.raw.swipes_cdc (
    user_id STRING,
    target_id STRING,
    is_like BOOLEAN,
    event_timestamp LONG,
    ingest_time TIMESTAMP,
    subscription_tier STRING,
    region STRING
)
USING iceberg
PARTITIONED BY (days(ingest_time), region); 
-- ^ PARTITIONING: Optimizes queries filtering by date or user region

-- 2. Validate/Maintenance Job (Run Daily)
-- Resume Point: "Z-order clustering... reducing analytical query latency by 40%"
CALL pulse_lake.system.rewrite_data_files(
    table => 'pulse_lake.raw.swipes_cdc',
    strategy => 'sort',
    sort_order => 'zorder(user_id, target_id)' 
);
-- ^ Z-ORDER: Co-locates related user interactions in file layout, 
-- speeding up "who swiped whom" queries significantly.

-- 3. Historical Trend Analysis View
CREATE OR REPLACE VIEW pulse_lake.analytics.daily_swipe_trends AS
SELECT 
    date_trunc('day', ingest_time) as swipe_date,
    region,
    subscription_tier,
    COUNT(*) as total_swipes,
    SUM(CASE WHEN is_like THEN 1 ELSE 0 END) as total_likes
FROM pulse_lake.raw.swipes_cdc
GROUP BY 1, 2, 3;
