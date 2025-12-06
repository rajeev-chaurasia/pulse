package com.pulse.processor.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.processor.model.MatchEvent;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

/**
 * Serializes MatchEvent POJO to Kafka ProducerRecord.
 * Uses JSON serialization for compatibility with downstream consumers.
 */
public class MatchSerializer implements KafkaRecordSerializationSchema<MatchEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(MatchSerializer.class);
    private final String topic;
    private transient ObjectMapper objectMapper;

    public MatchSerializer(String topic) {
        this.topic = topic;
    }

    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper;
    }

    @Nullable
    @Override
    public ProducerRecord<byte[], byte[]> serialize(MatchEvent match, KafkaRecordSerializationSchema.KafkaSinkContext context, Long timestamp) {
        try {
            // Use matchId as the Kafka key for partitioning
            byte[] key = match.getMatchId().getBytes(StandardCharsets.UTF_8);

            // Serialize the MatchEvent to JSON bytes
            byte[] value = getObjectMapper().writeValueAsBytes(match);

            LOG.debug("Serializing match: {}", match.getMatchId());
            return new ProducerRecord<>(topic, null, timestamp, key, value);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize MatchEvent: {}", match, e);
            throw new RuntimeException("Failed to serialize MatchEvent", e);
        }
    }
}
