package com.fuzzybalancer.fuzzy.rules;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.fuzzybalancer.fuzzy.rules.FuzzyRule.ResponseSet.*;
import static com.fuzzybalancer.fuzzy.rules.FuzzyRule.PrioritySet.VERY_HIGH;
import static com.fuzzybalancer.fuzzy.rules.FuzzyRule.PrioritySet.VERY_LOW;
import com.fuzzybalancer.fuzzy.rules.FuzzyRule.CpuSet;
import com.fuzzybalancer.fuzzy.rules.FuzzyRule.RamSet;
import com.fuzzybalancer.fuzzy.rules.FuzzyRule.RequestsSet;
import com.fuzzybalancer.fuzzy.rules.FuzzyRule.PrioritySet;

/**
 * FuzzyRuleBase — The complete rule base for the load balancer fuzzy system.
 *
 * Contains 25 expert-defined fuzzy rules that capture the intuition:
 *   "A server is a BETTER candidate (higher priority) when it has:
 *    - Lower CPU usage
 *    - Lower RAM usage
 *    - Fewer active requests
 *    - Faster response times"
 *
 * Rule Design Philosophy:
 *   Rules are designed to cover all significant input combinations.
 *   Not all 3×3×3×3 = 81 combinations are needed — many combinations
 *   lead to the same logical conclusion and can be grouped.
 *
 * Rule Categories:
 *   1. Optimal conditions   → Very High priority (rules 1–5)
 *   2. Good conditions      → High priority (rules 6–10)
 *   3. Average conditions   → Medium priority (rules 11–16)
 *   4. Poor conditions      → Low priority (rules 17–21)
 *   5. Critical conditions  → Very Low priority (rules 22–25)
 *
 * @Component — Spring singleton — the rule base is created once and reused.
 */
@Component
public class FuzzyRuleBase {

    private final List<FuzzyRule> rules;

    public FuzzyRuleBase() {
        rules = new ArrayList<>();
        initializeRules();
    }

    /**
     * initializeRules() — Defines all 25 fuzzy inference rules.
     *
     * Each rule is documented with:
     *   - The reasoning behind the rule
     *   - Real-world scenario where it fires
     */
    private void initializeRules() {

        // =============================================================
        // CATEGORY 1: OPTIMAL CONDITIONS → VERY HIGH PRIORITY (Rules 1–5)
        // Server is in excellent state. Should receive maximum traffic.
        // =============================================================

        /*
         * Rule 1 — Idle server, fastest response
         * Scenario: Server just came online with minimal load.
         * CPU is low (e.g., 10%), RAM is low (e.g., 20%),
         * handling very few requests (e.g., 5), responding fast (<100ms).
         * This is the ideal server — route all traffic here first.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(1)
            .description("Idle server with minimal load and fast response → route here first")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.LOW).responseCondition(FAST)
            .priorityOutput(VERY_HIGH).build());

        /*
         * Rule 2 — Light load, adequate memory, fast response
         * Scenario: Server has some CPU usage but still very responsive.
         * RAM is medium (50%), meaning memory isn't a bottleneck yet.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(2)
            .description("Low CPU, medium RAM, few requests, fast response → excellent candidate")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.LOW).responseCondition(FAST)
            .priorityOutput(VERY_HIGH).build());

        /*
         * Rule 3 — Fresh server with moderate concurrent load
         * Scenario: CPU and RAM are low but requests are picking up.
         * Still responds fast, so it should continue receiving traffic.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(3)
            .description("Low CPU and RAM, moderate requests, fast response → strong candidate")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.MEDIUM).responseCondition(FAST)
            .priorityOutput(VERY_HIGH).build());

        /*
         * Rule 4 — Moderate CPU but all other metrics are excellent
         * Scenario: Some CPU usage (e.g., 40%) but RAM is low,
         * requests are few, and response is fast. Still very desirable.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(4)
            .description("Moderate CPU, low RAM, low requests, fast response → very good candidate")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.LOW).responseCondition(FAST)
            .priorityOutput(VERY_HIGH).build());

        /*
         * Rule 5 — All metrics low with normal response
         * Scenario: CPU, RAM, requests all low, response time is normal
         * (not lightning fast, but still well within limits).
         */
        rules.add(FuzzyRule.builder()
            .ruleId(5)
            .description("All metrics low, normal response → high quality candidate")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.LOW).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.HIGH).build());

        // =============================================================
        // CATEGORY 2: GOOD CONDITIONS → HIGH PRIORITY (Rules 6–10)
        // Server is in good shape. Preferred over average servers.
        // =============================================================

        /*
         * Rule 6 — Average everything, but fast response
         * Scenario: CPU ~50%, RAM ~60%, medium requests, but response
         * is still fast. Fast response is a strong positive signal.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(6)
            .description("Medium CPU and RAM, moderate requests, fast response → good choice")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.MEDIUM).responseCondition(FAST)
            .priorityOutput(PrioritySet.HIGH).build());

        /*
         * Rule 7 — Low CPU with moderate RAM and requests
         * Scenario: CPU is free, RAM is half-used, handling some requests.
         * Good candidate for more traffic.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(7)
            .description("Low CPU, medium RAM, medium requests, normal response → good candidate")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.MEDIUM).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.HIGH).build());

        /*
         * Rule 8 — Moderate load across all metrics
         * Scenario: Standard operating conditions for a healthy server.
         * Not excellent but clearly above average.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(8)
            .description("Low CPU, low RAM, high requests, fast response → still a good choice")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.HIGH).responseCondition(FAST)
            .priorityOutput(PrioritySet.HIGH).build());

        /*
         * Rule 9 — Medium CPU, low RAM, normal response
         * Scenario: CPU is warming up but memory is plentiful.
         * Can absorb more requests before becoming a problem.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(9)
            .description("Medium CPU, low RAM, low requests, normal response → above average")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.LOW).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.HIGH).build());

        /*
         * Rule 10 — Low CPU, medium everything, normal response
         */
        rules.add(FuzzyRule.builder()
            .ruleId(10)
            .description("Low CPU, medium RAM, medium requests, normal response → preferred")
            .cpuCondition(CpuSet.LOW).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.MEDIUM).responseCondition(FAST)
            .priorityOutput(PrioritySet.HIGH).build());

        // =============================================================
        // CATEGORY 3: AVERAGE CONDITIONS → MEDIUM PRIORITY (Rules 11–16)
        // Server is functional but not ideal. Acceptable when better options unavailable.
        // =============================================================

        /*
         * Rule 11 — Everything in Medium range
         * Scenario: A server handling moderate load across all metrics.
         * Not a standout choice but perfectly viable.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(11)
            .description("All metrics medium, normal response → average server, acceptable choice")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.MEDIUM).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.MEDIUM).build());

        /*
         * Rule 12 — High CPU but everything else is fine
         * Scenario: CPU is pegged (e.g., 75%) but RAM is available and
         * response is still acceptable. Worth trying if others are worse.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(12)
            .description("High CPU but low RAM and low requests → use with caution")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.LOW).responseCondition(FAST)
            .priorityOutput(PrioritySet.MEDIUM).build());

        /*
         * Rule 13 — Moderate load, slow response beginning
         * Scenario: Medium metrics but response time is starting to slow.
         * Could be temporary (GC pause) or structural. Acceptable for now.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(13)
            .description("Medium metrics but slowing response → still usable, monitor closely")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.LOW).responseCondition(SLOW)
            .priorityOutput(PrioritySet.MEDIUM).build());

        /*
         * Rule 14 — Low requests but high resource usage
         * Scenario: Server is idle request-wise but burning CPU/RAM
         * (maybe a background job). Response is fast because queue is empty.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(14)
            .description("High CPU and RAM but low requests and fast response → background work suspected")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.LOW).responseCondition(FAST)
            .priorityOutput(PrioritySet.MEDIUM).build());

        /*
         * Rule 15 — Medium everything, slow response
         * Scenario: Load is moderate but server is struggling. Possibly
         * external dependency (DB) is slow. Medium priority as fallback.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(15)
            .description("Medium CPU, high RAM, medium requests, normal response → below average")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.MEDIUM).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.MEDIUM).build());

        /*
         * Rule 16 — High requests, medium resources, fast response
         * Scenario: Server is handling many requests efficiently (maybe
         * stateless, cached). Risky but currently holding up.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(16)
            .description("Medium CPU, medium RAM, high requests, fast response → under pressure but coping")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.HIGH).responseCondition(FAST)
            .priorityOutput(PrioritySet.MEDIUM).build());

        // =============================================================
        // CATEGORY 4: POOR CONDITIONS → LOW PRIORITY (Rules 17–21)
        // Server is struggling. Only use if no better option exists.
        // =============================================================

        /*
         * Rule 17 — High load, slow response
         * Scenario: Server is overwhelmed. High CPU, high requests,
         * and responses are slowing. Should be deprioritized.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(17)
            .description("High CPU, medium RAM, high requests, slow response → avoid if possible")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(PrioritySet.LOW).build());

        /*
         * Rule 18 — RAM pressure with slow response
         * Scenario: Memory is running out (swapping to disk?).
         * Response time is degrading as a result.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(18)
            .description("Medium CPU, high RAM, medium requests, slow response → memory pressure")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.MEDIUM).responseCondition(SLOW)
            .priorityOutput(PrioritySet.LOW).build());

        /*
         * Rule 19 — All resources high, normal response barely holding
         * Scenario: Every metric is in the High zone. The server is
         * at its limits. Response is still normal but may deteriorate.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(19)
            .description("High CPU, high RAM, high requests, normal response → at capacity limit")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.HIGH).responseCondition(NORMAL)
            .priorityOutput(PrioritySet.LOW).build());

        /*
         * Rule 20 — High CPU and RAM, moderate requests, slow
         */
        rules.add(FuzzyRule.builder()
            .ruleId(20)
            .description("High CPU, high RAM, low requests, slow response → resource-starved")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.LOW).responseCondition(SLOW)
            .priorityOutput(PrioritySet.LOW).build());

        /*
         * Rule 21 — Medium CPU, high everything else, slow response
         */
        rules.add(FuzzyRule.builder()
            .ruleId(21)
            .description("Medium CPU, high RAM, high requests, slow response → avoid")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(PrioritySet.LOW).build());

        // =============================================================
        // CATEGORY 5: CRITICAL CONDITIONS → VERY LOW PRIORITY (Rules 22–25)
        // Server is in critical state. Should not receive any new traffic.
        // =============================================================

        /*
         * Rule 22 — All metrics at maximum
         * Scenario: CPU at 95%, RAM at 95%, handling 150+ concurrent
         * requests, response time >2 seconds. Server is collapsing.
         * Under NO circumstances should this server receive more traffic.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(22)
            .description("All metrics HIGH and slow response → CRITICAL: do not route here")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(VERY_LOW).build());

        /*
         * Rule 23 — CPU critical, response slow
         * Scenario: CPU is maxed out and responses are slow.
         * Adding more requests will only make things worse.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(23)
            .description("High CPU, high requests, slow response → CPU-bound server, avoid")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.MEDIUM).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(VERY_LOW).build());

        /*
         * Rule 24 — RAM critical, response slow
         * Scenario: Server is likely swapping to disk (OOM condition imminent).
         * Response times are terrible. Emergency: do not send traffic.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(24)
            .description("High RAM, high requests, slow response → memory-bound critical state")
            .cpuCondition(CpuSet.MEDIUM).ramCondition(RamSet.HIGH).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(VERY_LOW).build());

        /*
         * Rule 25 — High CPU, medium RAM, high requests, slow response
         * Scenario: CPU is overwhelmed with requests backing up.
         * Response time has collapsed. Last resort only.
         */
        rules.add(FuzzyRule.builder()
            .ruleId(25)
            .description("High CPU and requests, slow response → severely overloaded")
            .cpuCondition(CpuSet.HIGH).ramCondition(RamSet.LOW).requestsCondition(RequestsSet.HIGH).responseCondition(SLOW)
            .priorityOutput(VERY_LOW).build());
    }

    /**
     * getRules() — Returns the complete rule base.
     * Called by FuzzyRuleEngine during evaluation.
     *
     * @return Unmodifiable list of all 25 fuzzy rules
     */
    public List<FuzzyRule> getRules() {
        return List.copyOf(rules);
    }

    /**
     * getRuleCount() — Returns the total number of rules.
     */
    public int getRuleCount() {
        return rules.size();
    }
}
