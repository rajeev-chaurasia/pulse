package com.pulse.processor.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * POJO representing a successful match between two users.
 * Used for Flink serialization - requires explicit getters/setters.
 */
@Data
public class MatchEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String matchId;
    private String userAId;
    private String userBId;
    private long timestamp;

    // Required no-arg constructor for Flink POJO serialization
    public MatchEvent() {
    }

    public MatchEvent(String matchId, String userAId, String userBId, long timestamp) {
        this.matchId = matchId;
        this.userAId = userAId;
        this.userBId = userBId;
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchEvent that = (MatchEvent) o;
        return timestamp == that.timestamp &&
                Objects.equals(matchId, that.matchId) &&
                Objects.equals(userAId, that.userAId) &&
                Objects.equals(userBId, that.userBId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, userAId, userBId, timestamp);
    }

    @Override
    public String toString() {
        return String.format("MatchEvent{id=%s, %s <--> %s, ts=%d}",
                matchId, userAId, userBId, timestamp);
    }
}
