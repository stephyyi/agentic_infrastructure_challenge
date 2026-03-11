package com.chimera;

import com.chimera.agent.ChimeraAgentException;
import com.chimera.agent.TrendFetcherService;
import com.chimera.model.TrendRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for TrendFetcherService — RED PHASE.
 *
 * <p>These tests define the acceptance criteria from specs/functional.md:
 * <ul>
 *   <li>T-001 — Fetch Trending Keywords</li>
 *   <li>T-002 — Store Trends with Confidence Score</li>
 *   <li>T-003 — Rank and Surface Top Trends</li>
 * </ul>
 *
 * <p>ALL TESTS ARE EXPECTED TO FAIL until TrendFetcherService is implemented.
 * That is intentional — this is the TDD "empty slot" that the AI agent must fill.
 * Do NOT add an implementation here. Do NOT mark tests @Disabled.
 */
@DisplayName("T-001/T-002/T-003 — TrendFetcherService")
class TrendFetcherTest {

    /**
     * The service under test.
     * No implementation class exists yet — this will be null until Day 3 wiring.
     * Tests that invoke methods on a null reference will fail with NullPointerException,
     * which is the expected red-phase behaviour.
     */
    private TrendFetcherService service;

    private UUID testCycleId;

    @BeforeEach
    void setUp() {
        testCycleId = UUID.randomUUID();
        // TODO (Day 3): inject a real implementation here
        // e.g. service = new TrendFetcherServiceImpl(mockPlatformClient, mockLlmClient);
        service = null; // intentionally null — tests must fail
    }

    // ─── T-001: Fetch Trending Keywords ───────────────────────────────────────

    @Test
    @DisplayName("T-001: fetchTrends returns at least 10 results for a valid cycleId")
    void fetchTrends_returnsAtLeast10Results() {
        List<TrendRecord> trends = service.fetchTrends(testCycleId);
        assertNotNull(trends, "fetchTrends must not return null");
        assertTrue(trends.size() >= 10,
                "Expected at least 10 trends, got: " + trends.size());
    }

    @Test
    @DisplayName("T-001: each TrendRecord has all required non-null fields")
    void fetchedTrend_matchesApiContract_allFieldsNonNull() {
        List<TrendRecord> trends = service.fetchTrends(testCycleId);
        assertFalse(trends.isEmpty(), "Trends list must not be empty");

        TrendRecord first = trends.get(0);
        assertNotNull(first.id(), "TrendRecord.id must not be null");
        assertNotNull(first.cycleId(), "TrendRecord.cycleId must not be null");
        assertNotNull(first.keyword(), "TrendRecord.keyword must not be null");
        assertNotNull(first.source(), "TrendRecord.source must not be null");
        assertNotNull(first.fetchedAt(), "TrendRecord.fetchedAt must not be null");
        assertFalse(first.keyword().isBlank(), "TrendRecord.keyword must not be blank");
    }

    @Test
    @DisplayName("T-001: each TrendRecord.score is in range [0.0, 1.0]")
    void fetchedTrend_score_isBetweenZeroAndOne() {
        List<TrendRecord> trends = service.fetchTrends(testCycleId);
        for (TrendRecord trend : trends) {
            assertTrue(trend.score() >= 0.0f,
                    "score must be >= 0.0, got: " + trend.score() + " for keyword: " + trend.keyword());
            assertTrue(trend.score() <= 1.0f,
                    "score must be <= 1.0, got: " + trend.score() + " for keyword: " + trend.keyword());
        }
    }

    @Test
    @DisplayName("T-001: each TrendRecord.cycleId matches the input cycleId")
    void fetchedTrend_cycleId_matchesInput() {
        List<TrendRecord> trends = service.fetchTrends(testCycleId);
        for (TrendRecord trend : trends) {
            assertEquals(testCycleId, trend.cycleId(),
                    "TrendRecord.cycleId must match the input cycleId");
        }
    }

    @Test
    @DisplayName("T-001: fetchTrends on API failure throws ChimeraAgentException after retries")
    void fetchTrends_onApiFailure_throwsChimeraAgentException() {
        UUID badCycleId = UUID.fromString("00000000-dead-beef-0000-000000000000");
        // Passing a sentinel UUID that the implementation should treat as a simulated API failure
        assertThrows(ChimeraAgentException.class,
                () -> service.fetchTrends(badCycleId),
                "fetchTrends must throw ChimeraAgentException after all retry attempts fail");
    }

    // ─── T-002: Deduplication ─────────────────────────────────────────────────

    @Test
    @DisplayName("T-002: duplicate keywords within the same cycle are deduplicated")
    void fetchTrends_deduplicates_sameKeywordSameCycle() {
        List<TrendRecord> trends = service.fetchTrends(testCycleId);
        long distinctKeywords = trends.stream()
                .map(TrendRecord::keyword)
                .distinct()
                .count();
        assertEquals(trends.size(), distinctKeywords,
                "All keywords within a cycle must be unique (T-002 deduplication)");
    }

    // ─── T-003: Ranking ───────────────────────────────────────────────────────

    @Test
    @DisplayName("T-003: rankTopTrends returns results sorted descending by score")
    void rankTopTrends_returnsSortedDescendingByScore() {
        // Arrange — fixture dataset with known scores
        UUID cycleId = UUID.randomUUID();
        List<TrendRecord> input = List.of(
                new TrendRecord(UUID.randomUUID(), cycleId, "low",    0.30f, "TEST", Instant.now()),
                new TrendRecord(UUID.randomUUID(), cycleId, "high",   0.95f, "TEST", Instant.now()),
                new TrendRecord(UUID.randomUUID(), cycleId, "medium", 0.60f, "TEST", Instant.now()),
                new TrendRecord(UUID.randomUUID(), cycleId, "vhigh",  0.99f, "TEST", Instant.now())
        );

        List<TrendRecord> ranked = service.rankTopTrends(input, 3);

        assertEquals(3, ranked.size(), "rankTopTrends must return exactly topN results");
        assertTrue(ranked.get(0).score() >= ranked.get(1).score(),
                "First result must have score >= second result");
        assertTrue(ranked.get(1).score() >= ranked.get(2).score(),
                "Second result must have score >= third result");
        assertEquals("vhigh", ranked.get(0).keyword(),
                "Highest-score keyword must be first");
    }

    @Test
    @DisplayName("T-003: rankTopTrends with topN greater than list size returns all items")
    void rankTopTrends_topNGreaterThanList_returnsAllItems() {
        UUID cycleId = UUID.randomUUID();
        List<TrendRecord> input = List.of(
                new TrendRecord(UUID.randomUUID(), cycleId, "only", 0.5f, "TEST", Instant.now())
        );
        List<TrendRecord> ranked = service.rankTopTrends(input, 10);
        assertEquals(1, ranked.size(), "Should return all items when topN > list size");
    }

    // ─── Immutability assertion ───────────────────────────────────────────────

    @Test
    @DisplayName("TrendRecord is a Java Record (compile-time immutability check)")
    void trendRecord_isImmutable_recordType() {
        assertTrue(TrendRecord.class.isRecord(),
                "TrendRecord must be a Java Record to guarantee immutability for OCC");
    }
}
