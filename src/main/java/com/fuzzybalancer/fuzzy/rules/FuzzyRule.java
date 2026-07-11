package com.fuzzybalancer.fuzzy.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FuzzyRule — Represents a single IF-THEN fuzzy rule.
 *
 * Each rule encodes expert knowledge in the form:
 *   IF (cpu is <cpuSet>) AND (ram is <ramSet>) AND
 *      (requests is <requestsSet>) AND (response is <responseSet>)
 *   THEN (priority is <outputSet>)
 *
 * Rule Components:
 *   - Antecedents (IF part): CPU, RAM, Requests, Response conditions
 *   - Consequent (THEN part): Priority output set
 *
 * Fuzzy AND operation:
 *   The activation strength of a rule = MIN of all antecedent memberships.
 *   This is the Mamdani fuzzy inference method.
 *
 * Example — Rule 1:
 *   IF CPU is LOW AND RAM is LOW AND REQUESTS is LOW AND RESPONSE is FAST
 *   THEN PRIORITY is VERY_HIGH
 *
 *   For CPU=10, RAM=20, Req=5, RT=50ms:
 *   μ(CPU=LOW) = 1.0, μ(RAM=LOW) = 1.0, μ(REQ=LOW) = 1.0, μ(RT=FAST) = 1.0
 *   Rule Activation = MIN(1.0, 1.0, 1.0, 1.0) = 1.0
 *   → Priority is VERY_HIGH with strength 1.0
 *
 * Enum constants are used for set names to prevent typos and enable
 * exhaustive switch expressions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuzzyRule {

    /** Unique rule identifier for logging and debugging. */
    private int ruleId;

    /** Human-readable description of the rule's intent. */
    private String description;

    // =========================================================================
    // ANTECEDENT CONDITIONS (IF part)
    // =========================================================================

    /** Required CPU set for this rule to fire. */
    private CpuSet cpuCondition;

    /** Required RAM set for this rule to fire. */
    private RamSet ramCondition;

    /** Required Requests set for this rule to fire. */
    private RequestsSet requestsCondition;

    /** Required Response Time set for this rule to fire. */
    private ResponseSet responseCondition;

    // =========================================================================
    // CONSEQUENT (THEN part)
    // =========================================================================

    /** The output priority set if this rule fires. */
    private PrioritySet priorityOutput;

    /**
     * Weight — How strongly this rule's conclusion should influence the output.
     * Default is 1.0. Rules with higher certainty can have weight > 1.
     * Not commonly used in standard Mamdani inference, but useful for tuning.
     */
    @Builder.Default
    private double weight = 1.0;

    // =========================================================================
    // ENUM DEFINITIONS
    // =========================================================================

    public enum CpuSet { LOW, MEDIUM, HIGH }
    public enum RamSet { LOW, MEDIUM, HIGH }
    public enum RequestsSet { LOW, MEDIUM, HIGH }
    public enum ResponseSet { FAST, NORMAL, SLOW }
    public enum PrioritySet { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }

    /**
     * toString() — Human-readable rule representation for logging.
     * Example: "Rule 1: IF CPU=LOW AND RAM=LOW AND REQ=LOW AND RT=FAST THEN PRIORITY=VERY_HIGH"
     */
    @Override
    public String toString() {
        return String.format(
            "Rule %d: IF CPU=%s AND RAM=%s AND REQUESTS=%s AND RESPONSE=%s THEN PRIORITY=%s [weight=%.1f]",
            ruleId, cpuCondition, ramCondition, requestsCondition, responseCondition, priorityOutput, weight
        );
    }
}
