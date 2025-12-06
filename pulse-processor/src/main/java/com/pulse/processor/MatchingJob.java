package com.pulse.processor;

import com.pulse.processor.model.MatchEvent;
import com.pulse.processor.model.SwipeEvent;
import com.pulse.processor.utils.SwipeDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingJob {

    private static final Logger LOG = LoggerFactory.getLogger(MatchingJob.class);
    private static final String MATCHING_JOB_NAME = "Pulse Matching Job";
    private static final String SWIPES_TOPIC = "swipes";
    private static final String PULSE_GROUP_ID = "pulse-group";

    public static void main(String[] args) throws Exception {

        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

        // execution environment : context where stream runs
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        int parallelism = Integer.parseInt(System.getenv().getOrDefault("FLINK_PARALLELISM", "1"));
        env.setParallelism(parallelism);
        LOG.info("Flink parallelism set to: {}", parallelism);

        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                3, // max retry attempts
                Time.seconds(10) // delay between retries
        ));

        // define Kafka source
        KafkaSource<SwipeEvent> source = KafkaSource.<SwipeEvent>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics(SWIPES_TOPIC)
                .setGroupId(PULSE_GROUP_ID)
                .setStartingOffsets(OffsetsInitializer.latest()) // Only read new data
                .setValueOnlyDeserializer(new SwipeDeserializer()) // Use our translator
                .build();

        // Create the Data Stream
        // WatermarkStrategy.noWatermarks() means "Ignore time for now, just process fast"
        DataStream<SwipeEvent> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        DataStream<MatchEvent> matches = stream
                .keyBy(SwipeEvent::getPairKey)  // Groups A->B and B->A together
                .process(new MatchFunction());

        matches.print();

        // Flink is "Lazy". Nothing happens until you call execute().
        // It builds a "Plan" (DAG) and then sends it to the cluster.
        LOG.info("Pulse Engine is starting...");
        env.execute(MATCHING_JOB_NAME);
    }
}