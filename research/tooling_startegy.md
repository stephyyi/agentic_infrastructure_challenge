# Project Chimera — Tooling Strategy

## Overview

Project Chimera uses two distinct tool layers that must never be confused:

| Layer | What it is | Who uses it | Lifecycle |
|---|---|---|---|
| **Developer MCPs** | MCP servers that help the human/IDE agent BUILD and INSPECT the system | Human developer + IDE AI co-pilot (e.g., Claude Code) | Development time only |
| **Runtime Skills** | Capability packages the Chimera agent invokes AUTONOMOUSLY at runtime | Chimera Worker agents inside `StructuredTaskScope` | Production runtime |

Mixing these two layers is a security and operational error. A runtime skill has access to live platform APIs and LLM budgets. A developer MCP has access to the local filesystem and git. They have different trust boundaries, different auth scopes, and different failure modes.

---

## Part A — Developer Tools: MCP Servers

MCP (Model Context Protocol) servers extend the IDE AI agent's capabilities during development. They allow Claude Code (or Cursor) to perform actions on the developer's behalf — committing code, querying the database, reading files — all while operating under the IDE's permission model.

### Selected MCP Servers

#### 1. `github-mcp`
**Purpose:** Git and GitHub operations from the IDE agent
**Operations:** commit, branch, create PR, list issues, read file history

```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "${GITHUB_TOKEN}"
      }
    }
  }
}
```

**Use cases for Chimera development:**
- Committing spec updates and code files with conventional commit messages
- Creating PRs for each Day 2 deliverable
- Reading commit history to understand what changed

**Security boundary:** `GITHUB_TOKEN` scoped to the Chimera repository only. Never the same token used by the runtime agent.

---

#### 2. `filesystem-mcp`
**Purpose:** Read and write project files from the IDE agent context
**Operations:** read file, write file, list directory, search content

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/llm_enginnering"]
    }
  }
}
```

**Use cases for Chimera development:**
- The IDE agent reads `specs/technical.md` before writing a new Java class
- The IDE agent writes generated test files to `tests/`
- Used by `make spec-check` to traverse the project tree

**Security boundary:** Scoped to the project root only. Never the entire filesystem.

---

#### 3. `postgres-mcp`
**Purpose:** Query and inspect the Chimera PostgreSQL database live during development
**Operations:** run SELECT queries, describe tables, inspect indexes

```json
{
  "mcpServers": {
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres", "${DATABASE_URL}"]
    }
  }
}
```

**Use cases for Chimera development:**
- Verify that the DDL from `specs/technical.md` was applied correctly
- Query `content_draft` and `agent_log` tables during debugging
- Check index usage on `trend` table for performance analysis

**Security boundary:** Read-only access to the dev/test database. Never production credentials.

---

#### 4. `anthropic-mcp` (Claude API)
**Purpose:** Call the Claude API from within the IDE agent for spec-to-code generation
**Operations:** message creation, model selection, streaming responses

```json
{
  "mcpServers": {
    "anthropic": {
      "command": "npx",
      "args": ["-y", "@anthropic-ai/mcp-server"],
      "env": {
        "ANTHROPIC_API_KEY": "${ANTHROPIC_API_KEY}"
      }
    }
  }
}
```

**Use cases for Chimera development:**
- IDE agent generates Java Record stubs from spec field definitions
- IDE agent writes Javadoc referencing story IDs from `specs/functional.md`
- IDE agent drafts failing JUnit 5 tests from acceptance criteria

**Security boundary:** Developer's personal API key. Separate key from runtime agent's `ANTHROPIC_API_KEY`.

---

### MCP Configuration Location

Add to Claude Code config (`~/.claude/settings.json`) or Cursor settings. Never commit `settings.json` to the repository — it contains API keys.

---

## Part B — Agent Runtime Skills

Runtime Skills are capability packages invoked by Chimera Worker agents during autonomous operation. They are defined in the `skills/` directory with explicit Input/Output contracts.

### Why Runtime Skills Are Separate from Developer MCPs

| Concern | Developer MCPs | Runtime Skills |
|---|---|---|
| Auth model | Developer's personal tokens (GitHub PAT, personal API key) | Service accounts, agent-specific API keys, budget-limited keys |
| Invoked by | Human or IDE AI agent interactively | Chimera Worker agents inside `StructuredTaskScope` autonomously |
| Failure consequence | Developer sees error message | Agent logs failure, retries, escalates to HITL if needed |
| Budget enforcement | None (developer pays manually) | Hard `BudgetExceededException` enforced before each LLM call |
| Security surface | Scoped to developer workstation | Scoped to deployed agent environment |

### Defined Runtime Skills

| Skill | Directory | Spec Stories |
|---|---|---|
| `skill_trend_fetcher` | `skills/skill_trend_fetcher/` | T-001, T-002, T-003 |
| `skill_content_generator` | `skills/skill_content_generator/` | C-001, C-002, C-003, C-005 |

For full Input/Output contracts, error handling, and LLM tier usage, see each skill's `README.md`.

### Skills Not Yet Defined (Day 3+)

| Planned Skill | Purpose | Spec Stories |
|---|---|---|
| `skill_video_publisher` | Upload video to YouTube / TikTok via MCP | P-001 |
| `skill_moltbook_poster` | Post summaries and engagement reports to Moltbook | P-002, E-003 |
| `skill_comment_replier` | Draft and post approved replies to platform comments | E-001, E-002 |
| `skill_injection_guard` | Sanitise inbound Moltbook messages (3-step pipeline) | Protocol 6 |
