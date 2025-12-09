package com.pulse.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.ingestion.repository.TableInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Service
public class MatchConsumerService {

    private static final Logger log = LoggerFactory.getLogger(MatchConsumerService.class);
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;

    public MatchConsumerService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = new ObjectMapper();
    }

    // Listens to the 'matches' topic
    @KafkaListener(topics = "matches", groupId = "pulse-persistence-group")
    public void consumeMatch(byte[] messageBytes) {
        try {
            // Parse JSON message
            JsonNode match = objectMapper.readTree(messageBytes);

            String matchId = match.get("matchId").asText();
            String userAId = match.get("userAId").asText();
            String userBId = match.get("userBId").asText();
            long timestamp = match.get("timestamp").asLong();

            log.info("Processing Match: {} <--> {}", userAId, userBId);

            // Save for User A (so A can see B)
            saveToDynamo(userAId, userBId, matchId, timestamp);

            // Save for User B (so B can see A)
            saveToDynamo(userBId, userAId, matchId, timestamp);

            log.info("Match {} persisted to DynamoDB", matchId);

        } catch (Exception e) {
            log.error("Failed to process match event: {}", new String(messageBytes), e);
        }
    }

    private void saveToDynamo(String ownerId, String partnerId, String matchId, long timestamp) {
        Map<String, AttributeValue> item = new HashMap<>();

        // PK: USER#alice
        item.put("PK", AttributeValue.builder().s("USER#" + ownerId).build());
        // SK: MATCH#1234567890 (Sorted by time)
        item.put("SK", AttributeValue.builder().s("MATCH#" + timestamp).build());

        // Data Attributes
        item.put("partner_id", AttributeValue.builder().s(partnerId).build());
        item.put("match_id", AttributeValue.builder().s(matchId).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TableInitializer.TABLE_NAME)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

}
