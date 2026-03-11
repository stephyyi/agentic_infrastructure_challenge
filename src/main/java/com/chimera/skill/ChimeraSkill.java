package com.chimera.skill;

/**
 * Core contract for all Chimera agent runtime skills.
 *
 * <p>A "Skill" is a specific capability package that the Chimera agent can invoke
 * as an atomic unit of work. Skills are the runtime tool layer — separate from the
 * developer MCP servers documented in research/tooling_strategy.md.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Each skill has a single, well-defined Input type and Output type (both Records).</li>
 *   <li>Skills are stateless — all state lives in the input record.</li>
 *   <li>Skills throw {@link SkillExecutionException} on external failures.</li>
 *   <li>Skills throw {@link BudgetExceededException} when the LLM cost budget is exceeded.</li>
 *   <li>Skills are invoked by Workers inside a {@code StructuredTaskScope} — they must
 *       be safe to cancel (no open resources after the scope shuts down).</li>
 * </ul>
 *
 * <p>Implemented skills (see skills/ directory for I/O contracts):
 * <ul>
 *   <li>{@code skill_trend_fetcher} — fetches and scores trending keywords</li>
 *   <li>{@code skill_content_generator} — runs multi-tier LLM pipeline to produce a ContentDraft</li>
 * </ul>
 *
 * @param <I> immutable input record type
 * @param <O> immutable output record type
 */
public interface ChimeraSkill<I, O> {

    /**
     * Executes the skill with the provided input.
     *
     * <p>Implementations must:
     * <ol>
     *   <li>Validate all input fields before making any external call.</li>
     *   <li>Apply exponential backoff (max 3 attempts, base 1s) on transient failures.</li>
     *   <li>Throw {@link BudgetExceededException} before exceeding the cost limit.</li>
     *   <li>Log every LLM call to agent_logs (model, prompt_hash, token_count, cost_usd).</li>
     * </ol>
     *
     * @param input immutable skill input
     * @return immutable skill output
     * @throws SkillExecutionException if the skill fails after all retries
     * @throws BudgetExceededException if the skill would exceed its budget
     */
    O execute(I input) throws SkillExecutionException, BudgetExceededException;

    /**
     * Returns the canonical name of this skill (e.g., "skill_trend_fetcher").
     * Used for logging, telemetry, and skill registry lookups.
     *
     * @return skill name
     */
    String skillName();
}
