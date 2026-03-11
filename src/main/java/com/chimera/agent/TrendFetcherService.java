package com.chimera.agent;

import com.chimera.model.TrendRecord;
import java.util.List;
import java.util.UUID;

/**
 * Contract for the TrendResearchAgent's fetching capability.
 *
 * <p>Implements spec stories:
 * <ul>
 *   <li>T-001 — Fetch Trending Keywords</li>
 *   <li>T-002 — Store Trends with Confidence Score</li>
 *   <li>T-003 — Rank and Surface Top Trends</li>
 * </ul>
 *
 * <p>This interface is intentionally NOT implemented yet — the failing tests in
 * tests/TrendFetcherTest.java define the acceptance criteria that the implementation
 * must satisfy (TDD red phase). The AI agent's task on Day 3 is to implement this
 * interface such that all tests turn green.
 *
 * <p>Concurrency contract: implementations must be safe to invoke from a Virtual Thread
 * inside a {@code StructuredTaskScope}. No mutable shared state.
 */
public interface TrendFetcherService {

    /**
     * Fetches trending keywords for the given cycle from the configured platform API.
     *
     * <p>Acceptance criteria (T-001):
     * <ul>
     *   <li>Returns at least 10 TrendRecord objects within 10 seconds</li>
     *   <li>Each TrendRecord has all required non-null fields</li>
     *   <li>On API failure: retries with exponential backoff (max 3, base 1s)</li>
     *   <li>After 3 failures: throws ChimeraAgentException with root cause</li>
     * </ul>
     *
     * @param cycleId the parent cycle's UUID
     * @return list of TrendRecord objects, unranked
     * @throws com.chimera.agent.ChimeraAgentException if all retry attempts fail
     */
    List<TrendRecord> fetchTrends(UUID cycleId);

    /**
     * Returns the top N trends from a list, ranked by score descending.
     *
     * <p>Acceptance criteria (T-003):
     * <ul>
     *   <li>Result is sorted descending by score</li>
     *   <li>N is configurable (default 3 via AGENT_TREND_TOP_N env var)</li>
     * </ul>
     *
     * @param trends all fetched trends for the cycle
     * @param topN   number of top trends to return
     * @return list of up to topN trends, sorted descending by score
     */
    List<TrendRecord> rankTopTrends(List<TrendRecord> trends, int topN);
}
