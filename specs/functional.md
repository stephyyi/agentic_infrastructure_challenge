# Project Chimera — Functional Specification

## Conventions

- **Story format**: `As a [Role], I need to [Goal] so that [Outcome].`
- **Roles**: OrchestratorAgent, TrendResearchAgent, ContentGenerationAgent, PublicationAgent, EngagementAgent, HumanOperator
- **Acceptance Criteria**: All criteria are testable and implementation-language-agnostic. They describe observable system behavior, not internal implementation.
- **Story IDs**: Prefixed by role (`O-` = Orchestrator, `T-` = TrendResearch, `C-` = ContentGeneration, `P-` = Publication, `E-` = Engagement, `H-` = HumanOperator)

---

## 1. OrchestratorAgent

The OrchestratorAgent is the top of the Hierarchical Swarm. It manages cycle lifecycle, fans out tasks to sub-agents, and enforces the human approval gate.

### O-001 — Scheduled Cycle Trigger
As an OrchestratorAgent, I need to receive a scheduled trigger so that I can initiate a new research-and-publish cycle automatically.

**Acceptance Criteria:**
- Given a cron trigger fires, the Orchestrator creates a new `CycleRecord` in the database with `status=RUNNING` within 5 seconds
- The `CycleRecord` captures `trigger_type=SCHEDULED`, `agent_id`, and `started_at` timestamp
- If a cycle is already `RUNNING` for this agent, the new trigger is logged and discarded (no duplicate cycles)

### O-002 — Manual Cycle Trigger
As an OrchestratorAgent, I need to accept a manual trigger via the REST API so that an operator can start a cycle on demand.

**Acceptance Criteria:**
- `POST /cycles` with valid `agent_id` and `trigger_type=MANUAL` creates a `CycleRecord` with `status=RUNNING` and returns HTTP 201 with `cycle_id`
- `POST /cycles` when a cycle is already `RUNNING` for the same agent returns HTTP 409 Conflict

### O-003 — Parallel Fan-out to Sub-agents
As an OrchestratorAgent, I need to fan out TrendResearch and EngagementPoll tasks in parallel so that both complete within the cycle SLA.

**Acceptance Criteria:**
- TrendResearchAgent and EngagementAgent are invoked concurrently (not sequentially) using `StructuredTaskScope`
- If either sub-agent throws an unrecoverable exception, the Orchestrator marks the `CycleRecord` as `status=FAILED`, logs the error, and sends an alert
- Total time for the parallel phase must not exceed the configured `cycle.research.timeout` (default 60 seconds)

### O-004 — Human Approval Gate Enforcement
As an OrchestratorAgent, I need to prevent PublicationAgent from receiving any draft with `status != APPROVED` so that no content reaches external platforms without human sign-off.

**Acceptance Criteria:**
- PublicationAgent rejects (throws `IllegalStateException`) any `ContentDraft` not in `APPROVED` status
- Orchestrator logs and alerts on any attempt to pass a non-APPROVED draft to PublicationAgent
- The approval gate logic is covered by a unit test that asserts `PENDING_REVIEW` and `REJECTED` drafts are never forwarded

### O-005 — Cycle Completion
As an OrchestratorAgent, I need to mark a cycle as completed when all sub-agent tasks finish successfully so that the operator can track throughput.

**Acceptance Criteria:**
- When TrendResearch, ContentGeneration, and Publication all complete without error, `CycleRecord.status` is updated to `COMPLETED` and `completed_at` is set
- Cycle duration (completed_at − started_at) is logged to `agent_logs`

### O-006 — Agent Status Broadcast
As an OrchestratorAgent, I need to update the agent's status in the database after each phase so that the heartbeat endpoint reflects real-time state.

**Acceptance Criteria:**
- `INFLUENCER_AGENT.status` is updated to `GENERATING` when ContentGenerationAgent starts, `POSTING` when PublicationAgent starts, `IDLE` when the cycle completes or fails
- Status transitions are logged to `agent_logs`

---

## 2. TrendResearchAgent

The TrendResearchAgent fetches, scores, and stores trending topics that will drive content generation.

### T-001 — Fetch Trending Keywords
As a TrendResearchAgent, I need to fetch trending keywords from a platform trend API so that I can surface relevant topics for content generation.

**Acceptance Criteria:**
- Given a valid API key in the environment, the agent returns a list of at least 10 `TrendRecord` objects within 10 seconds
- Each `TrendRecord` has non-null fields: `id` (uuid), `keyword` (string), `score` (float 0.0–1.0), `source` (string), `fetched_at` (timestamp), `cycle_id` (uuid)
- If the trend API returns an error, the agent retries with exponential backoff (max 3 attempts, base 1s). After 3 failures, it throws `ChimeraAgentException` with the root cause

### T-002 — Store Trends with Confidence Score
As a TrendResearchAgent, I need to persist fetched trends to the database with a confidence score so that the ContentGenerationAgent can prioritize high-signal topics.

**Acceptance Criteria:**
- All fetched `TrendRecord`s are batch-inserted into the `trend` table in a single transaction
- `score` reflects the platform's trend velocity metric, normalized to 0.0–1.0
- Duplicate keywords within the same cycle are deduplicated (same keyword + same `cycle_id` = one record, higher score wins)

### T-003 — Rank and Surface Top Trends
As a TrendResearchAgent, I need to return the top N trends to the Orchestrator ranked by score so that ContentGenerationAgent works on the most impactful topics first.

**Acceptance Criteria:**
- The Orchestrator receives a list sorted descending by `score`
- `N` is configurable via `agent.trend.top_n` (default: 3)
- The ranking logic is covered by a unit test with a known fixture dataset

---

## 3. ContentGenerationAgent

The ContentGenerationAgent takes a trend and produces a draft ready for human review.

### C-001 — Generate ContentDraft from TrendRecord
As a ContentGenerationAgent, I need to receive a `TrendRecord` and produce a `ContentDraft` so that there is content ready for human review.

**Acceptance Criteria:**
- Given a valid `TrendRecord`, the agent returns a `ContentDraft` with non-null: `id`, `agent_id`, `trend_id`, `title`, `script`, `hashtags`, `platform_metadata`, `status=PENDING_REVIEW`, `confidence_score`, `created_at`
- `script` is at most 500 words
- `hashtags` contains at most 30 entries
- `platform_metadata` is valid JSON with at least `platform` and `video_duration_seconds` fields

### C-002 — LLM Model Tiering
As a ContentGenerationAgent, I need to use the appropriate LLM tier for each sub-task so that cost is controlled without sacrificing quality.

**Acceptance Criteria:**
- Hashtag generation and trend classification use `claude-haiku-4-5` (or equivalent cheap model)
- Script drafting uses `claude-sonnet-4-6`
- Final content quality review and confidence scoring use `claude-opus-4-6`
- Model used is logged in `agent_logs` for every LLM call

### C-003 — Confidence Scoring
As a ContentGenerationAgent, I need to attach a confidence score to each `ContentDraft` so that low-confidence drafts can be flagged for extra attention during review.

**Acceptance Criteria:**
- `confidence_score` is a float 0.0–1.0 derived from a self-evaluation LLM prompt that assesses relevance, originality, and platform fit
- Drafts with `confidence_score < 0.6` have `status=NEEDS_REVIEW` (not `PENDING_REVIEW`)
- The confidence scoring prompt is a documented constant in the codebase

### C-004 — Store Draft for Human Review
As a ContentGenerationAgent, I need to persist the `ContentDraft` to the database immediately after generation so that the human operator is notified without delay.

**Acceptance Criteria:**
- The draft is inserted with `status=PENDING_REVIEW` or `NEEDS_REVIEW` in a single transaction
- The webhook notification (O-004 dependency) fires within 60 seconds of the insert
- Draft insert and webhook dispatch are logged to `agent_logs`

### C-005 — LLM Call Audit Trail
As a ContentGenerationAgent, I need every LLM call to be logged with model, prompt hash, token count, and estimated cost so that the operator can audit and optimize spend.

**Acceptance Criteria:**
- Every call to the LLM client produces an `AGENT_LOG` entry with: `llm_model`, `prompt_hash` (SHA-256 of the prompt), `token_count`, `cost_usd` (calculated from model pricing)
- Log entries are written even when the LLM call fails (log the error, model, and partial token info if available)

---

## 4. PublicationAgent

The PublicationAgent consumes APPROVED drafts and publishes them to external platforms.

### P-001 — Publish to Video Platform
As a PublicationAgent, I need to publish an APPROVED `ContentDraft` to the target video platform so that the influencer's content reaches its audience.

**Acceptance Criteria:**
- Given `ContentDraft.status=APPROVED`, the agent calls the platform API, receives an `external_id`, and stores a `Publication` record with `published_at` timestamp and `platform`
- If the platform API returns an error, the agent retries with exponential backoff (max 3 attempts). After 3 failures, `Publication` is stored with `status=FAILED` and the operator is alerted
- PublicationAgent throws `IllegalStateException` if called with any draft status other than `APPROVED`

### P-002 — Publish to Moltbook
As a PublicationAgent, I need to post a structured summary to Moltbook after each successful video platform publication so that the agent's activity is visible on the agent social network.

**Acceptance Criteria:**
- After a successful video platform publication, a Moltbook post is created with: `agent_id`, `content_type=VIDEO_SUMMARY`, `title`, `platform`, `external_url`, `hashtags`, `published_at`
- Moltbook HTTP 200 response is confirmed within 30 seconds
- Failure to post to Moltbook does NOT block the video platform publication (Moltbook post is best-effort; failure is logged but not retried more than once)

### P-003 — Publication Record
As a PublicationAgent, I need to store a `Publication` record after every publication attempt so that the operator can track what was published and where.

**Acceptance Criteria:**
- `Publication` record is inserted regardless of success or failure (success: `status=PUBLISHED`; failure: `status=FAILED`)
- Record includes: `draft_id`, `platform`, `external_id` (null on failure), `published_at`, initial `engagement_metrics` as `{}`

---

## 5. EngagementAgent

The EngagementAgent monitors published content for audience interactions and prepares reply drafts.

### E-001 — Poll Platform for Comments
As an EngagementAgent, I need to monitor comments on published content so that I can identify engagement opportunities.

**Acceptance Criteria:**
- Agent polls the platform API for new comments on each active `Publication` at the configured interval (default: every 1 hour)
- New comments are stored as `EngagementEvent` records with: `publication_id`, `event_type=COMMENT`, `external_user_id`, `content`, `received_at`
- Duplicate comments (same `external_user_id` + `content` + `publication_id`) are deduplicated

### E-002 — Draft Replies to Comments
As an EngagementAgent, I need to draft replies to prioritized comments so that the human operator can review and approve before posting.

**Acceptance Criteria:**
- For each new `EngagementEvent` of type `COMMENT`, the agent generates a reply draft using the LLM (model: Haiku for routine replies, Sonnet for high-engagement replies)
- The reply draft is stored as a new `ContentDraft` with `status=PENDING_REVIEW` and linked via `EngagementEvent.reply_draft_id`
- Human approval is required before any reply is posted (same approval flow as content drafts, see H-001/H-002)

### E-003 — Engagement Metrics Refresh
As an EngagementAgent, I need to refresh `engagement_metrics` on `Publication` records periodically so that the operator can track performance.

**Acceptance Criteria:**
- `Publication.engagement_metrics` JSONB is updated with current `views`, `likes`, `comments` counts at the configured refresh interval (default: every 4 hours)
- Metric updates are logged to `agent_logs` with before/after values

---

## 6. HumanOperator

The HumanOperator stories define the interface between the autonomous system and the human in the loop.

### H-001 — Receive Review Notification
As a HumanOperator, I need to receive a notification when a `ContentDraft` is ready for review so that I can approve or reject it in a timely manner.

**Acceptance Criteria:**
- A webhook fires within 60 seconds of `ContentDraft.status` being set to `PENDING_REVIEW` or `NEEDS_REVIEW`
- Notification payload includes: `draft_id`, `title`, `confidence_score`, `trend_keyword`, `status`, `created_at`
- Webhook destination is configurable via `notification.webhook_url` environment variable
- `NEEDS_REVIEW` drafts include a `low_confidence_flag: true` field in the payload

### H-002 — Approve or Reject a Draft
As a HumanOperator, I need to approve or reject a `ContentDraft` via the API so that the system has a machine-readable record of my decision.

**Acceptance Criteria:**
- `PATCH /drafts/{id}` with body `{"status": "APPROVED", "notes": "string"}` updates `ContentDraft.status` to `APPROVED`, sets `approved_at`, and returns HTTP 200
- `PATCH /drafts/{id}` with body `{"status": "REJECTED", "notes": "string"}` updates status to `REJECTED` and returns HTTP 200
- Any status other than `APPROVED` or `REJECTED` in the request body returns HTTP 400
- After approval, PublicationAgent is notified (poll-based; draft becomes available in the next publication poll cycle, max delay = poll interval)
- The operator's `notes` field is required for `REJECTED` status and optional for `APPROVED`

### H-003 — Draft Timeout Auto-Rejection
As a HumanOperator, I need drafts to be automatically rejected if I don't respond within the configured TTL so that stale drafts don't accumulate and block the pipeline.

**Acceptance Criteria:**
- If a `ContentDraft` remains in `PENDING_REVIEW` or `NEEDS_REVIEW` status for longer than `review.timeout_hours` (default: 24 hours), a scheduled job sets `status=REJECTED` with `notes="Auto-rejected: review timeout exceeded"`
- A notification is sent to the operator when auto-rejection occurs
- Auto-rejected drafts are excluded from publication but remain in the database for audit purposes

### H-004 — View Publication History
As a HumanOperator, I need to retrieve a list of publications so that I can track what content has been published and monitor its performance.

**Acceptance Criteria:**
- `GET /publications` returns a paginated list of `Publication` records ordered by `published_at` descending
- Each record includes: `id`, `draft_id`, `platform`, `external_id`, `published_at`, `engagement_metrics`
- Supports filtering by `platform` and `agent_id` query parameters
- Default page size: 20; max page size: 100
