# Skill: `skill_trend_fetcher`

**Spec stories:** T-001 (Fetch Trending Keywords), T-002 (Store Trends), T-003 (Rank Top Trends)
**LLM tier:** Haiku only (classification of raw API results) — no LLM for the raw HTTP fetch
**Invoked by:** TrendResearchAgent Worker inside `StructuredTaskScope`

---

## Purpose

Fetches trending keywords from a platform trend API (YouTube Trending, TikTok Discover, or Moltbook Submolts), normalises scores to [0.0, 1.0], deduplicates within a cycle, and returns a ranked list of `TrendRecord` objects ready for ContentGenerationAgent.

---

## Input Contract

```java
public record TrendFetchInput(
    UUID cycleId,        // required — parent CycleRecord identifier
    String platform,     // required — "YOUTUBE" | "TIKTOK" | "MOLTBOOK"
    int topN,            // required — number of top trends to return (default: 3)
    String apiKey        // required — platform API key (from environment, NOT hardcoded)
) {}
```

**Validation rules:**
- `cycleId` must not be null
- `platform` must be one of: `YOUTUBE`, `TIKTOK`, `MOLTBOOK`
- `topN` must be ≥ 1 and ≤ 50
- `apiKey` must not be null or blank

---

## Output Contract

```java
public record TrendFetchOutput(
    List<TrendRecord> trends,   // top N trends, sorted descending by score
    int totalFetched,           // total raw results before deduplication and ranking
    Instant fetchedAt           // timestamp of the API call
) {}
```

**Guarantees:**
- `trends` is sorted descending by `TrendRecord.score`
- `trends.size()` ≤ `input.topN()`
- No two entries in `trends` share the same `keyword` (deduplication applied)
- All `TrendRecord` fields are non-null and pass `TrendRecord`'s compact constructor validation
- `fetchedAt` reflects the actual time the API call completed

---

## LLM Usage

| Sub-task | Model | Purpose |
|---|---|---|
| Keyword classification | `claude-haiku-4-5-20251001` | Filter out non-relevant or spam keywords from raw API results |

No LLM is used for the raw HTTP fetch or score normalisation.

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Platform API returns 4xx/5xx | Retry with exponential backoff (max 3, base 1s). After 3: throw `SkillExecutionException` |
| Platform API returns < 10 results | Proceed with what was returned; log `WARN "Low trend count: {n} from {platform}"` |
| LLM guard call fails | Skip classification for that keyword; log `WARN`; do not throw |
| Token/cost budget exceeded | Throw `BudgetExceededException` before making the LLM call |

**Retry policy:** 3 attempts, exponential backoff, base 1s (per `specs/_meta.md`).

---

## Example Usage (Worker inside StructuredTaskScope)

```java
// Inside OrchestratorAgent.runCycle()
var trendInput = new TrendFetchInput(cycleId, "YOUTUBE", 3, System.getenv("PLATFORM_API_KEY_YOUTUBE"));

try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var trendTask = scope.fork(() -> trendFetcherSkill.execute(trendInput));
    scope.join().throwIfFailed();
    TrendFetchOutput output = trendTask.get();
    // output.trends() is ready for ContentGenerationAgent
}
```

---

## Sequence

```
Worker
  │
  ├─► Platform API (HTTP GET /trends)
  │       └─► raw keyword list (unnormalised scores)
  │
  ├─► Normalise scores to [0.0, 1.0]
  │
  ├─► Haiku LLM: classify keywords (filter spam/irrelevant)
  │
  ├─► Deduplicate by keyword within cycleId
  │
  ├─► Sort descending by score, return top N
  │
  └─► TrendFetchOutput → Judge → TrendResearchAgent → DB (batch insert)
```
