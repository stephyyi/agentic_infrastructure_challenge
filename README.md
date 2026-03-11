# Project Chimera — Autonomous AI Influencer Network

> **"By the end of Day 2, this repository is so well-specified and context-engineered that a swarm of AI agents could enter the codebase and build the final features with minimal human conflict."**

---

## What Is Project Chimera?

Project Chimera is an autonomous AI influencer system. It researches trending topics, generates platform-optimized video content, and manages audience engagement on YouTube and TikTok — without continuous human intervention.

Every content decision passes through a mandatory human approval gate before reaching any external platform. `HUMAN_IN_LOOP = true` is an immutable system constraint.

Chimera also participates in the [OpenClaw](specs/openclaw_integration.md) / Moltbook agent social network, broadcasting its status and consuming trend signals from peer agents.

---

## Architecture: FastRender Swarm

```
Human Super-Orchestrator
        │
        ▼
Central Orchestrator
        │
   ┌────┴────┐
   ▼         ▼
Planner ──► DAG of tasks
        │
   ┌────┼────┬────┐
   ▼    ▼    ▼    ▼
Worker  Worker  Worker  Worker
(Trend) (Draft) (Image) (Reply)
        │
        ▼
     Judge (OCC + Confidence Score)
        │
   ┌────┼────┐
   ▼    ▼    ▼
Auto  HITL  Retry
Approve Queue
        │
        ▼
     MCP Layer
   ┌───┬───┬───┐
   ▼   ▼   ▼   ▼
YouTube TikTok Moltbook Coinbase
```

**Three roles:**
- **Planner** — decomposes goals into a DAG of atomic tasks
- **Worker** — stateless, ephemeral executor of one atomic task
- **Judge** — gates every Worker output with OCC before it reaches external systems

**Concurrency:** Java 21 Virtual Threads + `StructuredTaskScope.ShutdownOnFailure`

**HITL routing:** confidence > 0.90 → auto-approve | 0.70–0.90 → human queue | < 0.70 → retry

---

## Repository Structure

```
chimera/
├── CLAUDE.md                          # AI co-pilot rules — Prime Directive, Java directives, OCC
├── Makefile                           # make setup | test | lint | spec-check | docker-test
├── Dockerfile                         # Multi-stage: compile → test inside container
├── pom.xml                            # Maven build (Java 21, Spring Boot 3.x, JUnit 5)
├── checkstyle.xml                     # Google Java Style + Java 21 adjustments
│
├── specs/                             # Source of truth — read before writing any code
│   ├── _meta.md                       # Vision, immutable constraints, non-goals
│   ├── functional.md                  # User stories (O-xxx T-xxx C-xxx P-xxx E-xxx H-xxx)
│   ├── technical.md                   # REST API contracts, database DDL, concurrency model
│   └── openclaw_integration.md        # OpenClaw skill bundle + 6 Moltbook social protocols
│
├── src/main/java/com/chimera/
│   ├── ChimeraApplication.java        # Spring Boot entry point
│   ├── model/                         # Immutable Java Records (DTOs)
│   │   ├── TrendRecord.java           # T-001/T-002 — trend data from platform APIs
│   │   ├── ContentDraftRecord.java    # C-001/C-003 — draft ready for human review
│   │   ├── CycleRecord.java           # O-001/O-002 — research-publish cycle lifecycle
│   │   ├── AgentStatus.java           # O-006 — agent heartbeat and availability
│   │   ├── DraftStatus.java           # enum: PENDING_REVIEW | NEEDS_REVIEW | APPROVED | REJECTED
│   │   └── TriggerType.java           # enum: SCHEDULED | MANUAL
│   ├── skill/                         # Runtime skill contracts
│   │   ├── ChimeraSkill.java          # Interface: execute(I) → O throws SkillExecutionException
│   │   ├── SkillExecutionException.java
│   │   └── BudgetExceededException.java
│   └── agent/
│       ├── TrendFetcherService.java   # Interface stub — NOT implemented (TDD red)
│       └── ChimeraAgentException.java # Unchecked — thrown after max retries exhausted
│
├── src/main/resources/
│   └── application.yaml               # Spring config (Virtual Threads, DB, LLM model tiers)
│
├── tests/                             # JUnit 5 tests — EXPECTED TO FAIL (TDD red phase)
│   ├── TrendFetcherTest.java          # 9 tests for T-001/T-002/T-003 — all fail (no impl)
│   └── SkillsInterfaceTest.java       # 14 tests for skill contracts — structural tests pass
│
├── skills/                            # Agent runtime skill definitions
│   ├── README.md                      # Skills system overview
│   ├── skill_trend_fetcher/
│   │   └── README.md                  # Input/Output contract, LLM tier, retry policy
│   └── skill_content_generator/
│       └── README.md                  # 3-tier LLM pipeline, confidence scoring, OCC note
│
├── research/
│   ├── architecture_strategy.md       # FastRender Swarm decision + Mermaid diagrams
│   ├── tooling_strategy.md            # Dev MCPs vs runtime skills explained
│   └── day1_submission_final.md       # Day 1 report (research summary + architecture)
│
├── scripts/
│   └── spec_check.sh                  # Verifies code alignment with specs/
│
└── .github/
    └── workflows/
        └── main.yml                   # CI: test → lint → spec-check on every push
```

---

## Quick Start

### Prerequisites

```bash
brew install openjdk@21 maven
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

### Commands

```bash
make setup        # Download dependencies (skip tests)
make test         # Run JUnit 5 tests — WILL FAIL (TDD red phase, intentional)
make lint         # Checkstyle code quality check
make spec-check   # Verify code alignment with specs/
make docker-test  # Build Docker image and run tests inside container
```

### Run Tests (TDD Red Phase)

```bash
mvn test
```

**Expected output:**
```
Tests run: 23, Failures: 1, Errors: 7
BUILD FAILURE
```

This is correct. `TrendFetcherTest` fails because `TrendFetcherService` has no implementation yet. The tests define the acceptance criteria — the implementation is the Day 3 task.

---

## Specifications

All specs live in `specs/`. **Always read the relevant spec before writing any implementation.**

| File | Contents |
|---|---|
| [specs/_meta.md](specs/_meta.md) | Vision, immutable constraints (`HUMAN_IN_LOOP=true`), non-goals |
| [specs/functional.md](specs/functional.md) | User stories with acceptance criteria for all 6 agent roles |
| [specs/technical.md](specs/technical.md) | REST API contracts (JSON), PostgreSQL DDL (7 tables), Java 21 concurrency model |
| [specs/openclaw_integration.md](specs/openclaw_integration.md) | OpenClaw skill bundle structure + 6 Moltbook social protocols |

---

## Data Model (Key Tables)

```
influencer_agent ──► cycle_record ──► trend ──► content_draft ──► publication
                                                      │                 │
                                                      │                 ▼
                                                      │          engagement_event
                                                      │
                                                      ▼
                                                 agent_log
```

All agent-to-agent payloads are **Java Records** (immutable) to enforce OCC safety. See [specs/technical.md](specs/technical.md) for the full DDL.

---

## LLM Model Tiering

| Task | Model | Reason |
|---|---|---|
| Hashtag generation, classification, injection guard | `claude-haiku-4-5-20251001` | High-frequency, low-stakes — cost control |
| Script drafting, reply generation | `claude-sonnet-4-6` | Quality/cost balance |
| Final content review, confidence scoring, Planner decomposition | `claude-opus-4-6` | Premium quality, used sparingly |

Every LLM call produces an `agent_log` entry: `model`, `prompt_hash`, `token_count`, `cost_usd`.

---

## Agent Skills

Runtime skills are capability packages invoked by Workers inside `StructuredTaskScope`. See [skills/README.md](skills/README.md).

| Skill | Stories | Status |
|---|---|---|
| [skill_trend_fetcher](skills/skill_trend_fetcher/README.md) | T-001, T-002, T-003 | Interface defined — implementation pending |
| [skill_content_generator](skills/skill_content_generator/README.md) | C-001, C-002, C-003, C-005 | Interface defined — implementation pending |

---

## CI/CD Pipeline

Every push triggers `.github/workflows/main.yml`:

```
push → test (JUnit 5) → lint (Checkstyle) → spec-check (alignment)
```

AI review on every PR via `.coderabbit.yaml` — checks for:
- Spec story traceability in every class
- Java Records for all DTOs (not POJOs)
- Virtual Threads (no legacy `new Thread()`)
- No hardcoded credentials
- OCC compliance on all DB writes

---

## AI Co-pilot Rules

[CLAUDE.md](CLAUDE.md) governs how the AI agent (Claude Code / Cursor) behaves in this codebase.

**Prime Directive:** `NEVER generate code without checking specs/ first.`

Before writing any code, the agent must state:
1. Which spec story it is implementing
2. Which acceptance criteria it is satisfying
3. Its implementation plan in plain English

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes | PostgreSQL username |
| `DATABASE_PASSWORD` | Yes | PostgreSQL password |
| `ANTHROPIC_API_KEY` | Yes | Anthropic Claude API key |
| `NOTIFICATION_WEBHOOK_URL` | Yes | Human review notification webhook |
| `PLATFORM_API_KEY_YOUTUBE` | No | YouTube Data API key |
| `PLATFORM_API_KEY_TIKTOK` | No | TikTok API key |
| `MOLTBOOK_API_KEY` | No | Moltbook network API key |

Full reference: [specs/technical.md — Section 5](specs/technical.md)

**Never commit secrets.** All credentials via environment variables only.

---

## Security

- All inbound Moltbook messages pass through a 3-step injection defense pipeline before entering agent reasoning: length gate → pattern sanitisation → guard LLM classification (Haiku)
- No credentials in source code or committed files
- `PublicationAgent` throws `IllegalStateException` if called with any draft status other than `APPROVED`
- Circuit breaker activates after 5 consecutive external API failures

---

## Day 2 Deliverables Checklist

- [x] `CLAUDE.md` — AI co-pilot rules with Prime Directive, Java directives, OCC, traceability
- [x] `specs/` — Complete functional, technical, and OpenClaw integration specs
- [x] `skills/` — 2 skill READMEs with explicit Input/Output contracts
- [x] `tests/` — Failing JUnit 5 tests (TDD red phase)
- [x] `Makefile` — `setup`, `test`, `lint`, `spec-check`, `docker-test`
- [x] `.github/workflows/main.yml` — CI pipeline on every push
- [x] `.coderabbit.yaml` — AI review policy
- [x] `Dockerfile` — containerised test execution
- [x] `research/tooling_strategy.md` — dev MCPs vs runtime skills

---

## Built With

- **Java 21** — Virtual Threads, `StructuredTaskScope`, Records
- **Spring Boot 3.x** — REST API, JPA, Virtual Thread integration
- **Maven** — build, test, lint
- **JUnit 5** — TDD test framework
- **PostgreSQL 16 + JSONB** — ACID transactions + flexible platform metadata
- **Claude API** — Haiku / Sonnet / Opus model tiering
- **GitHub Actions** — CI/CD governance pipeline
- **CodeRabbit** — AI-powered PR review
