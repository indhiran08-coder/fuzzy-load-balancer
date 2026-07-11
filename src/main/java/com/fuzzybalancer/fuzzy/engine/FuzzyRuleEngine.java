package com.fuzzybalancer.fuzzy.engine;

import com.fuzzybalancer.fuzzy.membership.MembershipFunction;
import com.fuzzybalancer.fuzzy.rules.FuzzyRule;
import com.fuzzybalancer.fuzzy.rules.FuzzyRuleBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FuzzyRuleEngine — Implements the Mamdani Fuzzy Inference System.
 *
 * The complete fuzzy inference pipeline:
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    FUZZY INFERENCE PIPELINE                     │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                 │
 * │  Crisp Inputs (CPU=65, RAM=40, Req=30, RT=200ms)               │
 * │                    │                                            │
 * │                    ▼                                            │
 * │           ┌─ FUZZIFICATION ─┐                                  │
 * │           │ CPU:  LOW=0.0   │                                   │
 * │           │       MED=0.83  │                                   │
 * │           │       HIGH=0.25 │                                   │
 * │           │ RAM:  LOW=1.0   │                                   │
 * │           │       MED=0.33  │                                   │
 * │           │       HIGH=0.0  │                                   │
 * │           └────────┬────────┘                                   │
 * │                    │                                            │
 * │                    ▼                                            │
 * │           ┌─ RULE EVALUATION ─┐                                 │
 * │           │ Rule 1: MIN(0,1,1,1) = 0.0 → VH                   │
 * │           │ Rule 6: MIN(0.83,0.33,…) = 0.25 → HIGH            │
 * │           │ Rule 11: MIN(0.83,0.33,…) = 0.3 → MED             │
 * │           └────────┬──────────┘                                 │
 * │                    │                                            │
 * │                    ▼                                            │
 * │           ┌─ AGGREGATION ─┐                                     │
 * │           │ VeryHigh: 0.0 │ ← MAX of all rules firing VH      │
 * │           │ High:     0.25│ ← MAX of all rules firing HIGH     │
 * │           │ Medium:   0.3 │ ← MAX of all rules firing MED      │
 * │           │ Low:      0.0 │                                     │
 * │           │ VeryLow:  0.0 │                                     │
 * │           └────────┬──────┘                                     │
 * │                    │                                            │
 * │                    ▼                                            │
 * │           ┌─ DEFUZZIFICATION ─┐                                 │
 * │           │ Centroid of Gravity                                 │
 * │           │ Score = Σ(x * μ(x)) / Σ(μ(x))                     │
 * │           │ Result: 68.4 (out of 100)                           │
 * │           └────────┬──────────┘                                 │
 * │                    │                                            │
 * │                    ▼                                            │
 * │            Priority Score: 68.4                                 │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * @Component — Spring-managed singleton.
 * @RequiredArgsConstructor — Constructor injection for dependencies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FuzzyRuleEngine {

    private final FuzzyVariables vars;
    private final FuzzyRuleBase ruleBase;

    /** Number of points used in defuzzification integration (higher = more accurate) */
    private static final int DEFUZZ_POINTS = 200;

    /** Output universe range */
    private static final double OUTPUT_MIN = 0.0;
    private static final double OUTPUT_MAX = 100.0;

    /**
     * evaluate() — Main entry point: computes fuzzy priority score for a server.
     *
     * @param cpu         CPU usage percentage (0–100)
     * @param ram         RAM usage percentage (0–100)
     * @param requests    Active request count (0–200)
     * @param responseTime Response time in milliseconds (0–5000)
     * @return FuzzyEvaluationResult containing the score and intermediate values
     */
    public FuzzyEvaluationResult evaluate(
        double cpu,
        double ram,
        double requests,
        double responseTime
    ) {
        log.debug("Fuzzy evaluation: CPU={}, RAM={}, Req={}, RT={}ms", cpu, ram, requests, responseTime);

        // =====================================================================
        // STEP 1: FUZZIFICATION
        // Convert crisp inputs into membership degrees for each fuzzy set.
        // =====================================================================
        Map<String, Double> cpuMemberships = fuzzify(cpu, vars.getCpuMemberships());
        Map<String, Double> ramMemberships = fuzzify(ram, vars.getRamMemberships());
        Map<String, Double> requestMemberships = fuzzify(requests, vars.getRequestsMemberships());
        Map<String, Double> responseMemberships = fuzzify(responseTime, vars.getResponseMemberships());

        log.debug("CPU memberships: {}", cpuMemberships);
        log.debug("RAM memberships: {}", ramMemberships);
        log.debug("Request memberships: {}", requestMemberships);
        log.debug("Response memberships: {}", responseMemberships);

        // =====================================================================
        // STEP 2: RULE EVALUATION & AGGREGATION
        // For each rule: compute activation strength = MIN(antecedent memberships)
        // Aggregate: for each output set, take MAX of all activating rules.
        // This is the Mamdani (min-max) inference method.
        // =====================================================================
        Map<String, Double> outputActivations = new HashMap<>();
        // Initialize all output sets to 0
        outputActivations.put("VERY_LOW", 0.0);
        outputActivations.put("LOW", 0.0);
        outputActivations.put("MEDIUM", 0.0);
        outputActivations.put("HIGH", 0.0);
        outputActivations.put("VERY_HIGH", 0.0);

        List<FuzzyRule> rules = ruleBase.getRules();
        StringBuilder ruleLog = new StringBuilder();

        for (FuzzyRule rule : rules) {
            // Get the membership degree for each antecedent condition
            double cpuDegree = cpuMemberships.getOrDefault(rule.getCpuCondition().name(), 0.0);
            double ramDegree = ramMemberships.getOrDefault(rule.getRamCondition().name(), 0.0);
            double reqDegree = requestMemberships.getOrDefault(rule.getRequestsCondition().name(), 0.0);
            double rtDegree = responseMemberships.getOrDefault(rule.getResponseCondition().name(), 0.0);

            // AND operation = MIN of all antecedent degrees (Mamdani method)
            double ruleActivation = Math.min(Math.min(cpuDegree, ramDegree),
                                             Math.min(reqDegree, rtDegree));

            // Apply rule weight (scales the activation)
            ruleActivation *= rule.getWeight();

            // Only log rules that actually fire (activation > 0)
            if (ruleActivation > 0.001) {
                ruleLog.append(String.format("\n  Rule %d [%.3f]: %s → %s",
                    rule.getRuleId(), ruleActivation,
                    rule.getDescription(), rule.getPriorityOutput()));
            }

            // OR aggregation = MAX (keeps the highest activation for each output set)
            String outputKey = rule.getPriorityOutput().name();
            outputActivations.merge(outputKey, ruleActivation, Math::max);
        }

        log.debug("Fired rules:{}", ruleLog);
        log.debug("Output activations: {}", outputActivations);

        // =====================================================================
        // STEP 3: DEFUZZIFICATION — Centroid of Gravity (COG)
        // Convert the aggregated fuzzy output back to a crisp priority score.
        // =====================================================================
        double crispScore = defuzzify(outputActivations);
        log.debug("Defuzzified priority score: {}", crispScore);

        return FuzzyEvaluationResult.builder()
            .crispScore(crispScore)
            .cpuMemberships(cpuMemberships)
            .ramMemberships(ramMemberships)
            .requestMemberships(requestMemberships)
            .responseMemberships(responseMemberships)
            .outputActivations(outputActivations)
            .rulesEvaluated(rules.size())
            .build();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * fuzzify() — Computes membership degrees for a crisp value across all sets.
     *
     * Returns a map like: {"LOW": 0.0, "MEDIUM": 0.83, "HIGH": 0.25}
     * The values don't need to sum to 1.0 — they are independent degrees.
     *
     * @param crispValue The input value (e.g., CPU = 65)
     * @param memberships Map of set name → membership function
     * @return Map of set name → membership degree
     */
    private Map<String, Double> fuzzify(
        double crispValue,
        Map<String, MembershipFunction> memberships
    ) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, MembershipFunction> entry : memberships.entrySet()) {
            double degree = entry.getValue().getMembership(crispValue);
            result.put(entry.getKey(), degree);
        }
        return result;
    }

    /**
     * defuzzify() — Centroid of Gravity (CoG) defuzzification.
     *
     * The CoG method finds the center of mass of the aggregated output
     * membership function.
     *
     * Formula: x* = Σ[x * μ_agg(x)] / Σ[μ_agg(x)]
     *
     * where:
     *   x = points in the output universe [0, 100]
     *   μ_agg(x) = aggregated membership at point x
     *
     * Implementation:
     *   We discretize the output universe into DEFUZZ_POINTS points.
     *   At each point x, we compute the MAXIMUM of all output set memberships
     *   clipped by their activation strengths (Mamdani clipping).
     *   Then we compute the weighted average (centroid).
     *
     * Why CoG?
     *   CoG produces smooth, continuous outputs.
     *   It is the most widely used defuzzification method.
     *   Other methods (Mean of Maxima, Centroid of Sums) tend to be
     *   more sensitive to rule interactions.
     *
     * @param outputActivations Map of priority set name → activation strength
     * @return Crisp priority score in [0, 100]
     */
    private double defuzzify(Map<String, Double> outputActivations) {
        Map<String, MembershipFunction> priorityMFs = vars.getPriorityMemberships();

        double numerator = 0.0;
        double denominator = 0.0;

        double step = (OUTPUT_MAX - OUTPUT_MIN) / DEFUZZ_POINTS;

        for (int i = 0; i <= DEFUZZ_POINTS; i++) {
            double x = OUTPUT_MIN + i * step;

            // Compute the aggregated membership at this x value.
            // For each output set, clip (min) the MF value at the activation strength.
            // Then take the MAX across all output sets (Mamdani aggregation).
            double aggregatedMembership = 0.0;

            for (Map.Entry<String, MembershipFunction> entry : priorityMFs.entrySet()) {
                String setName = entry.getKey();
                MembershipFunction mf = entry.getValue();

                double activationStrength = outputActivations.getOrDefault(setName, 0.0);

                if (activationStrength > 0.0) {
                    // Clip: membership at x cannot exceed the rule's activation strength
                    double clipped = Math.min(mf.getMembership(x), activationStrength);
                    // Aggregate: take the max across all output sets
                    aggregatedMembership = Math.max(aggregatedMembership, clipped);
                }
            }

            // Accumulate for centroid calculation
            numerator += x * aggregatedMembership;
            denominator += aggregatedMembership;
        }

        // If no rules fired (denominator = 0), return minimum priority
        if (denominator == 0.0) {
            log.warn("No fuzzy rules fired — returning minimum priority 0.0");
            return 0.0;
        }

        double score = numerator / denominator;
        // Clamp to [0, 100] to handle floating point edge cases
        return Math.max(0.0, Math.min(100.0, score));
    }
}
