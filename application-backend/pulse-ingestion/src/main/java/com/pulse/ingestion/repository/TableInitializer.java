package com.pulse.ingestion.repository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
public class TableInitializer {

    private static final Logger log = LoggerFactory.getLogger(TableInitializer.class);
    private final DynamoDbClient dynamoDbClient;
    public static final String TABLE_NAME = "pulse_core";

    public TableInitializer(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @PostConstruct
    public void init() {
        try {
            if (tableExists()) {
                log.info("Table '{}' already exists. Skipping creation.", TABLE_NAME);
                return;
            }
            createTable();
        } catch (ResourceInUseException e) {
            log.info("Table '{}' already exists. Skipping creation.", TABLE_NAME);
        } catch (Exception e) {
            log.warn("Could not initialize DynamoDB table: {}. Make sure DynamoDB Local is running (docker-compose up -d)", e.getMessage());
        }
    }

    private boolean tableExists() {
        try {
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .build());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void createTable() {
        log.info("Creating DynamoDB Table '{}'...", TABLE_NAME);
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(TABLE_NAME)

                // define attributes
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build()
                )

                // define Keys (Partition Key & Sort Key)
                .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build()
                )

                // define Throughput
                .provisionedThroughput(
                        ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()
                )
                .build();

        dynamoDbClient.createTable(request);
        log.info("Table '{}' created successfully!", TABLE_NAME);
    }
}