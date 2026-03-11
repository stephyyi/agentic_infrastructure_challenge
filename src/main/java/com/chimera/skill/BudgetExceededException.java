package com.chimera.skill;

import java.math.BigDecimal;

/**
 * Thrown when a Chimera skill's execution would exceed the configured
 * LLM token or cost budget for a single invocation.
 *
 * <p>Extends {@link SkillExecutionException} — callers that catch the
 * parent can handle budget overruns uniformly, or catch this subclass
 * specifically to apply budget-aware retry logic (e.g., switch to a
 * cheaper model tier before retrying).
 *
 * <p>The CFO Judge sub-agent inspects this exception for financial
 * transactions — see specs/technical.md and specs/_meta.md.
 */
public class BudgetExceededException extends SkillExecutionException {

    /** The budget limit that was exceeded, in USD. */
    private final BigDecimal budgetLimitUsd;

    /** The actual cost that would have been incurred, in USD. */
    private final BigDecimal actualCostUsd;

    /**
     * Constructs a BudgetExceededException with budget and actual cost details.
     *
     * @param message        human-readable description
     * @param budgetLimitUsd the configured budget ceiling in USD
     * @param actualCostUsd  the cost that triggered this exception in USD
     */
    public BudgetExceededException(
            final String message,
            final BigDecimal budgetLimitUsd,
            final BigDecimal actualCostUsd) {
        super(message);
        this.budgetLimitUsd = budgetLimitUsd;
        this.actualCostUsd = actualCostUsd;
    }

    /**
     * Returns the budget limit that was exceeded.
     *
     * @return budget limit in USD
     */
    public BigDecimal getBudgetLimitUsd() {
        return budgetLimitUsd;
    }

    /**
     * Returns the actual cost that would have been incurred.
     *
     * @return actual cost in USD
     */
    public BigDecimal getActualCostUsd() {
        return actualCostUsd;
    }
}
