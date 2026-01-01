package com.pulse.processor;

import com.pulse.processor.model.MatchEvent;
import com.pulse.processor.model.SwipeEvent;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * KeyedProcessFunction that detects matches when two users mutually like each other.
 * Key: Canonical pair key (e.g., "alice:bob" - alphabetically sorted)
 * Input: SwipeEvent
 * Output: MatchEvent
 */
public class MatchFunction extends KeyedProcessFunction<String, SwipeEvent, MatchEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(MatchFunction.class);

    // State to store the ID of the user who swiped first
    private ValueState<String> pendingSwipeState;

    @Override
    public void open(Configuration parameters) {
        // Configure TTL - cleanup abandoned likes after 15 days
        StateTtlConfig ttlConfig = StateTtlConfig
                .newBuilder(Time.days(15))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();

        // Initialize the State descriptor
        ValueStateDescriptor<String> descriptor = new ValueStateDescriptor<>(
                "pending-swipe",
                String.class
        );

        // Enable TTL on state
        descriptor.enableTimeToLive(ttlConfig);

        pendingSwipeState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void processElement(SwipeEvent swipe, Context ctx, Collector<MatchEvent> out) throws Exception {
        // Ignore PASS events - only LIKE can create matches
        if (!swipe.isLike()) {
            LOG.debug("Ignoring PASS from {} to {}", swipe.getUserId(), swipe.getTargetId());
            return;
        }

        String currentUserId = swipe.getUserId();
        String targetUserId = swipe.getTargetId();

        // Check if someone is already waiting for a match in this pair
        String waitingUser = pendingSwipeState.value();

        if (waitingUser == null) {
            // Case A: First swipe in this pair - store and wait
            pendingSwipeState.update(currentUserId);
            LOG.info("Stored swipe: {} is waiting for {}", currentUserId, targetUserId);
        } else if (!waitingUser.equals(currentUserId)) {
            // Case B: Someone else already swiped - it's a MATCH!
            // (The waitingUser liked the currentUser, and now currentUser liked back)

            LOG.info("MATCH DETECTED: {} <--> {}", waitingUser, currentUserId);

            // Create and emit the match event
            MatchEvent match = new MatchEvent(
                    UUID.randomUUID().toString(),
                    waitingUser,      // The one who swiped first
                    currentUserId,    // The one who completed the match
                    System.currentTimeMillis()
            );

            out.collect(match);

            // Clear state - match is complete
            pendingSwipeState.clear();
        } else {
            // Case C: Same user swiped again (duplicate) - just update timestamp
            LOG.debug("Duplicate swipe from {} to {}", currentUserId, targetUserId);
        }
    }

}
