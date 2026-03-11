package com.chimera.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing the current status of an InfluencerAgent.
 *
 * <p>Implements spec story O-006 (Agent Status Broadcast).
 * Matches the GET /agents/{id}/status response in specs/technical.md.
 *
 * @param agentId        unique identifier
 * @param name           human-readable agent name
 * @param platformTarget primary publication target ("YOUTUBE" or "TIKTOK")
 * @param moltbookHandle this agent's handle on the Moltbook network
 * @param status         current operational status
 * @param lastSeen       timestamp of the last heartbeat or state change
 * @param currentCycleId the cycle currently running (null if IDLE or ERROR)
 */
public record AgentStatus(
        UUID agentId,
        String name,
        String platformTarget,
        String moltbookHandle,
        String status,
        Instant lastSeen,
        UUID currentCycleId) {

    /** Agent is idle — no cycle running. */
    public static final String STATUS_IDLE = "IDLE";

    /** Agent is actively researching trends. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** Agent is running the content generation LLM pipeline. */
    public static final String STATUS_GENERATING = "GENERATING";

    /** Agent is publishing to a video platform or Moltbook. */
    public static final String STATUS_POSTING = "POSTING";

    /** Agent encountered an unrecoverable error; circuit breaker engaged. */
    public static final String STATUS_ERROR = "ERROR";

    /**
     * Compact constructor — validates invariants.
     */
    public AgentStatus {
        if (agentId == null) {
            throw new IllegalArgumentException("AgentStatus.agentId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AgentStatus.name must not be null or blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("AgentStatus.status must not be null or blank");
        }
        if (lastSeen == null) {
            throw new IllegalArgumentException("AgentStatus.lastSeen must not be null");
        }
    }
}
