package com.fuzzybalancer.fuzzy.engine;

import com.fuzzybalancer.fuzzy.membership.MembershipFunction;
import com.fuzzybalancer.fuzzy.membership.TrapezoidalMF;
import com.fuzzybalancer.fuzzy.membership.TriangularMF;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * FuzzyVariables — Defines all fuzzy input and output variables with their
 * membership functions.
 *
 * This class is the "knowledge base" of the fuzzy system — it encodes
 * human expert knowledge about what "Low CPU", "Fast Response", etc. mean
 * in terms of numerical ranges.
 *
 * Input Variables:
 *   1. CPU Usage       (0–100%)
 *   2. RAM Usage       (0–100%)
 *   3. Active Requests (0–200)
 *   4. Response Time   (0–5000ms)
 *
 * Output Variable:
 *   Server Priority (0–100 score)
 *
 * Membership Function Selection:
 *   - "Low" and "High" sets use Trapezoidal MFs:
 *     Open-ended on one side (flat top extends to the boundary)
 *   - "Medium" sets use Triangular MFs:
 *     Has a clear peak in the middle
 *
 * @Component — Marks this as a Spring-managed singleton bean.
 *   Injected into FuzzyRuleEngine to access membership functions.
 */
@Component
@Slf4j
public class FuzzyVariables {

    // =========================================================================
    // CPU USAGE MEMBERSHIP FUNCTIONS (0–100%)
    // =========================================================================

    /**
     * CPU "Low" — Server is lightly loaded.
     * TrapezoidalMF(0, 0, 20, 40):
     *   0–20%  → μ = 1.0 (definitely Low)
     *   20-40% → μ = 0.0–1.0 (transitioning)
     *   >40%   → μ = 0.0 (not Low)
     */
    public final MembershipFunction cpuLow = new TrapezoidalMF("Low", 0, 0, 20, 40);

    /**
     * CPU "Medium" — Server is moderately loaded.
     * TriangularMF(20, 50, 80):
     *   20% → μ = 0.0, 50% → μ = 1.0, 80% → μ = 0.0
     */
    public final MembershipFunction cpuMedium = new TriangularMF("Medium", 20, 50, 80);

    /**
     * CPU "High" — Server is heavily loaded.
     * TrapezoidalMF(60, 80, 100, 100):
     *   <60%    → μ = 0.0 (not High)
     *   60–80%  → μ = 0.0–1.0 (becoming High)
     *   80–100% → μ = 1.0 (definitely High)
     */
    public final MembershipFunction cpuHigh = new TrapezoidalMF("High", 60, 80, 100, 100);

    // =========================================================================
    // RAM USAGE MEMBERSHIP FUNCTIONS (0–100%)
    // =========================================================================

    /**
     * RAM "Low" — Plenty of memory available.
     * Flat from 0–30%, transitions between 30–50%.
     */
    public final MembershipFunction ramLow = new TrapezoidalMF("Low", 0, 0, 30, 50);

    /**
     * RAM "Medium" — Normal memory usage.
     * Peak at 60% RAM usage.
     */
    public final MembershipFunction ramMedium = new TriangularMF("Medium", 30, 60, 85);

    /**
     * RAM "High" — Memory pressure is high.
     * Flat from 80–100%.
     */
    public final MembershipFunction ramHigh = new TrapezoidalMF("High", 65, 85, 100, 100);

    // =========================================================================
    // ACTIVE REQUESTS MEMBERSHIP FUNCTIONS (0–200 concurrent requests)
    // =========================================================================

    /**
     * Requests "Low" — Server is serving few concurrent requests.
     * 0–15 requests → definitely Low.
     */
    public final MembershipFunction requestsLow = new TrapezoidalMF("Low", 0, 0, 15, 30);

    /**
     * Requests "Medium" — Normal concurrent load.
     * Peak at 50 concurrent requests.
     */
    public final MembershipFunction requestsMedium = new TriangularMF("Medium", 15, 50, 100);

    /**
     * Requests "High" — Server is handling many concurrent requests.
     * Fully High at 100+ requests.
     */
    public final MembershipFunction requestsHigh = new TrapezoidalMF("High", 70, 100, 200, 200);

    // =========================================================================
    // RESPONSE TIME MEMBERSHIP FUNCTIONS (0–5000ms)
    // =========================================================================

    /**
     * Response "Fast" — Server responds quickly.
     * ≤100ms → definitely Fast. Transitions up to 300ms.
     */
    public final MembershipFunction responseFast = new TrapezoidalMF("Fast", 0, 0, 100, 300);

    /**
     * Response "Normal" — Acceptable response time.
     * Peak at 500ms (typical for a loaded microservice).
     */
    public final MembershipFunction responseNormal = new TriangularMF("Normal", 200, 500, 1000);

    /**
     * Response "Slow" — Server is responding slowly.
     * Fully Slow at 1500ms+. Clients may be timing out.
     */
    public final MembershipFunction responseSlow = new TrapezoidalMF("Slow", 800, 1500, 5000, 5000);

    // =========================================================================
    // OUTPUT: SERVER PRIORITY MEMBERSHIP FUNCTIONS (0–100 score)
    // =========================================================================
    // These are used in defuzzification (Centroid of Gravity method)
    // Each output set has a representative centroid value.

    /** Priority "Very Low" — Server should NOT receive traffic. Centroid ≈ 10 */
    public final MembershipFunction priorityVeryLow = new TrapezoidalMF("VeryLow", 0, 0, 10, 20);

    /** Priority "Low" — Server is under-preferred. Centroid ≈ 30 */
    public final MembershipFunction priorityLow = new TriangularMF("Low", 10, 25, 40);

    /** Priority "Medium" — Server is acceptable. Centroid ≈ 50 */
    public final MembershipFunction priorityMedium = new TriangularMF("Medium", 35, 50, 65);

    /** Priority "High" — Server is preferred. Centroid ≈ 75 */
    public final MembershipFunction priorityHigh = new TriangularMF("High", 60, 75, 90);

    /** Priority "Very High" — Server is the best choice. Centroid ≈ 95 */
    public final MembershipFunction priorityVeryHigh = new TrapezoidalMF("VeryHigh", 80, 90, 100, 100);

    // =========================================================================
    // ACCESSOR MAPS (for dynamic rule engine access)
    // =========================================================================

    /**
     * getCpuMemberships() — Returns all CPU membership functions as a map.
     * Allows the rule engine to iterate over all sets dynamically.
     */
    public Map<String, MembershipFunction> getCpuMemberships() {
        Map<String, MembershipFunction> map = new HashMap<>();
        map.put("LOW", cpuLow);
        map.put("MEDIUM", cpuMedium);
        map.put("HIGH", cpuHigh);
        return map;
    }

    public Map<String, MembershipFunction> getRamMemberships() {
        Map<String, MembershipFunction> map = new HashMap<>();
        map.put("LOW", ramLow);
        map.put("MEDIUM", ramMedium);
        map.put("HIGH", ramHigh);
        return map;
    }

    public Map<String, MembershipFunction> getRequestsMemberships() {
        Map<String, MembershipFunction> map = new HashMap<>();
        map.put("LOW", requestsLow);
        map.put("MEDIUM", requestsMedium);
        map.put("HIGH", requestsHigh);
        return map;
    }

    public Map<String, MembershipFunction> getResponseMemberships() {
        Map<String, MembershipFunction> map = new HashMap<>();
        map.put("FAST", responseFast);
        map.put("NORMAL", responseNormal);
        map.put("SLOW", responseSlow);
        return map;
    }

    public Map<String, MembershipFunction> getPriorityMemberships() {
        Map<String, MembershipFunction> map = new HashMap<>();
        map.put("VERY_LOW", priorityVeryLow);
        map.put("LOW", priorityLow);
        map.put("MEDIUM", priorityMedium);
        map.put("HIGH", priorityHigh);
        map.put("VERY_HIGH", priorityVeryHigh);
        return map;
    }
}
