package com.chimera.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing a single trending keyword discovered by TrendResearchAgent.
 *
 * <p>Implements spec story T-001 (Fetch Trending Keywords) and T-002 (Store Trends).
 * All fields match the API contract in specs/technical.md GET /trends response shape.
 *
 * <p>Using a Java Record guarantees:
 * <ul>
 *   <li>Compile-time immutability — no setters, all fields final.</li>
 *   <li>Thread-safe sharing between Planner, Worker, and Judge without defensive copies.</li>
 *   <li>OCC safety — the Judge cannot receive a mutated trend payload from a Worker.</li>
 * </ul>
 *
 * <p>Acceptance criteria (T-001):
 * <ul>
 *   <li>id — non-null UUID</li>
 *   <li>cycleId — non-null UUID matching the parent CycleRecord</li>
 *   <li>keyword — non-null, non-empty string</li>
 *   <li>score — float in range [0.0, 1.0]</li>
 *   <li>source — non-null string (e.g., "YOUTUBE_TRENDING", "MOLTBOOK_SUBMOLT")</li>
 *   <li>fetchedAt — non-null Instant</li>
 * </ul>
 *
 * @param id        unique identifier
 * @param cycleId   the CycleRecord this trend belongs to
 * @param keyword   the trending keyword or phrase
 * @param score     trend velocity score, normalised to [0.0, 1.0]
 * @param source    data source identifier
 * @param fetchedAt when this trend was fetched from the source API
 */
public record TrendRecord(
        UUID id,
        UUID cycleId,
        String keyword,
        float score,
        String source,
        Instant fetchedAt) {

    /**
     * Compact constructor — validates field invariants at construction time.
     * Fails fast rather than passing invalid records into the agent pipeline.
     */
    public TrendRecord {
        if (id == null) {
            throw new IllegalArgumentException("TrendRecord.id must not be null");
        }
        if (cycleId == null) {
            throw new IllegalArgumentException("TrendRecord.cycleId must not be null");
        }
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("TrendRecord.keyword must not be null or blank");
        }
        if (score < 0.0f || score > 1.0f) {
            throw new IllegalArgumentException("TrendRecord.score must be between 0.0 and 1.0, got: " + score);
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("TrendRecord.source must not be null or blank");
        }
        if (fetchedAt == null) {
            throw new IllegalArgumentException("TrendRecord.fetchedAt must not be null");
        }
    }
}
