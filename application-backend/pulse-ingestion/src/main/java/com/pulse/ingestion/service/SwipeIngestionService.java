package com.pulse.ingestion.service;

import com.pulse.grpc.SwipeRequest;
import com.pulse.grpc.SwipeResponse;
import com.pulse.grpc.SwipeServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * gRPC Service for Swipe Ingestion.
 * Receives swipes from clients and publishes them to the Kafka 'swipes' topic.
 */
@GrpcService
public class SwipeIngestionService extends SwipeServiceGrpc.SwipeServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SwipeIngestionService.class);
    private static final String TOPIC = "swipes";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public SwipeIngestionService(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void swipe(SwipeRequest request, StreamObserver<SwipeResponse> responseObserver) {

        // In production, debug logging should be conditional or removed for high
        // throughput
        if (log.isDebugEnabled()) {
            log.debug("Processing swipe: {} -> {}", request.getUserId(), request.getTargetId());
        }

        // Publish to Kafka
        // Key: user_id (ensures ordering per user)
        // Value: Protobuf byte array
        kafkaTemplate.send(TOPIC, request.getUserId(), request.toByteArray());

        SwipeResponse response = SwipeResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Swipe processed successfully")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
