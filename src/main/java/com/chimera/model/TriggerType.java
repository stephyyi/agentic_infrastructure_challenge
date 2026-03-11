package com.chimera.model;

/**
 * How a CycleRecord was initiated (see specs/functional.md O-001, O-002).
 */
public enum TriggerType {

    /** Triggered automatically by the cron scheduler. */
    SCHEDULED,

    /** Triggered on-demand via POST /cycles (specs/technical.md). */
    MANUAL
}
