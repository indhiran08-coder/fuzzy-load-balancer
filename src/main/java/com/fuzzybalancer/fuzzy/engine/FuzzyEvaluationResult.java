package com.fuzzybalancer.fuzzy.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * FuzzyEvaluationResult — Captures the complete result of one fuzzy inference cycle.
 *
 * Returned by FuzzyRuleEngine.evaluate() for each server evaluation.
 *
 * Contains:
 *   1. crispScore        — The final defuzzified priority (0–100)
 *   2. cpuMemberships    — Fuzzified CPU values (LOW/MED/HIGH degrees)
 *   3. ramMemberships    — Fuzzified RAM values
 *   4. requestMemberships— Fuzzified request count values
 *   5. responseMemberships— Fuzzified response time values
 *   6. outputActivations — Aggregated output activation per priority set
 *   7. rulesEvaluated    — How many rules were in the rule base
 *
 * This transparency is valuable for:
 *   - Debugging: Why did Server-A get a higher score than Server-B?
 *   - Audit logs: What was the fuzzy state when this decision was made?
 *   - UI display: Show users the fuzzy breakdown on the dashboard
 *   - Testing: Verify membership degrees against expected values
 *
 * Example JSON output:
 * {
 *   "crispScore": 72.4,
 *   "cpuMemberships": {"LOW": 0.0, "MEDIUM": 0.83, "HIGH": 0.25},
 *   "ramMemberships": {"LOW": 1.0, "MEDIUM": 0.33, "HIGH": 0.0},
 *   "requestMemberships": {"LOW": 0.87, "MEDIUM": 0.13, "HIGH": 0.0},
 *   "responseMemberships": {"FAST": 0.5, "NORMAL": 0.5, "SLOW": 0.0},
 *   "outputActivations": {"VERY_HIGH": 0.0, "HIGH": 0.5, "MEDIUM": 0.25, ...},
 *   "rulesEvaluated": 25
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuzzyEvaluationResult {

    /** The final crisp priority score (0–100). Higher = better candidate. */
    private double crispScore;

    /** Membership degrees for each CPU fuzzy set. Keys: LOW, MEDIUM, HIGH. */
    private Map<String, Double> cpuMemberships;

    /** Membership degrees for each RAM fuzzy set. Keys: LOW, MEDIUM, HIGH. */
    private Map<String, Double> ramMemberships;

    /** Membership degrees for each request count fuzzy set. Keys: LOW, MEDIUM, HIGH. */
    private Map<String, Double> requestMemberships;

    /** Membership degrees for each response time fuzzy set. Keys: FAST, NORMAL, SLOW. */
    private Map<String, Double> responseMemberships;

    /** Aggregated activation strength per output priority set. Keys: VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH. */
    private Map<String, Double> outputActivations;

    /** Total rules evaluated (should equal FuzzyRuleBase.getRuleCount()). */
    private int rulesEvaluated;

    /**
     * getPriorityLabel() — Returns a human-readable label for the crisp score.
     *
     * Used in decision logs and dashboard display.
     *
     * @return "VERY_HIGH", "HIGH", "MEDIUM", "LOW", or "VERY_LOW"
     */
    public String getPriorityLabel() {
        if (crispScore >= 80) return "VERY_HIGH";
        if (crispScore >= 60) return "HIGH";
        if (crispScore >= 40) return "MEDIUM";
        if (crispScore >= 20) return "LOW";
        return "VERY_LOW";
    }

    /**
     * getDominantCpuSet() — Returns the CPU fuzzy set with the highest membership.
     * Example: "MEDIUM" if cpuMemberships = {LOW: 0.0, MEDIUM: 0.83, HIGH: 0.17}
     */
    public String getDominantCpuSet() {
        return getDominantSet(cpuMemberships);
    }

    public String getDominantRamSet() {
        return getDominantSet(ramMemberships);
    }

    public String getDominantRequestSet() {
        return getDominantSet(requestMemberships);
    }

    public String getDominantResponseSet() {
        return getDominantSet(responseMemberships);
    }

    private String getDominantSet(Map<String, Double> memberships) {
        if (memberships == null || memberships.isEmpty()) return "UNKNOWN";
        return memberships.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }
}
