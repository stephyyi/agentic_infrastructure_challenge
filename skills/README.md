# Project Chimera — Skills System

## What Is a Skill?

A **Skill** is a specific, self-contained capability package that the Chimera agent can invoke as an atomic unit of work at runtime. Skills are the runtime tool layer — they are distinct from the developer MCP servers documented in `research/tooling_strategy.md`.

| Layer | Purpose | Who uses it |
|---|---|---|
| **Developer MCPs** | Help the human developer build and inspect the system (git, filesystem, database) | Human + IDE AI agent |
| **Runtime Skills** | Power the Chimera agent's autonomous capabilities at runtime | Chimera Workers at runtime |

Skills are invoked by Worker agents inside a `StructuredTaskScope` (Java 21). Each skill is:
- **Stateless** — all state is passed in the input record
- **Typed** — strict `Input` and `Output` Java Records define the contract
- **Fault-tolerant** — each skill applies exponential backoff internally before throwing
- **Auditable** — every LLM call inside a skill produces an `AGENT_LOG` entry

---

## Skill Interface

All skills implement `com.chimera.skill.ChimeraSkill<I, O>`:

```java
public interface ChimeraSkill<I, O> {
    O execute(I input) throws SkillExecutionException, BudgetExceededException;
    String skillName();
}
```

---

## Available Skills

| Skill | Directory | Status |
|---|---|---|
| `skill_trend_fetcher` | `skills/skill_trend_fetcher/` | Interface defined — implementation pending (TDD red) |
| `skill_content_generator` | `skills/skill_content_generator/` | Interface defined — implementation pending (TDD red) |

---

## Adding a New Skill

1. Create a directory: `skills/skill_<name>/`
2. Write `skills/skill_<name>/README.md` with the full Input/Output contract
3. Create the Java input and output Records in `src/main/java/com/chimera/skill/`
4. Implement `ChimeraSkill<Input, Output>` in `src/main/java/com/chimera/skill/`
5. Write failing JUnit 5 tests in `tests/` before implementing
6. Reference the spec story ID that this skill satisfies in all Javadoc

---

## Error Budget

All skills share the retry policy from `specs/_meta.md`:
- Max 3 attempts
- Base delay 1 second
- Multiplier 2× (1s → 2s → 4s)
- After 3 failures: throw `SkillExecutionException`
- If cost budget exceeded before execution: throw `BudgetExceededException`
