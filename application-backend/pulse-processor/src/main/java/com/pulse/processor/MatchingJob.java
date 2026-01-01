package com.pulse.processor;

import com.pulse.processor.model.MatchEvent;
import com.pulse.processor.model.SwipeEvent;
import com.pulse.processor.utils.MatchSerializer;
import com.pulse.processor.utils.SwipeDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulse Matching Job
 * <p>
 * Entry point for the Flink streaming data pipeline.
 * Consumes 'SwipeEvent' objects from the 'swipes' Kafka topic, identifies
 * mutual matches,
 * and produces 'MatchEvent' objects to the 'matches' topic.
 */
public class MatchingJob {

        private static final Logger LOG = LoggerFactory.getLogger(MatchingJob.class);
        private static final String JOB_NAME = "Pulse Matching Engine";

        // Topics
        private static final String TOPIC_SWIPES = "swipes";
        private static final String TOPIC_MATCHES = "matches";
        private static final String CONSUMER_GROUP_ID = "pulse-processor-group";

        public static void main(String[] args) throws Exception {

                // Configuration
                final String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS",
                                "127.0.0.1:9094");
                final int parallelism = Integer.parseInt(System.getenv().getOrDefault("FLINK_PARALLELISM", "1"));

                // Initialize execution environment
                final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.setParallelism(parallelism);

                env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                                3, // Max restart attempts
                                Time.seconds(10) // Delay between restarts
                ));

                LOG.info("Initializing {} with Parallelism: {}", JOB_NAME, parallelism);

                // Kafka Source Configuration
                KafkaSource<SwipeEvent> source = KafkaSource.<SwipeEvent>builder()
                                .setBootstrapServers(kafkaBootstrapServers)
                                .setTopics(TOPIC_SWIPES)
                                .setGroupId(CONSUMER_GROUP_ID)
                                .setStartingOffsets(OffsetsInitializer.latest())
                                .setValueOnlyDeserializer(new SwipeDeserializer())
                                .build();

                // Build Data Stream
                DataStream<SwipeEvent> inputStream = env.fromSource(
                                source,
                                WatermarkStrategy.noWatermarks(),
                                "Kafka Source: " + TOPIC_SWIPES);

                // Processing Logic: Key by 'PairKey' to route mutual swipes to the same task
                // slot
                DataStream<MatchEvent> matchStream = inputStream
                                .keyBy(SwipeEvent::getPairKey)
                                .process(new MatchFunction())
                                .name("Match Processor");

                // Kafka Sink Configuration
                KafkaSink<MatchEvent> sink = KafkaSink.<MatchEvent>builder()
                                .setBootstrapServers(kafkaBootstrapServers)
                                .setRecordSerializer(new MatchSerializer(TOPIC_MATCHES))
                                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                                .build();

                // Output
                matchStream.sinkTo(sink).name("Kafka Sink: " + TOPIC_MATCHES);

                // Execute
                env.execute(JOB_NAME);
        }
}