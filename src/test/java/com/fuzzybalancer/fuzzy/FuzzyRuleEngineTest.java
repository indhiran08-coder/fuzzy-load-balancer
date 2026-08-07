package com.fuzzybalancer.fuzzy;

import com.fuzzybalancer.fuzzy.engine.FuzzyEvaluationResult;
import com.fuzzybalancer.fuzzy.engine.FuzzyRuleEngine;
import com.fuzzybalancer.fuzzy.engine.FuzzyVariables;
import com.fuzzybalancer.fuzzy.rules.FuzzyRuleBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * FuzzyRuleEngineTest — Unit tests for the complete Fuzzy Inference pipeline.
 *
 * Tests verify that:
 *   1. Scores are always in [0, 100]
 *   2. A server with excellent metrics scores higher than one with poor metrics
 *   3. The engine handles edge cases (all zeros, all max values)
 *   4. Priority labels match expected zones
 *
 * These tests are PURE UNIT TESTS — no Spring context, no DB.
 * FuzzyVariables, FuzzyRuleBase, FuzzyRuleEngine are instantiated directly.
 *
 * This is intentional: the fuzzy engine is pure business logic
 * with zero infrastructure dependencies. Unit testing it in isolation
 * is faster and pinpoints failures precisely.
 */
@DisplayName("Fuzzy Rule Engine — Inference Pipeline")
class FuzzyRuleEngineTest {

    private FuzzyRuleEngine engine;

    /**
     * @BeforeEach — Runs before EACH test method.
     * Creates a fresh FuzzyRuleEngine for every test to avoid state leakage.
     */
    @BeforeEach
    void setUp() {
        FuzzyVariables vars = new FuzzyVariables();
        FuzzyRuleBase ruleBase = new FuzzyRuleBase();
        engine = new FuzzyRuleEngine(vars, ruleBase);
    }

    // =========================================================================
    // SCORE RANGE VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Score is always in [0, 100] for minimal load (ideal server)")
    void evaluate_idealServer_scoreInRange() {
        // CPU=5%, RAM=10%, Requests=2, ResponseTime=50ms → expect VERY HIGH priority
        FuzzyEvaluationResult result = engine.evaluate(5.0, 10.0, 2.0, 50.0);

        assertThat(result.getCrispScore())
            .isBetween(0.0, 100.0);
        assertThat(result.getCrispScore())
            .as("Ideal server should score above 70")
            .isGreaterThan(70.0);
    }

    @Test
    @DisplayName("Score is always in [0, 100] for maximum load (worst server)")
    void evaluate_worstServer_scoreInRange() {
        // CPU=98%, RAM=97%, Requests=199, ResponseTime=4900ms → expect VERY LOW priority
        FuzzyEvaluationResult result = engine.evaluate(98.0, 97.0, 199.0, 4900.0);

        assertThat(result.getCrispScore())
            .isBetween(0.0, 100.0);
        assertThat(result.getCrispScore())
            .as("Critically overloaded server should score below 30")
            .isLessThan(30.0);
    }

    @Test
    @DisplayName("Score is always in [0, 100] for all-zero inputs")
    void evaluate_allZeroInputs_scoreInRange() {
        FuzzyEvaluationResult result = engine.evaluate(0.0, 0.0, 0.0, 0.0);
        assertThat(result.getCrispScore()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Score is always in [0, 100] for boundary maximum inputs")
    void evaluate_maxInputs_scoreInRange() {
        FuzzyEvaluationResult result = engine.evaluate(100.0, 100.0, 200.0, 5000.0);
        assertThat(result.getCrispScore()).isBetween(0.0, 100.0);
    }

    // =========================================================================
    // ORDERING TESTS — Ensures fuzzy logic correctly ranks servers
    // =========================================================================

    @Test
    @DisplayName("Excellent server scores higher than average server")
    void evaluate_excellentVsAverage_excellentWins() {
        // Excellent: low CPU, low RAM, low requests, fast response
        FuzzyEvaluationResult excellent = engine.evaluate(10.0, 15.0, 5.0, 80.0);

        // Average: medium CPU, medium RAM, medium requests, normal response
        FuzzyEvaluationResult average = engine.evaluate(50.0, 55.0, 45.0, 500.0);

        assertThat(excellent.getCrispScore())
            .as("Excellent server should score higher than average server")
            .isGreaterThan(average.getCrispScore());
    }

    @Test
    @DisplayName("Average server scores higher than critical server")
    void evaluate_averageVsCritical_averageWins() {
        FuzzyEvaluationResult average = engine.evaluate(50.0, 55.0, 45.0, 500.0);
        FuzzyEvaluationResult critical = engine.evaluate(92.0, 91.0, 165.0, 4200.0);

        assertThat(average.getCrispScore())
            .as("Average server should score higher than critically overloaded server")
            .isGreaterThan(critical.getCrispScore());
    }

    @Test
    @DisplayName("Higher CPU usage results in lower score (all else equal)")
    void evaluate_higherCpu_lowerScore() {
        // Only vary CPU
        FuzzyEvaluationResult lowCpu = engine.evaluate(10.0, 30.0, 10.0, 100.0);
        FuzzyEvaluationResult highCpu = engine.evaluate(85.0, 30.0, 10.0, 100.0);

        assertThat(lowCpu.getCrispScore())
            .as("Server with 10% CPU should score higher than one with 85% CPU")
            .isGreaterThan(highCpu.getCrispScore());
    }

    @Test
    @DisplayName("Faster response time results in higher score (all else equal)")
    void evaluate_fasterResponse_higherScore() {
        // Only vary response time
        FuzzyEvaluationResult fast = engine.evaluate(40.0, 40.0, 20.0, 80.0);
        FuzzyEvaluationResult slow = engine.evaluate(40.0, 40.0, 20.0, 3000.0);

        assertThat(fast.getCrispScore())
            .as("Server with 80ms response should score higher than one with 3000ms")
            .isGreaterThan(slow.getCrispScore());
    }

    // =========================================================================
    // PRIORITY LABEL TESTS
    // =========================================================================

    @Test
    @DisplayName("Priority label is VERY_HIGH for idle server")
    void evaluate_idleServer_priorityLabelIsVeryHigh() {
        FuzzyEvaluationResult result = engine.evaluate(5.0, 8.0, 2.0, 60.0);
        assertThat(result.getPriorityLabel()).isEqualTo("VERY_HIGH");
    }

    @Test
    @DisplayName("Priority label is VERY_LOW for critically loaded server")
    void evaluate_criticalServer_priorityLabelIsVeryLow() {
        FuzzyEvaluationResult result = engine.evaluate(95.0, 93.0, 185.0, 4800.0);
        assertThat(result.getPriorityLabel()).isEqualTo("VERY_LOW");
    }

    @ParameterizedTest(name = "CPU={0}, RAM={1}, Req={2}, RT={3}ms → score > {4}")
    @CsvSource({
        "5,   5,   2,   50,   75",   // Near-idle → very high
        "20,  25,  10,  100,  60",   // Light load → high
        "50,  50,  40,  500,  30",   // Medium load → medium
        "85,  80,  120, 2000, 5",    // Heavy load → low/very low
    })
    @DisplayName("Parametrized score ordering across load levels")
    void evaluate_parametrized_scoresDecreaseWithLoad(
        double cpu, double ram, double req, double rt, double minExpectedScore
    ) {
        FuzzyEvaluationResult result = engine.evaluate(cpu, ram, req, rt);
        assertThat(result.getCrispScore())
            .as("Score should be > %s for inputs CPU=%s RAM=%s Req=%s RT=%s", minExpectedScore, cpu, ram, req, rt)
            .isGreaterThan(minExpectedScore);
    }

    // =========================================================================
    // EVALUATION RESULT STRUCTURE TESTS
    // =========================================================================

    @Test
    @DisplayName("Evaluation result contains all membership maps")
    void evaluate_result_hasAllMembershipMaps() {
        FuzzyEvaluationResult result = engine.evaluate(40.0, 50.0, 20.0, 200.0);

        assertThat(result.getCpuMemberships()).isNotNull().isNotEmpty();
        assertThat(result.getRamMemberships()).isNotNull().isNotEmpty();
        assertThat(result.getRequestMemberships()).isNotNull().isNotEmpty();
        assertThat(result.getResponseMemberships()).isNotNull().isNotEmpty();
        assertThat(result.getOutputActivations()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("CPU memberships contain LOW, MEDIUM, HIGH keys")
    void evaluate_cpuMemberships_containExpectedKeys() {
        FuzzyEvaluationResult result = engine.evaluate(50.0, 50.0, 30.0, 300.0);

        assertThat(result.getCpuMemberships())
            .containsKeys("LOW", "MEDIUM", "HIGH");
    }

    @Test
    @DisplayName("Response memberships contain FAST, NORMAL, SLOW keys")
    void evaluate_responseMemberships_containExpectedKeys() {
        FuzzyEvaluationResult result = engine.evaluate(30.0, 30.0, 15.0, 400.0);

        assertThat(result.getResponseMemberships())
            .containsKeys("FAST", "NORMAL", "SLOW");
    }

    @Test
    @DisplayName("All membership values are in [0, 1]")
    void evaluate_allMemberships_inValidRange() {
        FuzzyEvaluationResult result = engine.evaluate(45.0, 60.0, 35.0, 350.0);

        result.getCpuMemberships().values()
            .forEach(v -> assertThat(v).isBetween(0.0, 1.0));
        result.getRamMemberships().values()
            .forEach(v -> assertThat(v).isBetween(0.0, 1.0));
        result.getRequestMemberships().values()
            .forEach(v -> assertThat(v).isBetween(0.0, 1.0));
        result.getResponseMemberships().values()
            .forEach(v -> assertThat(v).isBetween(0.0, 1.0));
    }

    @Test
    @DisplayName("Rules evaluated count equals rule base size")
    void evaluate_rulesEvaluated_matchesRuleBaseSize() {
        FuzzyRuleBase ruleBase = new FuzzyRuleBase();
        FuzzyEvaluationResult result = engine.evaluate(40.0, 40.0, 20.0, 200.0);

        assertThat(result.getRulesEvaluated())
            .as("Number of evaluated rules should match rule base size")
            .isEqualTo(ruleBase.getRuleCount());
    }

    @Test
    @DisplayName("getDominantCpuSet() returns expected set for low CPU")
    void evaluate_getDominantCpuSet_lowCpu() {
        // CPU = 5% → should be in LOW set
        FuzzyEvaluationResult result = engine.evaluate(5.0, 50.0, 20.0, 200.0);
        assertThat(result.getDominantCpuSet()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("getDominantResponseSet() returns FAST for low response time")
    void evaluate_getDominantResponseSet_fastResponse() {
        // RT = 50ms → should be FAST
        FuzzyEvaluationResult result = engine.evaluate(40.0, 40.0, 20.0, 50.0);
        assertThat(result.getDominantResponseSet()).isEqualTo("FAST");
    }
}
