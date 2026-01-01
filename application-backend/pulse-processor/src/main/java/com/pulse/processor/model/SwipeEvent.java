package com.pulse.processor.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * POJO for Flink serialization.
 */
@Data
public class SwipeEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private String targetId;
    private boolean like;
    private long timestamp;

    // Required no-arg constructor for Flink POJO serialization
    public SwipeEvent() {
    }

    public SwipeEvent(String userId, String targetId, boolean like, long timestamp) {
        this.userId = userId;
        this.targetId = targetId;
        this.like = like;
        this.timestamp = timestamp;
    }

    /**
     * Creates a canonical key for this swipe pair.
     * Sorting ensures A->B and B->A produce the same key.
     */
    public String getPairKey() {
        return userId.compareTo(targetId) < 0
                ? userId + ":" + targetId
                : targetId + ":" + userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwipeEvent that = (SwipeEvent) o;
        return like == that.like &&
                timestamp == that.timestamp &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(targetId, that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, targetId, like, timestamp);
    }

    @Override
    public String toString() {
        return String.format("SwipeEvent{%s -> %s (%s) @ %d}",
                userId, targetId, like ? "LIKE" : "PASS", timestamp);
    }
}


