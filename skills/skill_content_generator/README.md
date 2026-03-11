# Skill: `skill_content_generator`

**Spec stories:** C-001 (Generate ContentDraft), C-002 (LLM Model Tiering), C-003 (Confidence Scoring), C-005 (LLM Call Audit Trail)
**LLM tier:** Haiku → Sonnet → Opus (multi-tier pipeline)
**Invoked by:** ContentGenerationAgent Worker inside `StructuredTaskScope`

---

## Purpose

Takes a single `TrendRecord` and runs a multi-tier LLM pipeline to produce a `ContentDraftRecord` with title, script, hashtags, platform metadata, and a confidence score. The output is immutable — the Judge checks `state_version` before committing it to the database.

---

## Input Contract

```java
public record ContentGenerationInput(
    TrendRecord trend,        // required — the trend this content is based on
    UUID agentId,             // required — the InfluencerAgent's UUID
    String platform,          // required — "YOUTUBE" | "TIKTOK"
    int maxScriptWords,       // required — max word count for the script (default: 500)
    BigDecimal budgetLimitUsd // required — max USD this skill may spend on LLM calls
) {}
```

**Validation rules:**
- `trend` must not be null; all `TrendRecord` fields must pass their own validation
- `platform` must be `YOUTUBE` or `TIKTOK`
- `maxScriptWords` must be ≥ 50 and ≤ 1000
- `budgetLimitUsd` must be > 0

---

## Output Contract

```java
public record ContentGenerationOutput(
    ContentDraftRecord draft,     // immutable draft ready for Judge review
    int totalTokensUsed,          // total tokens across all LLM calls in this invocation
    BigDecimal estimatedCostUsd   // sum of estimated costs for all LLM calls
) {}
```

**Guarantees:**
- `draft.status()` is always `PENDING_REVIEW` (or `NEEDS_REVIEW` if `confidenceScore < 0.6`)
- `draft.script()` word count ≤ `input.maxScriptWords()`
- `draft.hashtags().size()` ≤ 30
- `draft.platformMetadata()` contains at minimum: `platform`, `video_duration_seconds`
- Every LLM call during execution produces an `AGENT_LOG` entry (C-005)

---

## LLM Pipeline (3 Tiers)

| Stage | Model | Input | Output |
|---|---|---|---|
| 1 — Hashtag generation | `claude-haiku-4-5-20251001` | `trend.keyword()` + platform | List of ≤ 30 hashtags |
| 2 — Script drafting | `claude-sonnet-4-6` | Trend keyword + hashtags + platform + maxScriptWords | Full video script |
| 3 — Confidence scoring | `claude-opus-4-6` | Script + trend keyword + platform | Float score [0.0, 1.0] |

**Cost estimate** (approximate at current pricing):
- Haiku: ~$0.001 per invocation
- Sonnet: ~$0.05 per invocation
- Opus: ~$0.25 per invocation
- Total per draft: ~$0.30 USD

---

## Confidence Scoring Rules (C-003)

The Opus model evaluates the script on three dimensions:

| Dimension | Weight | Description |
|---|---|---|
| Relevance | 40% | Does the script address the trending keyword directly? |
| Originality | 30% | Is the angle fresh, or is it generic content? |
| Platform fit | 30% | Does the format, length, and tone suit the target platform? |

- Score ≥ 0.6: `status = PENDING_REVIEW`
- Score < 0.6: `status = NEEDS_REVIEW` (flagged for extra human attention)

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Any LLM call fails (all 3 retry attempts) | Throw `SkillExecutionException` with the failing stage name |
| Estimated cost would exceed `budgetLimitUsd` before calling LLM | Throw `BudgetExceededException` with budget and estimated cost |
| Confidence score LLM returns invalid format | Retry once with clarifying prompt; if still invalid: set `confidenceScore = 0.0`, `status = NEEDS_REVIEW` |
| Script exceeds `maxScriptWords` | Retry drafting with an explicit truncation instruction (max 1 additional attempt) |

---

## OCC Safety

The output `ContentDraftRecord` is immutable (Java Record). The Judge must verify:
```java
// Before DB commit:
long currentVersion = db.getCycleStateVersion(draft.trendId());
if (currentVersion != cycleStateVersionAtFork) {
    // Stale — discard and re-queue
}
```

---

## Sequence

```
Worker
  │
  ├─► Stage 1: Haiku → hashtags[]
  │       └─► AGENT_LOG entry (model=haiku, prompt_hash, tokens, cost)
  │
  ├─► Stage 2: Sonnet → script (≤ maxScriptWords words)
  │       └─► AGENT_LOG entry (model=sonnet, prompt_hash, tokens, cost)
  │
  ├─► Stage 3: Opus → confidence_score [0.0, 1.0]
  │       └─► AGENT_LOG entry (model=opus, prompt_hash, tokens, cost)
  │
  ├─► Assemble ContentDraftRecord (immutable)
  │       status = PENDING_REVIEW or NEEDS_REVIEW
  │
  └─► ContentGenerationOutput → Judge (OCC check) → DB insert → webhook notification
```
