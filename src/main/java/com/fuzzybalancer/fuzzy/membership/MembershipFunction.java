package com.fuzzybalancer.fuzzy.membership;

/**
 * MembershipFunction — Interface for all fuzzy membership functions.
 *
 * A membership function maps a crisp (real-valued) input to a fuzzy
 * membership degree in the range [0.0, 1.0]:
 *   - 0.0 = the input does NOT belong to this fuzzy set
 *   - 1.0 = the input FULLY belongs to this fuzzy set
 *   - 0.0–1.0 = partial membership (the essence of fuzzy logic)
 *
 * Why fuzzy membership instead of crisp sets?
 *   In classical (crisp) logic: CPU = 55% is EITHER Low OR Medium.
 *   In fuzzy logic: CPU = 55% can be 30% Low AND 70% Medium simultaneously.
 *   This captures the natural imprecision of real-world metrics.
 *
 * Implementations:
 *   - TriangularMF  — triangle-shaped membership (peak at one value)
 *   - TrapezoidalMF — trapezoid-shaped (flat top, sloped sides)
 *
 * Mathematical representation:
 *   For a triangular MF with vertices (a, b, c):
 *   μ(x) = 0           if x ≤ a
 *           (x-a)/(b-a) if a < x ≤ b   (rising slope)
 *           (c-x)/(c-b) if b < x ≤ c   (falling slope)
 *           0           if x > c
 */
public interface MembershipFunction {

    /**
     * getMembership() — Computes the degree of membership for a crisp input.
     *
     * @param x The crisp input value (e.g., CPU = 65.0%)
     * @return Membership degree in [0.0, 1.0]
     */
    double getMembership(double x);

    /**
     * getName() — Returns the name of this fuzzy set (e.g., "Low", "Medium").
     * Used for logging and debugging.
     */
    String getName();
}
