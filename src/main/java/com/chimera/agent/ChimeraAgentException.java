package com.chimera.agent;

/**
 * Unchecked exception thrown when an agent sub-task fails after all retry attempts.
 *
 * <p>Used by TrendFetcherService, ContentGenerationAgent, PublicationAgent, and
 * EngagementAgent to signal an unrecoverable failure to the OrchestratorAgent.
 *
 * <p>When thrown inside a {@code StructuredTaskScope.ShutdownOnFailure}, the scope
 * shuts down all sibling tasks and propagates this exception to the orchestrator,
 * which marks the CycleRecord as FAILED and alerts the operator.
 *
 * <p>See specs/technical.md Section 4 (Error Handling Contract) for full behavior.
 */
public class ChimeraAgentException extends RuntimeException {

    /**
     * Constructs a ChimeraAgentException with a detail message.
     *
     * @param message human-readable description of the failure
     */
    public ChimeraAgentException(final String message) {
        super(message);
    }

    /**
     * Constructs a ChimeraAgentException with a detail message and root cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying exception after retries were exhausted
     */
    public ChimeraAgentException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
