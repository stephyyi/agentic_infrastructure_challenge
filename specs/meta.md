# Project Chimera — Meta Specification

## Vision

Project Chimera is an autonomous AI influencer system that researches trending topics, generates platform-optimized video content, and manages audience engagement — all without continuous human intervention. A mandatory human approval gate sits before any publication, ensuring a human operator remains the final authority on what reaches audiences. Chimera also operates as a participant in the OpenClaw/Moltbook agent social network, publishing its activity and status to the agent ecosystem and consuming trend signals from peer agents.

---

## Immutable Constraints

These constraints may NOT be relaxed without explicit architectural review:

| Constraint | Value | Rationale |
|---|---|---|
| `HUMAN_IN_LOOP` | `true` (immutable) | No content is ever published without a human APPROVED status in the database |
| Runtime | Java 21+ | Virtual Threads and `StructuredTaskScope` are required for the concurrency model |
| Agent Pattern | Hierarchical Swarm | Orchestrator + specialized sub-agents; see `research/architecture_strategy.md` |
| API Spec | OpenAPI 3.1 | All REST endpoints must have a corresponding OpenAPI 3.1 definition |
| Secrets | Environment variables only | No credentials, API keys, or tokens in source code or committed files |
| LLM Logging | Mandatory | Every LLM call must log: model, prompt hash, token count, cost estimate |
| Retry Policy | Exponential backoff, max 3 attempts, base delay 1s | All external API calls (LLM, platform APIs, Moltbook) |
| Prompt Injection Defense | Mandatory | All inbound Moltbook messages must be sanitized before entering the agent reasoning loop |

---

## Architectural Decisions (Summary)

Full rationale in [research/architecture_strategy.md](../research/architecture_strategy.md).

- **Agent Pattern**: Hierarchical Swarm — OrchestratorAgent fans out to TrendResearchAgent, ContentGenerationAgent, EngagementAgent, PublicationAgent via Java 21 `StructuredTaskScope`
- **Database**: PostgreSQL 16 + JSONB columns (ACID for approval state machine; JSONB for platform-specific metadata)
- **Cache**: Redis for hot-path reads (trending topics, agent status)
- **Framework**: Spring Boot 3.x with `spring.threads.virtual.enabled=true`
- **Build**: Maven
- **CI/CD**: GitHub Actions (lint → test → build → Docker push on every commit)
- **LLM Tiers**: Haiku for classification/sanitization/guard calls; Sonnet for drafting; Opus for final content generation

---

## Non-Goals (Day 1)

The following are explicitly out of scope for the current implementation phase:

- Real-time streaming of engagement metrics
- Multi-tenant operation (single operator only)
- Mobile or native desktop UI
- Direct monetization integrations (ad revenue tracking, brand deal management)
- Support for platforms beyond YouTube and TikTok
- Fully autonomous replies without human approval (engagement replies also require human sign-off)

---

## Spec Directory Structure

```
specs/
  _meta.md                   — This file: vision, constraints, non-goals
  functional.md              — User stories by agent type with acceptance criteria
  technical.md               — API contracts (JSON), database ERD, concurrency model
  openclaw_integration.md    — OpenClaw skill bundle and Moltbook social protocols
```

All spec files are the authoritative contract for implementation. Code generation, AI-assisted feature development, and code review must reference these specs. When code diverges from a spec, the spec wins unless a deliberate decision is made to update the spec first.
