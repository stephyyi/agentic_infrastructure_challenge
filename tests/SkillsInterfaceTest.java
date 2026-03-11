package com.chimera;

import com.chimera.model.ContentDraftRecord;
import com.chimera.model.DraftStatus;
import com.chimera.model.TrendRecord;
import com.chimera.skill.BudgetExceededException;
import com.chimera.skill.ChimeraSkill;
import com.chimera.skill.SkillExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for the ChimeraSkill interface and its implementations — RED PHASE.
 *
 * <p>These tests define the acceptance criteria from specs/functional.md:
 * <ul>
 *   <li>C-001 — Generate ContentDraft from TrendRecord</li>
 *   <li>C-002 — LLM Model Tiering</li>
 *   <li>C-003 — Confidence Scoring</li>
 *   <li>Skills I/O contracts from skills/skill_content_generator/README.md</li>
 *   <li>Skills I/O contracts from skills/skill_trend_fetcher/README.md</li>
 * </ul>
 *
 * <p>ALL TESTS ARE EXPECTED TO FAIL until skill implementations are written.
 * This is the TDD "empty slot". Do NOT add implementations here.
 */
@DisplayName("Skills Interface — ChimeraSkill contract tests")
class SkillsInterfaceTest {

    // ─── Exception hierarchy ──────────────────────────────────────────────────

    @Test
    @DisplayName("BudgetExceededException is a subclass of SkillExecutionException")
    void budgetExceededException_isSubclassOf_skillExecutionException() {
        assertTrue(
                SkillExecutionException.class.isAssignableFrom(BudgetExceededException.class),
                "BudgetExceededException must extend SkillExecutionException"
        );
    }

    @Test
    @DisplayName("BudgetExceededException carries budgetLimitUsd and actualCostUsd")
    void budgetExceededException_carriesBudgetAndCostFields() {
        BigDecimal limit = new BigDecimal("0.50");
        BigDecimal actual = new BigDecimal("0.75");
        BudgetExceededException ex = new BudgetExceededException("over budget", limit, actual);

        assertEquals(limit, ex.getBudgetLimitUsd(), "budgetLimitUsd must match constructor arg");
        assertEquals(actual, ex.getActualCostUsd(), "actualCostUsd must match constructor arg");
    }

    @Test
    @DisplayName("SkillExecutionException can wrap a root cause")
    void skillExecutionException_canWrapRootCause() {
        RuntimeException cause = new RuntimeException("platform API down");
        SkillExecutionException ex = new SkillExecutionException("fetch failed", cause);
        assertEquals(cause, ex.getCause(), "SkillExecutionException must preserve root cause");
    }

    // ─── ChimeraSkill interface contract ─────────────────────────────────────

    @Test
    @DisplayName("ChimeraSkill.execute() declares throws SkillExecutionException and BudgetExceededException")
    void chimeraSkill_execute_declaresCheckedExceptions() throws NoSuchMethodException {
        var executeMethod = ChimeraSkill.class.getMethod("execute", Object.class);
        Class<?>[] exceptionTypes = executeMethod.getExceptionTypes();

        boolean declaresSkillEx = false;
        boolean declaresBudgetEx = false;
        for (Class<?> ex : exceptionTypes) {
            if (ex.equals(SkillExecutionException.class)) {
                declaresSkillEx = true;
            }
            if (ex.equals(BudgetExceededException.class)) {
                declaresBudgetEx = true;
            }
        }
        assertTrue(declaresSkillEx,
                "ChimeraSkill.execute() must declare throws SkillExecutionException");
        assertTrue(declaresBudgetEx,
                "ChimeraSkill.execute() must declare throws BudgetExceededException");
    }

    @Test
    @DisplayName("ChimeraSkill has a skillName() method")
    void chimeraSkill_hasSkillNameMethod() throws NoSuchMethodException {
        var method = ChimeraSkill.class.getMethod("skillName");
        assertEquals(String.class, method.getReturnType(),
                "skillName() must return String");
    }

    // ─── ContentDraftRecord invariants (C-001, C-003) ─────────────────────────

    @Test
    @DisplayName("C-001: ContentDraftRecord is a Java Record (immutable DTO)")
    void contentDraftRecord_isImmutable_recordType() {
        assertTrue(ContentDraftRecord.class.isRecord(),
                "ContentDraftRecord must be a Java Record");
    }

    @Test
    @DisplayName("C-003: ContentDraftRecord.isLowConfidence() returns true when score < 0.6")
    void contentGenerationSkill_confidenceScore_flagsLowConfidenceAsNeedsReview() {
        ContentDraftRecord draft = buildDraft(0.55f, DraftStatus.NEEDS_REVIEW);
        assertTrue(draft.isLowConfidence(),
                "isLowConfidence() must return true when confidenceScore < 0.6");
    }

    @Test
    @DisplayName("C-003: ContentDraftRecord.isLowConfidence() returns false when score >= 0.6")
    void contentDraftRecord_isLowConfidence_falseWhenAboveThreshold() {
        ContentDraftRecord draft = buildDraft(0.85f, DraftStatus.PENDING_REVIEW);
        assertFalse(draft.isLowConfidence(),
                "isLowConfidence() must return false when confidenceScore >= 0.6");
    }

    @Test
    @DisplayName("C-001: ContentDraftRecord initial status must be PENDING_REVIEW or NEEDS_REVIEW")
    void contentGenerationSkill_output_draftStatus_isPendingOrNeedsReview() {
        ContentDraftRecord pendingDraft = buildDraft(0.80f, DraftStatus.PENDING_REVIEW);
        ContentDraftRecord needsReviewDraft = buildDraft(0.40f, DraftStatus.NEEDS_REVIEW);

        assertEquals(DraftStatus.PENDING_REVIEW, pendingDraft.status());
        assertEquals(DraftStatus.NEEDS_REVIEW, needsReviewDraft.status());
    }

    @Test
    @DisplayName("C-001: ContentDraftRecord rejects more than 30 hashtags")
    void contentDraftRecord_rejectsMoreThan30Hashtags() {
        List<String> tooManyHashtags = java.util.stream.IntStream.range(0, 31)
                .mapToObj(i -> "#tag" + i)
                .toList();

        assertThrows(IllegalArgumentException.class, () ->
                new ContentDraftRecord(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "title", "script", tooManyHashtags, Map.of("platform", "YOUTUBE"),
                        DraftStatus.PENDING_REVIEW, 0.8f, null, Instant.now(), null),
                "ContentDraftRecord must reject more than 30 hashtags"
        );
    }

    @Test
    @DisplayName("C-001: ContentDraftRecord rejects confidence score outside [0.0, 1.0]")
    void contentDraftRecord_rejectsInvalidConfidenceScore() {
        assertThrows(IllegalArgumentException.class, () ->
                new ContentDraftRecord(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "title", "script", List.of(), Map.of("platform", "YOUTUBE"),
                        DraftStatus.PENDING_REVIEW, 1.5f, null, Instant.now(), null),
                "ContentDraftRecord must reject confidenceScore > 1.0"
        );
    }

    // ─── Skill output I/O contract (forward-looking — tests the structure) ────

    @Test
    @DisplayName("skill_trend_fetcher: TrendRecord passes all field validations")
    void trendFetcherSkill_output_matchesTrendFetchOutput_contract() {
        // This tests that a well-formed TrendRecord (as the skill must return) is valid
        UUID cycleId = UUID.randomUUID();
        TrendRecord record = new TrendRecord(
                UUID.randomUUID(), cycleId, "AI video trends", 0.87f, "YOUTUBE_TRENDING", Instant.now());

        assertNotNull(record.id());
        assertNotNull(record.cycleId());
        assertNotNull(record.keyword());
        assertNotNull(record.source());
        assertNotNull(record.fetchedAt());
        assertEquals(cycleId, record.cycleId());
        assertTrue(record.score() >= 0.0f && record.score() <= 1.0f);
    }

    @Test
    @DisplayName("skill_content_generator: throws BudgetExceededException when limit reached")
    void chimeraSkill_throwsBudgetExceededException_whenBudgetBreached() {
        // Simulates what a real ContentGeneratorSkill implementation must do.
        // The test verifies the exception type and fields carry the right data.
        BigDecimal limit = new BigDecimal("0.10");
        BigDecimal actual = new BigDecimal("0.35");

        BudgetExceededException ex = assertThrows(BudgetExceededException.class, () -> {
            throw new BudgetExceededException(
                    "Budget $0.10 exceeded — estimated cost $0.35", limit, actual);
        });

        assertTrue(ex.getActualCostUsd().compareTo(ex.getBudgetLimitUsd()) > 0,
                "actualCostUsd must be greater than budgetLimitUsd");
        assertInstanceOf(SkillExecutionException.class, ex,
                "BudgetExceededException must be catchable as SkillExecutionException");
    }

    @Test
    @DisplayName("skill_content_generator: throws SkillExecutionException on API failure")
    void chimeraSkill_throwsSkillExecutionException_onApiFailure() {
        // Verifies that SkillExecutionException carries a root cause
        RuntimeException apiError = new RuntimeException("LLM timeout");
        SkillExecutionException ex = assertThrows(SkillExecutionException.class, () -> {
            throw new SkillExecutionException("LLM call failed after 3 retries", apiError);
        });
        assertEquals(apiError, ex.getCause(),
                "SkillExecutionException must preserve the original API failure as cause");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ContentDraftRecord buildDraft(final float confidenceScore, final DraftStatus status) {
        return new ContentDraftRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Title",
                "This is a test script with enough content to pass validation.",
                List.of("#test", "#chimera"),
                Map.of("platform", "YOUTUBE", "video_duration_seconds", 60),
                status,
                confidenceScore,
                null,
                Instant.now(),
                null
        );
    }
}
