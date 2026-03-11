package com.chimera.skill;

/**
 * Thrown when a Chimera skill fails to execute due to an external error
 * (API failure, timeout, invalid response, etc.).
 *
 * <p>This is a checked exception — callers must handle or declare it.
 * The retry policy (exponential backoff, max 3 attempts, base 1s) defined
 * in specs/_meta.md applies before this exception is thrown.
 *
 * <p>Subclass {@link BudgetExceededException} is thrown specifically when
 * the LLM token/cost budget for a skill invocation is exceeded.
 */
public class SkillExecutionException extends Exception {

    /**
     * Constructs a SkillExecutionException with a detail message.
     *
     * @param message human-readable description of the failure
     */
    public SkillExecutionException(final String message) {
        super(message);
    }

    /**
     * Constructs a SkillExecutionException with a detail message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying exception that caused this failure
     */
    public SkillExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
