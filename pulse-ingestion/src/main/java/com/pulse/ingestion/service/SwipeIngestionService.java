package com.pulse.ingestion.service;

import com.pulse.grpc.SwipeRequest;
import com.pulse.grpc.SwipeResponse;
import com.pulse.grpc.SwipeServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

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
        // TODO: remove log before deployment
        log.info("Received swipe: {} -> {} ({})",
                request.getUserId(), request.getTargetId(), request.getIsLike() ? "LIKE" : "PASS");

        // Key = user_id (ensures all swipes by one user go to the same partition)
        // Value = request.toByteArray() (Raw Protobuf bytes)
        kafkaTemplate.send(TOPIC, request.getUserId(), request.toByteArray());

        SwipeResponse response = SwipeResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Swipe processed successfully")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
