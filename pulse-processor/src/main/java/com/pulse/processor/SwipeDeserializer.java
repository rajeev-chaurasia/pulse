package com.pulse.processor;

import com.pulse.grpc.SwipeRequest;
import com.pulse.processor.model.SwipeEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

/**
 * Deserializes Kafka bytes (Protobuf) into SwipeEvent POJO.
 */
public class SwipeDeserializer implements DeserializationSchema<SwipeEvent> {

    @Override
    public SwipeEvent deserialize(byte[] message) throws IOException {
        // Parse Protobuf and convert to POJO
        SwipeRequest proto = SwipeRequest.parseFrom(message);
        return new SwipeEvent(
                proto.getUserId(),
                proto.getTargetId(),
                proto.getIsLike(),
                proto.getTimestamp()
        );
    }

    @Override
    public boolean isEndOfStream(SwipeEvent swipeEvent) {
        // For Kafka (infinite stream), this is always false.
        return false;
    }

    @Override
    public TypeInformation<SwipeEvent> getProducedType() {
        // Use TypeInformation for POJO - Flink will use efficient PojoSerializer
        return TypeInformation.of(SwipeEvent.class);
    }
}

