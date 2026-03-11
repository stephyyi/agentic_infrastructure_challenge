# Project Chimera — AI Co-pilot Rules

## Project Context

This is **Project Chimera**, an autonomous AI influencer system built in Java 21 with Spring Boot 3.x. Chimera researches trending topics, generates platform-optimized video content (YouTube, TikTok), manages audience engagement, and participates in the OpenClaw/Moltbook agent social network — all without continuous human intervention.

The system follows a **FastRender Swarm** architecture: a Planner decomposes goals into a Directed Acyclic Graph (DAG) of tasks; stateless Workers execute atomic tasks in parallel via Java 21 `StructuredTaskScope`; a Judge gates every Worker output using Optimistic Concurrency Control (OCC) before any result reaches an external system.

A mandatory human approval gate sits before every publication. `HUMAN_IN_LOOP = true` is an **immutable constraint**.

All specs live in `specs/`. The authoritative files are:
- `specs/_meta.md` — vision, immutable constraints, non-goals
- `specs/functional.md` — user stories with acceptance criteria (story IDs: O-xxx, T-xxx, C-xxx, P-xxx, E-xxx, H-xxx)
- `specs/technical.md` — REST API contracts, database DDL, concurrency model
- `specs/openclaw_integration.md` — OpenClaw skill bundle and Moltbook social protocols

---

## The Prime Directive

> **NEVER generate code without checking `specs/` first.**

Before writing any implementation:
1. Identify the relevant spec file and section.
2. Read the acceptance criteria for the story you are implementing.
3. Confirm the API contract (request/response shape) in `specs/technical.md` if touching any endpoint.
4. Only then write code.

If a spec is ambiguous, **ask for clarification — do not assume.**

---

## Java-Specific Directives

### Language Version
- Target **Java 21+** exclusively. Use `--enable-preview` features where appropriate.
- Do NOT use deprecated APIs, raw types, or pre-Java-16 patterns.

### Immutable DTOs — Java Records
- **ALL data transfer objects (DTOs) passing between Planner, Worker, and Judge MUST be Java Records.**
- Records are immutable by design — they prevent the ghost updates that OCC is designed to catch.
- Never use POJOs with mutable setters for agent payloads. If you see a POJO with setters in agent-to-agent communication, refactor it to a Record.

```java
// CORRECT — immutable, thread-safe
public record TrendRecord(UUID id, UUID cycleId, String keyword, float score, String source, Instant fetchedAt) {}

// WRONG — mutable, not safe for concurrent Workers
public class TrendDTO { private String keyword; public void setKeyword(String k) { this.keyword = k; } }
```

### Concurrency — Virtual Threads and StructuredTaskScope
- Use `Executors.newVirtualThreadPerTaskExecutor()` for all agent task pools.
- Use `StructuredTaskScope.ShutdownOnFailure` for parallel Worker fan-out. Never use legacy `Thread`, `ThreadPoolExecutor`, or `ExecutorService` with fixed thread counts.
- `Thread.sleep()` inside a Virtual Thread is safe — it does not block an OS thread.

```java
// CORRECT
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var trendTask = scope.fork(() -> trendAgent.research(cycleId));
    var engagementTask = scope.fork(() -> engagementAgent.poll(cycleId));
    scope.join().throwIfFailed();
    // use trendTask.get(), engagementTask.get()
}
```

### OCC (Optimistic Concurrency Control)
- **Never commit a Worker result without verifying `state_version` has not changed** since the task was forked.
- The Judge must check `state_version` on every `ContentDraft` and `CycleRecord` before writing to the database.
- If `state_version` has advanced, discard the Worker result and re-queue.

### Testing
- Use **JUnit 5** (`@Test`, `@BeforeEach`, `@ExtendWith`) for all tests. Never JUnit 4.
- Test method naming: `methodName_scenario_expectedBehaviour()` (e.g., `fetchTrends_onApiFailure_throwsChimeraAgentException`).
- Tests live in `tests/`. Maven `testSourceDirectory` points there.
- Follow TDD: write the failing test first (red), then implement (green), then refactor.

### Error Handling
- Wrap all external API calls (LLM, platform APIs, Moltbook) in exponential backoff: max 3 attempts, base delay 1s, multiplier 2x.
- Throw `ChimeraAgentException` (unchecked) after max retries.
- Throw `BudgetExceededException` (checked, extends `SkillExecutionException`) when a skill exceeds its token/cost budget.
- `PublicationAgent` must throw `IllegalStateException` immediately if called with any `ContentDraft` status other than `APPROVED`.

### Security
- **Never put credentials, API keys, or tokens in source code or committed files.**
- All secrets via environment variables only (see `specs/technical.md` Section 5 for the full reference list).
- All inbound Moltbook messages must pass through the 3-step injection defense before entering the agent reasoning loop (length gate → pattern strip → guard LLM).

### LLM Model Tiering
| Task | Model |
|---|---|
| Hashtag generation, trend classification, injection guard | `claude-haiku-4-5-20251001` |
| Script drafting, reply generation | `claude-sonnet-4-6` |
| Final quality review, confidence scoring, Planner decomposition | `claude-opus-4-6` |

Every LLM call must produce an `AGENT_LOG` entry with: `llm_model`, `prompt_hash` (SHA-256), `token_count`, `cost_usd`.

---

## Traceability

**Before writing any code, state:**
1. Which spec story you are implementing (e.g., "Implementing T-001 — Fetch Trending Keywords").
2. Which acceptance criteria you are satisfying.
3. Your implementation plan in plain English before any code appears.

Example preamble:
```
Implementing: T-002 — Store Trends with Confidence Score (specs/functional.md)
Acceptance criteria: batch insert into `trend` table, deduplicate by (cycle_id, keyword), score 0.0–1.0.
Plan: Add TrendRepository.batchInsert(), deduplicate in service layer before insert, wrap in @Transactional.
```

---

## What This Repository Must Always Be

By end of Day 2, this repository must be so well-specified that a fresh swarm of AI agents can enter it, read `specs/` and this file, and build any remaining feature with minimal human conflict. Every file you generate contributes to or detracts from that goal.
